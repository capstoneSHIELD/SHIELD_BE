package org.example.shield.ai.provider;

/**
 * LLM judge 평가 요청 (Phase P5.5 Commit 1).
 *
 * <p>provider-neutral. 평가 대상 categories / domain hint를 지시하면 adapter가
 * provider별 프롬프트로 변환한다.
 *
 * @param domain          평가 대상 도메인 (예: {@code "legal_compliance"} — 변호사법 위반)
 * @param strictness      엄격도 ({@code "lenient" | "standard" | "strict"}). standard 권장.
 * @param maxOutputTokens 응답 토큰 상한 (judge는 짧은 verdict + reason만 필요)
 */
public record JudgeRequest(
        String domain,
        String strictness,
        int maxOutputTokens
) {

    public JudgeRequest {
        domain = domain == null || domain.isBlank() ? "legal_compliance" : domain;
        strictness = strictness == null || strictness.isBlank() ? "standard" : strictness;
        maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 256;
    }

    /**
     * 한국 법률 컴플라이언스 평가 기본 요청 (standard 엄격도, 256 token).
     */
    public static JudgeRequest legalCompliance() {
        return new JudgeRequest("legal_compliance", "standard", 256);
    }
}
