package org.example.shield.ai.application;

import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.domain.LegalCaseJpaRepository;
import org.example.shield.ai.domain.LegalChunkJpaRepository;
import org.example.shield.ai.dto.LegalChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OfflineRrfRetrievalService} 검증 (P5.4 Commit 5).
 *
 * <p>핵심 속성:
 * <ol>
 *   <li>{@code rrf-offline.enabled=false} (기본) → 호출 시 즉시 IllegalStateException</li>
 *   <li>{@code enabled=true} → 3개 path-specific 쿼리 호출 + RrfFusionService 통한 fusion</li>
 *   <li>fusion 결과의 score는 RRF 점수 (1/(K+rank) 누적)</li>
 * </ol>
 */
class OfflineRrfRetrievalServiceTest {

    private LegalChunkJpaRepository chunkRepo;
    private LegalCaseJpaRepository caseRepo;
    private RrfFusionService rrfFusionService;
    private QueryEmbeddingService queryEmbeddingService;
    private CohereApiConfig cohereConfig;
    private OfflineRrfRetrievalService service;

    @BeforeEach
    void setUp() {
        chunkRepo = mock(LegalChunkJpaRepository.class);
        caseRepo = mock(LegalCaseJpaRepository.class);
        rrfFusionService = new RrfFusionService();
        ReflectionTestUtils.setField(rrfFusionService, "defaultRrfK", 60);
        queryEmbeddingService = mock(QueryEmbeddingService.class);
        cohereConfig = mock(CohereApiConfig.class);
        when(cohereConfig.getEmbedDimension()).thenReturn(1024);

        service = new OfflineRrfRetrievalService(
                chunkRepo, caseRepo, rrfFusionService, queryEmbeddingService, cohereConfig);
        ReflectionTestUtils.setField(service, "rrfOfflineEnabled", false);
        ReflectionTestUtils.setField(service, "candidateN", 40);
    }

    @Test
    @DisplayName("production guard — enabled=false 시 IllegalStateException")
    void disabledThrows() {
        assertThatThrownBy(() ->
                service.retrieveLawsRrf("query", "키워드", new String[]{}, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rrf-offline.enabled=false");

        // repository 호출 안 됨
        verify(chunkRepo, never()).searchVectorOnly(anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("enabled=true — 3개 path-specific 쿼리 호출 후 RRF fusion")
    void enabledCallsAllPaths() {
        ReflectionTestUtils.setField(service, "rrfOfflineEnabled", true);
        when(queryEmbeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        // 3개 path 모두 빈 결과 → fusion도 빈 결과
        when(chunkRepo.searchVectorOnly(anyString(), any(), anyInt())).thenReturn(List.of());
        when(chunkRepo.searchBm25Only(anyString(), any(), anyInt())).thenReturn(List.of());
        when(chunkRepo.searchTrigramOnly(anyString(), any(), anyInt())).thenReturn(List.of());

        List<LegalChunk> result = service.retrieveLawsRrf("쿼리", "키워드", new String[]{"group:civil"}, 5);

        verify(chunkRepo).searchVectorOnly(anyString(), any(), anyInt());
        verify(chunkRepo).searchBm25Only(anyString(), any(), anyInt());
        verify(chunkRepo).searchTrigramOnly(anyString(), any(), anyInt());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("RRF fusion — 3개 path에서 동일 chunk가 등장하면 score 누적")
    void fusionAccumulatesAcrossPaths() {
        ReflectionTestUtils.setField(service, "rrfOfflineEnabled", true);
        when(queryEmbeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});

        // 같은 chunk가 3개 path 모두 rank 1
        LegalChunkJpaRepository.LegalChunkRow row = mockRow("민법", "제618조", 0.9);
        when(chunkRepo.searchVectorOnly(anyString(), any(), anyInt())).thenReturn(List.of(row));
        when(chunkRepo.searchBm25Only(anyString(), any(), anyInt())).thenReturn(List.of(row));
        when(chunkRepo.searchTrigramOnly(anyString(), any(), anyInt())).thenReturn(List.of(row));

        List<LegalChunk> result = service.retrieveLawsRrf("쿼리", "키워드", new String[]{}, 5);

        assertThat(result).hasSize(1);
        // RRF score = 3 * (1 / (60 + 1)) = 3/61 ≈ 0.0492
        assertThat(result.get(0).score()).isCloseTo(3.0 / 61.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("blank query → 영벡터 사용 (degrade)")
    void blankQueryUsesZeroVector() {
        ReflectionTestUtils.setField(service, "rrfOfflineEnabled", true);
        when(chunkRepo.searchVectorOnly(anyString(), any(), anyInt())).thenReturn(List.of());
        when(chunkRepo.searchBm25Only(anyString(), any(), anyInt())).thenReturn(List.of());
        when(chunkRepo.searchTrigramOnly(anyString(), any(), anyInt())).thenReturn(List.of());

        // blank query → embedQuery 호출 안 함 (영벡터 fallback)
        service.retrieveLawsRrf("", "키워드", new String[]{}, 5);

        verify(queryEmbeddingService, never()).embedQuery(anyString());
    }

    private static LegalChunkJpaRepository.LegalChunkRow mockRow(
            String lawName, String articleNo, double score) {
        LegalChunkJpaRepository.LegalChunkRow row = mock(LegalChunkJpaRepository.LegalChunkRow.class);
        when(row.getLawName()).thenReturn(lawName);
        when(row.getArticleNo()).thenReturn(articleNo);
        when(row.getArticleTitle()).thenReturn("title");
        when(row.getContent()).thenReturn("content");
        when(row.getEffectiveDate()).thenReturn("2023-01-01");
        when(row.getSourceUrl()).thenReturn("https://law.go.kr/" + articleNo);
        when(row.getScore()).thenReturn(score);
        return row;
    }
}
