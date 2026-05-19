package org.example.shield.ai.dto.slot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SlotSource {
    STATIC_CHECKLIST("static_checklist"),
    DYNAMIC("dynamic");

    private final String value;

    SlotSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SlotSource from(String value) {
        if (value == null || value.isBlank()) {
            return STATIC_CHECKLIST;
        }
        for (SlotSource source : values()) {
            if (source.value.equalsIgnoreCase(value) || source.name().equalsIgnoreCase(value)) {
                return source;
            }
        }
        return STATIC_CHECKLIST;
    }
}
