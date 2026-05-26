package org.example.shield.ai.provider;

import org.example.shield.ai.application.AiClient;

/**
 * Chat / Brief generation 진입 인터페이스.
 *
 * <p>현재는 기존 {@link AiClient} (chat + brief)와 동일한 contract.
 * 향후 Phase 3에서 provider A/B 도입 시 이 인터페이스를 통해 Claude/Gemini/OpenAI 등으로
 * 대체 가능하도록 별도 이름으로 노출한다.
 *
 * <p>현재 구현체: {@link org.example.shield.ai.infrastructure.CohereClient} (via {@link AiClient}).
 *
 * @since Phase P5.1
 */
public interface AiChatClient extends AiClient {
}
