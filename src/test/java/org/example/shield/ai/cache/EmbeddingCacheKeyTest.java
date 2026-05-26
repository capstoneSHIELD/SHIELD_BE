package org.example.shield.ai.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmbeddingCacheKey} 검증 (P5.3 Commit 1).
 *
 * <p>핵심 속성:
 * <ol>
 *   <li>같은 입력 → 항상 같은 key 문자열 (deterministic)</li>
 *   <li>모델 prefix가 다르면 다른 key (모델 버전 변경 시 stale 자동 무효화)</li>
 *   <li>SHA-256 64자 hex 출력</li>
 * </ol>
 */
class EmbeddingCacheKeyTest {

    @Test
    @DisplayName("asString — 모든 컴포넌트가 콜론 구분으로 결합")
    void asStringCombinesFields() {
        EmbeddingCacheKey key = new EmbeddingCacheKey(
                "cohere", "embed-v4.0", "search_query", 1024, "v1", "abc123");

        assertThat(key.asString())
                .isEqualTo("embed:cohere:embed-v4.0:search_query:1024:v1:abc123");
    }

    @Test
    @DisplayName("모델 prefix 변경 시 key 변경 — stale 자동 무효화")
    void modelChangeProducesDifferentKey() {
        EmbeddingCacheKey k1 = new EmbeddingCacheKey(
                "cohere", "embed-v4.0", "search_query", 1024, "v1", "hash");
        EmbeddingCacheKey k2 = new EmbeddingCacheKey(
                "cohere", "embed-v5.0", "search_query", 1024, "v1", "hash");

        assertThat(k1.asString()).isNotEqualTo(k2.asString());
    }

    @Test
    @DisplayName("normalizeVersion 변경 시 key 변경 — 알고리즘 마이그레이션")
    void normalizeVersionChangeProducesDifferentKey() {
        EmbeddingCacheKey k1 = new EmbeddingCacheKey(
                "cohere", "embed-v4.0", "search_query", 1024, "v1", "hash");
        EmbeddingCacheKey k2 = new EmbeddingCacheKey(
                "cohere", "embed-v4.0", "search_query", 1024, "v2", "hash");

        assertThat(k1.asString()).isNotEqualTo(k2.asString());
    }

    @Test
    @DisplayName("sha256Hex — 같은 입력 같은 결과, 64자 lowercase hex")
    void sha256HexDeterministic() {
        String hash1 = EmbeddingCacheKey.sha256Hex("전세금 반환");
        String hash2 = EmbeddingCacheKey.sha256Hex("전세금 반환");
        String hash3 = EmbeddingCacheKey.sha256Hex("위약금");

        assertThat(hash1).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    @DisplayName("sha256Hex — null 입력은 빈 문자열")
    void sha256HexNullSafe() {
        assertThat(EmbeddingCacheKey.sha256Hex(null)).isEmpty();
    }

    @Test
    @DisplayName("NoopEmbeddingCache — 항상 empty 반환")
    void noopAlwaysMiss() {
        NoopEmbeddingCache cache = new NoopEmbeddingCache();
        EmbeddingCacheKey key = new EmbeddingCacheKey(
                "cohere", "m", "search_query", 1024, "v1", "h");
        cache.put(key, new float[]{0.1f, 0.2f});

        assertThat(cache.get(key)).isEmpty();
    }
}
