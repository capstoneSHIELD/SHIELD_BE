package org.example.shield.ai.safety;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rerank 호출 회로 차단기 (Phase P5.4 Commit 3).
 *
 * <p>Runtime flag 변경이 불가능한 환경(Spring Cloud Config 없음, Q1/Q2 참조)에서
 * Bean 내부 atomic flag로 logical OFF를 구현해 비상 차단을 보완한다.
 *
 * <h3>Trip 조건</h3>
 * <ul>
 *   <li>fallback rate &gt; threshold (기본 5%, 최소 sample 20)</li>
 *   <li>이전 5분 window 내 통계 기준</li>
 * </ul>
 *
 * <p>차단 후 reset은 명시적 호출만 (수동 점검 후 enable). 자동 reset은 의도적으로 미구현 —
 * fallback 폭발의 근본 원인을 점검하지 않으면 다시 발생할 수 있음.
 */
@Component
@Slf4j
public class RerankCircuitBreaker {

    @Value("${app.ai.rag.rerank.circuit-breaker.fallback-rate-threshold:0.05}")
    private double fallbackRateThreshold;

    @Value("${app.ai.rag.rerank.circuit-breaker.min-samples:20}")
    private int minSamples;

    @Value("${app.ai.rag.rerank.circuit-breaker.window-minutes:5}")
    private int windowMinutes;

    private final AtomicBoolean logicalOff = new AtomicBoolean(false);
    private final Deque<Sample> samples = new ArrayDeque<>();

    private final AiRagOperationalMetrics operationalMetrics;

    public RerankCircuitBreaker() {
        this(null);
    }

    @Autowired
    public RerankCircuitBreaker(AiRagOperationalMetrics operationalMetrics) {
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * 호출 결과 기록 — fallback이면 trip 조건 평가.
     *
     * @param fallback rerank가 fallback으로 처리됐는지 (timeout, API error 등)
     */
    public synchronized void recordResult(boolean fallback) {
        Instant now = Instant.now();
        evictOldSamples(now);
        samples.addLast(new Sample(now, fallback));

        if (samples.size() < minSamples) {
            return;
        }
        long fallbackCount = samples.stream().filter(s -> s.fallback).count();
        double rate = (double) fallbackCount / samples.size();
        if (rate > fallbackRateThreshold) {
            tripIfNeeded(rate);
        }
    }

    /**
     * 현재 logical off 상태인지 — RerankingService가 호출 전 체크.
     */
    public boolean isLogicalOff() {
        return logicalOff.get();
    }

    /**
     * 차단 해제 (수동 점검 후 호출). 통계 윈도우도 함께 초기화.
     */
    public synchronized void reset() {
        boolean wasOff = logicalOff.getAndSet(false);
        samples.clear();
        if (wasOff) {
            log.warn("Rerank circuit breaker manually RESET. Re-enabling rerank calls.");
        }
    }

    private void tripIfNeeded(double rate) {
        if (logicalOff.compareAndSet(false, true)) {
            log.error("Rerank circuit breaker TRIPPED: fallback rate={} > threshold={} (samples={}). " +
                            "Logical OFF until manual reset().",
                    String.format("%.3f", rate),
                    String.format("%.3f", fallbackRateThreshold),
                    samples.size());
            if (operationalMetrics != null) {
                try {
                    operationalMetrics.recordRerankFallback("circuit_breaker");
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
    }

    private void evictOldSamples(Instant now) {
        Instant cutoff = now.minus(Duration.ofMinutes(windowMinutes));
        while (!samples.isEmpty() && samples.peekFirst().timestamp.isBefore(cutoff)) {
            samples.removeFirst();
        }
    }

    int sampleCount() {
        return samples.size();
    }

    private record Sample(Instant timestamp, boolean fallback) { }
}
