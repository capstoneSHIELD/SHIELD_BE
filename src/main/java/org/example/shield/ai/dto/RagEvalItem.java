package org.example.shield.ai.dto;

import java.util.List;
import java.util.Map;

public record RagEvalItem(
        String id,
        String split,
        String nodeId,
        String l1,
        String l2,
        String l3,
        String domain,
        String query,
        List<String> keywords,
        List<String> expectedChunkIds,
        List<RagEvalLawRef> expectedLawRefs,
        List<String> expectedDocumentIds,
        Map<String, Integer> relevanceJudgments,
        String failureType,
        String source,
        String reviewer,
        String createdAt
) {
    public RagEvalItem {
        split = split == null || split.isBlank() ? "dev" : split;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        expectedChunkIds = expectedChunkIds == null ? List.of() : List.copyOf(expectedChunkIds);
        expectedLawRefs = expectedLawRefs == null ? List.of() : List.copyOf(expectedLawRefs);
        expectedDocumentIds = expectedDocumentIds == null ? List.of() : List.copyOf(expectedDocumentIds);
        relevanceJudgments = relevanceJudgments == null ? Map.of() : Map.copyOf(relevanceJudgments);
    }

    public RagEvalItem(
            String id,
            String domain,
            String query,
            List<String> expectedChunkIds,
            List<RagEvalLawRef> expectedLawRefs,
            String failureType,
            String source,
            String reviewer
    ) {
        this(id,
                "dev",
                null,
                domain,
                null,
                null,
                domain,
                query,
                List.of(),
                expectedChunkIds,
                expectedLawRefs,
                List.of(),
                Map.of(),
                failureType,
                source,
                reviewer,
                null);
    }
}
