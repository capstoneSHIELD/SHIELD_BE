package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.provider.AiRerankClient;
import org.example.shield.ai.provider.RerankResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RerankingService} 검증 (P5.4 Commit 2).
 *
 * <p>4가지 mode 분기 (off/shadow/sampled/enforce) + fallback 동작 + 메트릭.
 */
class RerankingServiceTest {

    private AiRerankClient rerankClient;
    private CohereApiConfig cohereConfig;
    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private RerankingService service;

    @BeforeEach
    void setUp() {
        rerankClient = mock(AiRerankClient.class);
        cohereConfig = mock(CohereApiConfig.class);
        when(cohereConfig.getRerankModel()).thenReturn("rerank-v3.5");
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);
        service = new RerankingService(rerankClient, cohereConfig, metrics);
        ReflectionTestUtils.setField(service, "modeRaw", "off");
        ReflectionTestUtils.setField(service, "samplingRate", 0.0);
        ReflectionTestUtils.setField(service, "candidateN", 20);
        ReflectionTestUtils.setField(service, "topN", 5);
    }

    private static LegalChunk law(String articleNo, String content, double score) {
        return new LegalChunk("민법", articleNo, "title", content,
                "2023-01-01", "https://law.go.kr/" + articleNo, score);
    }

    private static Precedent caseDoc(String caseNo, String holding, double score) {
        // Precedent record 순서: caseNo, court, caseName, decisionDate, caseType,
        //                       headnote, holding, sourceUrl, score
        return new Precedent(caseNo, "대법원", "사건명",
                "2024-01-01", "민사",
                "headnote", holding,
                "https://law.go.kr/case/" + caseNo,
                score);
    }

    private static RerankResult rerankResult(List<int[]> indexScorePairs) {
        List<RerankResult.RerankedItem> items = indexScorePairs.stream()
                .map(p -> new RerankResult.RerankedItem(p[0], p[1] / 100.0))
                .toList();
        return new RerankResult(items, 200L, 1);
    }

    @Nested
    @DisplayName("mode=off — rerank 호출 안 함, weighted top 그대로")
    class OffMode {

        @Test
        @DisplayName("rerank 호출 없이 상위 topN 반환")
        void offReturnsTopWithoutRerank() {
            List<LegalChunk> candidates = List.of(
                    law("1", "c1", 0.9), law("2", "c2", 0.8), law("3", "c3", 0.7));

            List<LegalChunk> result = service.rerank("query", candidates, 2, "conv-1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).articleNo()).isEqualTo("1");
            verify(rerankClient, never()).rerank(anyString(), anyString(), anyList(), anyInt());

            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_OUTCOME,
                    "mode", "off", "outcome", "skipped").count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("mode=shadow — rerank 실행하지만 결과는 weighted 순서 유지")
    class ShadowMode {

        @BeforeEach
        void enable() {
            ReflectionTestUtils.setField(service, "modeRaw", "shadow");
        }

        @Test
        @DisplayName("rerank 호출은 하지만 반환은 weighted top")
        void shadowExecutesButReturnsWeighted() {
            List<LegalChunk> candidates = List.of(
                    law("1", "c1", 0.9), law("2", "c2", 0.8), law("3", "c3", 0.7));
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(rerankResult(List.of(new int[]{2, 95}, new int[]{0, 80}, new int[]{1, 60})));

            List<LegalChunk> result = service.rerank("query", candidates, 2, "conv-1");

            // weighted 순서 유지 (1, 2 — rerank가 (3, 1, 2)로 재정렬 권고했지만 무시)
            assertThat(result).hasSize(2);
            assertThat(result.get(0).articleNo()).isEqualTo("1");
            assertThat(result.get(1).articleNo()).isEqualTo("2");

            verify(rerankClient, times(1)).rerank(anyString(), anyString(), anyList(), anyInt());
            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_OUTCOME,
                    "mode", "shadow", "outcome", "shadow_executed").count()).isEqualTo(1.0);
            // latency 메트릭 기록
            assertThat(registry.timer(AiRagOperationalMetrics.RERANK_LATENCY,
                    "model", "rerank-v3.5", "status", "success").count()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("mode=enforce — rerank 결과 그대로 적용")
    class EnforceMode {

        @BeforeEach
        void enable() {
            ReflectionTestUtils.setField(service, "modeRaw", "enforce");
        }

        @Test
        @DisplayName("rerank 권고 순서대로 반환")
        void enforceAppliesRerankedOrder() {
            List<LegalChunk> candidates = List.of(
                    law("1", "c1", 0.9), law("2", "c2", 0.8), law("3", "c3", 0.7));
            // rerank: (idx 2, 0, 1) 순서로 추천
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(rerankResult(List.of(new int[]{2, 95}, new int[]{0, 80}, new int[]{1, 60})));

            List<LegalChunk> result = service.rerank("query", candidates, 2, "conv-1");

            // rerank 권고 순서대로 (3, 1)
            assertThat(result).hasSize(2);
            assertThat(result.get(0).articleNo()).isEqualTo("3");
            assertThat(result.get(1).articleNo()).isEqualTo("1");

            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_OUTCOME,
                    "mode", "enforce", "outcome", "applied").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("rerank 호출 실패 시 weighted topN으로 fallback + fallback 메트릭")
        void enforceFallsBackOnFailure() {
            List<LegalChunk> candidates = List.of(law("1", "c1", 0.9), law("2", "c2", 0.8));
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenThrow(new RuntimeException("API timeout"));

            List<LegalChunk> result = service.rerank("query", candidates, 2, "conv-1");

            // weighted 순서로 fallback (1, 2)
            assertThat(result).hasSize(2);
            assertThat(result.get(0).articleNo()).isEqualTo("1");
            // fallback 메트릭 카운팅
            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_FALLBACK,
                    "reason", "api_error").count()).isEqualTo(1.0);
            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_OUTCOME,
                    "mode", "enforce", "outcome", "fallback").count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("mode=sampled — conversationId deterministic")
    class SampledMode {

        @BeforeEach
        void enable() {
            ReflectionTestUtils.setField(service, "modeRaw", "sampled");
        }

        @Test
        @DisplayName("samplingRate=0 → 항상 skip")
        void samplingZeroAlwaysSkips() {
            ReflectionTestUtils.setField(service, "samplingRate", 0.0);

            List<LegalChunk> result = service.rerank(
                    "query", List.of(law("1", "c", 0.9)), 1, "conv-1");

            verify(rerankClient, never()).rerank(anyString(), anyString(), anyList(), anyInt());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("samplingRate=1.0 → 항상 적용")
        void samplingOneAlwaysApplies() {
            ReflectionTestUtils.setField(service, "samplingRate", 1.0);
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(rerankResult(List.of(new int[]{0, 95})));

            List<LegalChunk> result = service.rerank(
                    "query", List.of(law("1", "c", 0.9)), 1, "conv-1");

            verify(rerankClient, times(1)).rerank(anyString(), anyString(), anyList(), anyInt());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("같은 conversationId는 같은 결정 (deterministic)")
        void deterministicSampling() {
            ReflectionTestUtils.setField(service, "samplingRate", 0.5);
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(rerankResult(List.of(new int[]{0, 95})));

            String cid = "conv-stable";
            List<LegalChunk> candidates = List.of(law("1", "c", 0.9));
            service.rerank("query", candidates, 1, cid);
            service.rerank("query", candidates, 1, cid);
            service.rerank("query", candidates, 1, cid);

            // 같은 conversationId라 결정이 일관됨 — rerank 호출 횟수는 0 또는 3 (모두 같음)
            long callCount = registry.timer(AiRagOperationalMetrics.RERANK_LATENCY,
                    "model", "rerank-v3.5", "status", "success").count();
            assertThat(callCount).isIn(0L, 3L);
        }
    }

    @Nested
    @DisplayName("문서 텍스트 추출")
    class TextExtraction {

        @Test
        @DisplayName("LegalChunk → content")
        void legalChunkUsesContent() {
            String text = RerankingService.textOf(law("1", "법령 본문", 0.9));
            assertThat(text).isEqualTo("법령 본문");
        }

        @Test
        @DisplayName("Precedent → holding 우선, 없으면 headnote, 그것도 없으면 caseName")
        void precedentUsesHolding() {
            Precedent p = caseDoc("2024다1", "판결요지 내용", 0.8);
            assertThat(RerankingService.textOf(p)).isEqualTo("판결요지 내용");
        }

        @Test
        @DisplayName("Precedent holding 빈 경우 headnote fallback")
        void precedentFallbackHeadnote() {
            // headnote만 있고 holding null
            Precedent p = new Precedent("2024다1", "대법원", "사건명",
                    "2024-01-01", "민사",
                    "headnote 내용", null,
                    null, 0.8);
            assertThat(RerankingService.textOf(p)).isEqualTo("headnote 내용");
        }
    }

    @Nested
    @DisplayName("P5.4 Commit 3 — Circuit breaker logical OFF")
    class CircuitBreaker {

        @Test
        @DisplayName("circuitBreaker.isLogicalOff()=true → rerank 호출 안 함")
        void breakerOpenBypassesRerank() {
            org.example.shield.ai.safety.RerankCircuitBreaker breaker = mock(
                    org.example.shield.ai.safety.RerankCircuitBreaker.class);
            when(breaker.isLogicalOff()).thenReturn(true);

            RerankingService svc = new RerankingService(rerankClient, cohereConfig, metrics, breaker);
            ReflectionTestUtils.setField(svc, "modeRaw", "enforce");

            List<LegalChunk> candidates = List.of(law("1", "c", 0.9));
            List<LegalChunk> result = svc.rerank("query", candidates, 1, "conv");

            verify(rerankClient, never()).rerank(anyString(), anyString(), anyList(), anyInt());
            assertThat(result).hasSize(1);
            assertThat(registry.counter(AiRagOperationalMetrics.RERANK_OUTCOME,
                    "mode", "enforce", "outcome", "circuit_open").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("성공 호출 → recordResult(false), 실패 호출 → recordResult(true)")
        void breakerRecordsResults() {
            org.example.shield.ai.safety.RerankCircuitBreaker breaker = mock(
                    org.example.shield.ai.safety.RerankCircuitBreaker.class);
            when(breaker.isLogicalOff()).thenReturn(false);

            RerankingService svc = new RerankingService(rerankClient, cohereConfig, metrics, breaker);
            ReflectionTestUtils.setField(svc, "modeRaw", "enforce");

            // 성공
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(rerankResult(List.of(new int[]{0, 90})));
            svc.rerank("query", List.of(law("1", "c", 0.9)), 1, "conv");
            verify(breaker).recordResult(false);

            // 실패
            when(rerankClient.rerank(anyString(), anyString(), anyList(), anyInt()))
                    .thenThrow(new RuntimeException("API error"));
            svc.rerank("query", List.of(law("2", "c", 0.8)), 1, "conv");
            verify(breaker).recordResult(true);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("빈 candidates → 빈 결과")
        void emptyCandidates() {
            List<RetrievedDocument> result = service.rerank("query", List.of(), 5, "conv");
            assertThat(result).isEmpty();
            verify(rerankClient, never()).rerank(anyString(), anyString(), anyList(), anyInt());
        }

        @Test
        @DisplayName("blank query → rerank skip + weighted top")
        void blankQuerySkips() {
            ReflectionTestUtils.setField(service, "modeRaw", "enforce");

            List<LegalChunk> result = service.rerank(
                    "  ", List.of(law("1", "c", 0.9), law("2", "c", 0.8)), 1, "conv");

            assertThat(result).hasSize(1);
            verify(rerankClient, never()).rerank(anyString(), anyString(), anyList(), anyInt());
        }

        @Test
        @DisplayName("invalid mode → fail-fast")
        void invalidModeThrows() {
            ReflectionTestUtils.setField(service, "modeRaw", "bogus");

            assertThatThrownBy(() ->
                    service.rerank("q", List.of(law("1", "c", 0.9)), 1, "conv"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bogus");
        }
    }
}
