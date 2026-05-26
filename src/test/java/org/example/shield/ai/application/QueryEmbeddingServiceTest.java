package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiEmbeddingClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QueryEmbeddingService}의 provider 호출 위임 검증.
 *
 * <p>P5.1 Commit 2: {@code CohereClient} 직접 의존에서 {@link AiEmbeddingClient}
 * 인터페이스 의존으로 전환. 본 테스트는 (1) provider 위임, (2) 예외 전파,
 * (3) 메트릭 수집을 검증한다.</p>
 *
 * <p>Issue #38에서 임베딩 캐시 레이어를 제거하여 매 호출마다 provider를 호출한다
 * (P5.3에서 Caffeine L1 cache 재도입 예정).</p>
 */
class QueryEmbeddingServiceTest {

    private AiEmbeddingClient embeddingClient;
    private CohereApiConfig cohereConfig;
    private SimpleMeterRegistry meterRegistry;
    private RagMetrics ragMetrics;
    private QueryEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(AiEmbeddingClient.class);
        cohereConfig = mock(CohereApiConfig.class);
        meterRegistry = new SimpleMeterRegistry();
        ragMetrics = new RagMetrics(meterRegistry);
        when(cohereConfig.getEmbedModel()).thenReturn("embed-v4.0");
        service = new QueryEmbeddingService(embeddingClient, cohereConfig, ragMetrics);
    }

    private static EmbeddingResult resultOf(float[] vector) {
        return new EmbeddingResult(null, vector == null ? List.of() : List.of(vector), null, 0L);
    }

    @Test
    @DisplayName("provider 호출 위임 — firstVector 반환")
    void delegatesToProviderAndReturnsFirstVector() {
        float[] computed = new float[]{0.5f, -0.1f};
        when(embeddingClient.embedQuery(eq("embed-v4.0"), eq("임대차 해지")))
                .thenReturn(resultOf(computed));

        float[] result = service.embedQuery("임대차 해지");

        assertThat(result).isSameAs(computed);
        verify(embeddingClient, times(1)).embedQuery("embed-v4.0", "임대차 해지");
    }

    @Test
    @DisplayName("provider 실패 — 예외 상위 전파")
    void providerFailure_propagates() {
        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenThrow(new RuntimeException("Provider down"));

        try {
            service.embedQuery("소유권 이전");
        } catch (RuntimeException expected) {
            assertThat(expected).hasMessage("Provider down");
        }
    }

    @Test
    @DisplayName("빈 임베딩 결과 — null 반환")
    void emptyEmbedding_returnsNull() {
        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenReturn(new EmbeddingResult(null, List.of(), null, 0L));

        float[] result = service.embedQuery("무언가");

        assertThat(result).isNull();
    }

    // === 메트릭 수집 확인 ===

    @Test
    @DisplayName("메트릭 — provider 성공 시 success 타이머 기록")
    void metrics_providerSuccessRecordsSuccessTimer() {
        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenReturn(resultOf(new float[]{0.1f}));

        service.embedQuery("전세");

        assertThat(meterRegistry.timer(RagMetrics.METRIC_COHERE_EMBED, "outcome", "success").count())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("메트릭 — provider 실패 시 failure 타이머 기록")
    void metrics_providerFailureRecordsFailureTimer() {
        when(embeddingClient.embedQuery(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        try {
            service.embedQuery("전세");
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(meterRegistry.timer(RagMetrics.METRIC_COHERE_EMBED, "outcome", "failure").count())
                .isEqualTo(1L);
    }
}
