package org.example.shield.ai.dto;

public record AiRagRollbackDecision(
        boolean rollback,
        String reason
) {
    public static AiRagRollbackDecision rollback(String reason) {
        return new AiRagRollbackDecision(true, reason);
    }

    public static AiRagRollbackDecision keep(String reason) {
        return new AiRagRollbackDecision(false, reason);
    }
}
