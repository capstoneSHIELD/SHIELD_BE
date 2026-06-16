package org.example.shield.experiment.controller.dto;

import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentRouterResponse;

import java.util.List;

public record IntentRouteExperimentResponse(
        String provider,
        String requestedProvider,
        String mode,
        String inputDomain,
        String responseId,
        String rawJson,
        ParsedIntentRoute parsed,
        Integer tokensInput,
        Integer tokensOutput,
        Integer latencyMs,
        boolean parseSuccess,
        boolean schemaSuccess,
        boolean fallbackUsed,
        String errorType,
        String errorMessage
) {
    public static IntentRouteExperimentResponse success(
            String provider,
            String requestedProvider,
            String mode,
            String inputDomain,
            AiCallResult<String> raw,
            IntentRouterResponse parsed,
            boolean includeRaw) {
        return new IntentRouteExperimentResponse(
                provider,
                requestedProvider,
                mode,
                inputDomain,
                raw.responseId(),
                includeRaw ? raw.data() : null,
                ParsedIntentRoute.from(parsed),
                raw.tokensInput(),
                raw.tokensOutput(),
                raw.latencyMs(),
                true,
                true,
                false,
                null,
                null
        );
    }

    public static IntentRouteExperimentResponse error(
            String provider,
            String requestedProvider,
            String mode,
            String inputDomain,
            String errorType,
            String errorMessage) {
        return new IntentRouteExperimentResponse(
                provider,
                requestedProvider,
                mode,
                inputDomain,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                errorType,
                errorMessage
        );
    }

    public static IntentRouteExperimentResponse parseFailure(
            String provider,
            String requestedProvider,
            String mode,
            String inputDomain,
            AiCallResult<String> raw,
            String errorMessage) {
        return new IntentRouteExperimentResponse(
                provider,
                requestedProvider,
                mode,
                inputDomain,
                raw.responseId(),
                raw.data(),
                null,
                raw.tokensInput(),
                raw.tokensOutput(),
                raw.latencyMs(),
                false,
                false,
                false,
                "parse_failure",
                errorMessage
        );
    }

    public record ParsedIntentRoute(
            String schemaVersion,
            String dialogueIntent,
            double intentConfidence,
            CaseTypeResult caseType,
            List<String> matchedNodeIds,
            List<String> coreKeywords,
            List<String> retrievalQueries
    ) {
        static ParsedIntentRoute from(IntentRouterResponse response) {
            IntentClassificationResult classification = response.toClassificationResult();
            IntentClassificationResult.Keywords keywords = classification.keywords();
            return new ParsedIntentRoute(
                    response.schemaVersion(),
                    response.dialogueIntent().name(),
                    response.intentConfidence(),
                    response.caseType(),
                    classification.matchedNodeIds(),
                    keywords == null ? List.of() : keywords.core(),
                    response.retrievalQueries()
            );
        }
    }
}
