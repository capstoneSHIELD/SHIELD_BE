package org.example.shield.ai.provider;

import java.util.List;

/**
 * Embedding 호출 진입 인터페이스 (provider-neutral).
 *
 * <p>P5.1 Commit 2 도입. 현재 구현체:
 * {@link org.example.shield.ai.provider.cohere.CohereEmbeddingClientAdapter}.
 *
 * <p>기존 {@link org.example.shield.ai.infrastructure.CohereClient#embedQuery(String, String)}
 * 등을 직접 호출하던 코드는 본 인터페이스를 통해 호출하도록 단계적으로 전환된다.
 */
public interface AiEmbeddingClient {

    /**
     * 단일 쿼리 임베딩.
     *
     * @param model provider별 모델 ID (예: Cohere {@code "embed-v4.0"})
     * @param text  임베딩 대상 문자열
     * @return {@link EmbeddingResult} — {@link EmbeddingResult#firstVector()}로 단일 벡터 추출 가능
     */
    EmbeddingResult embedQuery(String model, String text);

    /**
     * 다중 문서 배치 임베딩.
     *
     * @param model provider별 모델 ID
     * @param texts 임베딩 대상 문자열 리스트 (provider 권장 배치 크기 내)
     * @return {@link EmbeddingResult} — {@code texts}와 동일 순서의 벡터 리스트
     */
    EmbeddingResult embedDocuments(String model, List<String> texts);
}
