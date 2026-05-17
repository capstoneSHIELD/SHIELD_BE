package org.example.shield.ai.dto;

public record RetrievalScoreGateDecision(
        boolean allowed,
        String reason,
        Double threshold
) {
    public static RetrievalScoreGateDecision allowed(String reason, Double threshold) {
        return new RetrievalScoreGateDecision(true, reason, threshold);
    }

    public static RetrievalScoreGateDecision blocked(String reason, Double threshold) {
        return new RetrievalScoreGateDecision(false, reason, threshold);
    }
}
