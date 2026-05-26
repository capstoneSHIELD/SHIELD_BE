package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.cache.EmbeddingCache;
import org.example.shield.ai.cache.EmbeddingCacheKey;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiEmbeddingClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 쿼리 임베딩 생성 서비스.
 *
 * <p>P5.1 Commit 2부터 {@link AiEmbeddingClient} 인터페이스를 통해 호출.
 * P5.3 Commit 2부터 {@link EmbeddingCache}로 동일 쿼리 반복 시 provider 호출 절감.
 *
 * <p>캐시 정책:
 * <ul>
 *   <li>Key: provider + model + input_type + dimension + normalize_version + sha256(normalized text)</li>
 *   <li>모델 변경 시 자동 무효화 (key prefix 다름)</li>
 *   <li>cache hit/miss/store 메트릭은 {@link AiRagOperationalMetrics#recordEmbeddingCache}로 기록</li>
 *   <li>Cohere 호출 지연·성공률은 기존 {@link RagMetrics#timeCohereEmbed} 타이머 유지</li>
 * </ul>
 */
@Service
@Slf4j
public class QueryEmbeddingService {

    private static final String PROVIDER = "cohere";
    private static final String INPUT_TYPE = "search_query";
    private static final String NORMALIZE_VERSION = "v1";

    private final AiEmbeddingClient embeddingClient;
    private final CohereApiConfig cohereConfig;
    private final RagMetrics ragMetrics;
    private final EmbeddingCache embeddingCache;
    private final AiRagOperationalMetrics operationalMetrics;

    /** Test-friendly 생성자 — cache + ops metric 미주입. */
    public QueryEmbeddingService(AiEmbeddingClient embeddingClient,
                                 CohereApiConfig cohereConfig,
                                 RagMetrics ragMetrics) {
        this(embeddingClient, cohereConfig, ragMetrics, null, null);
    }

    @Autowired
    public QueryEmbeddingService(AiEmbeddingClient embeddingClient,
                                 CohereApiConfig cohereConfig,
                                 RagMetrics ragMetrics,
                                 EmbeddingCache embeddingCache,
                                 AiRagOperationalMetrics operationalMetrics) {
        this.embeddingClient = embeddingClient;
        this.cohereConfig = cohereConfig;
        this.ragMetrics = ragMetrics;
        this.embeddingCache = embeddingCache;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * 쿼리 임베딩을 생성해 반환한다 (캐시 적용).
     *
     * @param query 검색 쿼리
     * @return 임베딩 벡터 (provider 호출 실패 시 RuntimeException 상위 전파)
     */
    public float[] embedQuery(String query) {
        String model = cohereConfig.getEmbedModel();
        EmbeddingCacheKey key = buildKey(query, model);

        // 1. cache lookup
        Optional<float[]> cached = lookup(key, model);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. provider 호출
        EmbeddingResult result = ragMetrics.timeCohereEmbed(() -> embeddingClient.embedQuery(model, query));
        float[] vector = result == null ? null : result.firstVector();

        // 3. cache store (성공 시만)
        if (key != null && embeddingCache != null && vector != null && vector.length > 0) {
            try {
                embeddingCache.put(key, vector);
                recordCache(model, "store");
            } catch (Exception e) {
                log.warn("Embedding cache put 실패 (model={}): {}", model, e.getMessage());
                recordCache(model, "error");
            }
        }
        return vector;
    }

    private EmbeddingCacheKey buildKey(String query, String model) {
        if (embeddingCache == null || query == null || query.isBlank() || model == null) {
            return null;
        }
        String normalized = normalize(query);
        String hash = EmbeddingCacheKey.sha256Hex(normalized);
        int dimension = cohereConfig.getEmbedDimension();
        return new EmbeddingCacheKey(
                PROVIDER, model, INPUT_TYPE, dimension, NORMALIZE_VERSION, hash);
    }

    private Optional<float[]> lookup(EmbeddingCacheKey key, String model) {
        if (key == null || embeddingCache == null) {
            return Optional.empty();
        }
        try {
            Optional<float[]> cached = embeddingCache.get(key);
            recordCache(model, cached.isPresent() ? "hit" : "miss");
            return cached;
        } catch (Exception e) {
            log.warn("Embedding cache get 실패 (model={}): {}", model, e.getMessage());
            recordCache(model, "error");
            return Optional.empty();
        }
    }

    private void recordCache(String model, String outcome) {
        if (operationalMetrics != null) {
            try {
                operationalMetrics.recordEmbeddingCache(model, outcome);
            } catch (Exception ignored) {
                // 메트릭 실패는 best-effort
            }
        }
    }

    /**
     * 텍스트 정규화 (normalize_version=v1).
     * trim → 연속 공백 1개로 → lowercase (영문/숫자만 영향).
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
