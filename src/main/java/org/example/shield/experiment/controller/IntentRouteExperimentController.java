package org.example.shield.experiment.controller;

import lombok.RequiredArgsConstructor;
import org.example.shield.experiment.application.IntentRouteExperimentService;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentPreflightRequest;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentPreflightResponse;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentRequest;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "test"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/experiments")
public class IntentRouteExperimentController {

    private final IntentRouteExperimentService service;

    @PostMapping("/intent-route/preflight")
    public IntentRouteExperimentPreflightResponse preflight(
            @RequestBody(required = false) IntentRouteExperimentPreflightRequest request) {
        return new IntentRouteExperimentPreflightResponse(
                service.preflight(request == null ? null : request.providers()));
    }

    @PostMapping("/intent-route")
    public IntentRouteExperimentResponse route(@RequestBody IntentRouteExperimentRequest request) {
        return service.route(request);
    }
}
