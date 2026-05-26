package org.example.shield.ai.config;

/**
 * RAG 기능 활성화 단계를 나타내는 mode flag.
 *
 * <p>Phase P5 시리즈에서 신규 기능(Retrieval Gate, Rerank, Context Budget,
 * Intent-aware Retrieval 등)은 모두 이 enum으로 단계 제어된다.
 *
 * <ul>
 *   <li>{@link #OFF} — 신규 behavior 실행 안 함 (기본값)</li>
 *   <li>{@link #SHADOW} — decision/comparison 로직만 실행, user-facing 변화 0</li>
 *   <li>{@link #SAMPLED} — 설정된 비율의 eligible request에 적용
 *       (conversationId 기반 deterministic — request 단위 random 금지)</li>
 *   <li>{@link #ENFORCE} — 전체 eligible request에 적용</li>
 * </ul>
 *
 * <p>{@link #fromOrThrow(String, String)}는 invalid 값에 대해 startup fail-fast로
 * 동작한다. 기존 {@code RagFusionMode.from(...)}처럼 silent fallback 하지 않는다 —
 * 잘못된 flag 값을 조용히 OFF로 처리하면 운영 중 의도와 다른 동작이 묻혀버린다.
 */
public enum RagFeatureMode {

    OFF,
    SHADOW,
    SAMPLED,
    ENFORCE;

    /**
     * 환경변수/yml에서 읽은 raw string을 enum으로 변환한다.
     * <ul>
     *   <li>null 또는 blank → {@link #OFF} (default-off 원칙)</li>
     *   <li>대소문자 무시 매칭 (예: {@code "shadow"}, {@code "SHADOW"}, {@code "Shadow"} 모두 허용)</li>
     *   <li>매칭 실패 → {@link IllegalStateException} (fail-fast)</li>
     * </ul>
     *
     * @param value    yml 또는 환경변수에서 읽은 원본 문자열
     * @param flagName 진단 메시지에 포함될 flag 이름
     *                 (예: {@code "AI_RAG_RETRIEVAL_GATE_MODE"})
     * @return 매칭된 mode
     * @throws IllegalStateException invalid 값일 때
     */
    public static RagFeatureMode fromOrThrow(String value, String flagName) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim().toUpperCase();
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid mode '%s' for %s. Allowed: off|shadow|sampled|enforce"
                            .formatted(value, flagName));
        }
    }
}
