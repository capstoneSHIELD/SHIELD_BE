package org.example.shield.ai.dto;

public record RetrievalScoreObservation(
        String queryId,
        RetrievalScoreMethod method,
        double score,
        boolean relevant
) {
    public RetrievalScoreObservation {
        queryId = queryId == null ? "" : queryId.trim();
        method = method == null ? RetrievalScoreMethod.WEIGHTED : method;
    }
}
