package org.example.shield.ai.dto.slot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SlotLedger {

    @JsonProperty("schema_version")
    private String schemaVersion = "1.0";

    private CaseType caseType = new CaseType();

    private List<SlotStateItem> slots = new ArrayList<>();

    private String updatedAt;

    public static SlotLedger empty() {
        SlotLedger ledger = new SlotLedger();
        ledger.updatedAt = SlotStateItem.now();
        return ledger;
    }

    public boolean hasSlots() {
        return slots != null && !slots.isEmpty();
    }

    public void touch() {
        updatedAt = SlotStateItem.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CaseType {
        private String l1;
        private String l2;
        private String l3;

        public CaseType(String l1, String l2, String l3) {
            this.l1 = l1;
            this.l2 = l2;
            this.l3 = l3;
        }
    }
}
