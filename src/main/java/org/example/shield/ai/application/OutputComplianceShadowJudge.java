package org.example.shield.ai.application;

import org.example.shield.ai.dto.OutputComplianceResult;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.util.ConversationDeterministicSampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Component
public class OutputComplianceShadowJudge {

    private static final Pattern RRN = Pattern.compile("\\b\\d{6}[- ]?[1-4]\\d{6}\\b");
    private static final Pattern CARD = Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    private static final Pattern ACCOUNT = Pattern.compile("\\b\\d{3,4}-\\d{2,6}-\\d{2,6}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private final GuardrailFilter guardrailFilter;
    private final RagMetrics ragMetrics;

    @Value("${app.ai.output-judge.shadow-enabled:false}")
    private boolean shadowEnabled;

    @Value("${app.ai.output-judge.sampling-rate:0.0}")
    private double samplingRate;

    @Value("${app.ai.output-judge.max-p95-latency-increase-ms:200}")
    private int maxP95LatencyIncreaseMs;

    @Value("${app.ai.output-judge.max-cost-ratio:0.10}")
    private double maxCostRatio;

    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter, RagMetrics ragMetrics) {
        this.guardrailFilter = guardrailFilter;
        this.ragMetrics = ragMetrics;
    }

    /**
     * Legacy 1-arg overload — conversationId 없이 호출 (BC).
     * sampling은 {@link ThreadLocalRandom} 기반이라 같은 상담 내 일관성이 없음.
     * 신규 호출자는 {@link #evaluate(String, String)}을 사용할 것.
     */
    public OutputComplianceResult evaluate(String response) {
        boolean deterministicViolation = guardrailFilter.containsForbiddenText(response);
        boolean shadowScheduled = shouldSample(ThreadLocalRandom.current().nextDouble());
        String outcome = deterministicViolation ? "regex_violation" : shadowScheduled ? "sampled" : "skipped";
        ragMetrics.recordOutputJudgeShadow(outcome);
        return new OutputComplianceResult(
                deterministicViolation,
                shadowScheduled,
                false,
                shadowScheduled ? maskForJudge(response) : null,
                null,  // hashedConversationId 없음
                outcome
        );
    }

    /**
     * P5.2 Commit 4 — conversationId 기반 deterministic sampling 사용.
     *
     * <p>같은 상담 내에서 sampling 결정이 일관됨 (요청마다 바뀌지 않음).
     * 또한 결과에 {@code hashedConversationId}가 포함되어 후속 분석에서 같은
     * 상담의 sample들을 그룹화할 수 있음 (원본 conversationId 미저장).
     *
     * @param response       LLM 답변 (PII 가능성 있음 — 마스킹 처리됨)
     * @param conversationId 상담 ID (null 가능, null이면 sampling false)
     */
    public OutputComplianceResult evaluate(String response, String conversationId) {
        boolean deterministicViolation = guardrailFilter.containsForbiddenText(response);
        boolean shadowScheduled = shouldSampleByConversation(conversationId);
        String outcome = deterministicViolation ? "regex_violation"
                : shadowScheduled ? "sampled" : "skipped";
        ragMetrics.recordOutputJudgeShadow(outcome);
        String hashedConvId = conversationId == null ? null
                : ConversationDeterministicSampler.sha256Short(conversationId);
        return new OutputComplianceResult(
                deterministicViolation,
                shadowScheduled,
                false,
                shadowScheduled ? maskForJudge(response) : null,
                hashedConvId,
                outcome
        );
    }

    boolean shouldSampleByConversation(String conversationId) {
        // Judge는 hashedConversationId로 sample을 그룹화하므로 conversationId 없으면 의미 없음.
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        double safeRate = Math.max(0.0d, Math.min(1.0d, samplingRate));
        if (!shadowEnabled
                || safeRate <= 0.0d
                || maxP95LatencyIncreaseMs > 200
                || maxCostRatio > 0.10d) {
            return false;
        }
        return ConversationDeterministicSampler.shouldApply(conversationId, safeRate);
    }

    boolean shouldSample(double draw) {
        double safeRate = Math.max(0.0d, Math.min(1.0d, samplingRate));
        return shadowEnabled
                && safeRate > 0.0d
                && draw < safeRate
                && maxP95LatencyIncreaseMs <= 200
                && maxCostRatio <= 0.10d;
    }

    public String maskForJudge(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = RRN.matcher(text).replaceAll("[RRN]");
        masked = CARD.matcher(masked).replaceAll("[CARD]");
        masked = PHONE.matcher(masked).replaceAll("[PHONE]");
        masked = ACCOUNT.matcher(masked).replaceAll("[ACCOUNT]");
        masked = EMAIL.matcher(masked).replaceAll("[EMAIL]");
        return masked;
    }
}
