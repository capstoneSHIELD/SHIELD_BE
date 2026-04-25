package org.example.shield.ai.dto;

/**
 * RAG 파이프라인 1회 실행의 산출물.
 *
 * <p>{@code context} 는 LLM 시스템 프롬프트에 주입되는 RAG 컨텍스트 문자열.
 * {@code classification} 은 Layer1 의도 분류 결과로, 호출자가 활성 도메인 결정,
 * 체크리스트 anchor 등에 재사용할 수 있도록 외부로 노출된다.</p>
 *
 * <p>실패/스킵 시:
 * <ul>
 *   <li>RAG 자체 실패 → {@code context = ""}, {@code classification = null}
 *   <li>도메인 미분류 호출자 가드로 RAG 미수행 → 반환 자체가 없음 (호출 안 됨)
 * </ul>
 * </p>
 */
public record RagResult(String context, IntentClassificationResult classification) {

    public static RagResult empty() {
        return new RagResult("", null);
    }
}
