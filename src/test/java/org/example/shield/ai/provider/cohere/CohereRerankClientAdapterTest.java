package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.infrastructure.CohereRerankClient;
import org.example.shield.ai.infrastructure.CohereRerankResponse;
import org.example.shield.ai.infrastructure.CohereRerankResponse.BilledUnits;
import org.example.shield.ai.infrastructure.CohereRerankResponse.Meta;
import org.example.shield.ai.infrastructure.CohereRerankResponse.RerankItem;
import org.example.shield.ai.provider.RerankResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CohereRerankClientAdapter} 위임 + provider-neutral 변환 검증 (P5.4 Commit 1).
 */
class CohereRerankClientAdapterTest {

    private CohereRerankClient cohereRerankClient;
    private CohereRerankClientAdapter adapter;

    @BeforeEach
    void setUp() {
        cohereRerankClient = mock(CohereRerankClient.class);
        adapter = new CohereRerankClientAdapter(cohereRerankClient);
    }

    private static CohereRerankResponse mockResponse(int searchUnits, List<int[]> indexScorePairs) {
        CohereRerankResponse resp = new CohereRerankResponse();
        resp.setId("rerank-1");
        resp.setResults(indexScorePairs.stream().map(pair -> {
            RerankItem item = new RerankItem();
            item.setIndex(pair[0]);
            item.setRelevanceScore(pair[1] / 100.0);
            return item;
        }).toList());
        Meta meta = new Meta();
        BilledUnits bu = new BilledUnits();
        bu.setSearchUnits(searchUnits);
        meta.setBilledUnits(bu);
        resp.setMeta(meta);
        return resp;
    }

    @Test
    @DisplayName("rerank — CohereRerankClient에 위임 + RerankResult로 변환 + 순서 보존")
    void delegatesAndConverts() {
        CohereRerankResponse response = mockResponse(1,
                List.of(new int[]{2, 95}, new int[]{0, 78}, new int[]{1, 60}));
        when(cohereRerankClient.callRerank(eq("rerank-v3.5"), eq("쿼리"), anyList(), eq(3)))
                .thenReturn(new CohereRerankClient.RerankCallResult(response, 250L));

        RerankResult result = adapter.rerank(
                "rerank-v3.5", "쿼리", List.of("doc1", "doc2", "doc3"), 3);

        verify(cohereRerankClient).callRerank("rerank-v3.5", "쿼리", List.of("doc1", "doc2", "doc3"), 3);

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).index()).isEqualTo(2);
        assertThat(result.items().get(0).relevanceScore()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.items().get(1).index()).isEqualTo(0);
        assertThat(result.items().get(2).index()).isEqualTo(1);
        assertThat(result.latencyMs()).isEqualTo(250L);
        assertThat(result.inputTokens()).isEqualTo(1);   // search_units
    }

    @Test
    @DisplayName("meta 누락 시 inputTokens=null 안전 처리")
    void nullMetaSafe() {
        CohereRerankResponse response = new CohereRerankResponse();
        response.setId("rerank-2");
        RerankItem item = new RerankItem();
        item.setIndex(0);
        item.setRelevanceScore(0.5);
        response.setResults(List.of(item));
        // meta=null

        when(cohereRerankClient.callRerank(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn(new CohereRerankClient.RerankCallResult(response, 100L));

        RerankResult result = adapter.rerank("rerank-v3.5", "q", List.of("d"), 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.inputTokens()).isNull();
        assertThat(result.latencyMs()).isEqualTo(100L);
    }

    @Test
    @DisplayName("topN보다 적은 결과 — 받은 개수만큼 반환")
    void fewerResultsThanTopN() {
        CohereRerankResponse response = mockResponse(1, List.of(new int[]{0, 90}));
        when(cohereRerankClient.callRerank(anyString(), anyString(), anyList(), eq(5)))
                .thenReturn(new CohereRerankClient.RerankCallResult(response, 80L));

        RerankResult result = adapter.rerank("rerank-v3.5", "q", List.of("d1"), 5);

        assertThat(result.items()).hasSize(1);
    }
}
