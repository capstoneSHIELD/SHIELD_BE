package org.example.shield.ai.dto.slot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SlotValueType {
    MONEY("money"),
    DATE("date"),
    TEXT("text");

    private final String value;

    SlotValueType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SlotValueType from(String value) {
        if (value == null || value.isBlank()) {
            return TEXT;
        }
        for (SlotValueType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TEXT;
    }
}
