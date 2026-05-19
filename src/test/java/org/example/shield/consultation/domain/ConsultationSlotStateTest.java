package org.example.shield.consultation.domain;

import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationSlotStateTest {

    @Test
    @DisplayName("Consultation stores slot_state JSONB object as a SlotLedger")
    void updateSlotState() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(SlotStateItem.staticChecklist(
                "static_001", "deposit amount", true, 1, false, SlotValueType.MONEY)));

        consultation.updateSlotState(ledger);

        assertThat(consultation.getSlotState()).isSameAs(ledger);
        assertThat(consultation.getSlotState().getSlots()).hasSize(1);
    }
}
