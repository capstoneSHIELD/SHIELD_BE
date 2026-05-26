package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.application.CohereService;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.provider.AiClassificationClient;
import org.example.shield.ai.provider.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cohere {@link AiClassificationClient} adapter.
 *
 * <p>기존 {@link CohereService#callClassify(java.util.List)}를 wrapping하여
 * {@link ChatMessage} → {@link CohereChatRequest.Message}로 변환한다.
 *
 * <p>{@link #providerKey()}는 {@code "cohere"}로,
 * {@code AI_CLASSIFY_PROVIDER=cohere} 설정과 매칭된다.
 */
@Component
public class CohereClassificationClientAdapter implements AiClassificationClient {

    public static final String PROVIDER_KEY = "cohere";

    private final CohereService cohereService;

    public CohereClassificationClientAdapter(CohereService cohereService) {
        this.cohereService = cohereService;
    }

    @Override
    public AiCallResult<String> classify(List<ChatMessage> messages) {
        return cohereService.callClassify(CohereMessageConverter.toCohereMessages(messages));
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }
}
