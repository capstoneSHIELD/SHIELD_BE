package org.example.shield.ai.cache;

import java.util.Optional;

/**
 * 항상 cache miss를 반환하는 No-op 구현 (기본값).
 *
 * <p>{@code AI_EMBEDDING_CACHE_MODE=off} (default)일 때 Spring이 등록.
 * Issue #38 이전 동작과 동일 — 매번 provider 호출.
 */
public class NoopEmbeddingCache implements EmbeddingCache {

    @Override
    public Optional<float[]> get(EmbeddingCacheKey key) {
        return Optional.empty();
    }

    @Override
    public void put(EmbeddingCacheKey key, float[] vector) {
        // no-op
    }
}
