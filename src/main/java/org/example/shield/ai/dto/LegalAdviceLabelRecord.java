package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LegalAdviceLabelRecord(
        @JsonProperty("consultation_id")
        UUID consultationId,
        @JsonProperty("message_id")
        UUID messageId,
        @JsonProperty("expected_intent")
        DialogueIntent expectedIntent,
        @JsonProperty("actual_intent")
        DialogueIntent actualIntent,
        @JsonProperty("legal_advice_risk")
        boolean legalAdviceRisk,
        @JsonProperty("high_risk_leak")
        boolean highRiskLeak,
        @JsonProperty("skip_false_positive")
        boolean skipFalsePositive,
        @JsonProperty("reviewer_role")
        String reviewerRole,
        @JsonProperty("review_comment")
        String reviewComment
) {
}
