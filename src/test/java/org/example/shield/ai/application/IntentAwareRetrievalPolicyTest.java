package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.RetrievalStrategyDecision;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IntentAwareRetrievalPolicy} 검증 (P5.3 Commit 3).
 *
 * <p>3가지 mode 분기 (off/shadow/enforce) + 메트릭 발행 + legacy enabled BC.
 *
 * <p>본 commit에서는 ASK_LEGAL_ADVICE skip이 여전히 enforce 시 동작 (현재 로직 유지).
 * P5.3 Commit 4에서 "ASK_LEGAL_ADVICE skip 절대 금지"로 변경 예정.
 */
class IntentAwareRetrievalPolicyTest {

    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private IntentAwareRetrievalPolicy policy;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);
        policy = new IntentAwareRetrievalPolicy(metrics);
        ReflectionTestUtils.setField(policy, "legacyEnabled", false);
        ReflectionTestUtils.setField(policy, "modeRaw", "off");
        ReflectionTestUtils.setField(policy, "highConfidenceThreshold", 0.85);
        ReflectionTestUtils.setField(policy, "mediumConfidenceThreshold", 0.65);
    }

    private static IntentRouterResponse intent(DialogueIntent dialogueIntent, double confidence) {
        return new IntentRouterResponse(
                "2.0",
                dialogueIntent,
                confidence,
                List.of(),
                CaseTypeResult.empty(),
                List.of(),
                List.of(),
                false,
                null);
    }

    @Nested
    @DisplayName("mode=off — 항상 baseline (메트릭 발행은 함)")
    class OffMode {

        @Test
        @DisplayName("높은 신뢰도 GREETING도 baseline")
        void offReturnsBaselineEvenForHighConfidenceGreeting() {
            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.topK()).isEqualTo(3);
            assertThat(decision.reason()).isEqualTo("disabled");
        }

        @Test
        @DisplayName("메트릭은 mode=off로 기록")
        void offRecordsMetric() {
            policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            double count = registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "off", "intent", "GREETING",
                    "decision", "baseline_disabled", "confidence", "high").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("mode=shadow — 결정 계산 + 메트릭, 실제 라우팅은 baseline")
    class ShadowMode {

        @BeforeEach
        void enableShadow() {
            ReflectionTestUtils.setField(policy, "modeRaw", "shadow");
        }

        @Test
        @DisplayName("높은 신뢰도 GREETING (skip enabled) — shadow_greeting_skip 메트릭 + baseline 반환")
        void shadowGreetingRecordsButReturnsBaseline() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);
            ReflectionTestUtils.setField(policy, "greetingMinConfidence", 0.90);

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            // 실제 라우팅은 baseline (skipRag=false, topK 변경 없음)
            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.topK()).isEqualTo(3);
            assertThat(decision.reason()).startsWith("shadow_");

            // 메트릭에는 computed decision (greeting_skip) 기록
            double count = registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "shadow", "intent", "GREETING",
                    "decision", "greeting_skip", "confidence", "high").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("CHANGE_TOPIC + high confidence — shadow_broad_search 메트릭, baseline 반환")
        void shadowChangeTopicRecordsBroadSearch() {
            policy.decide(intent(DialogueIntent.CHANGE_TOPIC, 0.92), 3);

            double count = registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "shadow", "intent", "CHANGE_TOPIC",
                    "decision", "high_confidence_broad_search", "confidence", "high").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("mode=enforce — 결정이 라우팅에 반영")
    class EnforceMode {

        @BeforeEach
        void enableEnforce() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");
        }

        @Test
        @DisplayName("enableGreetingSkip=true + 0.95 confidence → 실제 skipRag=true (greeting_skip)")
        void enforceGreetingSkipsRag() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);
            ReflectionTestUtils.setField(policy, "greetingMinConfidence", 0.90);

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            assertThat(decision.skipRag()).isTrue();
            assertThat(decision.reason()).isEqualTo("greeting_skip");
        }

        @Test
        @DisplayName("CHANGE_TOPIC + high confidence → topK=10")
        void enforceChangeTopicBroadensTopK() {
            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.CHANGE_TOPIC, 0.92), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.topK()).isEqualTo(10);
            assertThat(decision.reason()).isEqualTo("high_confidence_broad_search");
        }

        @Test
        @DisplayName("low confidence → baseline (intent 무시)")
        void enforceLowConfidenceBaseline() {
            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.30), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("low_confidence_baseline");
        }
    }

    @Nested
    @DisplayName("Legacy enabled flag (BC)")
    class LegacyEnabled {

        @Test
        @DisplayName("legacyEnabled=true → ENFORCE로 마이그레이션 (greeting skip enabled 시)")
        void legacyTrueBehavesAsEnforce() {
            ReflectionTestUtils.setField(policy, "legacyEnabled", true);
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);
            ReflectionTestUtils.setField(policy, "greetingMinConfidence", 0.90);

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            assertThat(decision.skipRag()).isTrue();
            // 메트릭은 mode=enforce 태그
            double count = registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "enforce", "intent", "GREETING",
                    "decision", "greeting_skip", "confidence", "high").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("P5.3 Commit 4 — ASK_LEGAL_ADVICE는 절대 skip 금지 (안전성 핵심)")
    class AskLegalAdviceNeverSkipped {

        @Test
        @DisplayName("ENFORCE 모드 + 최고 신뢰도 ASK_LEGAL_ADVICE → skipRag=false (절대 skip 안 됨)")
        void enforceHighConfidenceAskLegalAdvice() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.ASK_LEGAL_ADVICE, 0.99), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("ask_legal_advice_force_rag");
        }

        @Test
        @DisplayName("ENFORCE + 낮은 신뢰도 ASK_LEGAL_ADVICE → 여전히 force RAG")
        void enforceLowConfidenceAskLegalAdvice() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.ASK_LEGAL_ADVICE, 0.30), 3);

            assertThat(decision.skipRag()).isFalse();
            // ASK_LEGAL_ADVICE는 confidence 검사 이전에 force RAG
            assertThat(decision.reason()).isEqualTo("ask_legal_advice_force_rag");
        }
    }

    @Nested
    @DisplayName("P5.3 Commit 4 — GREETING-only skip enforce")
    class GreetingOnlySkip {

        @BeforeEach
        void enableEnforce() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");
            ReflectionTestUtils.setField(policy, "greetingMinConfidence", 0.90);
        }

        @Test
        @DisplayName("enableGreetingSkip=false + 높은 신뢰도 GREETING → baseline 유지")
        void greetingSkipDisabledBaseline() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", false);

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.99), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("greeting_baseline");
        }

        @Test
        @DisplayName("enableGreetingSkip=true + confidence≥0.90 → skipRag=true")
        void greetingSkipEnabledHighConfidence() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.95), 3);

            assertThat(decision.skipRag()).isTrue();
            assertThat(decision.reason()).isEqualTo("greeting_skip");
        }

        @Test
        @DisplayName("enableGreetingSkip=true + confidence<0.90 → baseline (skip 엄격 threshold)")
        void greetingSkipEnabledBelowThreshold() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true);

            // 0.87은 high_confidence threshold(0.85)는 통과하지만 greeting threshold(0.90)는 미달
            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.GREETING, 0.87), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("greeting_baseline");
        }

        @Test
        @DisplayName("IRRELEVANT — 별도 enable flag 미적용 → baseline 유지")
        void irrelevantBaseline() {
            ReflectionTestUtils.setField(policy, "enableGreetingSkip", true); // greeting만 켜도

            RetrievalStrategyDecision decision = policy.decide(intent(DialogueIntent.IRRELEVANT, 0.99), 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("irrelevant_baseline");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("intent=null + mode=enforce → baseline_missing_intent")
        void nullIntent() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");

            RetrievalStrategyDecision decision = policy.decide(null, 3);

            assertThat(decision.reason()).isEqualTo("missing_intent");
            assertThat(registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "enforce", "intent", "UNKNOWN",
                    "decision", "baseline_missing_intent", "confidence", "unknown").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("dialogueIntent=null + mode=enforce → null_intent_baseline")
        void nullDialogueIntentFallsBackToBaseline() {
            ReflectionTestUtils.setField(policy, "modeRaw", "enforce");

            IntentRouterResponse malformed = mock(IntentRouterResponse.class);
            when(malformed.dialogueIntent()).thenReturn(null);
            when(malformed.intentConfidence()).thenReturn(0.99);

            RetrievalStrategyDecision decision = policy.decide(malformed, 3);

            assertThat(decision.skipRag()).isFalse();
            assertThat(decision.reason()).isEqualTo("null_intent_baseline");
            assertThat(registry.counter(AiRagOperationalMetrics.INTENT_ROUTING,
                    "mode", "enforce", "intent", "UNKNOWN",
                    "decision", "null_intent_baseline", "confidence", "high").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("mode=invalid → fail-fast")
        void invalidModeThrows() {
            ReflectionTestUtils.setField(policy, "modeRaw", "bogus");

            assertThatThrownBy(() ->
                    policy.decide(intent(DialogueIntent.GREETING, 0.95), 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bogus");
        }

        @Test
        @DisplayName("test-friendly no-arg constructor — 메트릭 미주입 안전 동작")
        void noArgConstructorSafe() {
            IntentAwareRetrievalPolicy bare = new IntentAwareRetrievalPolicy();
            ReflectionTestUtils.setField(bare, "modeRaw", "enforce");
            ReflectionTestUtils.setField(bare, "highConfidenceThreshold", 0.85);
            ReflectionTestUtils.setField(bare, "mediumConfidenceThreshold", 0.65);
            ReflectionTestUtils.setField(bare, "enableGreetingSkip", true);
            ReflectionTestUtils.setField(bare, "greetingMinConfidence", 0.90);

            // 메트릭 없이도 정상 결정
            RetrievalStrategyDecision decision = bare.decide(intent(DialogueIntent.GREETING, 0.95), 3);
            assertThat(decision.skipRag()).isTrue();
        }
    }
}
