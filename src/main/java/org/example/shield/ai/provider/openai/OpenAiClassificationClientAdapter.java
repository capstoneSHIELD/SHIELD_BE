package org.example.shield.ai.provider.openai;

import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.infrastructure.OpenAiClassifyClient;
import org.example.shield.ai.provider.AiClassificationClient;
import org.example.shield.ai.provider.ChatMessage;
import org.example.shield.ai.provider.cohere.CohereMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI {@link AiClassificationClient} adapter (RAG intent classification 전용).
 *
 * <p>기존 {@link OpenAiClassifyClient#callRawJson(java.util.List)}를 wrapping한다.
 * {@code OpenAiClassifyClient}는 내부적으로 {@link CohereChatRequest.Message} 타입을
 * 그대로 사용하므로, 본 adapter도 같은 변환을 수행한다.
 *
 * <p>{@link #providerKey()}는 {@code "openai"}로,
 * {@code AI_CLASSIFY_PROVIDER=openai} 설정과 매칭된다.
 *
 * <p>{@link ConditionalOnBean}: {@link OpenAiClassifyClient} 빈이 등록될 때만 생성된다
 * (API key 미설정 시 등록되지 않을 수 있음).
 */
@Component
@ConditionalOnBean(OpenAiClassifyClient.class)
public class OpenAiClassificationClientAdapter implements AiClassificationClient {

    public static final String PROVIDER_KEY = "openai";

    private final OpenAiClassifyClient openAiClassifyClient;

    @Autowired
    public OpenAiClassificationClientAdapter(OpenAiClassifyClient openAiClassifyClient) {
        this.openAiClassifyClient = openAiClassifyClient;
    }

    @Override
    public AiCallResult<String> classify(List<ChatMessage> messages) {
        return openAiClassifyClient.callRawJson(CohereMessageConverter.toCohereMessages(messages));
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }
}
