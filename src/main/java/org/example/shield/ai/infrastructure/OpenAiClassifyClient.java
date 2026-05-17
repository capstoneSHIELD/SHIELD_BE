package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.OpenAiApiConfig;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.consultation.exception.AnalysisFailedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions client used only for RAG intent classification.
 */
@Component
@Slf4j
public class OpenAiClassifyClient {

    private final WebClient openAiWebClient;
    private final OpenAiApiConfig config;

    public OpenAiClassifyClient(@Qualifier("openAiWebClient") WebClient openAiWebClient,
                                OpenAiApiConfig config) {
        this.openAiWebClient = openAiWebClient;
        this.config = config;
    }

    public AiCallResult<String> callRawJson(List<CohereChatRequest.Message> messages) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new AnalysisFailedException("OpenAI API key is not configured");
        }

        long startNanos = System.nanoTime();
        Map<String, Object> request = buildRequest(messages);

        try {
            JsonNode response = openAiWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(config.getClassifyReadTimeout()))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryable))
                    .block();

            if (response == null) {
                throw new AnalysisFailedException("OpenAI classify API response is null");
            }

            String contentJson = response.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText(null);
            if (contentJson == null || contentJson.isBlank()) {
                throw new AnalysisFailedException("OpenAI classify API response content is blank");
            }

            int latencyMs = (int) ((System.nanoTime() - startNanos) / 1_000_000);
            Integer tokensIn = intOrNull(response.path("usage").path("prompt_tokens"));
            Integer tokensOut = intOrNull(response.path("usage").path("completion_tokens"));
            String responseId = response.path("id").asText(null);

            log.info("OpenAI classify API call succeeded: id={}, model={}, tokensIn={}, tokensOut={}, latency={}ms",
                    responseId, config.getClassifyModel(), tokensIn, tokensOut, latencyMs);

            return new AiCallResult<>(responseId, contentJson, tokensIn, tokensOut, latencyMs);
        } catch (AnalysisFailedException e) {
            throw e;
        } catch (Exception e) {
            int latencyMs = (int) ((System.nanoTime() - startNanos) / 1_000_000);
            log.error("OpenAI classify API call failed: model={}, latency={}ms, error={}",
                    config.getClassifyModel(), latencyMs, e.getMessage(), e);
            throw new AnalysisFailedException("OpenAI classify API call failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequest(List<CohereChatRequest.Message> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.getClassifyModel());
        request.put("messages", messages.stream()
                .map(this::toOpenAiMessage)
                .toList());
        request.put("response_format", config.isStructuredOutputEnabled()
                ? strictJsonSchemaResponseFormat()
                : Map.of("type", "json_object"));
        request.put("max_completion_tokens", config.getClassifyMaxTokens());
        if (config.getClassifyReasoningEffort() != null
                && !config.getClassifyReasoningEffort().isBlank()) {
            request.put("reasoning_effort", config.getClassifyReasoningEffort());
        }
        return request;
    }

    private Map<String, Object> strictJsonSchemaResponseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "shield_intent_classification_v1",
                        "strict", true,
                        "schema", classifierSchema()
                )
        );
    }

    private Map<String, Object> classifierSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "schema_version",
                "intent_summary",
                "matched_node_ids",
                "core_keywords",
                "retrieval_query"
        ));
        schema.put("properties", Map.of(
                "schema_version", Map.of(
                        "type", "string",
                        "enum", List.of("1.0")
                ),
                "intent_summary", Map.of("type", "string"),
                "matched_node_ids", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                ),
                "core_keywords", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                ),
                "retrieval_query", Map.of("type", "string")
        ));
        return schema;
    }

    private Map<String, String> toOpenAiMessage(CohereChatRequest.Message message) {
        Map<String, String> converted = new LinkedHashMap<>();
        converted.put("role", message.getRole());
        converted.put("content", message.getContent());
        return converted;
    }

    private Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }
}
