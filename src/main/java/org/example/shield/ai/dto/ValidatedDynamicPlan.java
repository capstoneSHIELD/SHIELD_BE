package org.example.shield.ai.dto;

import java.util.List;

public record ValidatedDynamicPlan(
        CaseTypeResult caseType,
        double planConfidence,
        List<DynamicPlanSlotProposal> slots,
        List<String> rejectionReasons
) {
    public ValidatedDynamicPlan {
        caseType = caseType == null ? CaseTypeResult.empty() : caseType;
        slots = slots == null ? List.of() : List.copyOf(slots);
        rejectionReasons = rejectionReasons == null ? List.of() : List.copyOf(rejectionReasons);
    }

    public boolean hasAcceptedSlots() {
        return !slots.isEmpty();
    }
}
