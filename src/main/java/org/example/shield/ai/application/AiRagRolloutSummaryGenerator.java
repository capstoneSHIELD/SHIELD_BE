package org.example.shield.ai.application;

import org.example.shield.ai.dto.AiRagRollbackDecision;
import org.example.shield.ai.dto.AiRagRollbackSignal;
import org.example.shield.ai.dto.AiRagRolloutAction;
import org.example.shield.ai.dto.AiRagRolloutDecision;
import org.example.shield.ai.dto.AiRagRolloutFeature;
import org.example.shield.ai.dto.AiRagRolloutSummary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AiRagRolloutSummaryGenerator {

    private final AiRagRollbackPolicy rollbackPolicy;

    public AiRagRolloutSummaryGenerator(AiRagRollbackPolicy rollbackPolicy) {
        this.rollbackPolicy = rollbackPolicy;
    }

    public AiRagRolloutSummary summarize(List<AiRagRollbackSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return new AiRagRolloutSummary(LocalDateTime.now(), List.of());
        }
        return new AiRagRolloutSummary(
                LocalDateTime.now(),
                signals.stream()
                        .filter(signal -> signal != null)
                        .map(this::decide)
                        .toList());
    }

    public AiRagRolloutDecision decide(AiRagRollbackSignal signal) {
        int required = requiredSamples(signal.feature());
        if (signal.highRiskCount() <= 0 && signal.sampleCount() < required) {
            return new AiRagRolloutDecision(
                    signal.feature(),
                    AiRagRolloutAction.NOT_ENOUGH_EVIDENCE,
                    "sample_count_below_required",
                    signal.sampleCount(),
                    required);
        }

        AiRagRollbackDecision rollbackDecision = rollbackPolicy.evaluate(signal);
        return new AiRagRolloutDecision(
                signal.feature(),
                rollbackDecision.rollback() ? AiRagRolloutAction.ROLLBACK : AiRagRolloutAction.KEEP,
                rollbackDecision.reason(),
                signal.sampleCount(),
                required);
    }

    private int requiredSamples(AiRagRolloutFeature feature) {
        if (feature == null) {
            return 100;
        }
        return switch (feature) {
            case STRUCTURED_OUTPUT -> 30;
            case GUARDRAIL -> 100;
            case SLOT_LEDGER -> 100;
            case INTENT_SKIP -> 200;
            case SLOT_AUTO_UPDATE -> 100;
            case DYNAMIC_PLAN -> 100;
            case RETRIEVAL_GATE -> 150;
            case OUTPUT_JUDGE -> 1;
        };
    }
}
