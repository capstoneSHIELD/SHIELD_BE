package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record IntentShadowEvalRecord(
        @JsonProperty("consultation_id")
        UUID consultationId,
        @JsonProperty("message_id")
        UUID messageId,
        @JsonProperty("turn_index")
        int turnIndex,
        @JsonProperty("user_text_hash")
        String userTextHash,
        @JsonProperty("dialogue_intent")
        DialogueIntent dialogueIntent,
        @JsonProperty("intent_confidence")
        double intentConfidence,
        @JsonProperty("extracted_slots")
        List<ExtractedSlot> extractedSlots,
        @JsonProperty("case_type")
        CaseTypeResult caseType,
        @JsonProperty("would_skip_cohere")
        boolean wouldSkipCohere,
        @JsonProperty("fixed_response_type")
        String fixedResponseType,
        @JsonProperty("mixed_utterance_detected")
        boolean mixedUtteranceDetected,
        @JsonProperty("created_at")
        LocalDateTime createdAt
) {
    public IntentShadowEvalRecord {
        extractedSlots = extractedSlots == null ? List.of() : List.copyOf(extractedSlots);
        caseType = caseType == null ? CaseTypeResult.empty() : caseType;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
