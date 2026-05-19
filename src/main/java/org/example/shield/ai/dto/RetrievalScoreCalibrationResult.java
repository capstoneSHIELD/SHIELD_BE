package org.example.shield.ai.dto;

public record RetrievalScoreCalibrationResult(
        RetrievalScoreMethod method,
        double threshold,
        double falseDropRate,
        int totalObservations,
        int relevantObservations,
        int falseDroppedRelevant
) {
}
