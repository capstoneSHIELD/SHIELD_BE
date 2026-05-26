package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.infrastructure.CohereClient;
import org.example.shield.ai.provider.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CohereEmbeddingClientAdapter} 위임 동작 검증.
 *
 * <p>P5.1 Commit 3 이후: adapter는 {@code CohereClient.embed*WithMetadata}를 호출하여
 * {@link EmbeddingResult}를 그대로 전달한다. {@code inputTokens}와 {@code latencyMs}는
 * provider가 채운 실제 값.</p>
 */
class CohereEmbeddingClientAdapterTest {

    private CohereClient cohereClient;
    private CohereEmbeddingClientAdapter adapter;

    @BeforeEach
    void setUp() {
        cohereClient = mock(CohereClient.class);
        adapter = new CohereEmbeddingClientAdapter(cohereClient);
    }

    @Test
    @DisplayName("embedQuery — CohereClient.embedQueryWithMetadata에 위임")
    void embedQuery_delegatesToWithMetadata() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        EmbeddingResult expected = new EmbeddingResult("emb-1", List.of(vector), 42, 123L);
        when(cohereClient.embedQueryWithMetadata(eq("embed-v4.0"), eq("쿼리"))).thenReturn(expected);

        EmbeddingResult actual = adapter.embedQuery("embed-v4.0", "쿼리");

        verify(cohereClient).embedQueryWithMetadata("embed-v4.0", "쿼리");
        assertThat(actual).isSameAs(expected);
        assertThat(actual.responseId()).isEqualTo("emb-1");
        assertThat(actual.inputTokens()).isEqualTo(42);
        assertThat(actual.latencyMs()).isEqualTo(123L);
        assertThat(actual.firstVector()).isSameAs(vector);
    }

    @Test
    @DisplayName("embedDocuments — CohereClient.embedDocumentsWithMetadata에 위임 + 순서 보존")
    void embedDocuments_delegatesAndPreservesOrder() {
        List<float[]> vectors = List.of(
                new float[]{0.1f},
                new float[]{0.2f},
                new float[]{0.3f}
        );
        EmbeddingResult expected = new EmbeddingResult("emb-batch-1", vectors, 256, 500L);
        when(cohereClient.embedDocumentsWithMetadata(eq("embed-v4.0"), eq(List.of("a", "b", "c"))))
                .thenReturn(expected);

        EmbeddingResult actual = adapter.embedDocuments("embed-v4.0", List.of("a", "b", "c"));

        verify(cohereClient).embedDocumentsWithMetadata("embed-v4.0", List.of("a", "b", "c"));
        assertThat(actual).isSameAs(expected);
        assertThat(actual.vectors()).containsExactlyElementsOf(vectors);
        assertThat(actual.inputTokens()).isEqualTo(256);
        assertThat(actual.latencyMs()).isEqualTo(500L);
    }

    @Test
    @DisplayName("embedQuery — null inputTokens 응답도 정상 전달")
    void embedQuery_handlesNullTokens() {
        EmbeddingResult result = new EmbeddingResult(null, List.of(new float[]{0.5f}), null, 10L);
        when(cohereClient.embedQueryWithMetadata(eq("embed-v4.0"), eq("x"))).thenReturn(result);

        EmbeddingResult actual = adapter.embedQuery("embed-v4.0", "x");

        assertThat(actual.inputTokens()).isNull();
        assertThat(actual.latencyMs()).isEqualTo(10L);
    }
}
