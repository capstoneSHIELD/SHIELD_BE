package org.example.shield.ai.dto;

import java.util.List;
import java.util.Map;

public record RagEvalSetValidationResult(
        boolean valid,
        List<String> failures,
        List<String> warnings,
        Map<String, Long> splitCounts,
        int itemCount,
        int doubleLabeledItemCount,
        double doubleLabelRate
) {
    public RagEvalSetValidationResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        splitCounts = splitCounts == null ? Map.of() : Map.copyOf(splitCounts);
    }
}
