package org.example.shield.ai.dto;

public record CorrectedSlot(
        String slotId,
        String previousValue,
        String newValue,
        double confidence
) {
}
