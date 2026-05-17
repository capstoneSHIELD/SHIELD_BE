package org.example.shield.ai.dto;

public record RetrievalScoreCandidate(
        String id,
        RetrievalScoreMethod method,
        double score
) {
    public RetrievalScoreCandidate {
        id = id == null ? "" : id.trim();
        method = method == null ? RetrievalScoreMethod.WEIGHTED : method;
    }
}
