package org.example.shield.ai.dto;

import java.util.List;

public record RagRetrievalHintStatus(
        boolean usable,
        List<String> staleReasons
) {
    public RagRetrievalHintStatus {
        staleReasons = staleReasons == null ? List.of() : List.copyOf(staleReasons);
    }

    public static RagRetrievalHintStatus fresh() {
        return new RagRetrievalHintStatus(true, List.of());
    }

    public static RagRetrievalHintStatus stale(List<String> reasons) {
        return new RagRetrievalHintStatus(false, reasons);
    }
}
