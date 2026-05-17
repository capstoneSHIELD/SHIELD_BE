package org.example.shield.ai.dto;

import java.util.List;

public record RagEvalItem(
        String id,
        String domain,
        String query,
        List<String> expectedChunkIds,
        List<RagEvalLawRef> expectedLawRefs,
        String failureType,
        String source,
        String reviewer
) {
    public RagEvalItem {
        expectedChunkIds = expectedChunkIds == null ? List.of() : List.copyOf(expectedChunkIds);
        expectedLawRefs = expectedLawRefs == null ? List.of() : List.copyOf(expectedLawRefs);
    }
}
