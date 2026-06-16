package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExperimentSelectedLabel;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiClassificationClient;
import org.example.shield.ai.provider.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentClassificationServiceTest {

    private IntentClassificationService service;

    @BeforeEach
    void setUp() {
        // P5.1 Commit 2: 첫 번째 파라미터가 CohereService → List<AiClassificationClient>로 변경.
        // 본 테스트는 parser/prompt unit이므로 빈 리스트 전달.
        service = new IntentClassificationService(
                List.of(), new ObjectMapper(), "{\"id\":\"law-000\"}", null, 4, null);
    }

    @Test
    @DisplayName("legacy classifier JSON is parsed")
    void parseClassificationResult_validLegacyJson() {
        String json = """
                {
                  "intent_summary": "lease deposit return",
                  "matched_nodes": [
                    {"id": "law-007-01-03", "name": "deposit return", "confidence": 0.92},
                    {"id": "law-001-02-02", "name": "deposit and rent", "confidence": 0.78}
                  ],
                  "keywords": {
                    "core": ["lease deposit", "return", "tenant"],
                    "expanded": ["housing lease", "priority right"]
                  },
                  "retrieval_queries": [
                    "lease deposit return requirements",
                    "housing lease deposit return lawsuit"
                  ]
                }
                """;

        IntentClassificationResult result = service.parseClassificationResult(json);

        assertThat(result.schemaVersion()).isEqualTo("1.0");
        assertThat(result.intentSummary()).isEqualTo("lease deposit return");
        assertThat(result.matchedNodes()).hasSize(2);
        assertThat(result.matchedNodes().get(0).id()).isEqualTo("law-007-01-03");
        assertThat(result.matchedNodes().get(0).confidence()).isEqualTo(0.92);
        assertThat(result.matchedNodeIds()).containsExactly("law-007-01-03", "law-001-02-02");
        assertThat(result.keywords().core()).containsExactly("lease deposit", "return", "tenant");
        assertThat(result.keywords().expanded()).containsExactly("housing lease", "priority right");
        assertThat(result.retrievalQueries()).hasSize(2);
    }

    @Test
    @DisplayName("compact classifier JSON is parsed")
    void parseClassificationResult_compactJson() {
        String json = """
                {
                  "intent_summary": "wage issue",
                  "schema_version": "1.0",
                  "matched_node_ids": ["law-004-02", "law-004-04"],
                  "core_keywords": ["wage", "dismissal"],
                  "retrieval_query": "unpaid wage dismissal legal remedy"
                }
                """;

        IntentClassificationResult result = service.parseClassificationResult(json);

        assertThat(result.schemaVersion()).isEqualTo("1.0");
        assertThat(result.intentSummary()).isEqualTo("wage issue");
        assertThat(result.matchedNodeIds()).containsExactly("law-004-02", "law-004-04");
        assertThat(result.keywords().core()).containsExactly("wage", "dismissal");
        assertThat(result.keywords().expanded()).isEmpty();
        assertThat(result.retrievalQueries()).containsExactly("unpaid wage dismissal legal remedy");
    }

    @Test
    @DisplayName("P2 intent router JSON is parsed with 8-class intent and extracted slots")
    void parseIntentRouterResponse_p2Json() {
        String json = """
                {
                  "schema_version": "2.0",
                  "dialogueIntent": "ASK_LEGAL_ADVICE",
                  "intentConfidence": 0.91,
                  "extractedSlots": [
                    {
                      "slotId": "static_001",
                      "value": "30000000",
                      "rawText": "보증금은 3천만원",
                      "confidence": 0.89,
                      "valueType": "money",
                      "needsConfirmation": false
                    }
                  ],
                  "caseType": {
                    "l1": "부동산 거래",
                    "l2": "부동산 임대차",
                    "l3": "보증금 및 차임",
                    "confidence": 0.87
                  },
                  "intent_summary": "보증금과 승소 가능성 질문",
                  "matched_node_ids": ["law-001"],
                  "core_keywords": ["보증금"],
                  "retrieval_query": "전세 보증금 반환",
                  "retrievalQueries": ["전세 보증금 반환"],
                  "correctedSlotIds": [],
                  "topicChanged": false
                }
                """;

        IntentRouterResponse result = service.parseIntentRouterResponse(json);

        assertThat(result.schemaVersion()).isEqualTo("2.0");
        assertThat(result.dialogueIntent()).isEqualTo(DialogueIntent.ASK_LEGAL_ADVICE);
        assertThat(result.intentConfidence()).isEqualTo(0.91);
        assertThat(result.extractedSlots()).hasSize(1);
        assertThat(result.extractedSlots().get(0).slotId()).isEqualTo("static_001");
        assertThat(result.caseType().l1()).isEqualTo("부동산 거래");
        assertThat(result.retrievalQueries()).containsExactly("전세 보증금 반환");
        assertThat(result.toClassificationResult().matchedNodeIds()).containsExactly("law-001");
    }

    @Test
    @DisplayName("empty matched nodes remain empty")
    void parseClassificationResult_emptyNodes() {
        String json = """
                {
                  "intent_summary": "unknown",
                  "matched_nodes": [],
                  "keywords": {"core": [], "expanded": []},
                  "retrieval_queries": []
                }
                """;

        IntentClassificationResult result = service.parseClassificationResult(json);

        assertThat(result.matchedNodes()).isEmpty();
        assertThat(result.matchedNodeIds()).isEmpty();
    }

    @Test
    @DisplayName("invalid JSON throws runtime exception")
    void parseClassificationResult_invalidJson() {
        assertThatThrownBy(() -> service.parseClassificationResult("not a json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Intent classification JSON parsing failed");
    }

    @Test
    @DisplayName("experiment route includes selected labels and full history by default")
    void routeForExperiment_includesSelectedLabelsAndFullHistoryByDefault() {
        CapturingClassificationClient client = new CapturingClassificationClient();
        IntentClassificationService experimentService = experimentService(client);

        experimentService.routeForExperiment(
                "cohere",
                "A_FULL",
                null,
                List.of(
                        ChatMessage.user("turn 1"),
                        ChatMessage.user("turn 2"),
                        ChatMessage.user("turn 3"),
                        ChatMessage.user("turn 4"),
                        ChatMessage.user("turn 5")
                ),
                List.of("law-002-04-02", "law-004-02-01"),
                List.of(new ExperimentSelectedLabel(
                        "law-002-04-02",
                        "이혼·위자료·재산분할",
                        "자녀 및 양육",
                        "양육비 산정 및 청구"
                )),
                null,
                false
        );

        String userPrompt = client.capturedMessages().get(1).content();
        assertThat(userPrompt)
                .contains("User-selected legal areas")
                .contains("law-002-04-02")
                .contains("이혼·위자료·재산분할 > 자녀 및 양육 > 양육비 산정 및 청구")
                .contains("law-004-02-01")
                .contains("do not use selected areas as a hard scope")
                .contains("user: turn 1")
                .contains("user: turn 5");
    }

    @Test
    @DisplayName("experiment route can explicitly limit history window")
    void routeForExperiment_respectsExplicitHistoryWindow() {
        CapturingClassificationClient client = new CapturingClassificationClient();
        IntentClassificationService experimentService = experimentService(client);

        experimentService.routeForExperiment(
                "cohere",
                "A_FULL",
                null,
                List.of(
                        ChatMessage.user("turn 1"),
                        ChatMessage.user("turn 2"),
                        ChatMessage.user("turn 3")
                ),
                List.of(),
                List.of(),
                2,
                false
        );

        String userPrompt = client.capturedMessages().get(1).content();
        assertThat(userPrompt)
                .doesNotContain("user: turn 1")
                .contains("user: turn 2")
                .contains("user: turn 3");
    }

    private IntentClassificationService experimentService(CapturingClassificationClient client) {
        IntentClassificationService experimentService = new IntentClassificationService(
                List.of(client),
                new ObjectMapper(),
                "{\"id\":\"law-000\",\"name\":\"law\",\"c\":[]}",
                null,
                4,
                new RagMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(
                experimentService,
                "intentClassifierPromptTemplate",
                "Ontology:\n{ONTOLOGY_JSON}");
        return experimentService;
    }

    private static class CapturingClassificationClient implements AiClassificationClient {
        private final List<ChatMessage> capturedMessages = new ArrayList<>();

        @Override
        public AiCallResult<String> classify(List<ChatMessage> messages) {
            capturedMessages.clear();
            capturedMessages.addAll(messages);
            return new AiCallResult<>(
                    "response-1",
                    """
                            {
                              "schema_version": "2.0",
                              "dialogueIntent": "ASK_LEGAL_ADVICE",
                              "intentConfidence": 0.95,
                              "caseType": {
                                "l1": "임대차보호",
                                "l2": "주택임대차보호",
                                "l3": "보증금 반환 및 회수",
                                "confidence": 0.91
                              },
                              "matched_node_ids": ["law-007-01-05"],
                              "core_keywords": ["전세", "보증금"],
                              "retrievalQueries": []
                            }
                            """,
                    11,
                    22,
                    33
            );
        }

        @Override
        public String providerKey() {
            return "cohere";
        }

        List<ChatMessage> capturedMessages() {
            return capturedMessages;
        }
    }
}
