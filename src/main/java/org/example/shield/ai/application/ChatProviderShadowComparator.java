package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.provider.hyperclova.HyperClovaChatClientAdapter;
import org.example.shield.ai.util.ConversationDeterministicSampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat 응답 provider shadow 비교 서비스 (Phase P5.5 Commit 4).
 *
 * <p>SHIELD의 production chat은 항상 Cohere가 user-facing 응답을 생성한다. 본 컴포넌트는
 * 별도로 HyperCLOVA X에 동일 messages로 chat 호출을 던지고, 두 응답을 비교하는 메트릭을
 * 발행한다. 비교 결과는 user-facing 동작에 영향 없음 — 운영 데이터 축적 + 오프라인 분석용.
 *
 * <p><b>활성화 조건</b>:
 * <ul>
 *   <li>{@code app.ai.chat.provider=shadow_compare}</li>
 *   <li>{@code HyperClovaChatClientAdapter} bean이 등록됨 (위 조건과 일치)</li>
 *   <li>conversationId 기반 deterministic sampling이 {@code shadow-compare-sampling} rate에 매치</li>
 * </ul>
 *
 * <p><b>비교 메트릭</b>:
 * <ul>
 *   <li>응답 길이 (input/output 토큰 수)</li>
 *   <li>GuardrailFilter regex hit (한쪽만 잡힌 케이스가 의미 있음)</li>
 *   <li>한국 법령/판례 인용 정규식 매치 수</li>
 * </ul>
 *
 * <p><b>안전 원칙</b>: HyperCLOVA 호출 실패는 user-facing 동작에 영향 0. fail-open.
 * 비용 가드는 호출 빈도 자체를 sampling-rate으로 제어 (1~5% 권장).
 */
@Component
@Slf4j
public class ChatProviderShadowComparator {

    private static final Pattern STATUTE_REF = Pattern.compile(
            "(?:민법|상법|형법|민사소송법|형사소송법|근로기준법|주택임대차보호법|상가건물임대차보호법)\\s*제?\\d+조");
    private static final Pattern CASE_REF = Pattern.compile(
            "대?법원?\\s*\\d{2,4}[가-힣]+\\d+");

    public static final String METRIC_CHAT_SHADOW = "shield.ai.chat.shadow_compare";

    private final HyperClovaChatClientAdapter hyperClovaAdapter;
    private final GuardrailFilter guardrailFilter;
    private final AiRagOperationalMetrics metrics;

    @Value("${app.ai.chat.shadow-compare-sampling:0.0}")
    private double samplingRate;

    public ChatProviderShadowComparator(@Nullable HyperClovaChatClientAdapter hyperClovaAdapter,
                                        GuardrailFilter guardrailFilter,
                                        AiRagOperationalMetrics metrics) {
        this.hyperClovaAdapter = hyperClovaAdapter;
        this.guardrailFilter = guardrailFilter;
        this.metrics = metrics;
    }

    /**
     * Cohere chat 응답을 받은 직후 best-effort로 HyperCLOVA에 같은 messages를 호출해 비교 메트릭을 발행.
     *
     * <p>호출자(CohereService.chat)는 본 메서드 반환을 기다리지 않아도 됨 — 동기 호출이지만 try-catch로
     * 모든 예외를 흡수한다. 비동기 사용을 원하면 호출자에서 별도 thread/executor에 위임.
     *
     * @param messages         Cohere chat에 전달된 동일 messages
     * @param cohereResult     Cohere 응답 (비교 baseline)
     * @param conversationId   sampling 결정용 deterministic key
     * @param model            HyperCLOVA chat 모델 ID (null이면 adapter 기본값)
     */
    public void compare(List<CohereChatRequest.Message> messages,
                        AiCallResult<ChatParsedResponse> cohereResult,
                        @Nullable String conversationId,
                        @Nullable String model) {
        if (hyperClovaAdapter == null || cohereResult == null) {
            return;
        }
        if (!shouldShadow(conversationId)) {
            return;
        }
        try {
            AiCallResult<ChatParsedResponse> shadowResult = hyperClovaAdapter.callChat(model, messages);
            if (shadowResult == null || shadowResult.data() == null) {
                log.warn("Chat shadow compare returned empty result (provider=hyperclova, model={})", model);
                recordFailure();
                return;
            }
            recordComparison(cohereResult, shadowResult);
        } catch (Exception e) {
            log.warn("Chat shadow compare failed (provider=hyperclova): {}", e.getMessage());
            recordFailure();
        }
    }

    boolean shouldShadow(@Nullable String conversationId) {
        double safeRate = Math.max(0.0d, Math.min(1.0d, samplingRate));
        if (safeRate <= 0.0d) return false;
        if (conversationId == null || conversationId.isBlank()) return false;
        return ConversationDeterministicSampler.shouldApply(conversationId, safeRate);
    }

    void recordComparison(AiCallResult<ChatParsedResponse> cohere,
                          AiCallResult<ChatParsedResponse> shadow) {
        ComparisonSnapshot c = snapshotOf(cohere);
        ComparisonSnapshot s = snapshotOf(shadow);
        if (metrics == null) {
            return;
        }
        try {
            metrics.recordChatShadowCompare("cohere", "length", c.length);
            metrics.recordChatShadowCompare("hyperclova", "length", s.length);
            metrics.recordChatShadowCompare("cohere", "statute_refs", c.statuteRefs);
            metrics.recordChatShadowCompare("hyperclova", "statute_refs", s.statuteRefs);
            metrics.recordChatShadowCompare("cohere", "case_refs", c.caseRefs);
            metrics.recordChatShadowCompare("hyperclova", "case_refs", s.caseRefs);
            metrics.recordChatShadowCompare("cohere", "guardrail_violation", c.guardrailViolation ? 1 : 0);
            metrics.recordChatShadowCompare("hyperclova", "guardrail_violation", s.guardrailViolation ? 1 : 0);
            if (cohere.latencyMs() != null) {
                metrics.recordChatShadowCompare("cohere", "latency_ms", cohere.latencyMs());
            }
            if (shadow.latencyMs() != null) {
                metrics.recordChatShadowCompare("hyperclova", "latency_ms", shadow.latencyMs());
            }
        } catch (Exception ignored) {
            // best-effort metrics
        }
    }

    private void recordFailure() {
        if (metrics != null) {
            try {
                metrics.recordChatShadowCompareFailure("hyperclova");
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    ComparisonSnapshot snapshotOf(AiCallResult<ChatParsedResponse> r) {
        String text = r == null || r.data() == null || r.data().getNextQuestion() == null
                ? "" : r.data().getNextQuestion();
        int statute = countMatches(STATUTE_REF, text);
        int caseRefs = countMatches(CASE_REF, text);
        boolean violation = guardrailFilter != null && guardrailFilter.containsForbiddenText(text);
        return new ComparisonSnapshot(text.length(), statute, caseRefs, violation);
    }

    private static int countMatches(Pattern p, String s) {
        if (s == null || s.isBlank()) return 0;
        Matcher m = p.matcher(s);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    record ComparisonSnapshot(int length, int statuteRefs, int caseRefs, boolean guardrailViolation) { }
}
