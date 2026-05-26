package org.example.shield.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * conversationId 기반 deterministic sampling 유틸 (P5.2 Commit 4 도입).
 *
 * <p>같은 상담 내에서 동작이 매 턴 바뀌면 품질 분석이 어려워지므로,
 * sampling 결정을 conversationId hash로 고정한다.
 *
 * <p>{@link ThreadLocalRandom}처럼 매 호출마다 다른 결과를 주는 방식은 금지.
 *
 * <h3>사용처</h3>
 * <ul>
 *   <li>{@code OutputComplianceShadowJudge} — 상담 단위로 shadow judge 표본 결정</li>
 *   <li>{@code RerankingService} (P5.4) — sampled mode에서 상담 단위로 rerank 적용 여부</li>
 *   <li>기타 conversation-aware sampling이 필요한 경우</li>
 * </ul>
 */
public final class ConversationDeterministicSampler {

    private ConversationDeterministicSampler() {
        // 유틸 — 인스턴스화 금지
    }

    /**
     * conversationId에 대해 deterministic sampling 결정.
     *
     * <ul>
     *   <li>rate &le; 0.0 → false (sampling 비활성)</li>
     *   <li>rate &ge; 1.0 → true (모두 sampling)</li>
     *   <li>0.0 &lt; rate &lt; 1.0 → conversationId hash를 0..99 buckets에 매핑,
     *       bucket이 {@code rate * 100} 미만이면 true</li>
     *   <li>conversationId가 null/blank → false (안전한 default)</li>
     * </ul>
     *
     * @param conversationId 상담 ID (UUID 문자열 등)
     * @param rate           sampling 비율 (0.0 ~ 1.0)
     * @return 이 conversation을 sampling 대상에 포함할지 여부
     */
    public static boolean shouldApply(String conversationId, double rate) {
        if (rate <= 0.0) {
            return false;
        }
        if (rate >= 1.0) {
            return true;
        }
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        int bucket = Math.floorMod(hashBucket(conversationId), 100);
        return bucket < (int) Math.floor(rate * 100);
    }

    /**
     * conversationId를 SHA-256 hash의 short hex 표현으로 변환 (메트릭 태그/로그용).
     * 원본 ID는 저장하지 않으면서 같은 ID는 같은 hash로 매핑 가능.
     *
     * @return 8자 hex 문자열 (예: "a1b2c3d4")
     */
    public static String sha256Short(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 4 && i < digest.length; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JRE에서 항상 사용 가능
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * conversationId의 안정적 hash bucket. 32-bit signed 정수 — 호출 측에서
     * {@link Math#floorMod(int, int)}로 양수 bucket 추출.
     */
    private static int hashBucket(String conversationId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(conversationId.getBytes(StandardCharsets.UTF_8));
            // 앞 4 바이트를 int로 합성
            return ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
