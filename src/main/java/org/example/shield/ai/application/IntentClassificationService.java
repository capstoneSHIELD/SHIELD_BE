package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentClassificationResult.Keywords;
import org.example.shield.ai.dto.IntentClassificationResult.MatchedNode;
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
    }

    public IntentClassificationResult classify(List<Message> recentMessages, String domain) {
        try {
            String conversationHistory = buildConversationHistory(recentMessages);
            String systemPrompt = buildSystemPrompt(domain);

            List<CohereChatRequest.Message> messages = List.of(
                    CohereChatRequest.Message.system(systemPrompt),
                    CohereChatRequest.Message.user(buildConversationPrompt(conversationHistory))
            );

            AiCallResult<String> result = ragMetrics.timeClassify(
                    () -> callConfiguredClassifier(messages));
            return parseClassificationResult(result.data());

        } catch (Exception e) {
            log.warn("Intent classification failed, using fallback: domain={}, error={}", domain, e.getMessage());
            return createFallbackResult(domain);
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
        return promptTemplate
                .replace("{ONTOLOGY_JSON}", ontologyJson)
                .replace("{CONVERSATION_HISTORY}", "");
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

        try {
            JsonNode root = objectMapper.readTree(slimOntologyJson);
            JsonNode children = root.path("c");
            if (!children.isArray()) {
                return slimOntologyJson;
            }
            for (JsonNode child : children) {
                if (matchesOntologyNode(child, scopedDomain)) {
                    ObjectNode scopedRoot = objectMapper.createObjectNode();
                    scopedRoot.put("id", root.path("id").asText("law-000"));
                    scopedRoot.put("name", root.path("name").asText("law"));
                    ArrayNode scopedChildren = scopedRoot.putArray("c");
                    scopedChildren.add(child.deepCopy());
                    return objectMapper.writeValueAsString(scopedRoot);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scope ontology for domain={}, using full ontology: {}", domain, e.getMessage());
        }
        return slimOntologyJson;
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
        try {
            JsonNode root = objectMapper.readTree(json);

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

    private IntentClassificationResult createFallbackResult(String domain) {
        return new IntentClassificationResult(
                "Intent classification fallback",
                List.of(),
                new Keywords(List.of(), List.of()),
                List.of(domain != null ? domain + " legal query" : "legal consultation")
        );
    }
}
