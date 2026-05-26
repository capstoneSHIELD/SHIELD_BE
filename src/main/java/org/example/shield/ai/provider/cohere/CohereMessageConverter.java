package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.provider.ChatMessage;

import java.util.List;

/**
 * {@link ChatMessage} ↔ {@link CohereChatRequest.Message} 변환 유틸.
 *
 * <p>Cohere/OpenAI adapter 모두 내부적으로 {@link CohereChatRequest.Message}를 사용하므로
 * (OpenAiClassifyClient도 같은 message 타입을 받음), 변환 로직을 공용 위치에 둔다.
 */
public final class CohereMessageConverter {

    private CohereMessageConverter() {
        // 유틸 — 인스턴스화 금지
    }

    /**
     * 단일 {@link ChatMessage} → {@link CohereChatRequest.Message} 변환.
     */
    public static CohereChatRequest.Message toCohereMessage(ChatMessage msg) {
        return switch (msg.role()) {
            case SYSTEM -> CohereChatRequest.Message.system(msg.content());
            case USER -> CohereChatRequest.Message.user(msg.content());
            case ASSISTANT -> CohereChatRequest.Message.assistant(msg.content());
        };
    }

    /**
     * 리스트 변환 — 순서 보존.
     */
    public static List<CohereChatRequest.Message> toCohereMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(CohereMessageConverter::toCohereMessage)
                .toList();
    }
}
