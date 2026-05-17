package org.example.shield.ai.application;

import org.example.shield.ai.domain.DynamicPlanSlot;
import org.example.shield.ai.dto.DynamicPlanDriftResult;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPlanDriftDetectorTest {

    private final DynamicPlanDriftDetector detector = new DynamicPlanDriftDetector(null, null);

    @Test
    @DisplayName("drift detector reports no drift when plan slots and slot_state match")
    void noDrift() {
        SlotLedger ledger = ledger(slot("deposit", SlotStatus.COLLECTED, "30000000", null));
        List<DynamicPlanSlot> planSlots = List.of(planSlot("deposit", SlotStatus.COLLECTED, "30000000", null));

        DynamicPlanDriftResult result = detector.compare(UUID.randomUUID(), UUID.randomUUID(), planSlots, ledger);

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.statusMismatches()).isEmpty();
        assertThat(result.valueMismatches()).isEmpty();
    }

    @Test
    @DisplayName("drift detector finds missing slots on both sides")
    void missingSlots() {
        SlotLedger ledger = ledger(slot("slot_state_only", SlotStatus.MISSING, null, null));
        List<DynamicPlanSlot> planSlots = List.of(planSlot("plan_only", SlotStatus.MISSING, null, null));

        DynamicPlanDriftResult result = detector.compare(UUID.randomUUID(), UUID.randomUUID(), planSlots, ledger);

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.missingInSlotState()).containsExactly("plan_only");
        assertThat(result.missingInDynamicPlan()).containsExactly("slot_state_only");
    }

    @Test
    @DisplayName("drift detector finds status and value mismatches")
    void mismatches() {
        SlotLedger ledger = ledger(slot("deposit", SlotStatus.PENDING_CONFIRMATION, null, "30000000"));
        List<DynamicPlanSlot> planSlots = List.of(planSlot("deposit", SlotStatus.COLLECTED, "30000000", null));

        DynamicPlanDriftResult result = detector.compare(UUID.randomUUID(), UUID.randomUUID(), planSlots, ledger);

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.statusMismatches()).extracting("field").contains("status");
        assertThat(result.valueMismatches()).extracting("field")
                .contains("collected_value", "pending_value");
    }

    private SlotLedger ledger(SlotStateItem... slots) {
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(slots));
        return ledger;
    }

    private SlotStateItem slot(String id, SlotStatus status, String collected, String pending) {
        SlotStateItem item = SlotStateItem.staticChecklist(id, id, true, 1, false, SlotValueType.TEXT);
        item.setStatus(status);
        item.setCollectedValue(collected);
        item.setPendingValue(pending);
        return item;
    }

    private DynamicPlanSlot planSlot(String id, SlotStatus status, String collected, String pending) {
        return DynamicPlanSlot.create(
                UUID.randomUUID(),
                id,
                id,
                SlotSource.STATIC_CHECKLIST,
                null,
                true,
                1,
                status,
                collected,
                pending,
                "text",
                null,
                null,
                null);
    }
}
