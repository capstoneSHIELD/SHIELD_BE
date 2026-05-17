package org.example.shield.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.springframework.stereotype.Component;

@Component
public class RagBaselineReportWriter {

    private final ObjectMapper objectMapper;

    public RagBaselineReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(RagBaselineEvaluationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize RAG baseline result", e);
        }
    }

    public String toMarkdown(RagBaselineEvaluationResult result) {
        if (result == null) {
            return "# AI/RAG v2.2 Baseline\n\nNo result.\n";
        }
        return """
                # AI/RAG v2.2 Baseline

                | Metric | Value |
                |---|---:|
                | Evaluated At | %s |
                | Method | %s |
                | Query Count | %d |
                | Recall@5 | %.4f |
                | MRR | %.4f |
                | nDCG@5 | %.4f |
                | Latency p50 ms | %.1f |
                | Latency p95 ms | %.1f |
                | False Drop Candidates | %d |
                """.formatted(
                result.evaluatedAt(),
                result.method(),
                result.queryCount(),
                result.recallAt5(),
                result.mrr(),
                result.ndcgAt5(),
                result.latencyP50Ms(),
                result.latencyP95Ms(),
                result.falseDropCandidateCount());
    }
}
