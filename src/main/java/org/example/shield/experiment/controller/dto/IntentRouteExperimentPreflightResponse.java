package org.example.shield.experiment.controller.dto;

import java.util.Map;

public record IntentRouteExperimentPreflightResponse(
        Map<String, Boolean> providers
) {
}
