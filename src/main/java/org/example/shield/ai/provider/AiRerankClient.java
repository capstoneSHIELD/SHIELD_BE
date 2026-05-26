package org.example.shield.ai.provider;

import java.util.List;

/**
 * Passage rerank 호출 진입 인터페이스 (provider-neutral).
 *
 * <p>P5.1 Commit 2에서 contract만 정의된다. 실제 구현체는
 * P5.4 Commit 1에서 Cohere {@code rerank-v3.5} 기반 adapter로 추가될 예정이다.
 *
 * <p>설계상 retrieval pipeline에서 topN 후보를 받아 rerank 후 topK를 반환한다.
 */
public interface AiRerankClient {

    /**
     * Documents를 query 관련성 순으로 재정렬.
     *
     * @param model     provider별 rerank 모델 ID (예: Cohere {@code "rerank-v3.5"})
     * @param query     사용자 쿼리
     * @param documents 재정렬 대상 문서 (보통 weighted retrieval의 topN)
     * @param topN      반환할 상위 N 항목 수 (보통 5)
     * @return {@link RerankResult} — 재정렬된 (index, score) 리스트 + 메타데이터
     */
    RerankResult rerank(String model, String query, List<String> documents, int topN);
}
