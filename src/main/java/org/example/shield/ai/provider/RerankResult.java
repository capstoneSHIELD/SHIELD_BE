package org.example.shield.ai.provider;

import java.util.List;

/**
 * Rerank 호출 결과 (provider-neutral).
 *
 * <p>P5.4 Commit 1에서 Cohere {@code rerank-v3.5} 적용 예정.
 * 본 record는 인터페이스 contract 노출을 위해 P5.1에서 미리 정의된다.
 *
 * <ul>
 *   <li>{@code items} — 재정렬된 문서 (index = 원본 입력 documents의 인덱스, score 0~1)</li>
 *   <li>{@code latencyMs} — provider 호출 지연</li>
 *   <li>{@code inputTokens} — provider가 보고한 billed input tokens (없으면 null)</li>
 * </ul>
 */
public record RerankResult(
        List<RerankedItem> items,
        long latencyMs,
        Integer inputTokens
) {

    /**
     * 재정렬 결과 항목.
     *
     * @param index           원본 documents 배열의 인덱스
     * @param relevanceScore  provider가 매긴 관련성 점수 (보통 0~1)
     */
    public record RerankedItem(int index, double relevanceScore) { }
}
