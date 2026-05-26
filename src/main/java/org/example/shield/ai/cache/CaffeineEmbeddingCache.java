package org.example.shield.ai.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;

/**
 * Caffeine 기반 in-process L1 임베딩 캐시 (Phase P5.3 Commit 2).
 *
 * <p>같은 JVM 안에서 동일 normalized 텍스트가 반복 임베딩될 때 provider 호출을 절감한다.
 * 멀티 인스턴스 환경에서는 본 캐시 효과가 작아지므로 Redis L2 도입 검토는 후속 plan으로 분리.
 *
 * <h3>설정</h3>
 * <ul>
 *   <li>{@code AI_EMBEDDING_CACHE_MAX_SIZE} (기본 5000) — entry 수 상한</li>
 *   <li>{@code AI_EMBEDDING_CACHE_EXPIRE_AFTER_WRITE_MINUTES} (기본 60) — 쓰기 후 TTL</li>
 * </ul>
 *
 * <h3>thread-safety</h3>
 * Caffeine의 내부 자료구조는 thread-safe. 동시 get/put도 안전.
 */
public class CaffeineEmbeddingCache implements EmbeddingCache {

    private final Cache<String, float[]> cache;

    public CaffeineEmbeddingCache(long maxSize, Duration expireAfterWrite) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();
    }

    @Override
    public Optional<float[]> get(EmbeddingCacheKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(key.asString()));
    }

    @Override
    public void put(EmbeddingCacheKey key, float[] vector) {
        if (key == null || vector == null || vector.length == 0) {
            return;
        }
        cache.put(key.asString(), vector);
    }

    /**
     * Caffeine 내부 통계 (히트율, evict 카운트 등). 디버깅·테스트용.
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }
}
