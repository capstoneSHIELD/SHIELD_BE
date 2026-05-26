package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.example.shield.ai.dto.RagOptimizationAcceptanceDecision;
import org.example.shield.ai.dto.RagOptimizationCostSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RagOptimizationAcceptancePolicyTest {

    private final RagOptimizationAcceptancePolicy policy = new RagOptimizationAcceptancePolicy();

    @Test
    @DisplayName("accepts candidate that preserves recall and improves rank within latency/cost budget")
    void evaluate_acceptsSafeCandidate() {
        RagOptimizationAcceptanceDecision decision = policy.evaluate(
                metric(0.90, 0.90, 0.80, 0.70, 0.60, 1000),
                metric(0.91, 0.90, 0.80, 0.72, 0.61, 1150),
                new RagOptimizationCostSnapshot(100, 0.10),
                new RagOptimizationCostSnapshot(108, 0.108),
                0.01,
                0.01);

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.failures()).isEmpty();
    }

    @Test
    @DisplayName("rejects mixed recall regression even when rank metrics improve")
    void evaluate_rejectsMixedRecallRegression() {
        RagOptimizationAcceptanceDecision decision = policy.evaluate(
                metric(0.90, 0.90, 0.80, 0.70, 0.60, 1000),
                metric(0.89, 0.90, 0.80, 0.80, 0.70, 1000),
                new RagOptimizationCostSnapshot(100, 0.10),
                new RagOptimizationCostSnapshot(100, 0.10),
                0.0,
                0.0);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.failures()).contains("mixed_recall_regressed");
    }

    @Test
    @DisplayName("rejects candidates that exceed latency, cost, or false-drop budgets")
    void evaluate_rejectsBudgetOverrun() {
        RagOptimizationAcceptanceDecision decision = policy.evaluate(
                metric(0.90, 0.90, 0.80, 0.70, 0.60, 1000),
                metric(0.91, 0.90, 0.80, 0.72, 0.61, 1301),
                new RagOptimizationCostSnapshot(100, 0.10),
                new RagOptimizationCostSnapshot(112, 0.12),
                0.03,
                0.01);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.failures()).contains(
                "p95_latency_budget_exceeded",
                "daily_external_api_cost_budget_exceeded",
                "query_external_api_cost_budget_exceeded",
                "calibration_false_drop_rate_exceeded");
    }

    @Test
    @DisplayName("rejects reference mention coverage regression after baseline is available")
    void evaluate_rejectsReferenceMentionRegression() {
        RagOptimizationAcceptanceDecision decision = policy.evaluate(
                metric(0.90, 0.90, 0.80, 0.70, 0.60, 1000, 0.90, 10),
                metric(0.91, 0.90, 0.80, 0.72, 0.61, 1000, 0.84, 10),
                new RagOptimizationCostSnapshot(100, 0.10),
                new RagOptimizationCostSnapshot(100, 0.10),
                0.0,
                0.0);

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.failures()).contains("reference_mention_rate_regressed");
    }

    private RagBaselineEvaluationResult metric(
            double mixedRecall,
            double statuteRecall,
            double caseRecall,
            double mrr,
            double ndcg,
            double p95
    ) {
        return metric(mixedRecall, statuteRecall, caseRecall, mrr, ndcg, p95, 0.0, 0);
    }

    private RagBaselineEvaluationResult metric(
            double mixedRecall,
            double statuteRecall,
            double caseRecall,
            double mrr,
            double ndcg,
            double p95,
            double referenceMentionRate,
            int referenceMentionQueries
    ) {
        return new RagBaselineEvaluationResult(
                LocalDate.now(),
                "weighted",
                100,
                mixedRecall,
                50,
                50,
                statuteRecall,
                caseRecall,
                mixedRecall,
                mrr,
                ndcg,
                20,
                referenceMentionRate,
                referenceMentionQueries,
                0.05,                 // emptyRate
                500,                  // latencyP50Ms
                p95,                  // latencyP95Ms
                0,                    // falseDropCandidateCount
                java.util.Map.of());
    }
}
