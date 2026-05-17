package org.example.shield.ai.application;

import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.RetrievalStrategyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentAwareRetrieverTest {

    private IntentAwareRetrievalPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new IntentAwareRetrievalPolicy();
        ReflectionTestUtils.setField(policy, "enabled", true);
        ReflectionTestUtils.setField(policy, "highConfidenceThreshold", 0.85);
        ReflectionTestUtils.setField(policy, "mediumConfidenceThreshold", 0.65);
    }

    @Test
    @DisplayName("low confidence keeps baseline retrieval")
    void decide_lowConfidenceBaseline() {
        RetrievalStrategyDecision decision = policy.decide(response(DialogueIntent.PROVIDE_INFO, 0.60), 3);

        assertThat(decision.skipRag()).isFalse();
        assertThat(decision.applyIntentStrategy()).isFalse();
        assertThat(decision.topK()).isEqualTo(3);
        assertThat(decision.reason()).isEqualTo("low_confidence_baseline");
    }

    @Test
    @DisplayName("medium confidence keeps conservative baseline")
    void decide_mediumConfidenceConservative() {
        RetrievalStrategyDecision decision = policy.decide(response(DialogueIntent.PROVIDE_INFO, 0.70), 3);

        assertThat(decision.skipRag()).isFalse();
        assertThat(decision.applyIntentStrategy()).isFalse();
        assertThat(decision.reason()).isEqualTo("medium_confidence_conservative");
    }

    @Test
    @DisplayName("high confidence legal advice skips RAG")
    void decide_highConfidenceLegalAdviceSkips() {
        RetrievalStrategyDecision decision = policy.decide(response(DialogueIntent.ASK_LEGAL_ADVICE, 0.91), 3);

        assertThat(decision.skipRag()).isTrue();
        assertThat(decision.applyIntentStrategy()).isTrue();
    }

    @Test
    @DisplayName("high confidence topic change broadens retrieval")
    void decide_topicChangeBroadSearch() {
        RetrievalStrategyDecision decision = policy.decide(response(DialogueIntent.CHANGE_TOPIC, 0.91), 3);

        assertThat(decision.skipRag()).isFalse();
        assertThat(decision.applyIntentStrategy()).isTrue();
        assertThat(decision.topK()).isEqualTo(10);
    }

    private IntentRouterResponse response(DialogueIntent intent, double confidence) {
        return new IntentRouterResponse(
                "2.0",
                intent,
                confidence,
                List.of(),
                CaseTypeResult.empty(),
                List.of("query"),
                List.of(),
                false,
                null);
    }
}
