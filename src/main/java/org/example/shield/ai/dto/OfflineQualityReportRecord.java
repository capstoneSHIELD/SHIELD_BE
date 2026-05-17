package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record OfflineQualityReportRecord(
        @JsonProperty("consultation_id")
        UUID consultationId,
        String domain,
        @JsonProperty("repeat_question_count")
        int repeatQuestionCount,
        @JsonProperty("missing_slots")
        List<String> missingSlots,
        @JsonProperty("legal_leak_expressions")
        List<String> legalLeakExpressions,
        @JsonProperty("retrieval_failure_type")
        String retrievalFailureType,
        @JsonProperty("dynamic_to_static_candidates")
        List<String> dynamicToStaticCandidates,
        @JsonProperty("review_required")
        boolean reviewRequired
) {
    public OfflineQualityReportRecord {
        missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
        legalLeakExpressions = legalLeakExpressions == null ? List.of() : List.copyOf(legalLeakExpressions);
        dynamicToStaticCandidates = dynamicToStaticCandidates == null ? List.of() : List.copyOf(dynamicToStaticCandidates);
        retrievalFailureType = retrievalFailureType == null ? "none" : retrievalFailureType;
    }
}
