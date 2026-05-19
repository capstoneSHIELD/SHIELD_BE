package org.example.shield.ai.dto;

public record RrfFusionInput(
        String id,
        String source,
        int rank,
        double originalScore
) {
    public RrfFusionInput {
        id = id == null ? "" : id.trim();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        rank = Math.max(1, rank);
    }
}
