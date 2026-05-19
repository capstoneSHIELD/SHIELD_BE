package org.example.shield.ai.dto;

public record DynamicPlanSlotMismatch(
        String slotId,
        String field,
        String dynamicPlanValue,
        String slotStateValue
) {
}
