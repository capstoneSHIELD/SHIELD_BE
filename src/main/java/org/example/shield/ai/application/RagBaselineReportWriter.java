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
        StringBuilder sb = new StringBuilder("""
                # AI/RAG v2.2 Baseline

                | Metric | Value |
                |---|---:|
                | Evaluated At | %s |
                | Method | %s |
                | Query Count | %d |
                | Mixed Recall@5 | %.4f |
                | Statute Recall@5 | %.4f |
                | Case Recall@5 | %.4f |
                | MRR | %.4f |
                | nDCG@5 | %.4f |
                | Graded nDCG Query Count | %d |
                | Expected Reference Mention Rate | %.4f |
                | Expected Reference Mention Query Count | %d |
                | Empty Rate | %.4f |
                | Latency p50 ms | %.1f |
                | Latency p95 ms | %.1f |
                | False Drop Candidates | %d |
                """.formatted(
                result.evaluatedAt(),
                result.method(),
                result.queryCount(),
                result.mixedRecallAt5(),
                result.statuteRecallAt5(),
                result.caseRecallAt5(),
                result.mrr(),
                result.ndcgAt5(),
                result.gradedNdcgQueryCount(),
                result.expectedReferenceMentionRate(),
                result.expectedReferenceMentionQueryCount(),
                result.emptyRate(),
                result.latencyP50Ms(),
                result.latencyP95Ms(),
                result.falseDropCandidateCount()));
        if (result.splitMetrics() != null && !result.splitMetrics().isEmpty()) {
            sb.append("\n## Split Metrics\n\n");
            sb.append("| Split | Queries | Statute Q | Case Q | Mixed R@5 | Statute R@5 | Case R@5 | MRR | nDCG@5 | Ref Mention | Ref Q | Empty | p95 ms |\n");
            sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            result.splitMetrics().values().forEach(metric -> sb.append(
                    "| %s | %d | %d | %d | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %d | %.4f | %.1f |\n"
                            .formatted(
                                    metric.split(),
                                    metric.queryCount(),
                                    metric.statuteQueryCount(),
                                    metric.caseQueryCount(),
                                    metric.mixedRecallAt5(),
                                    metric.statuteRecallAt5(),
                                    metric.caseRecallAt5(),
                                    metric.mrr(),
                                    metric.ndcgAt5(),
                                    metric.expectedReferenceMentionRate(),
                                    metric.expectedReferenceMentionQueryCount(),
                                    metric.emptyRate(),
                                    metric.latencyP95Ms())));
        }
        return sb.toString();
    }
}
