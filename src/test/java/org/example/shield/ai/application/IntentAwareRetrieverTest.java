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
        // P5.3 Commit 3: 'enabled' → 'legacyEnabled' 이름 변경 (BC 위해 동작은 동일).
        // legacyEnabled=true는 ENFORCE 모드로 마이그레이션됨.
        ReflectionTestUtils.setField(policy, "legacyEnabled", true);
        ReflectionTestUtils.setField(policy, "modeRaw", "off");  // legacy가 우선
        ReflectionTestUtils.setField(policy, "highConfidenceThreshold", 0.85);
        ReflectionTestUtils.setField(policy, "mediumConfidenceThreshold", 0.65);
        // P5.3 Commit 4: GREETING skip enable (이 테스트는 GREETING/IRRELEVANT/ASK_LEGAL_ADVICE skip
        // 동작을 검증함 — 기존 의도 유지를 위해 enable 시킴)
        ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);
        ReflectionTestUtils.setField(policy, "greetingMinConfidence", 0.85);  // 일반 high threshold 동일
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
    @DisplayName("[P5.3 C4 안전 정책] ASK_LEGAL_ADVICE는 high confidence에도 절대 RAG skip 안 됨")
    void decide_askLegalAdviceNeverSkipsRag() {
        // P5.3 Commit 4: 법률 조언 요청은 항상 법령/판례 근거 필요.
        // 이전 동작(high confidence skip)은 안전성 위반으로 금지됨.
        RetrievalStrategyDecision decision = policy.decide(response(DialogueIntent.ASK_LEGAL_ADVICE, 0.91), 3);

        assertThat(decision.skipRag()).isFalse();
        assertThat(decision.reason()).isEqualTo("ask_legal_advice_force_rag");
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
