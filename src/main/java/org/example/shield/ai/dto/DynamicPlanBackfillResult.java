package org.example.shield.ai.dto;

import java.util.List;

public record DynamicPlanBackfillResult(
        boolean dryRun,
        int inspected,
        int convertible,
        int written,
        List<String> skippedReasons
) {
    public DynamicPlanBackfillResult {
        skippedReasons = skippedReasons == null ? List.of() : List.copyOf(skippedReasons);
    }
}
