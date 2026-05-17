package org.example.shield.ai.dto;

import java.time.LocalDate;

public record RagBaselineEvaluationResult(
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
}
