package org.example.shield.ai.dto;

import java.util.List;

public record RagRetrievalHint(
        String nodeId,
        List<String> lawIds,
        List<String> categoryIds,
        List<String> queryTerms,
        List<String> caseNos,
        String evidenceVersion,
        String generatedAt,
        String evidenceModifiedAt,
        String ontologyVersion,
        String mappingVersion,
        String generatorVersion
) {
    public RagRetrievalHint {
        lawIds = lawIds == null ? List.of() : List.copyOf(lawIds);
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        queryTerms = queryTerms == null ? List.of() : List.copyOf(queryTerms);
        caseNos = caseNos == null ? List.of() : List.copyOf(caseNos);
    }
}
