package org.example.shield.ai.provider.hyperclova;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.application.AiClient;
import org.example.shield.ai.config.HyperClovaApiConfig;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.BriefParsedResponse;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.infrastructure.HyperClovaChatClient;
import org.example.shield.ai.infrastructure.HyperClovaChatRequest;
import org.example.shield.ai.infrastructure.HyperClovaChatResponse;
import org.example.shield.ai.provider.AiChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HyperCLOVA X {@link AiChatClient} adapter (Phase P5.5 Commit 4).
 *
 * <p>Cohere {@link AiClient} 와 동일한 contract을 따르되 HyperCLOVA X Chat Completions
 * API를 호출한다. JSON 응답이 SHIELD ChatParsedResponse / BriefParsedResponse 스키마를
 * 따르지 않을 수 있으므로 robust 파싱과 fallback 처리가 핵심.
 *
 * <p><b>중요</b>: 본 adapter는 P5.5 phase 동안 <b>shadow only</b>로 사용된다.
 * production chat은 항상 Cohere를 통해 반환되며 본 adapter의 결과는 {@link
 * org.example.shield.ai.application.ChatProviderShadowComparator} 에서 비교용으로만 활용.
 *
 * <p>응답 파싱 정책:
 * <ul>
 *   <li>1차: 원문 JSON 파싱</li>
 *   <li>2차: 마크다운 fence 안의 JSON 추출</li>
 *   <li>3차: 평문에서 균형 잡힌 JSON 객체 추출</li>
 *   <li>모두 실패 시 fallback ({@code nextQuestion}만 raw text로 채운 최소 응답)</li>
 * </ul>
 */
