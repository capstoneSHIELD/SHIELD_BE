package org.example.shield.ai.provider;

import org.example.shield.ai.dto.AiCallResult;

import java.util.List;

/**
 * Intent classification 호출 진입 인터페이스 (provider-neutral).
 *
 * <p>P5.1 Commit 2 도입. 현재 구현체:
 * <ul>
 *   <li>{@link org.example.shield.ai.provider.cohere.CohereClassificationClientAdapter} —
 *       기존 {@code CohereService#callClassify(...)}를 wrapping</li>
 *   <li>{@link org.example.shield.ai.provider.openai.OpenAiClassificationClientAdapter} —
 *       기존 {@code OpenAiClassifyClient#callRawJson(...)}를 wrapping</li>
 * </ul>
 *
 * <p>{@code AI_CLASSIFY_PROVIDER} 설정으로 어떤 구현체를 사용할지 결정한다
 * ({@link org.example.shield.ai.application.IntentClassificationService}에서 라우팅).
 */
public interface AiClassificationClient {

    /**
     * Intent 분류 호출. 응답은 raw JSON 문자열(structured output).
     *
     * @param messages system + user message 리스트 (provider-neutral {@link ChatMessage})
     * @return {@link AiCallResult} — {@code data}에 raw JSON 문자열, tokens/latency 포함
     */
    AiCallResult<String> classify(List<ChatMessage> messages);

    /**
     * 이 provider 구현체의 식별 키 (config 값과 매칭됨).
     * <p>예: {@code "cohere"}, {@code "openai"}.
     */
    String providerKey();
}
