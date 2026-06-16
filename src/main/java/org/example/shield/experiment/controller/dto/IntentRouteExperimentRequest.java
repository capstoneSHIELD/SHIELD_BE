package org.example.shield.experiment.controller.dto;

import java.util.List;

public record IntentRouteExperimentRequest(
        String provider,
        String mode,
        String domain,
        List<MessagePayload> messages,
        Boolean includeRaw
) {
    public record MessagePayload(
            String role,
            String content
    ) {
    }
}
