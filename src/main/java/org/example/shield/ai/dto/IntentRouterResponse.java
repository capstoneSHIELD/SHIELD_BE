package org.example.shield.ai.dto;

import java.util.List;

public record IntentRouterResponse(
        String schemaVersion,
        DialogueIntent dialogueIntent,
        double intentConfidence,
        List<ExtractedSlot> extractedSlots,
        CaseTypeResult caseType,
        List<String> retrievalQueries,
        List<String> correctedSlotIds,
        boolean topicChanged,
        IntentClassificationResult legacyClassification
) {
    public IntentRouterResponse {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "2.0" : schemaVersion;
        dialogueIntent = dialogueIntent == null ? DialogueIntent.PROVIDE_INFO : dialogueIntent;
        extractedSlots = extractedSlots == null ? List.of() : List.copyOf(extractedSlots);
        caseType = caseType == null ? CaseTypeResult.empty() : caseType;
        retrievalQueries = retrievalQueries == null ? List.of() : List.copyOf(retrievalQueries);
        correctedSlotIds = correctedSlotIds == null ? List.of() : List.copyOf(correctedSlotIds);
    }

    public static IntentRouterResponse fromLegacy(IntentClassificationResult legacy) {
        return new IntentRouterResponse(
                "1.0",
                DialogueIntent.PROVIDE_INFO,
                0.0,
                List.of(),
                CaseTypeResult.empty(),
                legacy == null ? List.of() : legacy.retrievalQueries(),
                List.of(),
                false,
                legacy
        );
    }

    public static IntentRouterResponse fallback(String query) {
        IntentClassificationResult legacy = new IntentClassificationResult(
                "1.0",
                "Intent router fallback",
                List.of(),
                new IntentClassificationResult.Keywords(List.of(), List.of()),
                List.of(query == null || query.isBlank() ? "legal consultation" : query)
        );
        return fromLegacy(legacy);
    }

    public IntentClassificationResult toClassificationResult() {
        if (legacyClassification != null) {
            return legacyClassification;
        }
        return new IntentClassificationResult(
                schemaVersion,
                dialogueIntent.name(),
                List.of(),
                new IntentClassificationResult.Keywords(List.of(), List.of()),
                retrievalQueries
        );
    }

    public boolean hasExtractedSlots() {
        return extractedSlots != null && !extractedSlots.isEmpty();
    }
}
