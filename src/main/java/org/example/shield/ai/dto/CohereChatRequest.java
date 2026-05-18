package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cohere Chat API v2 요청 DTO.
 * POST https://api.cohere.com/v2/chat
 *
 * v2 주요 필드:
 * - model: command-a-03-2025 등
 * - messages: [{role, content}]  역할은 lowercase (system/user/assistant/tool)
 * - temperature: 0.0~1.0 (기본 0.3)
 * - max_tokens: 응답 최대 토큰 수
 * - p: top-p (기본 0.75)
 * - response_format: {type: "json_object"} — JSON 응답 강제 (schema 선택적 지원)
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CohereChatRequest {

    private String model;
    private List<Message> messages;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double p;

    @JsonProperty("response_format")
    private Map<String, Object> responseFormat;

    /**
     * messages[] 배열 내 개별 메시지.
     * v2에서 role은 lowercase: "system" | "user" | "assistant" | "tool"
     */
    @Getter
    @Builder
    public static class Message {
        private String role;
        private String content;

        public static Message system(String text) {
            return Message.builder()
                    .role("system")
                    .content(text)
                    .build();
        }

        public static Message assistant(String text) {
            return Message.builder()
                    .role("assistant")
                    .content(text)
                    .build();
        }

        public static Message user(String text) {
            return Message.builder()
                    .role("user")
                    .content(text)
                    .build();
        }
    }

    // --- Factory Methods ---

    /**
     * Phase 1 대화: 전체 chatHistory를 messages[]로 전달.
     * response_format=json_object 로 모델 출력을 JSON 객체로 강제 (Issue #56).
     */
    public static CohereChatRequest forChat(String model, List<Message> messages) {
        return forChat(model, messages, true);
    }

    public static CohereChatRequest forChat(String model, List<Message> messages, boolean structuredOutputEnabled) {
        return CohereChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.3)
                .maxTokens(1024)
                .p(0.9)
                .responseFormat(structuredOutputEnabled ? chatResponseFormat() : jsonObjectResponseFormat())
                .build();
    }

    /**
     * Phase 2 의뢰서 생성 (json_object 모드).
     * Cohere v2는 response_format={type: "json_object"}를 모든 command 계열에서 지원.
     */
    public static CohereChatRequest forBrief(String model, List<Message> messages) {
        return forBrief(model, messages, true);
    }

    public static CohereChatRequest forBrief(String model, List<Message> messages, boolean structuredOutputEnabled) {
        return CohereChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.5)
                .maxTokens(4096)
                .p(0.95)
                .responseFormat(structuredOutputEnabled ? briefResponseFormat() : jsonObjectResponseFormat())
                .build();
    }

    /**
     * RAG Layer 1 의도 분류 (json_object 모드, 저온도).
     * temperature 0.1로 결정적 출력, max_tokens 512로 경량 호출.
     */
    public static CohereChatRequest forClassify(String model, List<Message> messages) {
        return forClassify(model, messages, 0.1, 512);
    }

    public static CohereChatRequest forClassify(String model, List<Message> messages, double temperature, int maxTokens) {
        return forClassify(model, messages, temperature, maxTokens, true);
    }

    public static CohereChatRequest forClassify(
            String model, List<Message> messages, double temperature, int maxTokens, boolean structuredOutputEnabled) {
        return CohereChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .responseFormat(structuredOutputEnabled ? classifyResponseFormat() : jsonObjectResponseFormat())
                .build();
    }

    private static Map<String, Object> jsonObjectResponseFormat() {
        return Map.of("type", "json_object");
    }

    private static Map<String, Object> chatResponseFormat() {
        Map<String, Object> correctedSlotSchema = objectSchema(
                List.of("slotId", "previousValue", "newValue", "confidence"),
                Map.of(
                        "slotId", Map.of("type", "string"),
                        "previousValue", Map.of("type", "string"),
                        "newValue", Map.of("type", "string"),
                        "confidence", Map.of("type", "number")
                )
        );

        return responseFormat(objectSchema(
                List.of("schema_version", "nextQuestion", "aiDomains", "aiSubDomains", "aiTags", "allCompleted"),
                Map.of(
                        "schema_version", stringEnumSchema("1.0"),
                        "nextQuestion", Map.of("type", "string"),
                        "aiDomains", stringArraySchema(),
                        "aiSubDomains", stringArraySchema(),
                        "aiTags", stringArraySchema(),
                        "allCompleted", Map.of("type", "boolean"),
                        "correctedSlots", Map.of("type", "array", "items", correctedSlotSchema)
                )
        ));
    }

    private static Map<String, Object> briefResponseFormat() {
        Map<String, Object> keyIssueSchema = objectSchema(
                List.of("title", "description"),
                Map.of(
                        "title", Map.of("type", "string"),
                        "description", Map.of("type", "string")
                )
        );

        return responseFormat(objectSchema(
                List.of("schema_version", "title", "content", "keyIssues", "keywords", "strategy"),
                Map.of(
                        "schema_version", stringEnumSchema("1.0"),
                        "title", Map.of("type", "string"),
                        "content", Map.of("type", "string"),
                        "keyIssues", Map.of("type", "array", "items", keyIssueSchema),
                        "keywords", stringArraySchema(),
                        "strategy", Map.of("type", "string")
                )
        ));
    }

    private static Map<String, Object> classifyResponseFormat() {
        Map<String, Object> slotSchema = objectSchema(
                List.of("slotId", "value", "rawText", "confidence", "valueType", "needsConfirmation"),
                Map.of(
                        "slotId", Map.of("type", "string"),
                        "value", Map.of("type", "string"),
                        "rawText", Map.of("type", "string"),
                        "confidence", Map.of("type", "number"),
                        "valueType", Map.of("type", "string"),
                        "needsConfirmation", Map.of("type", "boolean")
                )
        );
        Map<String, Object> caseTypeSchema = objectSchema(
                List.of("l1", "l2", "l3", "confidence"),
                Map.of(
                        "l1", Map.of("type", "string"),
                        "l2", Map.of("type", "string"),
                        "l3", Map.of("type", "string"),
                        "confidence", Map.of("type", "number")
                )
        );
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("schema_version", stringEnumSchema("2.0"));
        properties.put("dialogueIntent", stringEnumSchema(
                "PROVIDE_INFO",
                "CORRECT_INFO",
                "CONFIRM",
                "CHANGE_TOPIC",
                "ASK_LEGAL_ADVICE",
                "IRRELEVANT",
                "GREETING",
                "END_CONSULTATION"));
        properties.put("intentConfidence", Map.of("type", "number"));
        properties.put("extractedSlots", Map.of("type", "array", "items", slotSchema));
        properties.put("caseType", caseTypeSchema);
        properties.put("intent_summary", Map.of("type", "string"));
        properties.put("matched_node_ids", stringArraySchema());
        properties.put("core_keywords", stringArraySchema());
        properties.put("retrieval_query", Map.of("type", "string"));
        properties.put("retrievalQueries", stringArraySchema());
        properties.put("correctedSlotIds", stringArraySchema());
        properties.put("topicChanged", Map.of("type", "boolean"));
        return responseFormat(objectSchema(
                List.of("schema_version", "dialogueIntent", "intentConfidence", "extractedSlots",
                        "caseType", "intent_summary", "matched_node_ids", "core_keywords",
                        "retrieval_query", "retrievalQueries", "correctedSlotIds", "topicChanged"),
                properties
        ));
    }

    private static Map<String, Object> responseFormat(Map<String, Object> schema) {
        return Map.of(
                "type", "json_object",
                "schema", schema
        );
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", required);
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
    }

    private static Map<String, Object> stringEnumSchema(String value) {
        return stringEnumSchema(List.of(value));
    }

    private static Map<String, Object> stringEnumSchema(String first, String... rest) {
        List<String> values = new java.util.ArrayList<>();
        values.add(first);
        values.addAll(List.of(rest));
        return stringEnumSchema(values);
    }

    private static Map<String, Object> stringEnumSchema(List<String> values) {
        return Map.of(
                "type", "string",
                "enum", values
        );
    }
}
