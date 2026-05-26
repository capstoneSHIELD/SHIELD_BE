package org.example.shield.ai.provider;

/**
 * Provider-neutral chat message.
 *
 * <p>Cohere/OpenAI 등 모든 provider가 공통으로 지원하는 role + content 구조.
 * Provider adapter는 이 record를 자신의 native message 타입으로 변환한다.
 *
 * <p>기존 {@code CohereChatRequest.Message}와 달리 provider 응답 shape를 누설하지 않는다.
 */
public record ChatMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
