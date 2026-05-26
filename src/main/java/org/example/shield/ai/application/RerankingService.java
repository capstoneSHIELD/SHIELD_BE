package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.config.RagFeatureMode;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.provider.AiRerankClient;
import org.example.shield.ai.provider.RerankResult;
import org.example.shield.ai.safety.RerankCircuitBreaker;
import org.example.shield.ai.util.ConversationDeterministicSampler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Retrieval 결과를 query 관련성 기준으로 후단 재정렬하는 서비스 (P5.4 Commit 2).
 *
 * <h3>Mode 분기 ({@code AI_RAG_RERANK_MODE})</h3>
 * <ul>
 *   <li>{@link RagFeatureMode#OFF} (기본) — 입력 그대로 상위 topN 반환</li>
 *   <li>{@link RagFeatureMode#SHADOW} — rerank 호출하고 메트릭 기록, 결과는 weighted 순서 유지</li>
 *   <li>{@link RagFeatureMode#SAMPLED} — conversationId 기반 deterministic 비율로 적용 (P5.4 C3)</li>
 *   <li>{@link RagFeatureMode#ENFORCE} — 모든 요청에 적용</li>
 * </ul>
 *
 * <h3>Fallback 정책</h3>
 * Rerank 호출 실패(timeout / API error / invalid response) 시 즉시 weighted 상위 topN으로 복귀.
 * user-facing request는 절대 rerank만으로 실패하지 않는다.
 */
@Service
@Slf4j
public class RerankingService {

    @Value("${app.ai.rag.rerank.mode:off}")
    private String modeRaw;

    @Value("${app.ai.rag.rerank.sampling-rate:0.0}")
    private double samplingRate;

    @Value("${app.ai.rag.rerank.candidate-n:20}")
    private int candidateN;

    @Value("${app.ai.rag.rerank.top-n:5}")
    private int topN;

    private final AiRerankClient rerankClient;
    private final CohereApiConfig cohereConfig;
    private final AiRagOperationalMetrics operationalMetrics;
    private final RerankCircuitBreaker circuitBreaker;

    /** Test-friendly 생성자 — 메트릭/breaker 미주입. */
    public RerankingService(AiRerankClient rerankClient, CohereApiConfig cohereConfig) {
        this(rerankClient, cohereConfig, null, null);
    }

    /** Test-friendly 생성자 — breaker 없이 메트릭만. */
    public RerankingService(AiRerankClient rerankClient,
                            CohereApiConfig cohereConfig,
                            AiRagOperationalMetrics operationalMetrics) {
        this(rerankClient, cohereConfig, operationalMetrics, null);
    }

    @Autowired
    public RerankingService(AiRerankClient rerankClient,
                            CohereApiConfig cohereConfig,
                            @Nullable AiRagOperationalMetrics operationalMetrics,
                            @Nullable RerankCircuitBreaker circuitBreaker) {
        this.rerankClient = rerankClient;
        this.cohereConfig = cohereConfig;
        this.operationalMetrics = operationalMetrics;
        this.circuitBreaker = circuitBreaker;
    }

    RagFeatureMode currentMode() {
        return RagFeatureMode.fromOrThrow(modeRaw, "AI_RAG_RERANK_MODE");
    }

    /**
     * 후보 리스트를 query 기준으로 재정렬. mode에 따라 동작 다름.
     *
     * @param query          사용자 쿼리
     * @param candidates     weighted retrieval의 상위 후보 (보통 topN×2~4개)
     * @param desiredTopN    최종 반환할 상위 N (기본 yaml topN 사용)
     * @param conversationId sampled mode에서 deterministic 비율 적용용 (null이면 무시)
     * @param <T>            {@link RetrievedDocument} 구현체
     * @return 재정렬된 상위 N (mode에 따라 weighted 또는 reranked 순서)
     */
    public <T extends RetrievedDocument> List<T> rerank(
            String query,
            List<T> candidates,
            int desiredTopN,
            String conversationId) {

        int targetTopN = desiredTopN > 0 ? desiredTopN : topN;
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        // 빈 query면 rerank 의미 없음 → weighted fallback
        if (query == null || query.isBlank()) {
            return takeTop(candidates, targetTopN);
        }

        RagFeatureMode mode = currentMode();
        String modeTag = mode.name().toLowerCase(Locale.ROOT);

        if (mode == RagFeatureMode.OFF) {
            record(modeTag, "skipped");
            return takeTop(candidates, targetTopN);
        }

        // P5.4 Commit 3: 회로 차단기가 trip된 경우 logical OFF
        if (circuitBreaker != null && circuitBreaker.isLogicalOff()) {
            record(modeTag, "circuit_open");
            return takeTop(candidates, targetTopN);
        }

        if (mode == RagFeatureMode.SAMPLED) {
            if (!ConversationDeterministicSampler.shouldApply(conversationId, samplingRate)) {
                record(modeTag, "skipped");
                return takeTop(candidates, targetTopN);
            }
        }

        // SHADOW / (SAMPLED && shouldApply) / ENFORCE 모두 rerank 시도
        try {
            RerankResult result = callRerank(query, candidates);
            List<T> reranked = applyRerankOrder(candidates, result, targetTopN);
            recordBreaker(false);

            if (mode == RagFeatureMode.SHADOW) {
                record(modeTag, "shadow_executed");
                // shadow: 결과는 weighted 순서 유지 (user-facing 변화 0)
                return takeTop(candidates, targetTopN);
            }
            record(modeTag, "applied");
            return reranked;
        } catch (Exception e) {
            String reason = classifyFailure(e);
            log.warn("Rerank fallback (mode={}, reason={}, error={})", modeTag, reason, e.getMessage());
            if (operationalMetrics != null) {
                operationalMetrics.recordRerankFallback(reason);
            }
            recordBreaker(true);
            record(modeTag, "fallback");
            return takeTop(candidates, targetTopN);
        }
    }

    private void recordBreaker(boolean fallback) {
        if (circuitBreaker != null) {
            try {
                circuitBreaker.recordResult(fallback);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private RerankResult callRerank(String query, List<? extends RetrievedDocument> candidates) {
        String model = cohereConfig.getRerankModel();
        List<String> documents = extractDocuments(candidates, candidateN);
        long startNanos = System.nanoTime();
        try {
            RerankResult result = rerankClient.rerank(model, query, documents, Math.min(documents.size(), candidateN));
            if (operationalMetrics != null) {
                long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
                operationalMetrics.recordRerankLatency(model, Duration.ofMillis(latencyMs), "success");
            }
            return result;
        } catch (Exception e) {
            if (operationalMetrics != null) {
                long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
                operationalMetrics.recordRerankLatency(model, Duration.ofMillis(latencyMs), "failure");
            }
            throw e;
        }
    }

    /**
     * Rerank 결과의 index 순서대로 후보 재배열 + 상위 targetTopN.
     * Rerank가 반환하지 않은 index는 결과에 포함되지 않는다.
     */
    private <T extends RetrievedDocument> List<T> applyRerankOrder(
            List<T> candidates, RerankResult result, int targetTopN) {
        if (result == null || result.items() == null || result.items().isEmpty()) {
            return takeTop(candidates, targetTopN);
        }
        List<T> reordered = new ArrayList<>(targetTopN);
        for (RerankResult.RerankedItem item : result.items()) {
            int idx = item.index();
            if (idx >= 0 && idx < candidates.size()) {
                reordered.add(candidates.get(idx));
                if (reordered.size() >= targetTopN) {
                    break;
                }
            }
        }
        return reordered.isEmpty() ? takeTop(candidates, targetTopN) : reordered;
    }

    /**
     * 후보의 텍스트 추출 — LegalChunk는 content, Precedent는 holding 우선.
     */
    static List<String> extractDocuments(List<? extends RetrievedDocument> candidates, int maxItems) {
        List<String> documents = new ArrayList<>(Math.min(candidates.size(), maxItems));
        for (int i = 0; i < candidates.size() && i < maxItems; i++) {
            documents.add(textOf(candidates.get(i)));
        }
        return documents;
    }

    static String textOf(RetrievedDocument doc) {
        if (doc instanceof LegalChunk law) {
            return safe(law.content());
        }
        if (doc instanceof Precedent pre) {
            String holding = safe(pre.holding());
            if (!holding.isBlank()) return holding;
            String headnote = safe(pre.headnote());
            if (!headnote.isBlank()) return headnote;
            return safe(pre.caseName());
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private <T> List<T> takeTop(List<T> list, int n) {
        return list.size() <= n ? List.copyOf(list) : List.copyOf(list.subList(0, n));
    }

    private void record(String mode, String outcome) {
        if (operationalMetrics != null) {
            try {
                operationalMetrics.recordRerankOutcome(mode, outcome);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private String classifyFailure(Exception e) {
        String name = e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("timeout")) {
            return "timeout";
        }
        if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("null")) {
            return "invalid_response";
        }
        return "api_error";
    }
}
