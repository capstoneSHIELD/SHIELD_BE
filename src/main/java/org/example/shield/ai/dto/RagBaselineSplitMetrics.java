package org.example.shield.ai.dto;

public record RagBaselineSplitMetrics(
        String split,
        int queryCount,
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
        double latencyP95Ms
) {
}
