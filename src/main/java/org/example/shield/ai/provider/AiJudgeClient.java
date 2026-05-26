package org.example.shield.ai.provider;

/**
 * LLM judge 호출 진입 인터페이스 (Phase P5.5 Commit 1).
 *
 * <p>SHIELD의 출력 컴플라이언스 shadow judge가 사용. GuardrailFilter regex가
 * 못 잡는 의미론적 법적 단정(예: "대항력을 인정하는 경향이 있습니다",
 * "승소 가능성이 있어 보입니다")을 한국 법조 문맥에 익숙한 LLM이 평가.
 *
 * <p>현재 구현체: {@link org.example.shield.ai.provider.hyperclova.HyperClovaJudgeClientAdapter}.
 * Adversarial diversity 원칙으로 Cohere가 생성한 답변을 다른 벤더(HyperCLOVA X)로 평가.
 */
public interface AiJudgeClient {

    /**
     * 응답 텍스트의 컴플라이언스를 평가.
     *
     * @param maskedResponse PII 마스킹된 응답 텍스트 (원본 절대 전달 금지)
     * @param request        평가 요청 (도메인·평가 카테고리 지시)
     * @return 평가 결과 (verdict + confidence + reason + tokens/latency)
     */
    JudgeResult judge(String maskedResponse, JudgeRequest request);

    /**
     * 이 provider 구현체의 식별 키 (메트릭/로깅용).
     * <p>예: {@code "hyperclova"}.
     */
    String providerKey();
}
