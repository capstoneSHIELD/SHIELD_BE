package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.provider.EmbeddingResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Cohere 호출 결과의 token/cost/latency 메트릭을 일괄 emit하는 컴포넌트
 * (P5.1 Commit 4 refine).
 *
 * <p>중복 제거: {@code CohereService}와 {@code CohereEmbeddingClientAdapter}가
 * 각자 동일한 emit 로직(token + cost + latency)을 갖고 있던 것을 본 컴포넌트로 통합.
 *
 * <p>모든 emit은 <b>best-effort</b>: 메트릭 발행 중 예외가 발생해도 호출 결과에는
 * 영향을 주지 않는다.
 */
@Component
@Slf4j
public class CohereMetricEmitter {

    private final AiRagOperationalMetrics metrics;
    private final CohereCostCalculator costCalculator;

    public CohereMetricEmitter(AiRagOperationalMetrics metrics, CohereCostCalculator costCalculator) {
        this.metrics = metrics;
        this.costCalculator = costCalculator;
    }

    /**
     * chat / brief / classify 등 {@link AiCallResult} 기반 호출 결과 emit.
     */
    public void emit(String model, String operation, AiCallResult<?> result) {
        if (metrics == null || result == null) {
            return;
        }
        emitInternal(model, operation, result.tokensInput(), result.tokensOutput(), result.latencyMs());
    }

    /**
     * embed 호출 결과 emit. embed는 output token이 없으므로 input만 기록.
     */
    public void emitEmbed(String model, EmbeddingResult result) {
        if (metrics == null || result == null) {
            return;
        }
        emitInternal(model, "embed", result.inputTokens(), null, (int) Math.min(result.latencyMs(), Integer.MAX_VALUE));
    }

    private void emitInternal(String model, String operation,
                              Integer tokensInput, Integer tokensOutput, Integer latencyMs) {
        try {
            metrics.recordCohereTokens(model, operation, "input", tokensInput, /*estimated=*/false);
            metrics.recordCohereTokens(model, operation, "output", tokensOutput, /*estimated=*/false);
            if (costCalculator != null) {
                double cost = costCalculator.estimate(model, tokensInput, tokensOutput);
                metrics.recordCohereEstimatedCost(model, operation, cost);
            }
            if (latencyMs != null && latencyMs >= 0) {
                metrics.recordCohereLatency(model, operation,
                        Duration.ofMillis(latencyMs), "success");
            }
        } catch (Exception e) {
            log.warn("Cohere metric emit 실패 (operation={}, model={}): {}",
                    operation, model, Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }
}
