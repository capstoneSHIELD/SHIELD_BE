package org.example.shield.ai.application;

import org.example.shield.ai.dto.AiRagRollbackSignal;
import org.example.shield.ai.dto.AiRagRolloutFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagRollbackPolicyTest {

    private final AiRagRollbackPolicy policy = new AiRagRollbackPolicy();

    @Test
    @DisplayName("structured output rollback requires threshold and duration or enough samples")
    void structuredOutputRequiresEvidence() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.STRUCTURED_OUTPUT)
                .primaryRate(0.02)
                .duration(Duration.ofMinutes(5))
                .sampleCount(10)
                .build()).rollback()).isFalse();

        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.STRUCTURED_OUTPUT)
                .primaryRate(0.02)
                .duration(Duration.ofMinutes(10))
                .build()).rollback()).isTrue();
    }

    @Test
    @DisplayName("guardrail rollback uses labeled sample size or repeated false positives")
    void guardrailUsesSampleOrConsecutiveEvidence() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.GUARDRAIL)
                .primaryRate(0.03)
                .sampleCount(50)
                .build()).rollback()).isFalse();

        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.GUARDRAIL)
                .consecutiveCount(10)
                .build()).rollback()).isTrue();
    }

    @Test
    @DisplayName("intent skip rolls back immediately on high-risk misclassification")
    void intentSkipHighRiskRollsBack() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.INTENT_SKIP)
                .highRiskCount(1)
                .build()).rollback()).isTrue();
    }

    @Test
    @DisplayName("slot auto update rollback requires low precision with samples or repeated same-slot pollution")
    void slotAutoUpdateEvidence() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.SLOT_AUTO_UPDATE)
                .precision(0.94)
                .sampleCount(99)
                .build()).rollback()).isFalse();

        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.SLOT_AUTO_UPDATE)
                .sameSlotPollutionCount(3)
                .build()).rollback()).isTrue();
    }

    @Test
    @DisplayName("retrieval gate rolls back on recall drop or repeated related document drops")
    void retrievalGateEvidence() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.RETRIEVAL_GATE)
                .primaryRate(0.03)
                .sampleCount(149)
                .build()).rollback()).isFalse();

        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.RETRIEVAL_GATE)
                .recallAt5DropPercentagePoints(2.0)
                .build()).rollback()).isTrue();
    }

    @Test
    @DisplayName("output judge rollback requires sustained latency or daily cost over budget")
    void outputJudgeEvidence() {
        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.OUTPUT_JUDGE)
                .p95LatencyIncreaseMs(250)
                .duration(Duration.ofMinutes(10))
                .build()).rollback()).isFalse();

        assertThat(policy.evaluate(AiRagRollbackSignal.builder(AiRagRolloutFeature.OUTPUT_JUDGE)
                .costIncreaseRatio(0.11)
                .build()).rollback()).isTrue();
    }
}
