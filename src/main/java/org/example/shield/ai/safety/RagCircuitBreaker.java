package org.example.shield.ai.safety;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * RAG 파이프라인 회로 차단기.
 *
 * <p>Cohere API 장애·DB 장애 등으로 RAG 파이프라인이 연속 실패할 때, 매 요청마다 동일한
 * timeout/error 를 반복해 사용자 응답 지연이 폭증하는 것을 방지한다. RAG 실패는
 * graceful-degrade (빈 컨텍스트 + LLM 호출) 로 처리되므로, 회로가 열려있는 동안에는
 * RAG 호출을 즉시 스킵하고 곧장 RAG-less 경로로 빠지게 한다.</p>
 *
 * <h3>상태 머신 (표준 CLOSED → OPEN → HALF_OPEN → CLOSED)</h3>
 * <ul>
 *   <li>{@code CLOSED} — 정상 호출 허용. window 내 실패가 threshold 도달 시 OPEN 으로 trip.</li>
 *   <li>{@code OPEN} — 모든 호출 차단. {@code openDuration} 경과 후 HALF_OPEN 으로 전이.</li>
 *   <li>{@code HALF_OPEN} — 1회 trial 만 허용. 성공 → CLOSED, 실패 → 다시 OPEN.</li>
 * </ul>
 *
 * <p>{@link RerankCircuitBreaker} 는 fallback rate 통계 기반의 "수동 reset 전용" 변형인 반면,
 * 이 클래스는 외부 의존성(Cohere/DB) 장애가 회복되면 자동으로 다시 RAG 를 시도할 수 있도록
 * 표준 FSM 으로 동작한다.</p>
 *
 * <p>이 컴포넌트는 동기적(synchronized)으로 동작 — 분당 수십~수백 RPS 수준에서는 충분.
 * 더 높은 throughput 이 필요해지면 Resilience4j 도입을 검토.</p>
 */
@Component
@Slf4j
public class RagCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    @Value("${app.ai.rag.circuit-breaker.enabled:true}")
    private boolean enabled;

    /** OPEN 으로 trip 시키는 window 내 실패 횟수 */
    @Value("${app.ai.rag.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    /** 실패 카운팅 window (초) */
    @Value("${app.ai.rag.circuit-breaker.window-seconds:60}")
    private int windowSeconds;

    /** OPEN 유지 시간 (초) — 경과 후 HALF_OPEN 으로 전이 */
    @Value("${app.ai.rag.circuit-breaker.open-duration-seconds:60}")
    private int openDurationSeconds;

    private final RagMetrics ragMetrics;

    private State state = State.CLOSED;
    private Instant openedAt;
    private final Deque<Instant> recentFailures = new ArrayDeque<>();

    public RagCircuitBreaker() {
        this(null);
    }

    @Autowired
    public RagCircuitBreaker(RagMetrics ragMetrics) {
        this.ragMetrics = ragMetrics;
    }

    /**
     * 호출 가능 여부 확인. 호출 직전 1회 호출.
     *
     * @return true 면 호출 허용 (CLOSED 또는 HALF_OPEN trial), false 면 차단 (OPEN)
     */
    public synchronized boolean tryAcquire() {
        if (!enabled) {
            return true;
        }
        if (state == State.OPEN) {
            if (openedAt != null
                    && Instant.now().isAfter(openedAt.plus(Duration.ofSeconds(openDurationSeconds)))) {
                // OPEN 만료 → HALF_OPEN 1회 trial 허용
                state = State.HALF_OPEN;
                log.info("RAG circuit breaker → HALF_OPEN (open duration elapsed, allowing trial)");
                recordMetric("trial");
                return true;
            }
            recordMetric("skipped");
            return false;
        }
        // CLOSED, HALF_OPEN: 통과
        return true;
    }

    /**
     * 호출 성공 기록. HALF_OPEN 에서 호출되면 CLOSED 로 복귀.
     */
    public synchronized void recordSuccess() {
        if (!enabled) {
            return;
        }
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            openedAt = null;
            recentFailures.clear();
            log.info("RAG circuit breaker → CLOSED (trial succeeded)");
            recordMetric("closed");
            return;
        }
        // CLOSED 에서 성공은 카운트 리셋이 아니라 자연 만료(window eviction) 로 처리
        // — 일시적 장애 후 곧장 다시 실패하는 케이스를 보호하기 위함.
    }

    /**
     * 호출 실패 기록. 누적 실패가 threshold 도달 시 OPEN 으로 trip.
     * HALF_OPEN 에서 실패하면 즉시 다시 OPEN.
     */
    public synchronized void recordFailure() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            openedAt = now;
            log.warn("RAG circuit breaker → OPEN (trial failed, re-opening for {}s)", openDurationSeconds);
            recordMetric("trip");
            return;
        }
        // CLOSED: window 누적
        recentFailures.addLast(now);
        evictOldFailures(now);
        if (recentFailures.size() >= failureThreshold) {
            state = State.OPEN;
            openedAt = now;
            log.error("RAG circuit breaker TRIPPED → OPEN: {} failures within {}s window. " +
                            "RAG calls will be skipped for {}s.",
                    recentFailures.size(), windowSeconds, openDurationSeconds);
            recordMetric("trip");
        }
    }

    /**
     * 현재 상태 — 테스트/관측용.
     */
    public synchronized State currentState() {
        return state;
    }

    /**
     * 수동 reset — 운영자가 장애 복구 확인 후 즉시 CLOSED 로 복귀시킬 때 사용.
     */
    public synchronized void reset() {
        State prev = state;
        state = State.CLOSED;
        openedAt = null;
        recentFailures.clear();
        if (prev != State.CLOSED) {
            log.warn("RAG circuit breaker manually RESET (was {} → CLOSED)", prev);
        }
    }

    private void evictOldFailures(Instant now) {
        Instant cutoff = now.minus(Duration.ofSeconds(windowSeconds));
        while (!recentFailures.isEmpty() && recentFailures.peekFirst().isBefore(cutoff)) {
            recentFailures.removeFirst();
        }
    }

    private void recordMetric(String outcome) {
        if (ragMetrics != null) {
            try {
                ragMetrics.recordCircuitBreaker(outcome);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
