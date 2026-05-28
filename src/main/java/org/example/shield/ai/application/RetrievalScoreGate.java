package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.RagFeatureMode;
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

/**
 * Retrieval 결과의 점수가 임계값 미만이면 drop하는 gate.
 *
 * <p>P5.1 Commit 5: 기존 boolean {@code enabled}를 {@link RagFeatureMode} 분기로 확장.
 * <ul>
 *   <li>{@link RagFeatureMode#OFF} — gate 미작동 (기본값)</li>
 *   <li>{@link RagFeatureMode#SHADOW} — pass/drop 결정만 메트릭 기록, 실제 filter 안 함</li>
 *   <li>{@link RagFeatureMode#ENFORCE} — threshold 미만 후보 drop</li>
 *   <li>{@link RagFeatureMode#SAMPLED} — gate에 무의미하므로 {@code OFF}와 동일</li>
 * </ul>
 *
 * <p>임계값은 점수 산정 방식 별로 별도 설정 (weighted/rrf/rerank 점수는 분포가 다름).
 */
@Component
@Slf4j
public class RetrievalScoreGate {

    private final RagMetrics ragMetrics;

    /** Legacy boolean flag — 하위 호환을 위해 유지. {@code true}면 ENFORCE로 강제. */
    @Value("${app.ai.rag.retrieval-gate.enabled:false}")
    private boolean legacyEnabled;

    /** P5.1 Commit 5 — 신규 mode 분기. legacy enabled=true면 무시되고 ENFORCE 강제. */
    @Value("${app.ai.rag.retrieval-gate.mode:off}")
    private String modeRaw;

    @Value("${app.ai.rag.retrieval-gate.weighted-threshold:}")
    private String weightedThreshold;

    @Value("${app.ai.rag.retrieval-gate.rrf-threshold:}")
    private String rrfThreshold;

    @Value("${app.ai.rag.retrieval-gate.rerank-threshold:}")
    private String rerankThreshold;

    public RetrievalScoreGate(RagMetrics ragMetrics) {
        this.ragMetrics = ragMetrics;
    }

    /**
     * 현재 mode 계산. legacy {@code enabled=true}는 {@code ENFORCE}로 마이그레이션.
     */
    RagFeatureMode currentMode() {
        if (legacyEnabled) {
            return RagFeatureMode.ENFORCE;
        }
        return RagFeatureMode.fromOrThrow(modeRaw, "AI_RAG_RETRIEVAL_GATE_MODE");
    }

    public RetrievalScoreGateDecision evaluate(RetrievalScoreCandidate candidate) {
        if (candidate == null) {
            return RetrievalScoreGateDecision.allowed("empty_candidate", null);
        }
        RagFeatureMode mode = currentMode();
        if (mode == RagFeatureMode.OFF || mode == RagFeatureMode.SAMPLED) {
            return RetrievalScoreGateDecision.allowed("disabled", null);
        }

        OptionalDouble threshold = thresholdFor(candidate.method());
        if (threshold.isEmpty()) {
            return RetrievalScoreGateDecision.allowed("uncalibrated", null);
        }

        double thresholdValue = threshold.getAsDouble();
        boolean passed = candidate.score() >= thresholdValue;
        String method = candidate.method().name().toLowerCase(Locale.ROOT);

        // SHADOW: pass/drop 모두 기록, 실제 filter는 OFF처럼 동작
        if (mode == RagFeatureMode.SHADOW) {
            ragMetrics.recordRetrievalGate(method, passed ? "shadow_pass" : "shadow_drop");
            return RetrievalScoreGateDecision.allowed(
                    passed ? "shadow_pass" : "shadow_drop",
                    thresholdValue);
        }

        // ENFORCE
        if (passed) {
            return RetrievalScoreGateDecision.allowed("passed", thresholdValue);
        }
        ragMetrics.recordRetrievalGate(method, "dropped");
        return RetrievalScoreGateDecision.blocked("below_calibrated_threshold", thresholdValue);
    }

    public <T extends RetrievedDocument> List<T> filter(List<T> documents, RetrievalScoreMethod method) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        RagFeatureMode mode = currentMode();
        if (mode == RagFeatureMode.OFF || mode == RagFeatureMode.SAMPLED) {
            return documents;
        }
        if (thresholdFor(method).isEmpty()) {
            return documents;
        }

        // SHADOW: 결정만 기록, 모두 통과시킴 (user-facing 변화 0)
        if (mode == RagFeatureMode.SHADOW) {
            documents.forEach(document -> evaluate(new RetrievalScoreCandidate(
                    document.kind(), method, document.score())));
            return documents;
        }

        // ENFORCE: 실제 filter
        return documents.stream()
                .filter(document -> evaluate(new RetrievalScoreCandidate(
                        document.kind(), method, document.score())).allowed())
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
            log.warn("RetrievalScoreGate threshold 파싱 실패 — method={}, raw='{}', errorMsg={}",
                    safeMethod, raw, e.getMessage());
            return OptionalDouble.empty();
        }
    }
}
