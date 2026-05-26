package org.example.shield.ai.provider.cohere;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.CohereClient;
import org.example.shield.ai.infrastructure.CohereCostCalculator;
import org.example.shield.ai.provider.AiEmbeddingClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Cohere {@link AiEmbeddingClient} adapter.
 *
 * <p>{@link CohereClient#embedQueryWithMetadata(String, String)} /
 * {@link CohereClient#embedDocumentsWithMetadata(String, List)}를 통해
 * provider 응답의 {@code inputTokens}와 {@code latencyMs}를 그대로 전달한다.
 *
 * <p>P5.1 Commit 4부터 호출 직후 token/cost/latency 메트릭을 best-effort로 emit한다
 * (metric 발행 실패는 호출 결과에 영향 없음).
 */
@Component
@Slf4j
public class CohereEmbeddingClientAdapter implements AiEmbeddingClient {

    private final CohereClient cohereClient;
    private final AiRagOperationalMetrics operationalMetrics;
    private final CohereCostCalculator costCalculator;

    public CohereEmbeddingClientAdapter(CohereClient cohereClient) {
        this(cohereClient, null, null);
    }

    @Autowired
    public CohereEmbeddingClientAdapter(CohereClient cohereClient,
                                        AiRagOperationalMetrics operationalMetrics,
                                        CohereCostCalculator costCalculator) {
        this.cohereClient = cohereClient;
        this.operationalMetrics = operationalMetrics;
        this.costCalculator = costCalculator;
    }

    @Override
    public EmbeddingResult embedQuery(String model, String text) {
        EmbeddingResult result = cohereClient.embedQueryWithMetadata(model, text);
        emitMetrics(model, result);
        return result;
    }

    @Override
    public EmbeddingResult embedDocuments(String model, List<String> texts) {
        EmbeddingResult result = cohereClient.embedDocumentsWithMetadata(model, texts);
        emitMetrics(model, result);
        return result;
    }

    private void emitMetrics(String model, EmbeddingResult result) {
        if (operationalMetrics == null || result == null) {
            return;
        }
        try {
            // Embed는 output token이 없음 — input만 기록
            operationalMetrics.recordCohereTokens(model, "embed", "input",
                    result.inputTokens(), /*estimated=*/false);
            if (costCalculator != null) {
                double cost = costCalculator.estimate(model, result.inputTokens(), 0);
                operationalMetrics.recordCohereEstimatedCost(model, "embed", cost);
            }
            if (result.latencyMs() >= 0) {
                operationalMetrics.recordCohereLatency(model, "embed",
                        Duration.ofMillis(result.latencyMs()), "success");
            }
        } catch (Exception e) {
            log.warn("Cohere embed metric emit 실패 (model={}): {}",
                    model, Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }
}
