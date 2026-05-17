package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentClassificationResult.Keywords;
import org.example.shield.ai.dto.IntentClassificationResult.MatchedNode;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.infrastructure.OpenAiClassifyClient;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IntentClassificationService {

    private final CohereService cohereService;
    private final ObjectMapper objectMapper;
    private final String slimOntologyJson;
    private final ResourceLoader resourceLoader;
    private final int contextWindowMessages;
    private final RagMetrics ragMetrics;
    private final OpenAiClassifyClient openAiClassifyClient;
    private final String classifyProvider;

    private String intentClassifierPromptTemplate;
    private JsonNode slimOntologyRoot;
    private boolean slimOntologyParseAttempted;
    private final Map<String, String> scopedOntologyJsonCache = new ConcurrentHashMap<>();

    /**
     * Test-friendly constructor for parser/prompt unit tests.
     */
    public IntentClassificationService(
            CohereService cohereService,
            ObjectMapper objectMapper,
            @Qualifier("slimOntologyJson") String slimOntologyJson,
            ResourceLoader resourceLoader,
            @Value("${cohere.classify.context-window-messages:4}") int contextWindowMessages,
            RagMetrics ragMetrics) {
        this(cohereService, objectMapper, slimOntologyJson, resourceLoader,
                contextWindowMessages, ragMetrics, null, "cohere");
    }

    @Autowired
    public IntentClassificationService(
            CohereService cohereService,
            ObjectMapper objectMapper,
            @Qualifier("slimOntologyJson") String slimOntologyJson,
            ResourceLoader resourceLoader,
            @Value("${cohere.classify.context-window-messages:4}") int contextWindowMessages,
            RagMetrics ragMetrics,
            OpenAiClassifyClient openAiClassifyClient,
            @Value("${ai.classify.provider:cohere}") String classifyProvider) {
        this.cohereService = cohereService;
        this.objectMapper = objectMapper;
        this.slimOntologyJson = slimOntologyJson;
        this.resourceLoader = resourceLoader;
        this.contextWindowMessages = contextWindowMessages;
        this.ragMetrics = ragMetrics;
        this.openAiClassifyClient = openAiClassifyClient;
        this.classifyProvider = classifyProvider == null ? "cohere" : classifyProvider.trim().toLowerCase();
    }

    @PostConstruct
    void loadPromptTemplate() {
        try {
            this.intentClassifierPromptTemplate = StreamUtils.copyToString(
                    resourceLoader.getResource("classpath:ai/prompts/rag/intent-classifier.md")
                            .getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load intent classifier prompt", e);
        }
        parseSlimOntologyRoot();
    }

    public IntentClassificationResult classify(List<Message> recentMessages, String domain) {
        return route(recentMessages, domain).toClassificationResult();
    }

    public IntentRouterResponse route(List<Message> recentMessages, String domain) {
        try {
            String conversationHistory = buildConversationHistory(recentMessages);
            String systemPrompt = buildSystemPrompt(domain);

            List<CohereChatRequest.Message> messages = List.of(
                    CohereChatRequest.Message.system(systemPrompt),
                    CohereChatRequest.Message.user(buildConversationPrompt(conversationHistory))
            );

            AiCallResult<String> result = ragMetrics.timeClassify(
                    () -> callConfiguredClassifier(messages));
            return parseIntentRouterResponse(result.data());

        } catch (Exception e) {
            log.warn("Intent classification failed, using fallback: domain={}, error={}", domain, e.getMessage());
            return IntentRouterResponse.fromLegacy(createFallbackResult(domain));
        }
    }

    private AiCallResult<String> callConfiguredClassifier(List<CohereChatRequest.Message> messages) {
        if ("openai".equals(classifyProvider)) {
            if (openAiClassifyClient == null) {
                throw new RuntimeException("OpenAI classify provider is selected but client is not configured");
            }
            return openAiClassifyClient.callRawJson(messages);
        }
        return cohereService.callClassify(messages);
    }

    String buildSystemPrompt() {
        return buildSystemPrompt(null);
    }

    String buildSystemPrompt(String domain) {
        return buildSystemPromptForOntology(selectOntologyJson(domain));
    }

    private String buildSystemPromptForOntology(String ontologyJson) {
        String promptTemplate = intentClassifierPromptTemplate;
        return promptTemplate.replace("{ONTOLOGY_JSON}", ontologyJson);
    }

    private String buildConversationPrompt(String conversationHistory) {
        String history = conversationHistory == null || conversationHistory.isBlank()
                ? "(no conversation)"
                : conversationHistory;
        return "Conversation:\n" + history + "\n\nReturn compact JSON only.";
    }

    private String selectOntologyJson(String domain) {
        String scopedDomain = ChecklistSlugMap.canonicalL1(domain);
        if (scopedDomain == null && domain != null && !domain.isBlank()) {
            scopedDomain = domain.trim();
        }
        if (scopedDomain == null || scopedDomain.isBlank()) {
            return slimOntologyJson;
        }

        return scopedOntologyJsonCache.computeIfAbsent(scopedDomain, this::buildScopedOntologyJson);
    }

    private String buildScopedOntologyJson(String scopedDomain) {
        JsonNode root = getSlimOntologyRoot();
        if (root == null) {
            return slimOntologyJson;
        }

        JsonNode children = root.path("c");
        if (!children.isArray()) {
            return slimOntologyJson;
        }
        for (JsonNode child : children) {
            if (matchesOntologyNode(child, scopedDomain)) {
                try {
                    ObjectNode scopedRoot = objectMapper.createObjectNode();
                    scopedRoot.put("id", root.path("id").asText("law-000"));
                    scopedRoot.put("name", root.path("name").asText("law"));
                    ArrayNode scopedChildren = scopedRoot.putArray("c");
                    scopedChildren.add(child.deepCopy());
                    return objectMapper.writeValueAsString(scopedRoot);
                } catch (Exception e) {
                    log.warn("Failed to serialize scoped ontology for domain={}, using full ontology: {}",
                            scopedDomain, e.getMessage());
                    return slimOntologyJson;
                }
            }
        }
        return slimOntologyJson;
    }

    private JsonNode getSlimOntologyRoot() {
        if (!slimOntologyParseAttempted) {
            parseSlimOntologyRoot();
        }
        return slimOntologyRoot;
    }

    private void parseSlimOntologyRoot() {
        slimOntologyParseAttempted = true;
        try {
            slimOntologyRoot = objectMapper.readTree(slimOntologyJson);
        } catch (Exception e) {
            slimOntologyRoot = null;
            log.warn("Failed to parse slim ontology once, using full ontology string without scoping: {}",
                    e.getMessage());
        }
    }

    private boolean matchesOntologyNode(JsonNode node, String domain) {
        String trimmed = domain.trim();
        return trimmed.equals(node.path("id").asText())
                || trimmed.equals(node.path("name").asText());
    }

    private String buildConversationHistory(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, messages.size() - contextWindowMessages);
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            String role = switch (msg.getRole()) {
                case USER -> "user";
                case CHATBOT -> "assistant";
                default -> null;
            };
            if (role == null) continue;
            String content = msg.getContent();
            if (content == null || content.isBlank()) continue;
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString().trim();
    }

    IntentClassificationResult parseClassificationResult(String json) {
        JsonNode versionRoot = readJson(json);
        if ("2.0".equals(versionRoot.path("schema_version").asText())) {
            return parseIntentRouterResponse(versionRoot).toClassificationResult();
        }
        try {
            JsonNode root = versionRoot;

            String schemaVersion = root.path("schema_version").asText("1.0");
            String intentSummary = root.path("intent_summary").asText("");

            List<MatchedNode> matchedNodes = parseMatchedNodes(root);
            List<String> coreKeywords = parseStringArray(
                    root.path("core_keywords").isArray()
                            ? root.path("core_keywords")
                            : root.path("keywords").path("core"));
            List<String> expandedKeywords = parseStringArray(
                    root.path("expanded_keywords").isArray()
                            ? root.path("expanded_keywords")
                            : root.path("keywords").path("expanded"));
            List<String> retrievalQueries = parseRetrievalQueries(root);

            return new IntentClassificationResult(
                    schemaVersion,
                    intentSummary,
                    matchedNodes,
                    new Keywords(coreKeywords, expandedKeywords),
                    retrievalQueries
            );

        } catch (Exception e) {
            log.error("Intent classification JSON parsing failed: {}", e.getMessage());
            throw new RuntimeException("Intent classification JSON parsing failed", e);
        }
    }

    IntentRouterResponse parseIntentRouterResponse(String json) {
        return parseIntentRouterResponse(readJson(json));
    }

    private IntentRouterResponse parseIntentRouterResponse(JsonNode root) {
        if (!"2.0".equals(root.path("schema_version").asText())) {
            return IntentRouterResponse.fromLegacy(parseClassificationResultV1(root));
        }

        IntentClassificationResult legacy = parseClassificationResultV1(root);
        return new IntentRouterResponse(
                root.path("schema_version").asText("2.0"),
                DialogueIntent.from(root.path("dialogueIntent").asText(
                        root.path("dialogue_intent").asText("PROVIDE_INFO"))),
                root.path("intentConfidence").asDouble(
                        root.path("intent_confidence").asDouble(0.0)),
                parseExtractedSlots(root.path("extractedSlots").isArray()
                        ? root.path("extractedSlots")
                        : root.path("extracted_slots")),
                parseCaseType(root.path("caseType").isObject()
                        ? root.path("caseType")
                        : root.path("case_type")),
                parseRouterRetrievalQueries(root),
                parseStringArray(root.path("correctedSlotIds").isArray()
                        ? root.path("correctedSlotIds")
                        : root.path("corrected_slot_ids")),
                root.path("topicChanged").asBoolean(root.path("topic_changed").asBoolean(false)),
                legacy
        );
    }

    private IntentClassificationResult parseClassificationResultV1(JsonNode root) {
        String schemaVersion = root.path("schema_version").asText("1.0");
        String intentSummary = root.path("intent_summary").asText(
                root.path("dialogueIntent").asText(root.path("dialogue_intent").asText("")));
        List<MatchedNode> matchedNodes = parseMatchedNodes(root);
        List<String> coreKeywords = parseStringArray(
                root.path("core_keywords").isArray()
                        ? root.path("core_keywords")
                        : root.path("keywords").path("core"));
        List<String> expandedKeywords = parseStringArray(
                root.path("expanded_keywords").isArray()
                        ? root.path("expanded_keywords")
                        : root.path("keywords").path("expanded"));
        List<String> retrievalQueries = parseRouterRetrievalQueries(root);
        return new IntentClassificationResult(
                schemaVersion,
                intentSummary,
                matchedNodes,
                new Keywords(coreKeywords, expandedKeywords),
                retrievalQueries
        );
    }

    private List<MatchedNode> parseMatchedNodes(JsonNode root) {
        List<MatchedNode> matchedNodes = new ArrayList<>();

        JsonNode compactIdsNode = root.path("matched_node_ids");
        if (compactIdsNode.isArray()) {
            compactIdsNode.forEach(n -> addMatchedNode(matchedNodes, n.asText(), "", 0.0));
            return matchedNodes;
        }

        if (root.hasNonNull("matched_node_id")) {
            addMatchedNode(matchedNodes, root.path("matched_node_id").asText(), "", 0.0);
            return matchedNodes;
        }

        JsonNode nodesNode = root.path("matched_nodes");
        if (nodesNode.isArray()) {
            for (JsonNode node : nodesNode) {
                addMatchedNode(
                        matchedNodes,
                        node.path("id").asText(),
                        node.path("name").asText(),
                        node.path("confidence").asDouble(0.0)
                );
            }
        }
        return matchedNodes;
    }

    private void addMatchedNode(List<MatchedNode> matchedNodes, String id, String name, double confidence) {
        if (id == null || id.isBlank()) {
            return;
        }
        matchedNodes.add(new MatchedNode(id, name == null ? "" : name, confidence));
    }

    private List<ExtractedSlot> parseExtractedSlots(JsonNode node) {
        List<ExtractedSlot> slots = new ArrayList<>();
        if (!node.isArray()) {
            return slots;
        }
        for (JsonNode slot : node) {
            String slotId = slot.path("slotId").asText(slot.path("slot_id").asText(""));
            if (slotId == null || slotId.isBlank()) {
                continue;
            }
            slots.add(new ExtractedSlot(
                    slotId,
                    slot.path("value").asText(null),
                    slot.path("rawText").asText(slot.path("raw_text").asText(null)),
                    slot.path("confidence").asDouble(0.0),
                    slot.path("valueType").asText(slot.path("value_type").asText("text")),
                    slot.path("needsConfirmation").asBoolean(
                            slot.path("needs_confirmation").asBoolean(false))
            ));
        }
        return slots;
    }

    private CaseTypeResult parseCaseType(JsonNode node) {
        if (node == null || !node.isObject()) {
            return CaseTypeResult.empty();
        }
        return new CaseTypeResult(
                textOrNull(node.path("l1")),
                textOrNull(node.path("l2")),
                textOrNull(node.path("l3")),
                node.path("confidence").asDouble(0.0)
        );
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String value = n.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private List<String> parseRetrievalQueries(JsonNode root) {
        List<String> retrievalQueries = new ArrayList<>();
        if (root.hasNonNull("retrieval_query")) {
            String retrievalQuery = root.path("retrieval_query").asText();
            if (!retrievalQuery.isBlank()) {
                retrievalQueries.add(retrievalQuery);
            }
        }
        JsonNode queriesNode = root.path("retrieval_queries");
        if (queriesNode.isArray()) {
            queriesNode.forEach(n -> {
                String value = n.asText();
                if (value != null && !value.isBlank() && !retrievalQueries.contains(value)) {
                    retrievalQueries.add(value);
                }
            });
        }
        return retrievalQueries;
    }

    private List<String> parseRouterRetrievalQueries(JsonNode root) {
        List<String> values = parseRetrievalQueries(root);
        JsonNode camel = root.path("retrievalQueries");
        if (camel.isArray()) {
            camel.forEach(n -> {
                String value = n.asText();
                if (value != null && !value.isBlank() && !values.contains(value)) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Intent classification JSON parsing failed: {}", e.getMessage());
            throw new RuntimeException("Intent classification JSON parsing failed", e);
        }
    }

    private IntentClassificationResult createFallbackResult(String domain) {
        return new IntentClassificationResult(
                "1.0",
                "Intent classification fallback",
                List.of(),
                new Keywords(List.of(), List.of()),
                List.of(domain != null ? domain + " legal query" : "legal consultation")
        );
    }
}
