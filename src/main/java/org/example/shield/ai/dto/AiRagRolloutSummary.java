package org.example.shield.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiRagRolloutSummary(
        LocalDateTime generatedAt,
        List<AiRagRolloutDecision> decisions
) {
    public AiRagRolloutSummary {
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
