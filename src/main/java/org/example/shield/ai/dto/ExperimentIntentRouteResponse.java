package org.example.shield.ai.dto;

/**
 * local/test 전용 intent classification 실험 응답.
 *
 * <p>provider별 raw 호출 결과와 parser 결과를 함께 담아 Python benchmark runner가
 * parse 안정성, latency, token, fallback 여부를 분리해 측정할 수 있게 한다.
 */
public record ExperimentIntentRouteResponse(
        String provider,
        String requestedProvider,
        String mode,
        String inputDomain,
        String responseId,
        String rawJson,
        ExperimentIntentRouteParsedResponse parsed,
        Integer tokensInput,
        Integer tokensOutput,
        Integer latencyMs,
        boolean parseSuccess,
        boolean schemaSuccess,
        boolean fallbackUsed,
        String errorType,
        String errorMessage
) {
    public static ExperimentIntentRouteResponse configError(
            String requestedProvider,
            String mode,
            String inputDomain,
            String message
    ) {
        return new ExperimentIntentRouteResponse(
                null,
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
                "config_error",
                message
        );
    }
}
