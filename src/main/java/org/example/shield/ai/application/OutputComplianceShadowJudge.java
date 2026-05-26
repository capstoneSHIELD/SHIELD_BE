package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.OutputComplianceResult;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.PiiMasker;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.provider.AiJudgeClient;
import org.example.shield.ai.provider.JudgeRequest;
import org.example.shield.ai.provider.JudgeResult;
import org.example.shield.ai.util.ConversationDeterministicSampler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Output compliance shadow judge.
 *
 * <p>P5.2 Commit 4 refine: PII masking 로직을 {@link PiiMasker}로 추출, 이름·주소 패턴 추가.
 * sampling은 conversationId 기반 deterministic.
 *
 * <p>P5.5 Commit 2: sampled 시 외부 LLM judge ({@link AiJudgeClient}, 기본 HyperCLOVA X) 호출.
 * <b>judge 결과는 운영 차단에 절대 사용 안 함</b> — shadow only. fail-open.
 */
@Component
@Slf4j
public class OutputComplianceShadowJudge {

    private final GuardrailFilter guardrailFilter;
    private final RagMetrics ragMetrics;
    private final PiiMasker piiMasker;
    private final AiJudgeClient judgeClient;
    private final AiRagOperationalMetrics operationalMetrics;

    @Value("${app.ai.output-judge.shadow-enabled:false}")
    private boolean shadowEnabled;

    @Value("${app.ai.output-judge.sampling-rate:0.0}")
    private double samplingRate;

    @Value("${app.ai.output-judge.max-p95-latency-increase-ms:200}")
    private int maxP95LatencyIncreaseMs;

    @Value("${app.ai.output-judge.max-cost-ratio:0.10}")
    private double maxCostRatio;

    /** Legacy 2-arg 생성자. */
    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter, RagMetrics ragMetrics) {
        this(guardrailFilter, ragMetrics, new PiiMasker(), null, null);
    }

    /** Legacy 3-arg 생성자 (P5.2 Commit 4). */
    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter, RagMetrics ragMetrics, PiiMasker piiMasker) {
        this(guardrailFilter, ragMetrics, piiMasker, null, null);
    }

    @Autowired
    public OutputComplianceShadowJudge(GuardrailFilter guardrailFilter,
                                       RagMetrics ragMetrics,
                                       PiiMasker piiMasker,
                                       @Nullable AiJudgeClient judgeClient,
                                       @Nullable AiRagOperationalMetrics operationalMetrics) {
        this.guardrailFilter = guardrailFilter;
        this.ragMetrics = ragMetrics;
        this.piiMasker = piiMasker;
        this.judgeClient = judgeClient;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * Legacy 1-arg overload (BC) — {@link ThreadLocalRandom} 기반 sampling.
     *
     * @deprecated P5.2 Commit 4부터 {@link #evaluate(String, String)} 사용.
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
     * P5.2 Commit 4 — conversationId 기반 deterministic sampling.
     * P5.5 Commit 2 — sampled 시 외부 LLM judge 호출 (fail-open).
     */
    public OutputComplianceResult evaluate(String response, String conversationId) {
        boolean deterministicViolation = guardrailFilter.containsForbiddenText(response);
        boolean shadowScheduled = shouldSampleByConversation(conversationId);
        String outcome = deterministicViolation ? "regex_violation"
                : shadowScheduled ? "sampled" : "skipped";
        ragMetrics.recordOutputJudgeShadow(outcome);
        String hashedConvId = conversationId == null ? null
                : ConversationDeterministicSampler.sha256Short(conversationId);
        String masked = shadowScheduled ? maskForJudge(response) : null;

        JudgeResult judgeResult = null;
        if (shadowScheduled && judgeClient != null && masked != null) {
            judgeResult = invokeJudgeSafely(masked);
        }

        return new OutputComplianceResult(
                deterministicViolation,
                shadowScheduled,
                false,
                masked,
                hashedConvId,
                outcome,
                judgeResult
        );
    }

    /**
     * P5.5 Commit 2 — judge 호출은 best-effort. 실패해도 user-facing request 정상 진행.
     */
    JudgeResult invokeJudgeSafely(String maskedResponse) {
        String provider = judgeClient.providerKey();
        long startNanos = System.nanoTime();
        try {
            JudgeResult result = judgeClient.judge(maskedResponse, JudgeRequest.legalCompliance());
            recordJudgeMetrics(provider, result, "success", startNanos);
            return result;
        } catch (Exception e) {
            log.warn("LLM judge call failed (provider={}): {}. Continuing without judge.",
                    provider, e.getMessage());
            recordJudgeMetrics(provider, null, "failure", startNanos);
            return null;
        }
    }

    private void recordJudgeMetrics(String provider, JudgeResult result, String status, long startNanos) {
        if (operationalMetrics == null) {
            return;
        }
        try {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            operationalMetrics.recordJudgeLatency(provider, Duration.ofMillis(latencyMs), status);
            String verdict = result == null ? "fallback" : result.verdict().name();
            String confBucket = result == null ? "unknown" : bucketize(result.confidence());
            operationalMetrics.recordJudgeOutcome(provider, verdict, confBucket);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String bucketize(double confidence) {
        if (confidence < 0.5) return "low";
        if (confidence < 0.85) return "medium";
        return "high";
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
     */
    public String maskForJudge(String text) {
        return piiMasker.mask(text);
    }
}
