package org.example.shield.ai.application;

public enum RagFusionMode {
    WEIGHTED,
    RRF;

    public static RagFusionMode from(String value) {
        if (value == null || value.isBlank()) {
            return WEIGHTED;
        }
        for (RagFusionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return WEIGHTED;
    }
}
