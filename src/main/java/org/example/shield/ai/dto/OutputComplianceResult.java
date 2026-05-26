package org.example.shield.ai.dto;

/**
 * Output compliance shadow judge 결과.
 *
 * <p>P5.2 Commit 4부터 {@code hashedConversationId} 필드 추가 — 원본 conversationId는
 * 절대 저장하지 않고 SHA-256 short hash만 보관해 후속 분석 시 추적 가능하게 한다.
 *
 * @param deterministicViolation regex/guardrail 결정적 위반 감지 여부
 * @param shadowScheduled        본 응답이 shadow judge 표본에 포함됐는지
 * @param blockingApplied        production 차단 적용 여부 (현재는 항상 false)
 * @param maskedText             PII 마스킹 처리된 텍스트 (shadow 표본일 때만 non-null,
 *                               원문은 절대 포함 금지)
 * @param hashedConversationId   conversationId SHA-256 short hash (8자 hex). 추적용,
 *                               null이면 conversationId 미제공
 * @param reason                 outcome 코드 ("regex_violation" / "sampled" / "skipped")
 */
public record OutputComplianceResult(
        boolean deterministicViolation,
        boolean shadowScheduled,
        boolean blockingApplied,
        String maskedText,
        String hashedConversationId,
        String reason
) {

    /**
     * 기존 5-arg 생성자 호환 (hashedConversationId=null).
     */
    public OutputComplianceResult(
            boolean deterministicViolation,
            boolean shadowScheduled,
            boolean blockingApplied,
            String maskedText,
            String reason
    ) {
        this(deterministicViolation, shadowScheduled, blockingApplied, maskedText, null, reason);
    }
}
