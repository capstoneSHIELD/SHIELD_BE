package org.example.shield.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CohereChatRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("forChat() 직렬화 — schema response_format 포함")
    void forChat_serialization() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("You are a helpful assistant."),
                CohereChatRequest.Message.user("Hello")
        );

        CohereChatRequest request = CohereChatRequest.forChat("command-a-03-2025", messages);
        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"model\":\"command-a-03-2025\"");
        assertThat(json).contains("\"messages\"");
        assertThat(json).contains("\"temperature\"");
        assertThat(json).contains("\"max_tokens\":1024");
        assertThat(json).contains("\"p\"");
        assertThat(json).contains("\"response_format\"");
        assertThat(json).contains("\"json_object\"");
        assertThat(json).contains("\"schema\"");
        assertThat(json).contains("\"schema_version\"");
        assertThat(json).contains("\"nextQuestion\"");
        // v2에서는 max_completion_tokens / top_p 대신 max_tokens / p 사용
        assertThat(json).doesNotContain("max_completion_tokens");
        assertThat(json).doesNotContain("top_p");
    }

    @Test
    @DisplayName("forBrief() 직렬화 — schema response_format 포함")
    void forBrief_serialization() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("Generate a brief in JSON."),
                CohereChatRequest.Message.user("My legal issue is...")
        );

        CohereChatRequest request = CohereChatRequest.forBrief("command-a-03-2025", messages);
        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"max_tokens\":4096");
        assertThat(json).contains("\"response_format\"");
        assertThat(json).contains("\"type\":\"json_object\"");
        assertThat(json).contains("\"schema\"");
        assertThat(json).contains("\"keyIssues\"");
        assertThat(json).contains("\"schema_version\"");
    }

    @Test
    @DisplayName("forClassify() 직렬화 — json_object 모드, 저온도, max_tokens 512")
    void forClassify_serialization() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("You are a legal intent classifier."),
                CohereChatRequest.Message.user("Classify the conversation intent.")
        );

        CohereChatRequest request = CohereChatRequest.forClassify("command-a-03-2025", messages);
        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"model\":\"command-a-03-2025\"");
        assertThat(json).contains("\"temperature\"");
        assertThat(json).contains("\"max_tokens\":512");
        assertThat(json).contains("\"response_format\"");
        assertThat(json).contains("\"type\":\"json_object\"");
        assertThat(json).contains("\"schema\"");
        assertThat(json).contains("\"matched_node_ids\"");
        assertThat(json).contains("\"schema_version\"");
        // forClassify should NOT include p (top-p)
        assertThat(json).doesNotContain("\"p\":");

        // Verify temperature is 0.1
        assertThat(request.getTemperature()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("structured output disabled — response_format 은 json_object 만 포함")
    void structuredOutputDisabled_jsonObjectOnly() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("You are a helpful assistant."),
                CohereChatRequest.Message.user("Hello")
        );

        CohereChatRequest request = CohereChatRequest.forChat("command-a-03-2025", messages, false);
        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"response_format\":{\"type\":\"json_object\"}");
        assertThat(json).doesNotContain("\"schema\"");
    }

    @Test
    @DisplayName("Message factory 메서드 — role 및 content 올바른 설정 (v2 lowercase)")
    void message_factories() {
        CohereChatRequest.Message system = CohereChatRequest.Message.system("sys");
        CohereChatRequest.Message user = CohereChatRequest.Message.user("usr");
        CohereChatRequest.Message assistant = CohereChatRequest.Message.assistant("ast");

        assertThat(system.getRole()).isEqualTo("system");
        assertThat(system.getContent()).isEqualTo("sys");
        assertThat(user.getRole()).isEqualTo("user");
        assertThat(user.getContent()).isEqualTo("usr");
        assertThat(assistant.getRole()).isEqualTo("assistant");
        assertThat(assistant.getContent()).isEqualTo("ast");
    }
}
