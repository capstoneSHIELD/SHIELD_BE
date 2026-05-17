package org.example.shield.ai.application;

import org.example.shield.ai.dto.OutputComplianceResult;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.RagMetrics;
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
                outcome
        );
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
