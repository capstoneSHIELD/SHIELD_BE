/**
 * AI Provider 추상화 계층 (Phase P5.1 도입).
 *
 * <p>본 패키지는 Cohere/OpenAI/Anthropic 등 LLM provider 호출을
 * provider-neutral 인터페이스로 노출한다. 목적은 두 가지:
 *
 * <ol>
 *   <li>Phase 3 provider A/B 실험 진입 비용을 낮춘다 (chat/brief generation만 교체 가능).</li>
 *   <li>호출자(application 계층)가 Cohere 응답 DTO에 직접 의존하지 않게 한다.</li>
 * </ol>
 *
 * <h3>인터페이스 목록</h3>
 * <ul>
 *   <li>{@link org.example.shield.ai.provider.AiChatClient} — chat/brief generation
 *       (기존 {@link org.example.shield.ai.application.AiClient}의 alias)</li>
 *   <li>{@link org.example.shield.ai.provider.AiEmbeddingClient} — query/document embedding</li>
 *   <li>{@link org.example.shield.ai.provider.AiClassificationClient} — intent classification</li>
 *   <li>{@link org.example.shield.ai.provider.AiRerankClient} — passage reranking (P5.4에서 구현)</li>
 * </ul>
 *
 * <h3>구현 위치</h3>
 * <ul>
 *   <li>{@code provider/cohere/} — Cohere adapter (기존 {@code CohereClient}를 wrapping)</li>
 *   <li>{@code provider/openai/} — OpenAI adapter (classify 한정)</li>
 * </ul>
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li><b>Low-risk wrapping</b>: 기존 {@code CohereClient}/{@code CohereService} 코드는
 *       그대로 두고, adapter만 신규로 추가한다.</li>
 *   <li><b>Provider-neutral DTO</b>: {@link org.example.shield.ai.provider.ChatMessage},
 *       {@link org.example.shield.ai.provider.EmbeddingResult},
 *       {@link org.example.shield.ai.provider.RerankResult}는 Cohere 응답 shape를
 *       누설하지 않는다.</li>
 *   <li><b>Backward compatibility</b>: 기존 호출처는 entry-point만 인터페이스로 전환되며,
 *       메서드 시그니처는 동일하거나 호환된다.</li>
 * </ul>
 *
 * @since Phase P5.1
 */
package org.example.shield.ai.provider;
