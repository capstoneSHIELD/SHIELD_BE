package org.example.shield.ai.safety;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RerankCircuitBreaker} 검증 (P5.4 Commit 3).
 *
 * <p>fallback rate 임계값 초과 시 logical OFF + 수동 reset 검증.
 */
class RerankCircuitBreakerTest {

    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private RerankCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);
        breaker = new RerankCircuitBreaker(metrics);
        ReflectionTestUtils.setField(breaker, "fallbackRateThreshold", 0.05);
        ReflectionTestUtils.setField(breaker, "minSamples", 20);
        ReflectionTestUtils.setField(breaker, "windowMinutes", 5);
    }

    @Test
    @DisplayName("초기 상태: logical OFF 아님")
    void initialNotTripped() {
        assertThat(breaker.isLogicalOff()).isFalse();
    }

    @Test
    @DisplayName("min-samples 미달 — fallback 많아도 trip 안 함")
    void belowMinSamplesNoTrip() {
        for (int i = 0; i < 19; i++) {
            breaker.recordResult(true);  // 19건 모두 fallback
        }
        assertThat(breaker.isLogicalOff()).isFalse();
    }

    @Test
    @DisplayName("min-samples 충족 + fallback rate > threshold → trip")
    void aboveThresholdTrips() {
        // 30 samples, fallback rate 0.10 (3/30) > 0.05
        for (int i = 0; i < 27; i++) breaker.recordResult(false);
        for (int i = 0; i < 3; i++) breaker.recordResult(true);

        assertThat(breaker.isLogicalOff()).isTrue();
        // 메트릭에 circuit_breaker 카운트
        assertThat(registry.counter(AiRagOperationalMetrics.RERANK_FALLBACK,
                "reason", "circuit_breaker").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("fallback rate < threshold → trip 안 함")
    void belowThresholdNoTrip() {
        // 30 samples, fallback rate 0.033 (1/30) < 0.05
        for (int i = 0; i < 29; i++) breaker.recordResult(false);
        breaker.recordResult(true);

        assertThat(breaker.isLogicalOff()).isFalse();
    }

    @Test
    @DisplayName("이미 tripped — 추가 recordResult는 중복 trip 메트릭 발행 안 함 (idempotent)")
    void tripIsIdempotent() {
        for (int i = 0; i < 25; i++) breaker.recordResult(true);
        assertThat(breaker.isLogicalOff()).isTrue();
        double count1 = registry.counter(AiRagOperationalMetrics.RERANK_FALLBACK,
                "reason", "circuit_breaker").count();

        // 추가 결과 누적
        for (int i = 0; i < 10; i++) breaker.recordResult(true);
        double count2 = registry.counter(AiRagOperationalMetrics.RERANK_FALLBACK,
                "reason", "circuit_breaker").count();

        assertThat(count2).isEqualTo(count1);   // 더 이상 증가하지 않음
    }

    @Test
    @DisplayName("reset() — logical OFF 해제 + sample 초기화")
    void resetReleases() {
        for (int i = 0; i < 25; i++) breaker.recordResult(true);
        assertThat(breaker.isLogicalOff()).isTrue();

        breaker.reset();

        assertThat(breaker.isLogicalOff()).isFalse();
        assertThat(breaker.sampleCount()).isZero();
    }

    @Test
    @DisplayName("breaker 메트릭 미주입 안전 동작")
    void nullMetricsSafe() {
        RerankCircuitBreaker bare = new RerankCircuitBreaker();
        ReflectionTestUtils.setField(bare, "fallbackRateThreshold", 0.05);
        ReflectionTestUtils.setField(bare, "minSamples", 20);
        ReflectionTestUtils.setField(bare, "windowMinutes", 5);

        for (int i = 0; i < 25; i++) bare.recordResult(true);

        // 메트릭 없어도 trip은 정상
        assertThat(bare.isLogicalOff()).isTrue();
    }
}
