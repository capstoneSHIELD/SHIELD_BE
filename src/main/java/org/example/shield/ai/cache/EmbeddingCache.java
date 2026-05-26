package org.example.shield.ai.cache;

import java.util.Optional;

/**
 * 임베딩 벡터 캐시 인터페이스 (Phase P5.3 Commit 1).
 *
 * <p>Issue #38에서 제거된 캐시 레이어를 재도입. 매 요청마다 Cohere Embed API를
 * 호출하던 비용·지연을 절감하기 위한 추상화.
 *
 * <h3>구현체 선택 (application.yml {@code AI_EMBEDDING_CACHE_MODE})</h3>
 * <ul>
 *   <li>{@code off} — {@link NoopEmbeddingCache} (항상 miss)</li>
 *   <li>{@code l1} — {@link CaffeineEmbeddingCache} (in-process LRU, P5.3 Commit 2)</li>
 *   <li>(future) {@code l1+l2} — Caffeine + Redis 2-tier</li>
 * </ul>
 *
 * <h3>thread-safety</h3>
 * 구현체는 동시 호출에 안전해야 하며, 같은 key 동시 lookup 시 provider 호출이 1회만
 * 발생하도록 stampede 방지를 권장한다 (Caffeine의 {@code AsyncLoadingCache} 또는
 * 호출자 측 mutex).
 */
public interface EmbeddingCache {

    /**
     * key에 매핑된 임베딩 벡터를 반환.
     *
     * @param key 캐시 key
     * @return 있으면 {@code Optional.of(vector)}, 없으면 {@link Optional#empty()}
     */
    Optional<float[]> get(EmbeddingCacheKey key);

    /**
     * key에 임베딩 벡터를 저장. 같은 key가 이미 있으면 덮어쓴다.
     *
     * @param key    캐시 key
     * @param vector 저장할 벡터 (null이면 no-op)
     */
    void put(EmbeddingCacheKey key, float[] vector);
}
