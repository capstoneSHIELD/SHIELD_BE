package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.IntentClassificationResult;
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
                  "matched_node_ids": ["law-004-02", "law-004-04"],
                  "core_keywords": ["wage", "dismissal"],
                  "retrieval_query": "unpaid wage dismissal legal remedy"
                }
                """;

        IntentClassificationResult result = service.parseClassificationResult(json);

        assertThat(result.intentSummary()).isEqualTo("wage issue");
        assertThat(result.matchedNodeIds()).containsExactly("law-004-02", "law-004-04");
        assertThat(result.keywords().core()).containsExactly("wage", "dismissal");
        assertThat(result.keywords().expanded()).isEmpty();
        assertThat(result.retrievalQueries()).containsExactly("unpaid wage dismissal legal remedy");
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
