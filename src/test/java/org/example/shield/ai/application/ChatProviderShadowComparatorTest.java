package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.provider.hyperclova.HyperClovaChatClientAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatProviderShadowComparator} 검증 (P5.5 Commit 4).
 *
 * <p>HyperCLOVA adapter는 mock으로 대체 — 실제 API 호출 없음. sampling/규제 비교 로직만 검증.
 */
class ChatProviderShadowComparatorTest {

    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private GuardrailFilter guardrail;
    private HyperClovaChatClientAdapter adapter;
    private ChatProviderShadowComparator comparator;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);
        guardrail = new GuardrailFilter();
        adapter = mock(HyperClovaChatClientAdapter.class);
        comparator = new ChatProviderShadowComparator(adapter, guardrail, metrics);
        // 기본 sampling=1.0 (모두 호출)
        ReflectionTestUtils.setField(comparator, "samplingRate", 1.0);
    }

    private static AiCallResult<ChatParsedResponse> cohereResult(String text, int tokensIn, int tokensOut, int latency) {
        ChatParsedResponse c = new ChatParsedResponse();
        c.setNextQuestion(text);
        return new AiCallResult<>("resp-1", c, tokensIn, tokensOut, latency);
    }

    @Test
    @DisplayName("sampling=0 → HyperCLOVA 호출 없음")
    void zeroSamplingSkipsCall() {
        ReflectionTestUtils.setField(comparator, "samplingRate", 0.0);

        comparator.compare(List.of(),
                cohereResult("질문", 100, 50, 1200),
                "conv-1", null);

        verify(adapter, never()).callChat(any(), any());
    }

    @Test
    @DisplayName("conversationId null → 호출 안 함")
    void nullConvIdSkipsCall() {
        comparator.compare(List.of(),
                cohereResult("질문", 100, 50, 1200),
                null, null);

        verify(adapter, never()).callChat(any(), any());
    }

    @Test
    @DisplayName("같은 conversationId는 deterministic — 두 번 호출하면 같은 결정")
    void deterministicByConvId() {
        ReflectionTestUtils.setField(comparator, "samplingRate", 0.5);

        boolean first = comparator.shouldShadow("conv-stable-1");
        boolean second = comparator.shouldShadow("conv-stable-1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("sampling 활성 + adapter 정상 → comparison 메트릭 발행")
    void recordsComparisonMetrics() {
        ChatParsedResponse hcxData = new ChatParsedResponse();
        hcxData.setNextQuestion("HyperCLOVA 응답 — 민법 제618조 안내");
        when(adapter.callChat(anyString(), any()))
                .thenReturn(new AiCallResult<>(null, hcxData, 110, 70, 1500));

        comparator.compare(List.of(),
                cohereResult("Cohere 응답입니다", 100, 50, 1200),
                "conv-1", "HCX-005");

        verify(adapter, times(1)).callChat(anyString(), any());
        // metric tags 검증 — DistributionSummary에 record 호출됐는지
        assertThat(registry.find("shield.ai.chat.shadow_compare")
                .tag("provider", "cohere").tag("metric", "length").summary()).isNotNull();
        assertThat(registry.find("shield.ai.chat.shadow_compare")
                .tag("provider", "hyperclova").tag("metric", "statute_refs").summary()
                .max()).isGreaterThanOrEqualTo(1.0);  // "민법 제618조" 1건 매치
    }

    @Test
    @DisplayName("HyperCLOVA 호출 실패 → fail-open + failure 메트릭")
    void failureRecorded() {
        // model 인자가 null일 수 있으므로 any() 사용 (anyString()은 null 미매치)
        when(adapter.callChat(any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        // 예외 흡수 — 호출자가 throw 받지 않음
        comparator.compare(List.of(),
                cohereResult("응답", 100, 50, 1200),
                "conv-1", null);

        assertThat(registry.find("shield.ai.chat.shadow_compare.failure")
                .tag("provider", "hyperclova").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("snapshot이 법령/판례 인용 수를 정확히 카운트")
    void snapshotCountsReferences() {
        String text = "민법 제618조와 상법 제5조 그리고 대법원 2020다12345 참조";
        ChatProviderShadowComparator.ComparisonSnapshot snap =
                comparator.snapshotOf(new AiCallResult<>(null, makeChat(text), 0, 0, 0));

        assertThat(snap.statuteRefs()).isEqualTo(2);
        assertThat(snap.caseRefs()).isEqualTo(1);
        assertThat(snap.length()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("snapshot이 guardrail violation 감지")
    void snapshotDetectsGuardrail() {
        String text = "이 사건은 승소 가능성이 높습니다.";
        ChatProviderShadowComparator.ComparisonSnapshot snap =
                comparator.snapshotOf(new AiCallResult<>(null, makeChat(text), 0, 0, 0));

        assertThat(snap.guardrailViolation()).isTrue();
    }

    private static ChatParsedResponse makeChat(String text) {
        ChatParsedResponse c = new ChatParsedResponse();
        c.setNextQuestion(text);
        return c;
    }
}
