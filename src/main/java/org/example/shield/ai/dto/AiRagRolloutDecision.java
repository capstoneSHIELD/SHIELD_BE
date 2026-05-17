package org.example.shield.ai.dto;

public record AiRagRolloutDecision(
        AiRagRolloutFeature feature,
        AiRagRolloutAction action,
        String reason,
        int sampleCount,
        int requiredSampleCount
) {
}
