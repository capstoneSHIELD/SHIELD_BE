package org.example.shield.ai.dto;

import java.util.List;

public record RagPipelineResult(
        IntentRouterResponse intent,
        String ragContext,
        List<RetrievedDocument> retrievalResults
) {
    public RagPipelineResult {
        ragContext = ragContext == null ? "" : ragContext;
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
    }

    public static RagPipelineResult empty(IntentRouterResponse intent) {
        return new RagPipelineResult(intent, "", List.of());
    }
}
