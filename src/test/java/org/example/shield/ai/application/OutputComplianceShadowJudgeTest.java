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
}
