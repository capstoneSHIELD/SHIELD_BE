package org.example.shield.ai.provider;

import java.util.List;

/**
 * Embedding 호출 결과 (provider-neutral).
 *
 * <p>Phase P5.1 Commit 2에서 인터페이스 시그니처용으로 도입.
 * Commit 3에서 {@link #inputTokens()}와 {@link #latencyMs()} 필드가 Cohere
 * {@code meta.billed_units.input_tokens}와 실제 호출 지연으로 채워지도록 plumbing 완료.
 *
 * <ul>
 *   <li>{@code responseId} — provider 응답 ID (Cohere: {@code id})</li>
 *   <li>{@code vectors} — texts와 동일 순서의 임베딩 벡터 리스트</li>
 *   <li>{@code inputTokens} — provider가 보고한 정확한 입력 토큰 수 (없으면 null)</li>
 *   <li>{@code latencyMs} — provider 호출에 소요된 시간 (밀리초)</li>
 * </ul>
 */
public record EmbeddingResult(
        String responseId,
        List<float[]> vectors,
        Integer inputTokens,
        long latencyMs
) {

    /**
     * 단일 쿼리 임베딩에서 사용. {@code vectors}가 비었으면 null.
     */
    public float[] firstVector() {
        return vectors == null || vectors.isEmpty() ? null : vectors.get(0);
    }

    /**
     * 벡터가 하나도 없거나 null이면 true.
     */
    public boolean isEmpty() {
        return vectors == null || vectors.isEmpty();
    }
}
