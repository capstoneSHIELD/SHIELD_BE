package org.example.shield.ai.infrastructure;

import org.example.shield.ai.config.OpenAiApiConfig;
import org.example.shield.ai.dto.CohereChatRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiClassifyClientTest {

    @Test
    @DisplayName("buildRequest — strict json_schema response_format 사용")
    void buildRequest_strictJsonSchema() throws Exception {
        OpenAiApiConfig config = mock(OpenAiApiConfig.class);
        when(config.getClassifyModel()).thenReturn("gpt-5.4-nano");
        when(config.getClassifyMaxTokens()).thenReturn(512);
        when(config.getClassifyReasoningEffort()).thenReturn("low");
        when(config.isStructuredOutputEnabled()).thenReturn(true);

        OpenAiClassifyClient client = new OpenAiClassifyClient(null, config);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) buildRequest().invoke(client, List.of(
                CohereChatRequest.Message.system("classify"),
                CohereChatRequest.Message.user("query")
        ));

        assertThat(request.get("model")).isEqualTo("gpt-5.4-nano");
        assertThat(request.get("reasoning_effort")).isEqualTo("low");

        @SuppressWarnings("unchecked")
        Map<String, Object> responseFormat = (Map<String, Object>) request.get("response_format");
        assertThat(responseFormat.get("type")).isEqualTo("json_schema");

        @SuppressWarnings("unchecked")
        Map<String, Object> jsonSchema = (Map<String, Object>) responseFormat.get("json_schema");
        assertThat(jsonSchema.get("strict")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) jsonSchema.get("schema");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(jsonSchema.get("name")).isEqualTo("shield_intent_router_v2");
        assertThat(schema.toString()).contains(
                "schema_version",
                "2.0",
                "dialogueIntent",
                "ASK_LEGAL_ADVICE",
                "extractedSlots",
                "caseType",
                "matched_node_ids",
                "core_keywords",
                "retrieval_query");
    }

    @Test
    @DisplayName("buildRequest — structured output 비활성화 시 json_object 사용")
    void buildRequest_jsonObjectFallback() throws Exception {
        OpenAiApiConfig config = mock(OpenAiApiConfig.class);
        when(config.getClassifyModel()).thenReturn("gpt-5.4-nano");
        when(config.getClassifyMaxTokens()).thenReturn(512);
        when(config.getClassifyReasoningEffort()).thenReturn("");
        when(config.isStructuredOutputEnabled()).thenReturn(false);

        OpenAiClassifyClient client = new OpenAiClassifyClient(null, config);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) buildRequest().invoke(client, List.of(
                CohereChatRequest.Message.system("classify"),
                CohereChatRequest.Message.user("query")
        ));

        assertThat(request.get("response_format")).isEqualTo(Map.of("type", "json_object"));
    }

    private Method buildRequest() throws Exception {
        Method method = OpenAiClassifyClient.class.getDeclaredMethod("buildRequest", List.class);
        method.setAccessible(true);
        return method;
    }
}
