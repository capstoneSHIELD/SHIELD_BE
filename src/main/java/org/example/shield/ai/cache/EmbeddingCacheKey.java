package org.example.shield.ai.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 임베딩 캐시 key — provider/모델/입력타입/차원/정규화버전/콘텐츠해시 조합.
 *
 * <p>P5.3 Commit 1: Issue #38에서 제거된 임베딩 캐시를 재도입하기 위한 provider-agnostic key.
 * 모델 prefix가 들어가므로 모델 버전이 바뀌면 stale key가 자동 무효화된다.
 *
 * <p>{@code contentHash}는 정규화된 텍스트의 SHA-256 (full 64자 hex). Redis L2 도입 시
 * key 길이를 줄이려면 호출자가 직접 short hash로 변환하면 됨.
 *
 * @param provider          {@code "cohere"}, {@code "openai"} 등
 * @param model             provider 모델 ID (예: {@code "embed-v4.0"})
 * @param inputType         {@code "search_query"} / {@code "search_document"}
 * @param dimension         임베딩 차원 (예: 1024)
 * @param normalizeVersion  텍스트 정규화 알고리즘 버전 (예: {@code "v1"})
 *                          — 알고리즘이 바뀌면 캐시 분리
 * @param contentHash       정규화된 텍스트의 SHA-256 (64자 lowercase hex)
 */
public record EmbeddingCacheKey(
        String provider,
        String model,
        String inputType,
        int dimension,
        String normalizeVersion,
        String contentHash
) {

    /**
     * 단일 String key — 캐시 백엔드(Caffeine, Redis 등)가 hashable key를 요구할 때 사용.
     */
    public String asString() {
        return "embed:" + safe(provider)
                + ":" + safe(model)
                + ":" + safe(inputType)
                + ":" + dimension
                + ":" + safe(normalizeVersion)
                + ":" + safe(contentHash);
    }

    /**
     * 정규화된 텍스트의 SHA-256 hex (lowercase, 64자) 계산.
     */
    public static String sha256Hex(String normalizedText) {
        if (normalizedText == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
