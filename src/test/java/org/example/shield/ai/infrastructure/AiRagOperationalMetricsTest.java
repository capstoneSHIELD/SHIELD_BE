package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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

    // === P5.1 Commit 4 — Cohere token/cost/latency metrics ===

    @Test
    @DisplayName("recordCohereTokens — counter 누적 + 태그 정확")
    void cohereTokensCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereTokens("command-a-03-2025", "chat", "input", 1234, false);
        metrics.recordCohereTokens("command-a-03-2025", "chat", "input", 500, false);

        double count = registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "command-a-03-2025",
                "operation", "chat",
                "direction", "input",
                "estimated", "false").count();
        assertThat(count).isEqualTo(1234.0 + 500.0);
    }

    @Test
    @DisplayName("recordCohereTokens — null/0/음수 토큰은 무시")
    void cohereTokensIgnoresInvalid() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereTokens("m", "chat", "input", null, false);
        metrics.recordCohereTokens("m", "chat", "input", 0, false);
        metrics.recordCohereTokens("m", "chat", "input", -5, false);

        // Counter는 한 번도 increment 안 됐으므로 등록조차 안 됨 → find로 검색
        assertThat(registry.find(AiRagOperationalMetrics.COHERE_TOKENS).counters())
                .isEmpty();
    }

    @Test
    @DisplayName("recordCohereTokens — estimated=true 태그 분리")
    void cohereTokensEstimatedTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereTokens("m", "embed", "input", 100, true);
        metrics.recordCohereTokens("m", "embed", "input", 200, false);

        assertThat(registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "m", "operation", "embed", "direction", "input", "estimated", "true").count())
                .isEqualTo(100.0);
        assertThat(registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "m", "operation", "embed", "direction", "input", "estimated", "false").count())
                .isEqualTo(200.0);
    }

    @Test
    @DisplayName("recordCohereEstimatedCost — DistributionSummary로 기록")
    void cohereCostDistribution() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereEstimatedCost("command-a-03-2025", "chat", 0.025);
        metrics.recordCohereEstimatedCost("command-a-03-2025", "chat", 0.030);

        var summary = registry.summary(AiRagOperationalMetrics.COHERE_COST_ESTIMATED_USD,
                "model", "command-a-03-2025", "operation", "chat");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isCloseTo(0.055, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("recordCohereEstimatedCost — 0/음수는 무시")
    void cohereCostIgnoresZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereEstimatedCost("m", "chat", 0.0);
        metrics.recordCohereEstimatedCost("m", "chat", -1.0);

        assertThat(registry.find(AiRagOperationalMetrics.COHERE_COST_ESTIMATED_USD).summaries())
                .isEmpty();
    }

    @Test
    @DisplayName("recordCohereLatency — Timer로 기록 + status 태그")
    void cohereLatencyTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereLatency("m", "chat", Duration.ofMillis(150), "success");
        metrics.recordCohereLatency("m", "chat", Duration.ofMillis(200), "success");

        var timer = registry.timer(AiRagOperationalMetrics.COHERE_LATENCY,
                "model", "m", "operation", "chat", "status", "success");
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("recordCohereLatency — null/음수 duration 무시")
    void cohereLatencyIgnoresInvalid() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordCohereLatency("m", "chat", null, "success");
        metrics.recordCohereLatency("m", "chat", Duration.ofMillis(-1), "success");

        assertThat(registry.find(AiRagOperationalMetrics.COHERE_LATENCY).timers()).isEmpty();
    }

    @Test
    @DisplayName("recordReferenceMention records hit/miss counters")
    void referenceMentionCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRagOperationalMetrics metrics = new AiRagOperationalMetrics(registry);

        metrics.recordReferenceMention("expected", "hit", 2);
        metrics.recordReferenceMention("expected", "miss");
        metrics.recordReferenceMention("expected", "hit", 0);

        assertThat(registry.counter(AiRagOperationalMetrics.REFERENCE_MENTION,
                "kind", "expected", "outcome", "hit").count()).isEqualTo(2.0);
        assertThat(registry.counter(AiRagOperationalMetrics.REFERENCE_MENTION,
                "kind", "expected", "outcome", "miss").count()).isEqualTo(1.0);
    }
}
