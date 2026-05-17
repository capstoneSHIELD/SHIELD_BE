package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class StaticQuestionSelector {

    public SlotStateItem selectNext(List<SlotStateItem> slots) {
        return selectNext(slots, false);
    }

    public SlotStateItem selectNext(List<SlotStateItem> slots, boolean prioritizePendingConfirmation) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }

        return slots.stream()
                .filter(s -> s.getStatus() == SlotStatus.MISSING
                        || s.getStatus() == SlotStatus.PENDING_CONFIRMATION)
                .sorted(Comparator
                        .comparingInt((SlotStateItem s) -> rank(s, prioritizePendingConfirmation))
                        .thenComparingInt(SlotStateItem::getPriority)
                        .thenComparing(SlotStateItem::getSlotId,
                                Comparator.nullsLast(String::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private int rank(SlotStateItem item, boolean prioritizePendingConfirmation) {
        if (item.getStatus() == SlotStatus.PENDING_CONFIRMATION) {
            return prioritizePendingConfirmation ? 0 : 3;
        }
        if (item.getSource() == SlotSource.STATIC_CHECKLIST && item.isRequired()) {
            return prioritizePendingConfirmation ? 1 : 0;
        }
        if (item.getSource() == SlotSource.STATIC_CHECKLIST) {
            return prioritizePendingConfirmation ? 2 : 1;
        }
        return prioritizePendingConfirmation ? 3 : 2;
    }
}
