package org.example.shield.experiment.controller.dto;

import java.util.List;

public record IntentRouteExperimentPreflightRequest(
        List<String> providers
) {
}
