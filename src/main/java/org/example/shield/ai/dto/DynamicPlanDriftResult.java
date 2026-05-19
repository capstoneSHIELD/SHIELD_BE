package org.example.shield.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DynamicPlanDriftResult(
        UUID consultationId,
        UUID planId,
        boolean driftDetected,
        List<String> missingInSlotState,
        List<String> missingInDynamicPlan,
        List<DynamicPlanSlotMismatch> statusMismatches,
        List<DynamicPlanSlotMismatch> valueMismatches,
        LocalDateTime checkedAt
) {
    public DynamicPlanDriftResult {
        missingInSlotState = missingInSlotState == null ? List.of() : List.copyOf(missingInSlotState);
        missingInDynamicPlan = missingInDynamicPlan == null ? List.of() : List.copyOf(missingInDynamicPlan);
        statusMismatches = statusMismatches == null ? List.of() : List.copyOf(statusMismatches);
        valueMismatches = valueMismatches == null ? List.of() : List.copyOf(valueMismatches);
        checkedAt = checkedAt == null ? LocalDateTime.now() : checkedAt;
    }
}
