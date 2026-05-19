package org.example.shield.ai.dto;

public record ExtractedSlot(
        String slotId,
        String value,
        String rawText,
        double confidence,
        String valueType,
        boolean needsConfirmation
) {
}
