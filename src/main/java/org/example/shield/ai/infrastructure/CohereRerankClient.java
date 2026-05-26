package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.consultation.exception.AnalysisFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Cohere {@code POST /v2/rerank} HTTP 클라이언트 (Phase P5.4 Commit 1).
 *
 * <p>저수준 HTTP 호출만 담당. provider-neutral 변환은
 * {@link org.example.shield.ai.provider.cohere.CohereRerankClientAdapter}가 수행.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>Timeout 짧게 (기본 2초) — RAG 파이프라인 지연 회귀 회피</li>
 *   <li>429/5xx 재시도 3회 (exponential backoff)</li>
 *   <li>실패는 {@link AnalysisFailedException}으로 wrapping — 호출자가 fallback 결정</li>
 *   <li>토큰 소모량 메트릭은 {@code billed_units.search_units}로 별도 기록</li>
 * </ul>
 */
@Component
@Slf4j
public class CohereRerankClient {

    private final WebClient cohereWebClient;
    private final CohereApiConfig config;
    private final AiRagOperationalMetrics operationalMetrics;

    public CohereRerankClient(@Qualifier("cohereWebClient") WebClient cohereWebClient,
                              CohereApiConfig config) {
        this(cohereWebClient, config, null);
    }

    @Autowired
    public CohereRerankClient(@Qualifier("cohereWebClient") WebClient cohereWebClient,
                              CohereApiConfig config,
                              AiRagOperationalMetrics operationalMetrics) {
        this.cohereWebClient = cohereWebClient;
        this.config = config;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * Rerank API 호출.
     *
     * @param model     예: {@code "rerank-v3.5"}
     * @param query     사용자 쿼리
     * @param documents 재정렬 대상 문서 텍스트 (1~1000개)
     * @param topN      반환할 상위 N
     * @return raw {@link CohereRerankResponse} + 측정 latency (밀리초)
     */
    public RerankCallResult callRerank(String model, String query, List<String> documents, int topN) {
        CohereRerankRequest request = CohereRerankRequest.of(model, query, documents, topN);
        long startNanos = System.nanoTime();

        try {
            CohereRerankResponse response = cohereWebClient.post()
                    .uri("/v2/rerank")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohereRerankResponse.class)
                    .timeout(Duration.ofMillis(config.getRerankReadTimeout()))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                            .filter(this::isRetryable))
                    .block();

            if (response == null || response.getResults() == null) {
                throw new AnalysisFailedException("Cohere Rerank API 응답이 null 또는 results 누락");
            }

            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("Cohere Rerank 호출 성공: id={}, model={}, candidates={}, returned={}, latency={}ms",
                    response.getId(), model, documents.size(), response.getResults().size(), latencyMs);

            return new RerankCallResult(response, latencyMs);
        } catch (AnalysisFailedException e) {
            recordError("rerank", e);
            throw e;
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordError("rerank", e);
            log.error("Cohere Rerank 호출 실패: latency={}ms, error={}", latencyMs, e.getMessage(), e);
            throw new AnalysisFailedException("Cohere Rerank 호출 실패: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private void recordError(String operation, Throwable e) {
        if (operationalMetrics == null) {
            return;
        }
        String status = e instanceof WebClientResponseException wce
                ? String.valueOf(wce.getStatusCode().value())
                : e.getClass().getSimpleName();
        operationalMetrics.recordAiApiError("cohere", operation, status);
    }

    /**
     * Rerank API 호출 결과 + latency 묶음.
     *
     * @param response  Cohere API 응답
     * @param latencyMs 호출 지연 (밀리초)
     */
    public record RerankCallResult(CohereRerankResponse response, long latencyMs) { }
}
