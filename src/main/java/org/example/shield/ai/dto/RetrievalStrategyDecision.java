package org.example.shield.ai.dto;

public record RetrievalStrategyDecision(
        boolean skipRag,
        boolean applyIntentStrategy,
        int topK,
        String reason
) {
    public RetrievalStrategyDecision {
        topK = Math.max(1, topK);
        reason = reason == null ? "" : reason;
    }

    public static RetrievalStrategyDecision baseline(int topK, String reason) {
        return new RetrievalStrategyDecision(false, false, topK, reason);
    }
}
