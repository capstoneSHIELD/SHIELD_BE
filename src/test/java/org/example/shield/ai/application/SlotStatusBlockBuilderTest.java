package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotStatusBlockBuilderTest {

    private final SlotStatusBlockBuilder builder = new SlotStatusBlockBuilder();

    @Test
    @DisplayName("Slot Status Block keeps collected, pending, missing, and asked sections at the top")
    void build_allSections() {
        SlotLedger ledger = SlotLedger.empty();
        SlotStateItem collected = SlotStateItem.staticChecklist(
                "static_001", "deposit amount", true, 1, true, SlotValueType.MONEY);
        collected.setCollectedValue("30000000");

        SlotStateItem pending = SlotStateItem.staticChecklist(
                "static_002", "lease end date", true, 2, false, SlotValueType.DATE);
        pending.setStatus(SlotStatus.PENDING_CONFIRMATION);
        pending.setPendingValue("last December");

        SlotStateItem missing = SlotStateItem.staticChecklist(
                "static_003", "landlord response", true, 3, false, SlotValueType.TEXT);
        missing.appendAskedQuestion("Why has the landlord refused?");
        ledger.setSlots(List.of(collected, pending, missing));

        String block = builder.build(ledger);

        assertThat(block).startsWith("=== COLLECTED INFORMATION (DO NOT ASK AGAIN) ===");
        assertThat(block).contains("deposit amount: 30000000");
        assertThat(block).contains("=== PENDING CONFIRMATION ===");
        assertThat(block).contains("lease end date: last December");
        assertThat(block).contains("=== MISSING INFORMATION (TARGET ONLY THESE) ===");
        assertThat(block).contains("landlord response");
        assertThat(block).contains("=== ALREADY ASKED QUESTIONS (DO NOT REPEAT) ===");
        assertThat(block).contains("Why has the landlord refused?");
        assertThat(block).contains("RULE: Never ask again about collected items.");
    }

    @Test
    @DisplayName("empty ledger returns empty block")
    void build_empty() {
        assertThat(builder.build(null)).isEmpty();
        assertThat(builder.build(SlotLedger.empty())).isEmpty();
    }
}
