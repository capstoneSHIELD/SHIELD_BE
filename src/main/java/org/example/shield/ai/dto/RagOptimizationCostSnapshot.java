package org.example.shield.ai.dto;

public record RagOptimizationCostSnapshot(
        double dailyExternalApiCost,
        double averageExternalApiCostPerQuery
) {
}
