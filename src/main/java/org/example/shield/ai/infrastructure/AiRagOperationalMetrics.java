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

    // P5.3 Commit 2 — 임베딩 캐시 hit/miss
    public static final String EMBEDDING_CACHE = "shield.ai.embedding.cache";

    // P5.3 Commit 3 — Intent-aware routing 결정
    public static final String INTENT_ROUTING = "shield.ai.intent.routing";

    // P5.3 Commit 5 — RAG context budget shadow 측정
    public static final String CONTEXT_BUDGET = "shield.ai.rag.context.budget";

    // P5.4 Commit 2 — Rerank 결과 (latency / fallback / outcome)
    public static final String RERANK_LATENCY = "shield.ai.rerank.latency";
    public static final String RERANK_FALLBACK = "shield.ai.rerank.fallback";
    public static final String RERANK_OUTCOME = "shield.ai.rerank.outcome";

    // P5.5 Commit 2 — LLM Judge outcome
    public static final String JUDGE_OUTCOME = "shield.ai.judge.outcome";
    public static final String JUDGE_LATENCY = "shield.ai.judge.latency";

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

    /**
     * P5.3 Commit 2 — 임베딩 캐시 hit/miss 기록.
     *
     * @param model   provider 모델 ID
     * @param outcome {@code "hit"} / {@code "miss"} / {@code "store"} / {@code "error"}
     */
    public void recordEmbeddingCache(String model, String outcome) {
        counter(EMBEDDING_CACHE,
                "model", value(model),
                "outcome", value(outcome))
                .increment();
    }

    /**
     * P5.4 Commit 2 — Rerank 호출 outcome 기록.
     *
     * @param mode    {@code "off" | "shadow" | "sampled" | "enforce"}
     * @param outcome {@code "skipped" | "shadow_executed" | "applied" | "fallback"}
     */
    public void recordRerankOutcome(String mode, String outcome) {
        counter(RERANK_OUTCOME,
                "mode", value(mode),
                "outcome", value(outcome))
                .increment();
    }

    /**
     * P5.4 Commit 2 — Rerank 호출 latency 기록.
     *
     * @param model    rerank 모델 ID
     * @param duration 호출 지연
     * @param status   {@code "success" | "failure" | "timeout"}
     */
    public void recordRerankLatency(String model, Duration duration, String status) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        registry.timer(RERANK_LATENCY,
                "model", value(model),
                "status", value(status))
                .record(duration);
    }

    /**
     * P5.5 Commit 2 — LLM judge outcome 기록.
     *
     * @param provider          {@code "hyperclova"} 등
     * @param verdict           {@code "PASS" | "SOFT_VIOLATION" | "HARD_VIOLATION" | "fallback" | "skipped"}
     * @param confidenceBucket  {@code "low" (<0.5) | "medium" (<0.85) | "high"}
     */
    public void recordJudgeOutcome(String provider, String verdict, String confidenceBucket) {
        counter(JUDGE_OUTCOME,
                "provider", value(provider),
                "verdict", value(verdict),
                "confidence", value(confidenceBucket))
                .increment();
    }

    /**
     * P5.5 Commit 2 — LLM judge 호출 latency 기록.
     */
    public void recordJudgeLatency(String provider, Duration duration, String status) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        registry.timer(JUDGE_LATENCY,
                "provider", value(provider),
                "status", value(status))
                .record(duration);
    }

    /**
     * P5.4 Commit 2 — Rerank fallback 발생 카운트.
     *
     * @param reason {@code "timeout" | "api_error" | "invalid_response" | "circuit_breaker"}
     */
    public void recordRerankFallback(String reason) {
        counter(RERANK_FALLBACK, "reason", value(reason)).increment();
    }

    /**
     * P5.3 Commit 5 — RAG context budget shadow 측정값 기록.
     *
     * <p>shadow mode에서 token 예산을 초과한 chunk가 얼마나 trim/drop될 수 있었는지 추정값을
     * 메트릭으로 기록한다. 실제 prompt context는 변경되지 않음 (enforce는 본 plan 범위 밖).
     *
     * @param kind   {@code "statute"} / {@code "case"} / {@code "total"}
     * @param action {@code "estimated"} / {@code "trimmed"} / {@code "dropped"} / {@code "kept"}
     * @param value  토큰 수 / 청크 수 등 (음수는 무시)
     */
    public void recordContextBudget(String kind, String action, long value) {
        if (value <= 0) {
            return;
        }
        counter(CONTEXT_BUDGET,
                "kind", value(kind),
                "action", value(action))
                .increment(value);
    }

    /**
     * P5.3 Commit 3 — Intent-aware retrieval 라우팅 결정 기록.
     *
     * <p>shadow mode에서는 결정만 기록하고 실제 라우팅은 baseline 유지.
     * enforce mode에서는 결정이 라우팅에 반영됨.
     *
     * @param mode             {@code "off" | "shadow" | "enforce"}
     * @param intent           {@code DialogueIntent.name()} 또는 {@code "UNKNOWN"}
     * @param decision         라우팅 결정 (예: {@code "high_confidence_rag_skip"},
     *                         {@code "baseline"}, {@code "broad_search"})
     * @param confidenceBucket 신뢰도 bucket (예: {@code "low"}, {@code "medium"}, {@code "high"})
     */
    public void recordIntentRouting(String mode, String intent, String decision, String confidenceBucket) {
        counter(INTENT_ROUTING,
                "mode", value(mode),
                "intent", value(intent),
                "decision", value(decision),
                "confidence", value(confidenceBucket))
                .increment();
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
