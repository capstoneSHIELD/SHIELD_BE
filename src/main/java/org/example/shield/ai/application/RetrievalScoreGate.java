package org.example.shield.ai.application;

import org.example.shield.ai.dto.RetrievalScoreCandidate;
import org.example.shield.ai.dto.RetrievalScoreGateDecision;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

@Component
public class RetrievalScoreGate {

    private final RagMetrics ragMetrics;

    @Value("${app.ai.rag.retrieval-gate.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.rag.retrieval-gate.weighted-threshold:}")
    private String weightedThreshold;

    @Value("${app.ai.rag.retrieval-gate.rrf-threshold:}")
    private String rrfThreshold;

    @Value("${app.ai.rag.retrieval-gate.rerank-threshold:}")
    private String rerankThreshold;

    public RetrievalScoreGate(RagMetrics ragMetrics) {
        this.ragMetrics = ragMetrics;
    }

    public RetrievalScoreGateDecision evaluate(RetrievalScoreCandidate candidate) {
        if (candidate == null) {
            return RetrievalScoreGateDecision.allowed("empty_candidate", null);
        }
        if (!enabled) {
            return RetrievalScoreGateDecision.allowed("disabled", null);
        }

        OptionalDouble threshold = thresholdFor(candidate.method());
        if (threshold.isEmpty()) {
            return RetrievalScoreGateDecision.allowed("uncalibrated", null);
        }

        double thresholdValue = threshold.getAsDouble();
        if (candidate.score() >= thresholdValue) {
            return RetrievalScoreGateDecision.allowed("passed", thresholdValue);
        }
        ragMetrics.recordRetrievalGate(candidate.method().name().toLowerCase(Locale.ROOT), "dropped");
        return RetrievalScoreGateDecision.blocked("below_calibrated_threshold", thresholdValue);
    }

    public <T extends RetrievedDocument> List<T> filter(List<T> documents, RetrievalScoreMethod method) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (!enabled || thresholdFor(method).isEmpty()) {
            return documents;
        }
        return documents.stream()
                .filter(document -> evaluate(new RetrievalScoreCandidate(
                        document.kind(),
                        method,
                        document.score())).allowed())
                .toList();
    }

    private OptionalDouble thresholdFor(RetrievalScoreMethod method) {
        RetrievalScoreMethod safeMethod = method == null ? RetrievalScoreMethod.WEIGHTED : method;
        String raw = switch (safeMethod) {
            case WEIGHTED -> weightedThreshold;
            case RRF -> rrfThreshold;
            case RERANK -> rerankThreshold;
        };
        if (raw == null || raw.isBlank()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
