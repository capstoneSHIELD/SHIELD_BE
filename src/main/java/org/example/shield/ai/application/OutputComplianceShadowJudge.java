package org.example.shield.ai.application;

import org.example.shield.ai.dto.OutputComplianceResult;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.PiiMasker;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.util.ConversationDeterministicSampler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Output compliance shadow judge.
 *
 * <p>P5.2 Commit 4 refine: PII masking 로직을 {@link PiiMasker}로 추출, 이름·주소 패턴 추가.
 * sampling은 conversationId 기반 deterministic.
 */
@Component
public class OutputComplianceShadowJudge {

    private final GuardrailFilter guardrailFilter;
    private final RagMetrics ragMetrics;
    private final PiiMasker piiMasker;

    @Value("${app.ai.output-judge.shadow-enabled:false}")
    private boolean shadowEnabled;

    @Value("${app.ai.output-judge.sampling-rate:0.0}")
    private double samplingRate;

    @Value("${app.ai.output-judge.max-p95-latency-increase-ms:200}")
    private int maxP95LatencyIncreaseMs;

    @Value("${app.ai.output-judge.max-cost-ratio:0.10}")
    private double maxCostRatio;

    /** Legacy 2-arg 생성자 — PiiMasker 없이 fallback (테스트 호환). */
    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter, RagMetrics ragMetrics) {
        this(guardrailFilter, ragMetrics, new PiiMasker());
    }

    @Autowired
    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter, RagMetrics ragMetrics, PiiMasker piiMasker) {
        this.guardrailFilter = guardrailFilter;
        this.ragMetrics = ragMetrics;
        this.piiMasker = piiMasker;
    }

    /**
     * Legacy 1-arg overload — conversationId 없이 호출 (BC).
     *
     * @deprecated P5.2 Commit 4 이후 {@link #evaluate(String, String)}을 사용할 것.
     *             sampling이 {@link ThreadLocalRandom} 기반이라 같은 상담 내 일관성이 없음.
     */
    @Deprecated(since = "P5.2 Commit 4", forRemoval = false)
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
                null,
                outcome
        );
    }

    /**
     * P5.2 Commit 4 — conversationId 기반 deterministic sampling 사용.
     *
     * <p>같은 상담 내에서 sampling 결정이 일관됨. 결과의 {@code hashedConversationId}로
     * 같은 상담 sample들을 그룹화 가능 (원본 conversationId 미저장).
     *
     * @param response       LLM 답변 (PII 가능성 있음 — 마스킹 처리됨)
     * @param conversationId 상담 ID (null이면 sampling false)
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

    /**
     * PII 마스킹 — {@link PiiMasker}에 위임.
     * <p>본 메서드는 BC를 위해 유지된다. 신규 코드는 {@link PiiMasker}를 직접 주입받을 것.
     */
    public String maskForJudge(String text) {
        return piiMasker.mask(text);
    }
}
