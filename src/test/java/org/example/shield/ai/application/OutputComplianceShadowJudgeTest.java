package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.OutputComplianceResult;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComplianceShadowJudgeTest {

    private SimpleMeterRegistry registry;
    private OutputComplianceShadowJudge judge;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        judge = new OutputComplianceShadowJudge(new GuardrailFilter(), new RagMetrics(registry));
        ReflectionTestUtils.setField(judge, "shadowEnabled", true);
        ReflectionTestUtils.setField(judge, "samplingRate", 0.50);
        ReflectionTestUtils.setField(judge, "maxP95LatencyIncreaseMs", 200);
        ReflectionTestUtils.setField(judge, "maxCostRatio", 0.10);
    }

    @Test
    @DisplayName("shadow judge samples only inside configured budget")
    void shouldSample_respectsRateAndBudget() {
        assertThat(judge.shouldSample(0.49)).isTrue();
        assertThat(judge.shouldSample(0.50)).isFalse();

        ReflectionTestUtils.setField(judge, "maxP95LatencyIncreaseMs", 201);
        assertThat(judge.shouldSample(0.01)).isFalse();
    }

    @Test
    @DisplayName("shadow judge masks PII before external evaluation")
    void maskForJudge_masksPii() {
        String masked = judge.maskForJudge(
                "phone 010-1234-5678 email user@example.com card 1234-5678-1234-5678");

        assertThat(masked).contains("[PHONE]", "[EMAIL]", "[CARD]");
        assertThat(masked).doesNotContain("010-1234-5678", "user@example.com", "1234-5678-1234-5678");
    }

    @Test
    @DisplayName("evaluate records shadow outcome without blocking production response")
    void evaluate_neverBlocks() {
        ReflectionTestUtils.setField(judge, "samplingRate", 1.0);

        OutputComplianceResult result = judge.evaluate("safe response");

        assertThat(result.blockingApplied()).isFalse();
        assertThat(result.shadowScheduled()).isTrue();
        assertThat(result.maskedText()).isEqualTo("safe response");
        assertThat(registry.counter(
                RagMetrics.METRIC_OUTPUT_JUDGE_SHADOW,
                "outcome", "sampled").count()).isEqualTo(1.0);
    }

    // === P5.2 Commit 4 — conversationId-based deterministic sampling ===

    @Test
    @DisplayName("P5.2 — evaluate(response, conversationId): same convId → deterministic")
    void evaluateWithConversationId_isDeterministic() {
        ReflectionTestUtils.setField(judge, "samplingRate", 0.5);

        OutputComplianceResult first = judge.evaluate("hello", "conv-stable-1");
        OutputComplianceResult second = judge.evaluate("hello", "conv-stable-1");

        // 같은 conversationId는 항상 같은 sampling 결정
        assertThat(first.shadowScheduled()).isEqualTo(second.shadowScheduled());
        assertThat(first.hashedConversationId()).isEqualTo(second.hashedConversationId());
        assertThat(first.hashedConversationId()).hasSize(8);  // SHA-256 short
    }

    @Test
    @DisplayName("P5.2 — hashedConversationId는 원본 conversationId를 노출하지 않음")
    void hashedConversationIdDoesNotLeakOriginal() {
        ReflectionTestUtils.setField(judge, "samplingRate", 1.0);

        String originalId = "user-12345-secret-conv-abc";
        OutputComplianceResult result = judge.evaluate("response", originalId);

        assertThat(result.hashedConversationId())
                .isNotNull()
                .doesNotContain(originalId)
                .doesNotContain("12345")
                .doesNotContain("secret");
    }

    @Test
    @DisplayName("P5.2 — null conversationId → hashedConversationId=null, sampling=false")
    void nullConversationIdNoSampling() {
        ReflectionTestUtils.setField(judge, "samplingRate", 1.0);

        OutputComplianceResult result = judge.evaluate("response", null);

        assertThat(result.hashedConversationId()).isNull();
        assertThat(result.shadowScheduled()).isFalse();
    }

    @Test
    @DisplayName("P5.2 — evaluate(response) BC는 hashedConversationId=null")
    void legacyEvaluateHasNoHashedId() {
        ReflectionTestUtils.setField(judge, "samplingRate", 1.0);

        OutputComplianceResult result = judge.evaluate("response");

        assertThat(result.hashedConversationId()).isNull();
    }

    // === P5.5 Commit 2 — LLM judge 호출 통합 ===

    @org.junit.jupiter.api.Nested
    @DisplayName("P5.5 — LLM judge 호출")
    class JudgeInvocation {

        @Test
        @DisplayName("sampled + judgeClient 주입됨 → judge 호출 + 결과를 result.judgeResult에 포함")
        void sampledInvokesJudge() {
            org.example.shield.ai.provider.AiJudgeClient mockJudge =
                    org.mockito.Mockito.mock(org.example.shield.ai.provider.AiJudgeClient.class);
            org.mockito.Mockito.when(mockJudge.providerKey()).thenReturn("hyperclova");
            org.example.shield.ai.provider.JudgeResult judgeOut = new org.example.shield.ai.provider.JudgeResult(
                    org.example.shield.ai.provider.JudgeResult.Verdict.PASS,
                    0.92, "법령 안내만 포함", java.util.List.of(), 100, 30, 250L);
            org.mockito.Mockito.when(mockJudge.judge(org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(judgeOut);

            OutputComplianceShadowJudge svc = new OutputComplianceShadowJudge(
                    new GuardrailFilter(), new RagMetrics(registry),
                    new org.example.shield.ai.infrastructure.PiiMasker(),
                    mockJudge,
                    new org.example.shield.ai.infrastructure.AiRagOperationalMetrics(registry));
            ReflectionTestUtils.setField(svc, "shadowEnabled", true);
            ReflectionTestUtils.setField(svc, "samplingRate", 1.0);
            ReflectionTestUtils.setField(svc, "maxP95LatencyIncreaseMs", 200);
            ReflectionTestUtils.setField(svc, "maxCostRatio", 0.10);

            OutputComplianceResult result = svc.evaluate("법령 안내 응답", "conv-1");

            assertThat(result.shadowScheduled()).isTrue();
            assertThat(result.judgeResult()).isNotNull();
            assertThat(result.judgeResult().verdict()).isEqualTo(
                    org.example.shield.ai.provider.JudgeResult.Verdict.PASS);
            assertThat(result.blockingApplied()).isFalse();  // shadow only
        }

        @Test
        @DisplayName("judge 호출 실패 → fail-open (judgeResult=null, request 정상 진행)")
        void judgeFailureFailsOpen() {
            org.example.shield.ai.provider.AiJudgeClient mockJudge =
                    org.mockito.Mockito.mock(org.example.shield.ai.provider.AiJudgeClient.class);
            org.mockito.Mockito.when(mockJudge.providerKey()).thenReturn("hyperclova");
            org.mockito.Mockito.when(mockJudge.judge(org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new RuntimeException("HyperCLOVA timeout"));

            OutputComplianceShadowJudge svc = new OutputComplianceShadowJudge(
                    new GuardrailFilter(), new RagMetrics(registry),
                    new org.example.shield.ai.infrastructure.PiiMasker(),
                    mockJudge,
                    new org.example.shield.ai.infrastructure.AiRagOperationalMetrics(registry));
            ReflectionTestUtils.setField(svc, "shadowEnabled", true);
            ReflectionTestUtils.setField(svc, "samplingRate", 1.0);
            ReflectionTestUtils.setField(svc, "maxP95LatencyIncreaseMs", 200);
            ReflectionTestUtils.setField(svc, "maxCostRatio", 0.10);

            OutputComplianceResult result = svc.evaluate("응답", "conv-1");

            // shadowScheduled은 그대로 true, judgeResult만 null
            assertThat(result.shadowScheduled()).isTrue();
            assertThat(result.judgeResult()).isNull();
            assertThat(result.blockingApplied()).isFalse();
            // judge_failure 메트릭 발행은 별도 SimpleMeterRegistry로 확인 가능 (생략)
        }

        @Test
        @DisplayName("judgeClient 미주입 → shadow는 동작하지만 judgeResult=null")
        void noJudgeClientStillSamples() {
            // 기본 setUp 의 judge에는 judgeClient 없음
            ReflectionTestUtils.setField(judge, "samplingRate", 1.0);

            OutputComplianceResult result = judge.evaluate("응답", "conv-1");

            assertThat(result.shadowScheduled()).isTrue();
            assertThat(result.judgeResult()).isNull();
        }

        @Test
        @DisplayName("not sampled → judge 호출 안 함")
        void notSampledSkipsJudge() {
            org.example.shield.ai.provider.AiJudgeClient mockJudge =
                    org.mockito.Mockito.mock(org.example.shield.ai.provider.AiJudgeClient.class);
            OutputComplianceShadowJudge svc = new OutputComplianceShadowJudge(
                    new GuardrailFilter(), new RagMetrics(registry),
                    new org.example.shield.ai.infrastructure.PiiMasker(),
                    mockJudge, null);
            ReflectionTestUtils.setField(svc, "shadowEnabled", false);  // 비활성
            ReflectionTestUtils.setField(svc, "samplingRate", 1.0);

            OutputComplianceResult result = svc.evaluate("응답", "conv-1");

            assertThat(result.shadowScheduled()).isFalse();
            assertThat(result.judgeResult()).isNull();
            org.mockito.Mockito.verifyNoInteractions(mockJudge);
        }
    }
}
