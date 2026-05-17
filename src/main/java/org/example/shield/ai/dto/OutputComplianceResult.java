package org.example.shield.ai.dto;

public record OutputComplianceResult(
        boolean deterministicViolation,
        boolean shadowScheduled,
        boolean blockingApplied,
        String maskedText,
        String reason
) {
}
