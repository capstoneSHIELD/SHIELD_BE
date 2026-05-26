package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.HyperClovaApiConfig;
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
 * HyperCLOVA X Chat Completions HTTP 클라이언트 — Chat/Brief 생성용 (Phase P5.5 Commit 4).
 *
 * <p>{@link HyperClovaJudgeClient}와 구조는 동일하지만 chat용으로 timeout/temperature 기본값이 다름.
 * judge는 짧고 결정적인 응답을, chat은 더 긴 자연어 응답이 필요해 별도 클라이언트로 분리.
 *
 * <p>본 클라이언트는 raw 응답만 반환. provider-neutral 변환은
 * {@link org.example.shield.ai.provider.hyperclova.HyperClovaChatClientAdapter} 에서 수행.
 *
 * <p>본 phase에서 chat은 항상 shadow only이며 user-facing 경로에 영향을 주지 않음.
 */
@Component
@Slf4j
public class HyperClovaChatClient {

    private final WebClient hyperClovaWebClient;
    private final HyperClovaApiConfig config;
    private final AiRagOperationalMetrics operationalMetrics;

    public HyperClovaChatClient(@Qualifier("hyperClovaWebClient") WebClient hyperClovaWebClient,
                                HyperClovaApiConfig config) {
        this(hyperClovaWebClient, config, null);
    }

    @Autowired
    public HyperClovaChatClient(@Qualifier("hyperClovaWebClient") WebClient hyperClovaWebClient,
                                HyperClovaApiConfig config,
                                AiRagOperationalMetrics operationalMetrics) {
        this.hyperClovaWebClient = hyperClovaWebClient;
        this.config = config;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * Chat Completions API 호출 — chat/brief 생성용.
     *
     * @param model     모델 ID (예: {@code "HCX-005"})
     * @param messages  system + user 메시지
     * @param maxTokens 응답 토큰 상한
     * @return raw 응답 + latency (ms)
     */
    public ChatCallResult callChat(String model, List<HyperClovaChatRequest.Message> messages, int maxTokens) {
        if (!config.isApiKeyConfigured()) {
            throw new AnalysisFailedException("HYPERCLOVA_API_KEY is not configured");
        }
        HyperClovaChatRequest request = HyperClovaChatRequest.builder()
                .messages(messages)
                .topP(0.8)
                .temperature(0.3)
                .maxTokens(maxTokens)
                .repetitionPenalty(1.1)
                .includeAiFilters(false)
                .build();
        long startNanos = System.nanoTime();

        try {
            HyperClovaChatResponse response = hyperClovaWebClient.post()
                    .uri("/testapp/v3/chat-completions/{model}", model)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(HyperClovaChatResponse.class)
                    .timeout(Duration.ofMillis(config.getReadTimeout()))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(750))
                            .filter(this::isRetryable))
                    .block();

            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            if (response == null || !response.isSuccess()) {
                String code = response == null || response.getStatus() == null
                        ? "null" : response.getStatus().getCode();
                throw new AnalysisFailedException(
                        "HyperCLOVA Chat API non-success response (code=" + code + ")");
            }
            log.debug("HyperCLOVA Chat 호출 성공: model={}, inputLen={}, outputLen={}, latency={}ms",
                    model,
                    response.getResult() == null ? null : response.getResult().getInputLength(),
                    response.getResult() == null ? null : response.getResult().getOutputLength(),
                    latencyMs);

            return new ChatCallResult(response, latencyMs);
        } catch (AnalysisFailedException e) {
            recordError("chat", e);
            throw e;
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordError("chat", e);
            log.warn("HyperCLOVA Chat 호출 실패: latency={}ms, error={}", latencyMs, e.getMessage());
            throw new AnalysisFailedException("HyperCLOVA Chat 호출 실패: " + e.getMessage(), e);
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

    public record ChatCallResult(HyperClovaChatResponse response, long latencyMs) { }
}
