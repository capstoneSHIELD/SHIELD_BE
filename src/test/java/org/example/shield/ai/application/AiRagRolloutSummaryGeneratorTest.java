package org.example.shield.ai.application;

import org.example.shield.ai.dto.AiRagRollbackSignal;
import org.example.shield.ai.dto.AiRagRolloutAction;
import org.example.shield.ai.dto.AiRagRolloutDecision;
import org.example.shield.ai.dto.AiRagRolloutFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagRolloutSummaryGeneratorTest {

    private final AiRagRolloutSummaryGenerator generator =
            new AiRagRolloutSummaryGenerator(new AiRagRollbackPolicy());

    @Test
    @DisplayName("insufficient samples produce NOT_ENOUGH_EVIDENCE")
    void notEnoughEvidence() {
        AiRagRolloutDecision decision = generator.decide(AiRagRollbackSignal
                .builder(AiRagRolloutFeature.INTENT_SKIP)
                .sampleCount(20)
                .primaryRate(0.0)
                .build());

        assertThat(decision.action()).isEqualTo(AiRagRolloutAction.NOT_ENOUGH_EVIDENCE);
    }

    @Test
    @DisplayName("high-risk intent skip issue recommends rollback even with small samples")
    void highRiskRollback() {
        AiRagRolloutDecision decision = generator.decide(AiRagRollbackSignal
                .builder(AiRagRolloutFeature.INTENT_SKIP)
                .sampleCount(1)
                .highRiskCount(1)
                .build());

        assertThat(decision.action()).isEqualTo(AiRagRolloutAction.ROLLBACK);
    }

    @Test
    @DisplayName("output judge latency and cost thresholds are reflected")
    void outputJudgeRollback() {
        AiRagRolloutDecision decision = generator.decide(AiRagRollbackSignal
                .builder(AiRagRolloutFeature.OUTPUT_JUDGE)
                .sampleCount(1)
                .p95LatencyIncreaseMs(250)
                .duration(Duration.ofMinutes(31))
                .build());

        assertThat(decision.action()).isEqualTo(AiRagRolloutAction.ROLLBACK);
    }
}
