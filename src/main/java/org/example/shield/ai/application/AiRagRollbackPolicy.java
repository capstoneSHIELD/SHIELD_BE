package org.example.shield.ai.application;

import org.example.shield.ai.dto.AiRagRollbackDecision;
import org.example.shield.ai.dto.AiRagRollbackSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiRagRollbackPolicy {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration THIRTY_MINUTES = Duration.ofMinutes(30);

    public AiRagRollbackDecision evaluate(AiRagRollbackSignal signal) {
        if (signal == null) {
            return AiRagRollbackDecision.keep("no_signal");
        }
        return switch (signal.feature()) {
            case STRUCTURED_OUTPUT -> structuredOutput(signal);
            case GUARDRAIL -> guardrail(signal);
            case SLOT_LEDGER -> slotLedger(signal);
            case INTENT_SKIP -> intentSkip(signal);
            case SLOT_AUTO_UPDATE -> slotAutoUpdate(signal);
            case DYNAMIC_PLAN -> dynamicPlan(signal);
            case RETRIEVAL_GATE -> retrievalGate(signal);
            case OUTPUT_JUDGE -> outputJudge(signal);
        };
    }

    private AiRagRollbackDecision structuredOutput(AiRagRollbackSignal signal) {
        boolean thresholdExceeded = signal.primaryRate() > 0.01d || signal.secondaryRate() > 0.05d;
        boolean enoughEvidence = signal.duration().compareTo(TEN_MINUTES) >= 0 || signal.sampleCount() >= 30;
        return thresholdExceeded && enoughEvidence
                ? AiRagRollbackDecision.rollback("structured_output_error_threshold")
                : AiRagRollbackDecision.keep("structured_output_within_threshold");
    }

    private AiRagRollbackDecision guardrail(AiRagRollbackSignal signal) {
        boolean rateExceeded = signal.primaryRate() > 0.02d && signal.sampleCount() >= 100;
        boolean repeatedFalsePositive = signal.consecutiveCount() >= 10;
        return rateExceeded || repeatedFalsePositive
                ? AiRagRollbackDecision.rollback("guardrail_false_positive_threshold")
                : AiRagRollbackDecision.keep("guardrail_within_threshold");
    }

    private AiRagRollbackDecision slotLedger(AiRagRollbackSignal signal) {
        boolean pollutionExceeded = signal.primaryRate() > 0.01d && signal.sampleCount() >= 100;
        boolean latencyExceeded = signal.p95LatencyIncreaseMs() > 300.0d
                && signal.duration().compareTo(THIRTY_MINUTES) >= 0;
        return pollutionExceeded || latencyExceeded
                ? AiRagRollbackDecision.rollback("slot_ledger_threshold")
                : AiRagRollbackDecision.keep("slot_ledger_within_threshold");
    }

    private AiRagRollbackDecision intentSkip(AiRagRollbackSignal signal) {
        boolean falsePositiveExceeded = signal.primaryRate() > 0.005d && signal.sampleCount() >= 200;
        boolean highRisk = signal.highRiskCount() >= 1;
        return falsePositiveExceeded || highRisk
                ? AiRagRollbackDecision.rollback("intent_skip_threshold")
                : AiRagRollbackDecision.keep("intent_skip_within_threshold");
    }

    private AiRagRollbackDecision slotAutoUpdate(AiRagRollbackSignal signal) {
        boolean precisionTooLow = signal.precision() > 0.0d
                && signal.precision() < 0.95d
                && signal.sampleCount() >= 100;
        boolean repeatedPollution = signal.sameSlotPollutionCount() >= 3;
        return precisionTooLow || repeatedPollution
                ? AiRagRollbackDecision.rollback("slot_auto_update_threshold")
                : AiRagRollbackDecision.keep("slot_auto_update_within_threshold");
    }

    private AiRagRollbackDecision dynamicPlan(AiRagRollbackSignal signal) {
        boolean validatorFalsePositive = signal.primaryRate() > 0.05d && signal.sampleCount() >= 100;
        boolean regenerationRate = signal.secondaryRate() > 0.30d
                && signal.duration().compareTo(THIRTY_MINUTES) >= 0;
        return validatorFalsePositive || regenerationRate
                ? AiRagRollbackDecision.rollback("dynamic_plan_threshold")
                : AiRagRollbackDecision.keep("dynamic_plan_within_threshold");
    }

    private AiRagRollbackDecision retrievalGate(AiRagRollbackSignal signal) {
        boolean falseDropRate = signal.primaryRate() > 0.02d && signal.sampleCount() >= 150;
        boolean recallDrop = signal.recallAt5DropPercentagePoints() >= 2.0d;
        boolean repeatedRelatedDrop = signal.relatedDropCount() >= 3;
        return falseDropRate || recallDrop || repeatedRelatedDrop
                ? AiRagRollbackDecision.rollback("retrieval_gate_threshold")
                : AiRagRollbackDecision.keep("retrieval_gate_within_threshold");
    }

    private AiRagRollbackDecision outputJudge(AiRagRollbackSignal signal) {
        boolean latencyExceeded = signal.p95LatencyIncreaseMs() > 200.0d
                && signal.duration().compareTo(THIRTY_MINUTES) >= 0;
        boolean costExceeded = signal.costIncreaseRatio() > 0.10d;
        return latencyExceeded || costExceeded
                ? AiRagRollbackDecision.rollback("output_judge_threshold")
                : AiRagRollbackDecision.keep("output_judge_within_threshold");
    }
}
