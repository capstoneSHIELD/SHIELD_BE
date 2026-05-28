package org.example.shield.ai.safety;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RagCircuitBreaker} 검증 — 표준 CLOSED → OPEN → HALF_OPEN → CLOSED FSM.
 *
 * <p>Mockito 미사용 (POJO + SimpleMeterRegistry + ReflectionTestUtils) — JDK 21
 * 환경의 byte-buddy agent attach 문제와 무관하게 항상 실행 가능.
 */
class RagCircuitBreakerTest {

    private SimpleMeterRegistry registry;
    private RagMetrics metrics;
    private RagCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RagMetrics(registry);
        breaker = new RagCircuitBreaker(metrics);
        ReflectionTestUtils.setField(breaker, "enabled", true);
        ReflectionTestUtils.setField(breaker, "failureThreshold", 3);
        ReflectionTestUtils.setField(breaker, "windowSeconds", 60);
        ReflectionTestUtils.setField(breaker, "openDurationSeconds", 60);
    }

    @Test
    @DisplayName("초기 상태: CLOSED — tryAcquire 통과")
    void initialClosed() {
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("disabled 면 어떤 상태든 tryAcquire 항상 통과")
    void disabledAlwaysAllows() {
        ReflectionTestUtils.setField(breaker, "enabled", false);
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure();
        }
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("threshold 미만 실패 — CLOSED 유지")
    void belowThresholdStaysClosed() {
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("threshold 도달 — OPEN 으로 trip 후 호출 차단")
    void tripToOpenBlocksCalls() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
        // skipped 메트릭이 카운트됨
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "trip").count())
                .isEqualTo(1.0);
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "skipped").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("openDuration 경과 후 HALF_OPEN 전이 → trial 1회 허용")
    void openExpiresToHalfOpen() {
        // OPEN 으로 trip
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        // openedAt 을 과거(70초 전)로 강제하여 만료 시뮬레이션
        ReflectionTestUtils.setField(breaker, "openedAt", Instant.now().minusSeconds(70));
        assertThat(breaker.tryAcquire()).isTrue();  // trial 허용
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.HALF_OPEN);
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "trial").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("HALF_OPEN 에서 성공 → CLOSED 복귀")
    void halfOpenSuccessClosesCircuit() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        ReflectionTestUtils.setField(breaker, "openedAt", Instant.now().minusSeconds(70));
        breaker.tryAcquire();  // HALF_OPEN 전이
        breaker.recordSuccess();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "closed").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("HALF_OPEN 에서 실패 → 다시 OPEN")
    void halfOpenFailureReopens() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        ReflectionTestUtils.setField(breaker, "openedAt", Instant.now().minusSeconds(70));
        breaker.tryAcquire();  // HALF_OPEN 전이
        breaker.recordFailure();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
        // trip 카운트 2회 (최초 trip + HALF_OPEN 실패 후 재-trip)
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "trip").count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("manual reset — 어떤 상태에서도 즉시 CLOSED 로 복귀")
    void manualReset() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.OPEN);
        breaker.reset();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("CLOSED 에서 recordSuccess 는 상태 변화 없음 (no-op)")
    void closedRecordSuccessNoOp() {
        breaker.recordSuccess();
        assertThat(breaker.currentState()).isEqualTo(RagCircuitBreaker.State.CLOSED);
        // closed 메트릭은 HALF_OPEN→CLOSED 전이 시에만 기록되므로 0
        assertThat(registry.counter(RagMetrics.METRIC_CIRCUIT_BREAKER, "outcome", "closed").count())
                .isEqualTo(0.0);
    }
}
