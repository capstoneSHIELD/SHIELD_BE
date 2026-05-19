package org.example.shield.ai.dto;

import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStatus;

public record DynamicPlanSlotProposal(
        String id,
        String label,
        SlotSource source,
        String staticMappingId,
        boolean required,
        int priority,
        SlotStatus status,
        String question,
        String validationHint,
        String skipCondition
) {
}
