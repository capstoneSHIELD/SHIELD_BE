package org.example.shield.ai.provider;

import java.util.List;

/**
 * LLM judge 평가 결과 (Phase P5.5 Commit 1).
 *
 * <p>provider-neutral. HyperCLOVA / 다른 LLM judge 구현체가 동일한 record로 반환.
 *
 * @param verdict        컴플라이언스 판정 (PASS / SOFT_VIOLATION / HARD_VIOLATION)
 * @param confidence     0.0 ~ 1.0 (낮으면 판단 신뢰 낮음)
 * @param reason         짧은 자연어 설명 (디버깅·표본 검토용, 1~2문장)
 * @param categories     위반 카테고리 (예: ["legal_conclusion", "case_citation_without_basis"])
 * @param inputTokens    judge 호출의 input token (비용 추적용, null 가능)
 * @param outputTokens   judge 호출의 output token (null 가능)
 * @param latencyMs      judge 호출 지연 (ms)
 */
public record JudgeResult(
        Verdict verdict,
        double confidence,
        String reason,
        List<String> categories,
        Integer inputTokens,
        Integer outputTokens,
        long latencyMs
) {

    public JudgeResult {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /**
     * 컴플라이언스 판정.
     */
    public enum Verdict {
        /** 위반 없음, 운영 차단 불필요. */
        PASS,
        /** 약한 위반 (모호한 표현, 경계선 케이스). shadow 단계에서 표본 검토 대상. */
        SOFT_VIOLATION,
        /** 강한 위반 (명확한 법적 조언/판례 단정). 향후 enforce 단계에서 차단 후보. */
        HARD_VIOLATION
    }
}
