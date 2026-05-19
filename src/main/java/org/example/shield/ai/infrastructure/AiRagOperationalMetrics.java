package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

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

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
