package org.example.shield.ai.dto;

import java.time.LocalDate;
import java.util.Map;

public record RagBaselineEvaluationResult(
        LocalDate evaluatedAt,
        String method,
        int queryCount,
        double recallAt5,
        int statuteQueryCount,
        int caseQueryCount,
        double statuteRecallAt5,
        double caseRecallAt5,
        double mixedRecallAt5,
        double mrr,
        double ndcgAt5,
        int gradedNdcgQueryCount,
        double expectedReferenceMentionRate,
        int expectedReferenceMentionQueryCount,
        double emptyRate,
        double latencyP50Ms,
        double latencyP95Ms,
        int falseDropCandidateCount,
        Map<String, RagBaselineSplitMetrics> splitMetrics
) {
    public RagBaselineEvaluationResult {
        splitMetrics = splitMetrics == null ? Map.of() : Map.copyOf(splitMetrics);
    }

    public RagBaselineEvaluationResult(
            LocalDate evaluatedAt,
            String method,
            int queryCount,
            double recallAt5,
            double mrr,
            double ndcgAt5,
            double latencyP50Ms,
            double latencyP95Ms,
            int falseDropCandidateCount
    ) {
        this(
                evaluatedAt,
                method,
                queryCount,
                recallAt5,
                0,
                0,
                0.0,
                0.0,
                recallAt5,
                mrr,
                ndcgAt5,
                0,
                0.0,
                0,
                0.0,
                latencyP50Ms,
                latencyP95Ms,
                falseDropCandidateCount,
                Map.of());
    }
}
