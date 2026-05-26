package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.infrastructure.CohereClient;
import org.example.shield.ai.infrastructure.CohereMetricEmitter;
import org.example.shield.ai.provider.AiEmbeddingClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cohere {@link AiEmbeddingClient} adapter.
 *
 * <p>{@link CohereClient#embedQueryWithMetadata(String, String)} /
 * {@link CohereClient#embedDocumentsWithMetadata(String, List)}를 통해
 * provider 응답의 {@code inputTokens}와 {@code latencyMs}를 그대로 전달한다.
 *
 * <p>P5.1 Commit 4 refine: 메트릭 emit은 {@link CohereMetricEmitter}에 위임 (중복 제거).
 */
@Component
public class CohereEmbeddingClientAdapter implements AiEmbeddingClient {

    private final CohereClient cohereClient;
    private final CohereMetricEmitter metricEmitter;

    public CohereEmbeddingClientAdapter(CohereClient cohereClient) {
        this(cohereClient, null);
    }

    @Autowired
    public CohereEmbeddingClientAdapter(CohereClient cohereClient, CohereMetricEmitter metricEmitter) {
        this.cohereClient = cohereClient;
        this.metricEmitter = metricEmitter;
    }

    @Override
    public EmbeddingResult embedQuery(String model, String text) {
        EmbeddingResult result = cohereClient.embedQueryWithMetadata(model, text);
        emit(model, result);
        return result;
    }

    @Override
    public EmbeddingResult embedDocuments(String model, List<String> texts) {
        EmbeddingResult result = cohereClient.embedDocumentsWithMetadata(model, texts);
        emit(model, result);
        return result;
    }

    private void emit(String model, EmbeddingResult result) {
        if (metricEmitter != null) {
            metricEmitter.emitEmbed(model, result);
        }
    }
}
