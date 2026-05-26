package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.config.CoherePricingProperties;
import org.example.shield.ai.config.CoherePricingProperties.ModelPricing;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.provider.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CohereMetricEmitter} 검증 (P5.1 Commit 4 refine).
 *
 * <p>chat/brief/classify ({@link AiCallResult}) 및 embed ({@link EmbeddingResult}) 호출
 * 후 token/cost/latency 메트릭이 정확히 emit되는지 검증.
 */
class CohereMetricEmitterTest {

    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private CohereCostCalculator costCalc;
    private CohereMetricEmitter emitter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);

        CoherePricingProperties props = new CoherePricingProperties();
        Map<String, ModelPricing> pricing = new HashMap<>();
        ModelPricing chat = new ModelPricing();
        chat.setInputPerMillion(2.50);
        chat.setOutputPerMillion(10.00);
        pricing.put("command-a-03-2025", chat);
        ModelPricing embed = new ModelPricing();
        embed.setInputPerMillion(0.10);
        embed.setOutputPerMillion(0.0);
        pricing.put("embed-v4.0", embed);
        props.setPricing(pricing);

        costCalc = new CohereCostCalculator(props);
        emitter = new CohereMetricEmitter(metrics, costCalc);
    }

    @Test
    @DisplayName("emit(chat) — token / cost / latency 모두 기록")
    void emitChatRecordsAll() {
        AiCallResult<String> result = new AiCallResult<>("id-1", "{}", 1000, 500, 200);

        emitter.emit("command-a-03-2025", "chat", result);

        // tokens
        assertThat(registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "command-a-03-2025", "operation", "chat",
                "direction", "input", "estimated", "false").count()).isEqualTo(1000.0);
        assertThat(registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "command-a-03-2025", "operation", "chat",
                "direction", "output", "estimated", "false").count()).isEqualTo(500.0);
        // cost: 1000*$2.50/1M + 500*$10/1M = 0.0025 + 0.005 = 0.0075
        var costSummary = registry.summary(AiRagOperationalMetrics.COHERE_COST_ESTIMATED_USD,
                "model", "command-a-03-2025", "operation", "chat");
        assertThat(costSummary.count()).isEqualTo(1);
        assertThat(costSummary.totalAmount()).isCloseTo(0.0075, org.assertj.core.data.Offset.offset(1e-6));
        // latency timer
        assertThat(registry.timer(AiRagOperationalMetrics.COHERE_LATENCY,
                "model", "command-a-03-2025", "operation", "chat", "status", "success").count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("emit(null result) — 안전하게 무시")
    void emitNullResultNoop() {
        emitter.emit("model", "chat", null);
        assertThat(registry.find(AiRagOperationalMetrics.COHERE_TOKENS).counters()).isEmpty();
    }

    @Test
    @DisplayName("emitEmbed — input token + cost만 기록 (output 없음)")
    void emitEmbedInputOnly() {
        EmbeddingResult result = new EmbeddingResult("emb-1",
                List.of(new float[]{0.1f}), 1_000_000, 80L);

        emitter.emitEmbed("embed-v4.0", result);

        // input only
        assertThat(registry.counter(AiRagOperationalMetrics.COHERE_TOKENS,
                "model", "embed-v4.0", "operation", "embed",
                "direction", "input", "estimated", "false").count()).isEqualTo(1_000_000.0);
        // output direction에는 emit 안 됨
        assertThat(registry.find(AiRagOperationalMetrics.COHERE_TOKENS)
                .tag("direction", "output").counters()).isEmpty();
        // cost: 1M * $0.10/1M = $0.10
        var costSummary = registry.summary(AiRagOperationalMetrics.COHERE_COST_ESTIMATED_USD,
                "model", "embed-v4.0", "operation", "embed");
        assertThat(costSummary.totalAmount()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("emit — metric 예외는 swallow되고 호출 결과에 영향 없음")
    void emitMetricExceptionIsSwallowed() {
        // metric registry를 broken state로 만들기 어렵지만, null result처럼 안전 처리 검증.
        // 본 테스트는 동작 자체가 throw하지 않음을 검증.
        AiCallResult<String> result = new AiCallResult<>("id", "{}", -1, -1, -1);
        // null tokens, negative latency → 모든 record는 ignore 처리되어야 함, 예외 없음
        emitter.emit("unknown-model", "chat", result);
        // 통과만 되면 OK
    }
}
