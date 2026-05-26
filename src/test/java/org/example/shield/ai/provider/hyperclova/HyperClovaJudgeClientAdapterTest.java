package org.example.shield.ai.provider.hyperclova;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.config.HyperClovaApiConfig;
import org.example.shield.ai.infrastructure.HyperClovaChatResponse;
import org.example.shield.ai.infrastructure.HyperClovaJudgeClient;
import org.example.shield.ai.provider.JudgeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HyperClovaJudgeClientAdapter} 검증 (P5.5 Commit 1).
 *
 * <p>실제 HyperCLOVA API는 호출하지 않고 raw 응답 객체를 직접 만들어 파싱·변환만 검증.
 */
class HyperClovaJudgeClientAdapterTest {

    private HyperClovaJudgeClient client;
    private HyperClovaApiConfig config;
    private ObjectMapper objectMapper;
    private HyperClovaJudgeClientAdapter adapter;

    @BeforeEach
    void setUp() {
        client = org.mockito.Mockito.mock(HyperClovaJudgeClient.class);
        config = new HyperClovaApiConfig();
        ReflectionTestUtils.setField(config, "judgeModel", "HCX-005");
        objectMapper = new ObjectMapper();
        adapter = new HyperClovaJudgeClientAdapter(
                client, config, new DefaultResourceLoader(), objectMapper);
        adapter.loadPromptTemplate();
    }

    private static HyperClovaJudgeClient.JudgeCallResult callResult(String content, int inputLen, int outputLen) {
        HyperClovaChatResponse resp = new HyperClovaChatResponse();
        HyperClovaChatResponse.Status status = new HyperClovaChatResponse.Status();
        status.setCode("20000");
        status.setMessage("OK");
        resp.setStatus(status);
        HyperClovaChatResponse.Result result = new HyperClovaChatResponse.Result();
        HyperClovaChatResponse.Message msg = new HyperClovaChatResponse.Message();
        msg.setRole("assistant");
        msg.setContent(content);
        result.setMessage(msg);
        result.setInputLength(inputLen);
        result.setOutputLength(outputLen);
        resp.setResult(result);
        return new HyperClovaJudgeClient.JudgeCallResult(resp, 250L);
    }

    @Test
    @DisplayName("providerKey is 'hyperclova'")
    void providerKey() {
        assertThat(adapter.providerKey()).isEqualTo("hyperclova");
    }

    @Test
    @DisplayName("정상 JSON 응답 — PASS 파싱")
    void parsesPassVerdict() {
        String content = """
                {"verdict":"PASS","confidence":0.92,"reason":"법령 정보 안내만 포함","categories":[]}
                """;
        JudgeResult result = adapter.parseResponse(callResult(content, 100, 30));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.PASS);
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.reason()).isEqualTo("법령 정보 안내만 포함");
        assertThat(result.categories()).isEmpty();
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(30);
        assertThat(result.latencyMs()).isEqualTo(250L);
    }

    @Test
    @DisplayName("HARD_VIOLATION 응답 + categories 파싱")
    void parsesHardViolation() {
        String content = """
                {"verdict":"HARD_VIOLATION","confidence":0.88,"reason":"법적 단정",
                 "categories":["legal_conclusion","win_prediction"]}
                """;
        JudgeResult result = adapter.parseResponse(callResult(content, 120, 50));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.HARD_VIOLATION);
        assertThat(result.categories()).containsExactly("legal_conclusion", "win_prediction");
    }

    @Test
    @DisplayName("마크다운 fence ```json ... ``` 안의 JSON 파싱")
    void parsesMarkdownFenceJson() {
        String content = """
                답변을 평가했습니다. 결과는:

                ```json
                {"verdict":"SOFT_VIOLATION","confidence":0.6,"reason":"모호한 경향성","categories":["tendency_or_likelihood"]}
                ```
                """;
        JudgeResult result = adapter.parseResponse(callResult(content, 110, 80));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.SOFT_VIOLATION);
        assertThat(result.confidence()).isEqualTo(0.6);
        assertThat(result.categories()).containsExactly("tendency_or_likelihood");
    }

    @Test
    @DisplayName("평문 + 균형 잡힌 JSON 객체 — 3차 fallback 파싱")
    void parsesBalancedJsonInText() {
        String content = "평가 결과는 다음과 같습니다: {\"verdict\":\"PASS\",\"confidence\":0.85," +
                "\"reason\":\"문제 없음\",\"categories\":[]} 이상입니다.";
        JudgeResult result = adapter.parseResponse(callResult(content, 100, 40));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.PASS);
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("파싱 완전 실패 — fallback PASS + confidence 0")
    void parseFailureFallsBackToPass() {
        JudgeResult result = adapter.parseResponse(callResult("이상한 응답입니다 JSON 없음", 50, 20));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.PASS);
        assertThat(result.confidence()).isZero();
        assertThat(result.reason()).isEqualTo("parse_failure");
    }

    @Test
    @DisplayName("invalid verdict 값 → PASS fallback")
    void invalidVerdictFallsBackToPass() {
        String content = "{\"verdict\":\"BOGUS\",\"confidence\":0.5,\"reason\":\"\",\"categories\":[]}";
        JudgeResult result = adapter.parseResponse(callResult(content, 50, 20));

        assertThat(result.verdict()).isEqualTo(JudgeResult.Verdict.PASS);
    }

    @Test
    @DisplayName("confidence 범위 초과 — 0~1로 clamp")
    void confidenceIsClamped() {
        String content = "{\"verdict\":\"PASS\",\"confidence\":1.5,\"reason\":\"\",\"categories\":[]}";
        JudgeResult result = adapter.parseResponse(callResult(content, 50, 20));

        assertThat(result.confidence()).isEqualTo(1.0);

        String content2 = "{\"verdict\":\"PASS\",\"confidence\":-0.3,\"reason\":\"\",\"categories\":[]}";
        JudgeResult result2 = adapter.parseResponse(callResult(content2, 50, 20));
        assertThat(result2.confidence()).isZero();
    }

    @Test
    @DisplayName("빈/blank maskedResponse — API 호출 없이 PASS 즉시 반환")
    void blankResponseShortCircuits() {
        JudgeResult result1 = adapter.judge(null, org.example.shield.ai.provider.JudgeRequest.legalCompliance());
        JudgeResult result2 = adapter.judge("", org.example.shield.ai.provider.JudgeRequest.legalCompliance());
        JudgeResult result3 = adapter.judge("   ", org.example.shield.ai.provider.JudgeRequest.legalCompliance());

        for (JudgeResult r : new JudgeResult[]{result1, result2, result3}) {
            assertThat(r.verdict()).isEqualTo(JudgeResult.Verdict.PASS);
            assertThat(r.reason()).isEqualTo("empty_response");
        }
        // API 호출 없음
        org.mockito.Mockito.verifyNoInteractions(client);
    }
}
