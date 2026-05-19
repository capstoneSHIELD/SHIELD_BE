package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagOperationalMetricsTest {

    @Test
    @DisplayName("operational metrics records parse, guardrail, repeated question, and pollution signals")
    void recordsOperationalSignals() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordStructuredOutputParse("cohere", "ChatParsedResponse", "fallback");
        metrics.recordGuardrailBlock("chat_next_question");
        metrics.recordRepeatedSlotQuestionCandidate("deposit_amount");
        metrics.recordSlotLedgerPollutionCandidate("value_validation_ignored");

        assertThat(registry.counter(AiRagOperationalMetrics.STRUCTURED_OUTPUT_PARSE,
                "provider", "cohere", "schema", "ChatParsedResponse", "outcome", "fallback").count())
                .isEqualTo(1.0);
        assertThat(registry.counter(AiRagOperationalMetrics.GUARDRAIL_BLOCK,
                "surface", "chat_next_question").count()).isEqualTo(1.0);
        assertThat(registry.counter(AiRagOperationalMetrics.REPEATED_SLOT_QUESTION_CANDIDATE,
                "slot_id", "deposit_amount").count()).isEqualTo(1.0);
        assertThat(registry.counter(AiRagOperationalMetrics.SLOT_LEDGER_POLLUTION_CANDIDATE,
                "reason", "value_validation_ignored").count()).isEqualTo(1.0);
    }
}
