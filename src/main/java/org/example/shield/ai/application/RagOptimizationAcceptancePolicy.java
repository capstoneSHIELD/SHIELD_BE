package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.example.shield.ai.dto.RagOptimizationAcceptanceDecision;
import org.example.shield.ai.dto.RagOptimizationCostSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagOptimizationAcceptancePolicy {

    private static final double MAX_SOURCE_RECALL_DROP = 0.01d;
    private static final double MAX_LATENCY_P95_INCREASE_MS = 200.0d;
    private static final double MAX_COST_INCREASE_RATIO = 0.10d;
    private static final double MAX_FALSE_DROP_RATE = 0.02d;
    private static final double MAX_REFERENCE_MENTION_DROP = 0.05d;

    public RagOptimizationAcceptanceDecision evaluate(
            RagBaselineEvaluationResult baseline,
            RagBaselineEvaluationResult candidate,
            RagOptimizationCostSnapshot baselineCost,
            RagOptimizationCostSnapshot candidateCost,
            double calibrationFalseDropRate,
            double holdoutFalseDropRate
    ) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (baseline == null || candidate == null) {
            failures.add("baseline_or_candidate_missing");
            return new RagOptimizationAcceptanceDecision(false, failures, warnings);
        }

        if (candidate.mixedRecallAt5() < baseline.mixedRecallAt5()) {
            failures.add("mixed_recall_regressed");
        }
        if (candidate.statuteRecallAt5() < baseline.statuteRecallAt5() - MAX_SOURCE_RECALL_DROP) {
            failures.add("statute_recall_regressed");
        }
        if (candidate.caseRecallAt5() < baseline.caseRecallAt5() - MAX_SOURCE_RECALL_DROP) {
            failures.add("case_recall_regressed");
        }
        boolean mrrImproved = candidate.mrr() > baseline.mrr();
        boolean gradedNdcgImproved = candidate.gradedNdcgQueryCount() > 0
                && candidate.ndcgAt5() > baseline.ndcgAt5();
        if (!mrrImproved && !gradedNdcgImproved) {
            failures.add("no_rank_metric_improvement");
        }
        if (candidate.emptyRate() > baseline.emptyRate()) {
            failures.add("empty_rate_regressed");
        }
        if (hasComparableReferenceMentionBaseline(baseline, candidate)
                && candidate.expectedReferenceMentionRate()
                < baseline.expectedReferenceMentionRate() - MAX_REFERENCE_MENTION_DROP) {
            failures.add("reference_mention_rate_regressed");
        }
        if (candidate.latencyP95Ms() - baseline.latencyP95Ms() > MAX_LATENCY_P95_INCREASE_MS) {
            failures.add("p95_latency_budget_exceeded");
        }
        validateCost(baselineCost, candidateCost, failures, warnings);
        if (calibrationFalseDropRate > MAX_FALSE_DROP_RATE) {
            failures.add("calibration_false_drop_rate_exceeded");
        }
        if (holdoutFalseDropRate > MAX_FALSE_DROP_RATE) {
            failures.add("holdout_false_drop_rate_exceeded");
        }
        return new RagOptimizationAcceptanceDecision(failures.isEmpty(), failures, warnings);
    }

    private boolean hasComparableReferenceMentionBaseline(
            RagBaselineEvaluationResult baseline,
            RagBaselineEvaluationResult candidate
    ) {
        return baseline.expectedReferenceMentionQueryCount() > 0
                && candidate.expectedReferenceMentionQueryCount() > 0;
    }

    private void validateCost(
            RagOptimizationCostSnapshot baseline,
            RagOptimizationCostSnapshot candidate,
            List<String> failures,
            List<String> warnings
    ) {
        if (baseline == null || candidate == null) {
            warnings.add("cost_snapshot_missing");
            return;
        }
        if (increaseRatio(baseline.dailyExternalApiCost(), candidate.dailyExternalApiCost())
                > MAX_COST_INCREASE_RATIO) {
            failures.add("daily_external_api_cost_budget_exceeded");
        }
        if (increaseRatio(baseline.averageExternalApiCostPerQuery(), candidate.averageExternalApiCostPerQuery())
                > MAX_COST_INCREASE_RATIO) {
            failures.add("query_external_api_cost_budget_exceeded");
        }
    }

    private double increaseRatio(double baseline, double candidate) {
        if (baseline <= 0.0) {
            return candidate <= 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return (candidate - baseline) / baseline;
    }
}
