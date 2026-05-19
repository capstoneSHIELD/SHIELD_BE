package org.example.shield.ai.dto.checklist;

import org.example.shield.ai.dto.slot.SlotValueType;

public record ChecklistScopeItem(
        String slotId,
        String label,
        ChecklistScopeLevel level,
        boolean required,
        int priority,
        String sourcePath,
        String nodeId,
        SlotValueType valueType
) {
}
