package org.example.shield.ai.provider.hyperclova;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.config.HyperClovaApiConfig;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.infrastructure.HyperClovaChatClient;
import org.example.shield.ai.infrastructure.HyperClovaChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HyperClovaChatClientAdapter} 검증 (P5.5 Commit 4).
 *
 * <p>실제 HyperCLOVA API는 호출하지 않고 raw 응답 객체를 만들어 파싱·변환만 검증.
 */
class HyperClovaChatClientAdapterTest {

    private HyperClovaChatClient client;
    private HyperClovaApiConfig config;
    private ObjectMapper objectMapper;
    private HyperClovaChatClientAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(HyperClovaChatClient.class);
        config = new HyperClovaApiConfig();
        ReflectionTestUtils.setField(config, "chatModel", "HCX-005");
        objectMapper = new ObjectMapper();
        adapter = new HyperClovaChatClientAdapter(client, config, objectMapper);
    }

    private static HyperClovaChatClient.ChatCallResult callResult(String content, int inputLen, int outputLen) {
        HyperClovaChatResponse resp = new HyperClovaChatResponse();
        HyperClovaChatResponse.Status status = new HyperClovaChatResponse.Status();
        status.setCode("20000");
        resp.setStatus(status);
        HyperClovaChatResponse.Result result = new HyperClovaChatResponse.Result();
        HyperClovaChatResponse.Message m = new HyperClovaChatResponse.Message();
        m.setRole("assistant");
        m.setContent(content);
        result.setMessage(m);
        result.setInputLength(inputLen);
        result.setOutputLength(outputLen);
        resp.setResult(result);
        return new HyperClovaChatClient.ChatCallResult(resp, 800L);
    }

    @Test
    @DisplayName("providerKey is 'hyperclova'")
    void providerKey() {
        assertThat(adapter.providerKey()).isEqualTo("hyperclova");
    }

    @Test
    @DisplayName("정상 JSON 응답 — ChatParsedResponse 파싱")
    void parsesValidJson() {
        when(client.callChat(anyString(), any(), anyInt()))
                .thenReturn(callResult(
                        "{\"nextQuestion\":\"임대차 계약 기간은 얼마였나요?\",\"allCompleted\":false}",
                        120, 50));

        AiCallResult<ChatParsedResponse> result = adapter.callChat(null,
                List.of(CohereChatRequest.Message.user("안녕하세요")));

        assertThat(result.data().getNextQuestion()).isEqualTo("임대차 계약 기간은 얼마였나요?");
        assertThat(result.data().isAllCompleted()).isFalse();
        assertThat(result.tokensInput()).isEqualTo(120);
        assertThat(result.tokensOutput()).isEqualTo(50);
    }

    @Test
    @DisplayName("JSON 아닌 plain 텍스트 — fallback으로 nextQuestion에 raw 텍스트")
    void fallsBackToRawText() {
        when(client.callChat(anyString(), any(), anyInt()))
                .thenReturn(callResult("그냥 평문 응답입니다", 50, 20));

        AiCallResult<ChatParsedResponse> result = adapter.callChat("HCX-005",
                List.of(CohereChatRequest.Message.user("안녕")));

        assertThat(result.data().getNextQuestion()).isEqualTo("그냥 평문 응답입니다");
        assertThat(result.data().isAllCompleted()).isFalse();
    }

    @Test
    @DisplayName("마크다운 fence 안의 JSON — 파싱 성공")
    void parsesMarkdownFenceJson() {
        String content = """
                응답:
                ```json
                {"nextQuestion":"보증금은 얼마였나요?","allCompleted":false}
                ```
                """;
        when(client.callChat(anyString(), any(), anyInt()))
                .thenReturn(callResult(content, 100, 30));

        AiCallResult<ChatParsedResponse> result = adapter.callChat(null,
                List.of(CohereChatRequest.Message.user("안녕")));

        assertThat(result.data().getNextQuestion()).isEqualTo("보증금은 얼마였나요?");
    }

    @Test
    @DisplayName("Cohere → HyperCLOVA message 변환은 role 그대로 유지")
    void messageConversionPreservesRoles() {
        List<CohereChatRequest.Message> input = List.of(
                CohereChatRequest.Message.system("시스템 프롬프트"),
                CohereChatRequest.Message.user("사용자 메시지"),
                CohereChatRequest.Message.assistant("이전 응답")
        );
        var converted = adapter.toHyperClovaMessages(input);
        assertThat(converted).hasSize(3);
        assertThat(converted.get(0).getRole()).isEqualTo("system");
        assertThat(converted.get(1).getRole()).isEqualTo("user");
        assertThat(converted.get(2).getRole()).isEqualTo("assistant");
        assertThat(converted.get(0).getContent()).isEqualTo("시스템 프롬프트");
    }
}
