package org.example.shield.ai.dto;

import java.util.List;

public record RagOptimizationAcceptanceDecision(
        boolean accepted,
        List<String> failures,
        List<String> warnings
) {
    public RagOptimizationAcceptanceDecision {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
