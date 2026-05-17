package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DialogueIntent {
    PROVIDE_INFO,
    CORRECT_INFO,
    CONFIRM,
    CHANGE_TOPIC,
    ASK_LEGAL_ADVICE,
    IRRELEVANT,
    GREETING,
    END_CONSULTATION;

    @JsonCreator
    public static DialogueIntent from(String value) {
        if (value == null || value.isBlank()) {
            return PROVIDE_INFO;
        }
        for (DialogueIntent intent : values()) {
            if (intent.name().equalsIgnoreCase(value.trim())) {
                return intent;
            }
        }
        return PROVIDE_INFO;
    }

    @JsonValue
    public String value() {
        return name();
    }
}
