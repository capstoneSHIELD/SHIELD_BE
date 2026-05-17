package org.example.shield.ai.dto;

import java.util.List;

public record RrfFusionResult(
        String id,
        double rrfScore,
        int bestRank,
        double bestOriginalScore,
        List<String> sources
) {
    public RrfFusionResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
