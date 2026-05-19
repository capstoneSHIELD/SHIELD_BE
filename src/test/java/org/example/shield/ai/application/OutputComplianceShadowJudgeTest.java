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
}
