package org.example.shield.ai.dto;

/**
 * AI Layer1 분류 결과와 사용자 선택 도메인을 교차 매칭해 결정된 활성 도메인.
 *
 * <p>각 필드는 한글명. 매칭 단계에 따라 일부 필드만 채워질 수 있다:
 * <ul>
 *   <li>L3 까지 매칭: l1, l2, l3 모두 채워짐 — 가장 정밀한 체크리스트
 *   <li>L2 매칭: l1, l2 채워지고 l3 = null — L2 단위 체크리스트
 *   <li>L1 매칭: l1 만 채워지고 l2, l3 = null — L1 단위 체크리스트 (드뭄)
 *   <li>전부 미매칭: fallback 으로 사용자 첫 선택값 — 기존 동작 유지
 * </ul>
 * </p>
 */
public record ResolvedDomain(String l1, String l2, String l3) {
    public static ResolvedDomain empty() {
        return new ResolvedDomain(null, null, null);
    }
}
