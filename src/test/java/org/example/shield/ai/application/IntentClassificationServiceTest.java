package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentClassificationServiceTest {

    private IntentClassificationService service;

    @BeforeEach
    void setUp() {
        service = new IntentClassificationService(
                null, new ObjectMapper(), "{\"id\":\"law-000\"}", null, 4, null);
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
}
