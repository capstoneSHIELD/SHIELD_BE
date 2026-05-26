package org.example.shield.ai.provider.hyperclova;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.HyperClovaApiConfig;
import org.example.shield.ai.infrastructure.HyperClovaChatRequest;
import org.example.shield.ai.infrastructure.HyperClovaChatResponse;
import org.example.shield.ai.infrastructure.HyperClovaJudgeClient;
import org.example.shield.ai.provider.AiJudgeClient;
import org.example.shield.ai.provider.JudgeRequest;
import org.example.shield.ai.provider.JudgeResult;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HyperCLOVA X {@link AiJudgeClient} adapter (Phase P5.5 Commit 1).
 *
 * <p>{@link HyperClovaJudgeClient}의 raw 응답을 {@link JudgeResult}로 변환.
 * Judge 프롬프트는 classpath {@code ai/prompts/judge/legal-compliance-judge.md}에서 로드.
 *
 * <p>응답 파싱 정책:
 * <ul>
 *   <li>1차: 원문 JSON 파싱</li>
 *   <li>2차: 마크다운 fence 안의 JSON 추출 (CohereClient 패턴 재사용)</li>
 *   <li>3차: 평문에서 균형 잡힌 첫 JSON 객체 추출</li>
 *   <li>모두 실패 시 fallback (PASS + low confidence + reason="parse_failure")</li>
 * </ul>
 */
@Component
@Slf4j
public class HyperClovaJudgeClientAdapter implements AiJudgeClient {

    public static final String PROVIDER_KEY = "hyperclova";

    private static final String PROMPT_PATH = "classpath:ai/prompts/judge/legal-compliance-judge.md";
    private static final String RESPONSE_PLACEHOLDER = "{RESPONSE}";

    private final HyperClovaJudgeClient client;
    private final HyperClovaApiConfig config;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private String promptTemplate;

    public HyperClovaJudgeClientAdapter(HyperClovaJudgeClient client,
                                        HyperClovaApiConfig config,
                                        ResourceLoader resourceLoader,
                                        ObjectMapper objectMapper) {
        this.client = client;
        this.config = config;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadPromptTemplate() {
        try {
            this.promptTemplate = StreamUtils.copyToString(
                    resourceLoader.getResource(PROMPT_PATH).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HyperCLOVA judge prompt: " + PROMPT_PATH, e);
        }
    }

    @Override
    public JudgeResult judge(String maskedResponse, JudgeRequest request) {
        if (maskedResponse == null || maskedResponse.isBlank()) {
            return new JudgeResult(JudgeResult.Verdict.PASS, 1.0, "empty_response", List.of(), 0, 0, 0L);
        }
        String systemPrompt = promptTemplate.replace(RESPONSE_PLACEHOLDER, maskedResponse);
        List<HyperClovaChatRequest.Message> messages = List.of(
                HyperClovaChatRequest.Message.system(systemPrompt),
                HyperClovaChatRequest.Message.user("위 응답을 평가하세요. JSON만 출력.")
        );
        HyperClovaJudgeClient.JudgeCallResult call = client.callJudge(
                config.getJudgeModel(), messages, request.maxOutputTokens());
        return parseResponse(call);
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    JudgeResult parseResponse(HyperClovaJudgeClient.JudgeCallResult call) {
        HyperClovaChatResponse response = call.response();
        String raw = response.extractContent();
        Integer inputTokens = response.getResult() == null ? null : response.getResult().getInputLength();
        Integer outputTokens = response.getResult() == null ? null : response.getResult().getOutputLength();

        JsonNode node = tryParseJson(raw);
        if (node == null) {
            log.warn("HyperCLOVA judge parse failure — fallback PASS. raw: {}",
                    raw.substring(0, Math.min(200, raw.length())));
            return new JudgeResult(JudgeResult.Verdict.PASS, 0.0,
                    "parse_failure", List.of(), inputTokens, outputTokens, call.latencyMs());
        }
        return toJudgeResult(node, inputTokens, outputTokens, call.latencyMs());
    }

    private JudgeResult toJudgeResult(JsonNode node, Integer inputTokens, Integer outputTokens, long latencyMs) {
        JudgeResult.Verdict verdict = parseVerdict(node.path("verdict").asText("PASS"));
        double confidence = clamp(node.path("confidence").asDouble(0.0));
        String reason = node.path("reason").asText("");

        List<String> categories = new ArrayList<>();
        JsonNode catsNode = node.path("categories");
        if (catsNode.isArray()) {
            catsNode.forEach(c -> {
                String text = c.asText("");
                if (!text.isBlank()) categories.add(text);
            });
        }
        return new JudgeResult(verdict, confidence, reason, categories,
                inputTokens, outputTokens, latencyMs);
    }

    private JudgeResult.Verdict parseVerdict(String text) {
        if (text == null) return JudgeResult.Verdict.PASS;
        try {
            return JudgeResult.Verdict.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("HyperCLOVA judge invalid verdict '{}', defaulting to PASS", text);
            return JudgeResult.Verdict.PASS;
        }
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * 3-단계 fallback JSON 파싱 (Cohere 패턴과 동일):
     * 1) 원문 그대로
     * 2) ```json fence 안의 본문
     * 3) 평문에서 균형 잡힌 첫 JSON 객체
     */
    JsonNode tryParseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // 1차
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            // pass
        }
        // 2차
        String fenceExtracted = extractFromMarkdownFence(raw);
        if (fenceExtracted != null) {
            try {
                return objectMapper.readTree(fenceExtracted);
            } catch (Exception ignored) {
                // pass
            }
        }
        // 3차
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
}
