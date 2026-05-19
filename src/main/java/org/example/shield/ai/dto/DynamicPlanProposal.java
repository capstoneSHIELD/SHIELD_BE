package org.example.shield.ai.dto;

import java.util.List;

public record DynamicPlanProposal(
        CaseTypeResult caseType,
        double planConfidence,
        List<DynamicPlanSlotProposal> slots,
        String nextSlotId,
        boolean allCompleted
) {
    public DynamicPlanProposal {
        caseType = caseType == null ? CaseTypeResult.empty() : caseType;
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
