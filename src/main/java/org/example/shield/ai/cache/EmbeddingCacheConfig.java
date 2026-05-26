package org.example.shield.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Locale;

/**
 * 임베딩 캐시 빈 등록 설정 (Phase P5.3 Commit 1-2).
 *
 * <p>{@code AI_EMBEDDING_CACHE_MODE} 값에 따라 적절한 {@link EmbeddingCache} 구현체를 등록:
 * <ul>
 *   <li>{@code off} (기본) — {@link NoopEmbeddingCache} — 매 호출마다 provider 직접</li>
 *   <li>{@code l1} — {@link CaffeineEmbeddingCache} — in-process LRU cache</li>
 * </ul>
 *
 * <p>invalid 값은 fail-fast로 throw (silent fallback 금지).
 */
@Configuration
@Slf4j
public class EmbeddingCacheConfig {

    @Value("${ai.embedding.cache.mode:off}")
    private String cacheMode;

    @Value("${ai.embedding.cache.max-size:5000}")
    private long maxSize;

    @Value("${ai.embedding.cache.expire-after-write-minutes:60}")
    private long expireAfterWriteMinutes;

    @Bean
    public EmbeddingCache embeddingCache() {
        String normalized = cacheMode == null ? "off" : cacheMode.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "off":
            case "":
                log.info("EmbeddingCache: NoopEmbeddingCache (mode=off)");
                return new NoopEmbeddingCache();
            case "l1":
                Duration ttl = Duration.ofMinutes(expireAfterWriteMinutes);
                log.info("EmbeddingCache: CaffeineEmbeddingCache (mode=l1, maxSize={}, expireAfterWrite={}min)",
                        maxSize, expireAfterWriteMinutes);
                return new CaffeineEmbeddingCache(maxSize, ttl);
            default:
                throw new IllegalStateException(
                        "Invalid AI_EMBEDDING_CACHE_MODE='" + cacheMode + "'. Allowed: off|l1");
        }
    }
}