/**
 * <p><b>Bean 등록 조건</b>: {@code app.ai.chat.provider} 가 {@code shadow_compare} 또는
 * {@code hyperclova} 일 때만 활성화. 기본값({@code cohere})에서는 bean이 등록되지 않으므로
 * 기존 {@link AiClient} 단일 구현체({@link org.example.shield.ai.infrastructure.CohereClient})와
 * 충돌하지 않는다. shadow_compare 모드에서는 CohereClient가 {@code @Primary}로 표시되어
 * 기존 {@code @Autowired AiClient} 의존성이 그대로 Cohere로 해석된다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.chat.provider",
        havingValue = "shadow_compare", matchIfMissing = false)
@Slf4j
public class HyperClovaChatClientAdapter implements AiChatClient {

    public static final String PROVIDER_KEY = "hyperclova";

    private static final int DEFAULT_CHAT_MAX_TOKENS = 1024;
    private static final int DEFAULT_BRIEF_MAX_TOKENS = 2048;

    private final HyperClovaChatClient client;
    private final HyperClovaApiConfig config;
    private final ObjectMapper objectMapper;

    public HyperClovaChatClientAdapter(HyperClovaChatClient client,
                                       HyperClovaApiConfig config,
                                       ObjectMapper objectMapper) {
        this.client = client;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiCallResult<ChatParsedResponse> callChat(String model,
                                                     List<CohereChatRequest.Message> messages) {
        String useModel = (model == null || model.isBlank()) ? config.getChatModel() : model;
        List<HyperClovaChatRequest.Message> hcxMessages = toHyperClovaMessages(messages);
        HyperClovaChatClient.ChatCallResult call = client.callChat(useModel, hcxMessages, DEFAULT_CHAT_MAX_TOKENS);

        HyperClovaChatResponse response = call.response();
        String raw = response.extractContent();
        ChatParsedResponse parsed = parseChatResponse(raw);
        return new AiCallResult<>(
                null,                                          // HyperCLOVA은 별도 ID 미제공
                parsed,
                extractInputTokens(response),
                extractOutputTokens(response),
                (int) call.latencyMs()
        );
    }

    @Override
    public AiCallResult<BriefParsedResponse> callBrief(String model,
                                                       List<CohereChatRequest.Message> messages) {
        String useModel = (model == null || model.isBlank()) ? config.getChatModel() : model;
        List<HyperClovaChatRequest.Message> hcxMessages = toHyperClovaMessages(messages);
        HyperClovaChatClient.ChatCallResult call = client.callChat(useModel, hcxMessages, DEFAULT_BRIEF_MAX_TOKENS);

        HyperClovaChatResponse response = call.response();
        String raw = response.extractContent();
        BriefParsedResponse parsed = parseBriefResponse(raw);
        return new AiCallResult<>(
                null,
                parsed,
                extractInputTokens(response),
                extractOutputTokens(response),
                (int) call.latencyMs()
        );
    }

    public String providerKey() {
        return PROVIDER_KEY;
    }

    /**
     * Cohere chat message → HyperCLOVA chat message 변환. role 값은 동일 ({system/user/assistant}).
     */
    List<HyperClovaChatRequest.Message> toHyperClovaMessages(List<CohereChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<HyperClovaChatRequest.Message> out = new ArrayList<>(messages.size());
        for (CohereChatRequest.Message m : messages) {
            if (m == null || m.getContent() == null) continue;
            String role = m.getRole() == null ? "user" : m.getRole().toLowerCase();
            HyperClovaChatRequest.Message converted = switch (role) {
                case "system" -> HyperClovaChatRequest.Message.system(m.getContent());
                case "assistant" -> HyperClovaChatRequest.Message.assistant(m.getContent());
                default -> HyperClovaChatRequest.Message.user(m.getContent());
            };
            out.add(converted);
        }
        return out;
    }

    ChatParsedResponse parseChatResponse(String raw) {
        JsonNode node = tryParseJson(raw);
        if (node != null) {
            try {
                return objectMapper.treeToValue(node, ChatParsedResponse.class);
            } catch (Exception e) {
                log.debug("HyperCLOVA chat JSON shape mismatch — fallback to minimal: {}", e.getMessage());
            }
        }
        // fallback: nextQuestion만 raw 텍스트로 채움
        ChatParsedResponse fallback = new ChatParsedResponse();
        fallback.setNextQuestion(raw == null ? "" : raw.trim());
        fallback.setAllCompleted(false);
        return fallback;
    }

    BriefParsedResponse parseBriefResponse(String raw) {
        JsonNode node = tryParseJson(raw);
        if (node != null) {
            try {
                return objectMapper.treeToValue(node, BriefParsedResponse.class);
            } catch (Exception e) {
                log.debug("HyperCLOVA brief JSON shape mismatch — fallback empty: {}", e.getMessage());
            }
        }
        // BriefParsedResponse는 NoArgsConstructor 있으면 빈 인스턴스. (필드 미설정 시 downstream에서
        // null/빈 값 처리 — shadow 비교는 메트릭만 보므로 OK)
        return new BriefParsedResponse();
    }

    JsonNode tryParseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            // pass
        }
        String fenceExtracted = extractFromMarkdownFence(raw);
        if (fenceExtracted != null) {
            try {
                return objectMapper.readTree(fenceExtracted);
            } catch (Exception ignored) {
                // pass
            }
        }
        String balanced = extractBalancedJsonObject(raw);
        if (balanced != null) {
            try {
                return objectMapper.readTree(balanced);
            } catch (Exception ignored) {
                // pass
            }
        }
        return null;
    }

    private static String extractFromMarkdownFence(String raw) {
        Matcher jsonFence = Pattern.compile("```(?:json)?\\s*\\R?(.*?)```",
                Pattern.DOTALL).matcher(raw);
        if (jsonFence.find()) {
            return jsonFence.group(1).trim();
        }
        return null;
    }

    private static String extractBalancedJsonObject(String raw) {
        int n = raw.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return raw.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private static Integer extractInputTokens(HyperClovaChatResponse response) {
        if (response == null || response.getResult() == null) return null;
        return response.getResult().getInputLength();
    }

    private static Integer extractOutputTokens(HyperClovaChatResponse response) {
        if (response == null || response.getResult() == null) return null;
        return response.getResult().getOutputLength();
    }
}
