package org.example.shield.ai.dto;

public record IntentShadowEvalSummary(
        int total,
        int labeled,
        int correctIntent,
        int highRiskLeakCount,
        int skipFalsePositiveCount,
        double intentAccuracy,
        double skipFalsePositiveRate
) {
    public static IntentShadowEvalSummary empty() {
        return new IntentShadowEvalSummary(0, 0, 0, 0, 0, 0.0, 0.0);
    }
}
