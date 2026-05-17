package org.example.shield.ai.dto.slot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SlotStatus {
    MISSING("missing"),
    COLLECTED("collected"),
    PENDING_CONFIRMATION("pending_confirmation"),
    SKIPPED("skipped");

    private final String value;

    SlotStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SlotStatus from(String value) {
        if (value == null || value.isBlank()) {
            return MISSING;
        }
        for (SlotStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return MISSING;
    }
}
