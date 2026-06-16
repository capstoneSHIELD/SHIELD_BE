package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExperimentIntentRouteParsedResponse;
import org.example.shield.ai.dto.ExperimentIntentRouteResponse;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentClassificationResult.Keywords;
import org.example.shield.ai.dto.IntentClassificationResult.MatchedNode;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiClassificationClient;
import org.example.shield.ai.provider.ChatMessage;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IntentClassificationService {

    private final ObjectMapper objectMapper;
    private final String slimOntologyJson;
    private final ResourceLoader resourceLoader;
    private final int contextWindowMessages;
    private final RagMetrics ragMetrics;
    private final Map<String, AiClassificationClient> classificationClientsByProvider;
    private final String classifyProvider;
    private final AiRagOperationalMetrics operationalMetrics;

    private String intentClassifierPromptTemplate;
    private JsonNode slimOntologyRoot;
    private boolean slimOntologyParseAttempted;
    private final Map<String, String> scopedOntologyJsonCache = new ConcurrentHashMap<>();

    /**
     * Test-friendly constructor for parser/prompt unit tests.
     *
     * <p>P5.1 Commit 2: 첫 번째 파라미터가 {@code CohereService}에서
     * {@code List<AiClassificationClient>}로 변경됨. parse-only 테스트는 {@code List.of()} 전달.
     */
    public IntentClassificationService(
            List<AiClassificationClient> classificationClients,
            ObjectMapper objectMapper,
            @Qualifier("slimOntologyJson") String slimOntologyJson,
            ResourceLoader resourceLoader,
            @Value("${cohere.classify.context-window-messages:4}") int contextWindowMessages,
            RagMetrics ragMetrics) {
        this(classificationClients, objectMapper, slimOntologyJson, resourceLoader,
                contextWindowMessages, ragMetrics, "cohere", null);
    }

    @Autowired
    public IntentClassificationService(
            List<AiClassificationClient> classificationClients,
            ObjectMapper objectMapper,
            @Qualifier("slimOntologyJson") String slimOntologyJson,
            ResourceLoader resourceLoader,
            @Value("${cohere.classify.context-window-messages:4}") int contextWindowMessages,
            RagMetrics ragMetrics,
            @Value("${ai.classify.provider:cohere}") String classifyProvider,
            AiRagOperationalMetrics operationalMetrics) {
        this.objectMapper = objectMapper;
        this.slimOntologyJson = slimOntologyJson;
        this.resourceLoader = resourceLoader;
        this.contextWindowMessages = contextWindowMessages;
        this.ragMetrics = ragMetrics;
        this.classificationClientsByProvider = indexByProvider(classificationClients);
        this.classifyProvider = classifyProvider == null ? "cohere" : classifyProvider.trim().toLowerCase();
        this.operationalMetrics = operationalMetrics;
    }

    private static Map<String, AiClassificationClient> indexByProvider(List<AiClassificationClient> clients) {
        if (clients == null || clients.isEmpty()) {
            return Map.of();
        }
        Map<String, AiClassificationClient> map = new HashMap<>();
        for (AiClassificationClient client : clients) {
            String key = client.providerKey();
            AiClassificationClient previous = map.put(key, client);
            if (previous != null) {
                // 같은 provider key를 두 adapter가 주장하면 의도치 않은 라우팅 위험.
                log.warn("Duplicate AiClassificationClient providerKey='{}' — '{}' replaced '{}'",
                        key, client.getClass().getSimpleName(), previous.getClass().getSimpleName());
            }
        }
        return Map.copyOf(map);
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

            List<ChatMessage> messages = List.of(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user(buildConversationPrompt(conversationHistory))
            );

            AiCallResult<String> result = ragMetrics.timeClassify(
                    () -> callConfiguredClassifier(messages));
            return parseIntentRouterResponse(result.data());

        } catch (Exception e) {
            recordParseFailure("intent_router", "fallback");
            log.warn("Intent classification failed, using fallback: domain={}, error={}", domain, e.getMessage());
            return IntentRouterResponse.fromLegacy(createFallbackResult(domain));
        }
    }

    /**
     * local/test benchmark adapter 전용 경로.
     *
     * <p>운영 {@link #route(List, String)}와 달리 provider fallback을 정상 결과로 숨기지 않는다.
     * 요청 provider가 없으면 {@code config_error}, parser가 실패하면 {@code parse_failure}로
     * raw provider 응답과 함께 반환해 실험 지표에서 분리할 수 있게 한다.
     */
    public ExperimentIntentRouteResponse routeForExperiment(
            String requestedProvider,
            String mode,
            String domain,
            List<ChatMessage> conversationMessages,
            boolean includeRaw
    ) {
        String provider = requestedProvider == null || requestedProvider.isBlank()
                ? classifyProvider
                : requestedProvider.trim().toLowerCase();
        AiClassificationClient client = classificationClientsByProvider.get(provider);
        if (client == null) {
            return ExperimentIntentRouteResponse.configError(
                    provider,
                    mode,
                    domain,
                    "No AiClassificationClient registered for provider: " + provider
            );
        }

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildSystemPrompt(domain)),
                ChatMessage.user(buildConversationPrompt(buildExperimentConversationHistory(conversationMessages)))
        );

        AiCallResult<String> result;
        try {
            result = ragMetrics.timeClassify(() -> client.classify(messages));
        } catch (Exception e) {
            return new ExperimentIntentRouteResponse(
                    provider,
                    provider,
                    mode,
                    domain,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    "upstream_error",
                    e.getMessage()
            );
        }

        try {
            IntentRouterResponse parsed = parseIntentRouterResponse(result.data());
            return new ExperimentIntentRouteResponse(
                    provider,
                    provider,
                    mode,
                    domain,
                    result.responseId(),
                    includeRaw ? result.data() : null,
                    ExperimentIntentRouteParsedResponse.from(parsed),
                    result.tokensInput(),
                    result.tokensOutput(),
                    result.latencyMs(),
                    true,
                    true,
                    false,
                    null,
                    null
            );
        } catch (Exception e) {
            return new ExperimentIntentRouteResponse(
                    provider,
                    provider,
                    mode,
                    domain,
                    result.responseId(),
                    includeRaw ? result.data() : null,
                    null,
                    result.tokensInput(),
                    result.tokensOutput(),
                    result.latencyMs(),
                    false,
                    false,
                    false,
                    "parse_failure",
                    e.getMessage()
            );
        }
    }

    public Map<String, Boolean> availableExperimentProviders(Collection<String> providers) {
        Set<String> requested = new LinkedHashSet<>();
        if (providers != null) {
            providers.stream()
                    .filter(provider -> provider != null && !provider.isBlank())
                    .map(provider -> provider.trim().toLowerCase())
                    .forEach(requested::add);
        }
        if (requested.isEmpty()) {
            requested.addAll(classificationClientsByProvider.keySet());
        }
        Map<String, Boolean> availability = new HashMap<>();
        for (String provider : requested) {
            availability.put(provider, classificationClientsByProvider.containsKey(provider));
        }
        return availability;
    }

    private AiCallResult<String> callConfiguredClassifier(List<ChatMessage> messages) {
        AiClassificationClient client = classificationClientsByProvider.get(classifyProvider);
        if (client == null) {
            // Fallback to Cohere if requested provider unavailable (e.g. OpenAI key missing).
            client = classificationClientsByProvider.get("cohere");
            if (client == null) {
                throw new RuntimeException("No AiClassificationClient available (requested provider: "
                        + classifyProvider + ")");
            }
            log.warn("Requested classify provider '{}' not registered, falling back to cohere", classifyProvider);
        }
        return client.classify(messages);
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

    private String buildExperimentConversationHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, messages.size() - contextWindowMessages);
        for (int i = start; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            String role = switch (message.role()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> null;
            };
            if (role != null) {
                sb.append(role).append(": ").append(message.content()).append("\n");
            }
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
            recordParseFailure("intent_router", "failure");
            throw new RuntimeException("Intent classification JSON parsing failed", e);
        }
    }

    private void recordParseFailure(String schema, String outcome) {
        if (operationalMetrics != null) {
            operationalMetrics.recordStructuredOutputParse(classifyProvider, schema, outcome);
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
