package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.cache.CaffeineEmbeddingCache;
import org.example.shield.ai.cache.EmbeddingCache;
import org.example.shield.ai.cache.NoopEmbeddingCache;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiEmbeddingClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P5.3 Commit 2 — {@link QueryEmbeddingService} cache 통합 동작 검증.
 *
 * <ul>
 *   <li>Caffeine 캐시 통합 시 동일 query 2회 호출 → provider 1회만</li>
 *   <li>Noop 캐시 시 매번 provider 호출 (BC)</li>
 *   <li>hit / miss / store 메트릭 카운팅</li>
 *   <li>normalize: 대소문자·공백 차이는 같은 캐시 키로 매핑</li>
 *   <li>모델 다르면 별도 캐시 슬롯</li>
 * </ul>
 */
class QueryEmbeddingServiceCacheTest {

    private AiEmbeddingClient embeddingClient;
    private CohereApiConfig cohereConfig;
    private RagMetrics ragMetrics;
    private SimpleMeterRegistry opsRegistry;
    private AiRagOperationalMetrics operationalMetrics;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(AiEmbeddingClient.class);
        cohereConfig = mock(CohereApiConfig.class);
        when(cohereConfig.getEmbedModel()).thenReturn("embed-v4.0");
        when(cohereConfig.getEmbedDimension()).thenReturn(1024);
        ragMetrics = new RagMetrics(new SimpleMeterRegistry());
        opsRegistry = new SimpleMeterRegistry();
        operationalMetrics = new AiRagOperationalMetrics(opsRegistry);
    }

    private static EmbeddingResult resultOf(float[] vector) {
        return new EmbeddingResult(null, vector == null ? List.of() : List.of(vector), 10, 50L);
    }

    @Test
    @DisplayName("Caffeine 캐시 — 동일 query 2회 호출 시 provider는 1회만")
    void caffeineCacheHitsOnSecondCall() {
        EmbeddingCache cache = new CaffeineEmbeddingCache(100, Duration.ofMinutes(60));
        QueryEmbeddingService svc = new QueryEmbeddingService(
                embeddingClient, cohereConfig, ragMetrics, cache, operationalMetrics);

        float[] vector = new float[]{0.5f, 0.6f};
        when(embeddingClient.embedQuery(eq("embed-v4.0"), eq("전세금 반환")))
                .thenReturn(resultOf(vector));

        float[] first = svc.embedQuery("전세금 반환");
        float[] second = svc.embedQuery("전세금 반환");

        assertThat(first).isSameAs(vector);
        assertThat(second).isSameAs(vector);
        verify(embeddingClient, times(1)).embedQuery("embed-v4.0", "전세금 반환");

        // miss → store → hit 순서로 기록
        assertThat(opsRegistry.counter(AiRagOperationalMetrics.EMBEDDING_CACHE,
                "model", "embed-v4.0", "outcome", "miss").count()).isEqualTo(1.0);
        assertThat(opsRegistry.counter(AiRagOperationalMetrics.EMBEDDING_CACHE,
                "model", "embed-v4.0", "outcome", "store").count()).isEqualTo(1.0);
        assertThat(opsRegistry.counter(AiRagOperationalMetrics.EMBEDDING_CACHE,
                "model", "embed-v4.0", "outcome", "hit").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Noop 캐시 — 매번 provider 호출 (BC)")
    void noopCacheAlwaysMiss() {
        QueryEmbeddingService svc = new QueryEmbeddingService(
                embeddingClient, cohereConfig, ragMetrics, new NoopEmbeddingCache(), operationalMetrics);

        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenReturn(resultOf(new float[]{0.1f}));

        svc.embedQuery("쿼리");
        svc.embedQuery("쿼리");

        verify(embeddingClient, times(2)).embedQuery("embed-v4.0", "쿼리");
    }

    @Test
    @DisplayName("normalize — 공백·대소문자 차이는 같은 캐시 슬롯")
    void normalizationEqualsCacheKey() {
        EmbeddingCache cache = new CaffeineEmbeddingCache(100, Duration.ofMinutes(60));
        QueryEmbeddingService svc = new QueryEmbeddingService(
                embeddingClient, cohereConfig, ragMetrics, cache, operationalMetrics);

        when(embeddingClient.embedQuery(eq("embed-v4.0"), anyString()))
                .thenReturn(resultOf(new float[]{0.7f}));

        svc.embedQuery("Hello World");
        svc.embedQuery("  hello   world  ");

        // 같은 캐시 슬롯이라 provider는 1회만
        verify(embeddingClient, times(1)).embedQuery(eq("embed-v4.0"), anyString());
    }

    @Test
    @DisplayName("다른 모델 — 별도 캐시 슬롯")
    void differentModelDifferentSlot() {
        EmbeddingCache cache = new CaffeineEmbeddingCache(100, Duration.ofMinutes(60));
        QueryEmbeddingService svc = new QueryEmbeddingService(
                embeddingClient, cohereConfig, ragMetrics, cache, operationalMetrics);

        when(embeddingClient.embedQuery(eq("embed-v4.0"), eq("q")))
                .thenReturn(resultOf(new float[]{0.1f}));
        when(embeddingClient.embedQuery(eq("embed-v5.0"), eq("q")))
                .thenReturn(resultOf(new float[]{0.9f}));

        // 1st call: model v4
        svc.embedQuery("q");
        // model change → 별도 캐시 슬롯
        when(cohereConfig.getEmbedModel()).thenReturn("embed-v5.0");
        svc.embedQuery("q");

        verify(embeddingClient, times(1)).embedQuery("embed-v4.0", "q");
        verify(embeddingClient, times(1)).embedQuery("embed-v5.0", "q");
    }

    @Test
    @DisplayName("legacy 3-arg constructor — cache/ops 없이 정상 동작 (BC)")
    void legacyConstructorWorks() {
        QueryEmbeddingService svc = new QueryEmbeddingService(embeddingClient, cohereConfig, ragMetrics);
        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenReturn(resultOf(new float[]{0.4f}));

        float[] result = svc.embedQuery("test");

        assertThat(result).containsExactly(0.4f);
    }
}
