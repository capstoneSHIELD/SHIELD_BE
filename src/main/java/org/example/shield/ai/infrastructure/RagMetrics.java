package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * RAG 파이프라인 전용 Micrometer 메트릭 센서 (Phase B-8b).
 *
 * <p>계측 대상:</p>
 * <ul>
 *   <li>Cohere 쿼리 임베딩 호출 — {@code shield.rag.cohere.embed} (timer, tag={@code outcome=success|failure})</li>
 *   <li>3-way retrieve 지연 — {@code shield.rag.retrieve} (timer, tag={@code outcome=success|failure|empty})</li>
 *   <li>벡터 경로 degrade(영벡터 fallback) — {@code shield.rag.vector.degrade} (counter, tag={@code reason=...})</li>
 *   <li>RAG-less fallback — {@code shield.rag.pipeline.fallback} (counter)</li>
 *   <li>의도 분류 LLM(#1) 호출 — {@code shield.rag.classify} (timer, tag={@code outcome=success|failure})</li>
 *   <li>RAG 파이프라인 전체(classify + retrieve + build) — {@code shield.rag.pipeline.total} (timer, tag={@code outcome=success|empty|failure})</li>
 * </ul>
 *
 * <p>모든 메트릭은 {@code /actuator/prometheus}에 자동 노출되며
 * {@code application} 태그가 {@code spring.application.name}으로 공통 부여된다.</p>
 *
 * <p>중앙화 이유:</p>
 * <ul>
 *   <li>호출 지점마다 Counter를 찾지 않도록 API를 단순화</li>
 *   <li>메트릭 이름/태그 컨벤션을 한 곳에서 통제 (renaming 리스크 최소화)</li>
 *   <li>테스트에서 {@code SimpleMeterRegistry}로 대체 용이</li>
 * </ul>
 */
@Component
public class RagMetrics {

    public static final String METRIC_COHERE_EMBED = "shield.rag.cohere.embed";
    public static final String METRIC_RETRIEVE = "shield.rag.retrieve";
    public static final String METRIC_VECTOR_DEGRADE = "shield.rag.vector.degrade";
    public static final String METRIC_PIPELINE_FALLBACK = "shield.rag.pipeline.fallback";
    public static final String METRIC_CLASSIFY = "shield.rag.classify";
    public static final String METRIC_PIPELINE_TOTAL = "shield.rag.pipeline.total";

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // === Cohere 쿼리 임베딩 ===

    /**
     * Cohere {@code /v2/embed} 호출을 계측한다. 예외 발생 시 {@code outcome=failure}로
     * 분류하고 예외를 상위로 re-throw 한다.
     */
    public <T> T timeCohereEmbed(Supplier<T> call) {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "failure";
            throw e;
        } finally {
            registry.timer(METRIC_COHERE_EMBED, Tags.of("outcome", outcome))
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    // === Retrieve ===

    /**
     * 3-way retrieve 수행 시간을 기록한다. 결과 크기에 따라 {@code empty} 라벨을 분리하여
     * "호출은 성공했지만 0건" 상황을 별도로 관측한다.
     */
    public Timer.Sample startRetrieve() {
        return Timer.start(registry);
    }

    public void stopRetrieveSuccess(Timer.Sample sample, int hits) {
        String outcome = hits == 0 ? "empty" : "success";
        sample.stop(registry.timer(METRIC_RETRIEVE, Tags.of("outcome", outcome)));
    }

    public void stopRetrieveFailure(Timer.Sample sample) {
        sample.stop(registry.timer(METRIC_RETRIEVE, Tags.of("outcome", "failure")));
    }

    // === Degrade ===

    /**
     * 벡터 경로가 영벡터로 degrade된 경우 호출. {@code reason} 라벨로 원인을 분리한다.
     *
     * @param reason {@code empty_query} | {@code cohere_error} | {@code empty_response}
     */
    public void recordVectorDegrade(String reason) {
        counter(METRIC_VECTOR_DEGRADE, Tags.of("reason", reason)).increment();
    }

    /**
     * MessageService.RAG 파이프라인 전체가 예외로 중단되고 RAG-less 응답으로
     * fallback한 경우 호출.
     */
    public void recordPipelineFallback() {
        counter(METRIC_PIPELINE_FALLBACK, Tags.empty()).increment();
    }

    // === Classify (Layer 1 LLM) ===

    /**
     * Cohere {@code /v2/chat} (의도 분류) 호출을 계측한다.
     * RAG 파이프라인의 LLM 호출 #1 단계 — 본 응답 생성(#2)과 분리하여 단계별 분해 가능.
     */
    public <T> T timeClassify(Supplier<T> call) {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "failure";
            throw e;
        } finally {
            registry.timer(METRIC_CLASSIFY, Tags.of("outcome", outcome))
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    // === Pipeline total (classify + retrieve + context build) ===

    /**
     * RagPipelineService.execute() 의 전체 지연을 기록한다.
     * outcome 분기:
     * <ul>
     *   <li>{@code success} — ragContext 비-empty 반환</li>
     *   <li>{@code empty}   — 정상 종료지만 ragContext 가 빈 문자열 (히트 0 또는 미수행)</li>
     *   <li>{@code failure} — 예외로 중단 (외부에서 fallback 처리)</li>
     * </ul>
     */
    public Timer.Sample startPipeline() {
        return Timer.start(registry);
    }

    public void stopPipelineSuccess(Timer.Sample sample) {
        sample.stop(registry.timer(METRIC_PIPELINE_TOTAL, Tags.of("outcome", "success")));
    }

    public void stopPipelineEmpty(Timer.Sample sample) {
        sample.stop(registry.timer(METRIC_PIPELINE_TOTAL, Tags.of("outcome", "empty")));
    }

    public void stopPipelineFailure(Timer.Sample sample) {
        sample.stop(registry.timer(METRIC_PIPELINE_TOTAL, Tags.of("outcome", "failure")));
    }

    private Counter counter(String name, Iterable<Tag> tags) {
        return registry.counter(name, tags);
    }
}
