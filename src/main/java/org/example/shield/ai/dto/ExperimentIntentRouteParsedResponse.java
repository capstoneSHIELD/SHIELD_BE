package org.example.shield.ai.dto;

import java.util.List;

/**
 * 실험용 intent-route adapter가 반환하는 parser 결과 요약 DTO.
 *
 * <p>운영 API 계약이 아니라 local/test benchmark runner 전용 계약이다.
 * persistence entity나 운영 response DTO와 섞지 않는다.
 */
public record ExperimentIntentRouteParsedResponse(
        String schemaVersion,
        DialogueIntent dialogueIntent,
        double intentConfidence,
        CaseTypeResult caseType,
        List<String> matchedNodeIds,
        List<String> coreKeywords,
        List<String> retrievalQueries
) {
    public ExperimentIntentRouteParsedResponse {
        matchedNodeIds = matchedNodeIds == null ? List.of() : List.copyOf(matchedNodeIds);
        coreKeywords = coreKeywords == null ? List.of() : List.copyOf(coreKeywords);
        retrievalQueries = retrievalQueries == null ? List.of() : List.copyOf(retrievalQueries);
    }

    public static ExperimentIntentRouteParsedResponse from(IntentRouterResponse response) {
        IntentClassificationResult legacy = response.legacyClassification();
        List<String> matchedNodeIds = legacy == null ? List.of() : legacy.matchedNodeIds();
        List<String> coreKeywords = legacy == null || legacy.keywords() == null
                ? List.of()
                : legacy.keywords().core();

        return new ExperimentIntentRouteParsedResponse(
                response.schemaVersion(),
                response.dialogueIntent(),
                response.intentConfidence(),
                response.caseType(),
                matchedNodeIds,
                coreKeywords,
                response.retrievalQueries()
        );
    }
}
