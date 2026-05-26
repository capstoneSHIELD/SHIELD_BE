package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class AiRagOperationalMetrics {

    public static final String STRUCTURED_OUTPUT_PARSE = "shield.ai.structured_output.parse";
    public static final String AI_API_ERROR = "shield.ai.api.error";
    public static final String GUARDRAIL_BLOCK = "shield.ai.guardrail.block";
    public static final String GUARDRAIL_FALSE_POSITIVE_CANDIDATE = "shield.ai.guardrail.false_positive_candidate";
    public static final String REPEATED_SLOT_QUESTION_CANDIDATE = "shield.ai.slot.repeated_question_candidate";
    public static final String SLOT_LEDGER_POLLUTION_CANDIDATE = "shield.ai.slot.pollution_candidate";
    public static final String CONSULTATION_TURN_LATENCY = "shield.ai.consultation.turn";

    // P5.1 Commit 4 — Cohere token/cost/latency 메트릭
    public static final String COHERE_TOKENS = "shield.ai.cohere.tokens";
    public static final String COHERE_COST_ESTIMATED_USD = "shield.ai.cohere.cost.estimated.usd";
    public static final String COHERE_LATENCY = "shield.ai.cohere.latency";

    private final MeterRegistry registry;

    public AiRagOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordStructuredOutputParse(String provider, String schema, String outcome) {
        counter(STRUCTURED_OUTPUT_PARSE,
                "provider", value(provider),
                "schema", value(schema),
                "outcome", value(outcome)).increment();
    }

    public void recordAiApiError(String provider, String operation, String status) {
        counter(AI_API_ERROR,
                "provider", value(provider),
                "operation", value(operation),
                "status", value(status)).increment();
    }

    public void recordGuardrailBlock(String surface) {
        counter(GUARDRAIL_BLOCK, "surface", value(surface)).increment();
    }

    public void recordGuardrailFalsePositiveCandidate(String surface) {
        counter(GUARDRAIL_FALSE_POSITIVE_CANDIDATE, "surface", value(surface)).increment();
    }

    public void recordRepeatedSlotQuestionCandidate(String slotId) {
        counter(REPEATED_SLOT_QUESTION_CANDIDATE, "slot_id", value(slotId)).increment();
    }

    public void recordSlotLedgerPollutionCandidate(String reason) {
        counter(SLOT_LEDGER_POLLUTION_CANDIDATE, "reason", value(reason)).increment();
    }

    public Timer.Sample startConsultationTurn() {
        return Timer.start(registry);
    }

    public void stopConsultationTurn(Timer.Sample sample, String outcome) {
        if (sample != null) {
            sample.stop(registry.timer(CONSULTATION_TURN_LATENCY, Tags.of("outcome", value(outcome))));
        }
    }

    public void recordConsultationTurn(long durationNanos, String outcome) {
        registry.timer(CONSULTATION_TURN_LATENCY, Tags.of("outcome", value(outcome)))
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    // === P5.1 Commit 4 — Cohere token/cost/latency metrics ===

    /**
     * Cohere 호출의 토큰 사용량 기록.
     *
     * @param model     Cohere 모델 ID (예: "command-a-03-2025")
     * @param operation chat/brief/classify/embed/rerank
     * @param direction "input" 또는 "output"
     * @param tokens    토큰 수 (null/음수면 무시)
     * @param estimated true면 추정값, false면 provider가 보고한 정확값
     */
    public void recordCohereTokens(String model, String operation, String direction,
                                   Integer tokens, boolean estimated) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        counter(COHERE_TOKENS,
                "model", value(model),
                "operation", value(operation),
                "direction", value(direction),
                "estimated", String.valueOf(estimated))
                .increment(tokens);
    }

    /**
     * Cohere 호출의 추정 비용(USD) 기록. 단가 테이블은 호출자가 계산해 전달.
     *
     * @param amountUsd 추정 비용 (음수/0이면 무시)
     */
    public void recordCohereEstimatedCost(String model, String operation, double amountUsd) {
        if (amountUsd <= 0) {
            return;
        }
        DistributionSummary.builder(COHERE_COST_ESTIMATED_USD)
                .tag("model", value(model))
                .tag("operation", value(operation))
                .register(registry)
                .record(amountUsd);
    }

    /**
     * Cohere 호출 지연 기록.
     *
     * @param duration null이거나 0 미만이면 무시
     * @param status   "success" / "failure" / "fallback"
     */
    public void recordCohereLatency(String model, String operation, Duration duration, String status) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        registry.timer(COHERE_LATENCY,
                "model", value(model),
                "operation", value(operation),
                "status", value(status))
                .record(duration);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
