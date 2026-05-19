package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaticQuestionSelectorTest {

    private final StaticQuestionSelector selector = new StaticQuestionSelector();

    @Test
    @DisplayName("selects static required before static optional, dynamic, and pending by default")
    void selectNext_defaultPriority() {
        SlotStateItem dynamic = item("dynamic", SlotSource.DYNAMIC, true, 1, SlotStatus.MISSING);
        SlotStateItem optional = item("optional", SlotSource.STATIC_CHECKLIST, false, 1, SlotStatus.MISSING);
        SlotStateItem required = item("required", SlotSource.STATIC_CHECKLIST, true, 5, SlotStatus.MISSING);
        SlotStateItem pending = item("pending", SlotSource.STATIC_CHECKLIST, true, 0,
                SlotStatus.PENDING_CONFIRMATION);

        SlotStateItem selected = selector.selectNext(List.of(dynamic, optional, pending, required));

        assertThat(selected.getSlotId()).isEqualTo("required");
    }

    @Test
    @DisplayName("pending confirmation can be prioritized when the deterministic heuristic says so")
    void selectNext_pendingFirstWhenRequested() {
        SlotStateItem required = item("required", SlotSource.STATIC_CHECKLIST, true, 1, SlotStatus.MISSING);
        SlotStateItem pending = item("pending", SlotSource.STATIC_CHECKLIST, true, 9,
                SlotStatus.PENDING_CONFIRMATION);

        SlotStateItem selected = selector.selectNext(List.of(required, pending), true);

        assertThat(selected.getSlotId()).isEqualTo("pending");
    }

    private SlotStateItem item(
            String id, SlotSource source, boolean required, int priority, SlotStatus status) {
        SlotStateItem item = SlotStateItem.staticChecklist(
                id, id, required, priority, false, SlotValueType.TEXT);
        item.setSource(source);
        item.setStatus(status);
        return item;
    }
}
