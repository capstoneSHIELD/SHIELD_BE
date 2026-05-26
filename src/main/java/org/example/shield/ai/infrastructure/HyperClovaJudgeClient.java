package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.HyperClovaApiConfig;
import org.example.shield.consultation.exception.AnalysisFailedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * HyperCLOVA X Chat Completions HTTP 클라이언트 — Judge 호출용 (Phase P5.5 Commit 1).
 *
 * <p>저수준 HTTP만 담당. provider-neutral 변환은
 * {@link org.example.shield.ai.provider.hyperclova.HyperClovaJudgeClientAdapter}.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>Timeout 짧게 (기본 3s) — judge 호출이 user-facing latency를 막지 않도록</li>
 *   <li>429/5xx 재시도 3회 (exponential backoff)</li>
 *   <li>API key 미설정 시 즉시 throw — 잘못된 sampling 활성을 조기 차단</li>
 * </ul>
 */
@Component
@Slf4j
public class HyperClovaJudgeClient {

    private final WebClient hyperClovaWebClient;
    private final HyperClovaApiConfig config;
    private final AiRagOperationalMetrics operationalMetrics;

    public HyperClovaJudgeClient(@Qualifier("hyperClovaWebClient") WebClient hyperClovaWebClient,
                                 HyperClovaApiConfig config) {
        this(hyperClovaWebClient, config, null);
    }

    public HyperClovaJudgeClient(@Qualifier("hyperClovaWebClient") WebClient hyperClovaWebClient,
                                 HyperClovaApiConfig config,
                                 AiRagOperationalMetrics operationalMetrics) {
        this.hyperClovaWebClient = hyperClovaWebClient;
        this.config = config;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * Chat Completions API 호출.
     *
     * @param model    모델 ID (예: {@code "HCX-005"})
     * @param messages system + user 메시지 (judge 프롬프트 포함)
     * @param maxTokens 응답 토큰 상한
     * @return raw 응답 + latency (ms)
     */
    public JudgeCallResult callJudge(String model, List<HyperClovaChatRequest.Message> messages, int maxTokens) {
        if (!config.isApiKeyConfigured()) {
            throw new AnalysisFailedException("HYPERCLOVA_API_KEY is not configured");
        }
        HyperClovaChatRequest request = HyperClovaChatRequest.forJudge(messages, maxTokens);
        long startNanos = System.nanoTime();

        try {
            HyperClovaChatResponse response = hyperClovaWebClient.post()
                    .uri("/testapp/v3/chat-completions/{model}", model)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(HyperClovaChatResponse.class)
                    .timeout(Duration.ofMillis(config.getJudgeReadTimeout()))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                            .filter(this::isRetryable))
                    .block();

            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            if (response == null || !response.isSuccess()) {
                String code = response == null || response.getStatus() == null
                        ? "null" : response.getStatus().getCode();
                throw new AnalysisFailedException(
                        "HyperCLOVA Judge API non-success response (code=" + code + ")");
            }
            log.info("HyperCLOVA Judge 호출 성공: model={}, inputLen={}, outputLen={}, latency={}ms",
                    model,
                    response.getResult() == null ? null : response.getResult().getInputLength(),
                    response.getResult() == null ? null : response.getResult().getOutputLength(),
                    latencyMs);

            return new JudgeCallResult(response, latencyMs);
        } catch (AnalysisFailedException e) {
            recordError("judge", e);
            throw e;
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordError("judge", e);
            log.error("HyperCLOVA Judge 호출 실패: latency={}ms, error={}", latencyMs, e.getMessage(), e);
            throw new AnalysisFailedException("HyperCLOVA Judge 호출 실패: " + e.getMessage(), e);
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
        operationalMetrics.recordAiApiError("hyperclova", operation, status);
    }

    /**
     * API 호출 결과 + latency 묶음.
     */
    public record JudgeCallResult(HyperClovaChatResponse response, long latencyMs) { }
}
