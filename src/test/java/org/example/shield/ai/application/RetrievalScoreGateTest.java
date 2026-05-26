package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.RetrievalScoreCandidate;
import org.example.shield.ai.dto.RetrievalScoreGateDecision;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RetrievalScoreGate} 검증 (P5.1 Commit 5 mode 분기 포함).
 *
 * <p>레거시 boolean {@code enabled} flag와 신규 {@code mode} flag 모두 검증.
 */
class RetrievalScoreGateTest {

    private SimpleMeterRegistry registry;
    private RetrievalScoreGate gate;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gate = new RetrievalScoreGate(new RagMetrics(registry));
        ReflectionTestUtils.setField(gate, "legacyEnabled", false);
        ReflectionTestUtils.setField(gate, "modeRaw", "off");
    }

    @Nested
    @DisplayName("Legacy enabled flag (BC)")
    class LegacyEnabled {

        @Test
        @DisplayName("legacyEnabled=false + mode=off → 모든 후보 통과")
        void disabledPasses() {
            RetrievalScoreGateDecision decision = gate.evaluate(
                    new RetrievalScoreCandidate("law-a", RetrievalScoreMethod.WEIGHTED, 0.01));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo("disabled");
        }

        @Test
        @DisplayName("legacyEnabled=true → ENFORCE로 마이그레이션 (uncalibrated 시 통과)")
        void legacyTrueMigratesToEnforce() {
            ReflectionTestUtils.setField(gate, "legacyEnabled", true);
            ReflectionTestUtils.setField(gate, "weightedThreshold", "");

            RetrievalScoreGateDecision decision = gate.evaluate(
                    new RetrievalScoreCandidate("law-a", RetrievalScoreMethod.WEIGHTED, 0.01));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo("uncalibrated");
        }

        @Test
        @DisplayName("legacyEnabled=true + threshold → ENFORCE filter")
        void legacyEnforceDrops() {
            ReflectionTestUtils.setField(gate, "legacyEnabled", true);
            ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

            List<LegalChunk> filtered = gate.filter(List.of(
                    new LegalChunk("A", "1", "title", "content", "2026-01-01", "", 0.50),
                    new LegalChunk("B", "2", "title", "content", "2026-01-01", "", 0.20)
            ), RetrievalScoreMethod.WEIGHTED);

            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).lawName()).isEqualTo("A");
            assertThat(registry.counter(RagMetrics.METRIC_RETRIEVAL_GATE,
                    "method", "weighted", "outcome", "dropped").count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("P5.1 Commit 5 — mode 분기")
    class ModeFlag {

        @Test
        @DisplayName("mode=enforce + threshold → drop (ENFORCE)")
        void enforceDrops() {
            ReflectionTestUtils.setField(gate, "modeRaw", "enforce");
            ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

            List<LegalChunk> filtered = gate.filter(List.of(
                    new LegalChunk("A", "1", "t", "c", "2026-01-01", "", 0.50),
                    new LegalChunk("B", "2", "t", "c", "2026-01-01", "", 0.20)
            ), RetrievalScoreMethod.WEIGHTED);

            assertThat(filtered).hasSize(1);
            assertThat(registry.counter(RagMetrics.METRIC_RETRIEVAL_GATE,
                    "method", "weighted", "outcome", "dropped").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("mode=shadow + threshold → 모두 통과 (user-facing 변화 0) + shadow_pass/shadow_drop 메트릭만")
        void shadowRecordsButDoesNotFilter() {
            ReflectionTestUtils.setField(gate, "modeRaw", "shadow");
            ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

            List<LegalChunk> filtered = gate.filter(List.of(
                    new LegalChunk("A", "1", "t", "c", "2026-01-01", "", 0.50),
                    new LegalChunk("B", "2", "t", "c", "2026-01-01", "", 0.20)
            ), RetrievalScoreMethod.WEIGHTED);

            // SHADOW에서는 filter 안 함 — 2개 모두 통과
            assertThat(filtered).hasSize(2);
            // 메트릭만 shadow_pass / shadow_drop 으로 분리 기록
            assertThat(registry.counter(RagMetrics.METRIC_RETRIEVAL_GATE,
                    "method", "weighted", "outcome", "shadow_pass").count()).isEqualTo(1.0);
            assertThat(registry.counter(RagMetrics.METRIC_RETRIEVAL_GATE,
                    "method", "weighted", "outcome", "shadow_drop").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("mode=shadow + evaluate → allowed=true (drop도 통과시킴)")
        void shadowEvaluateAlwaysAllowed() {
            ReflectionTestUtils.setField(gate, "modeRaw", "shadow");
            ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

            RetrievalScoreGateDecision dropDecision = gate.evaluate(
                    new RetrievalScoreCandidate("law-low", RetrievalScoreMethod.WEIGHTED, 0.10));

            assertThat(dropDecision.allowed()).isTrue();  // shadow는 항상 통과
            assertThat(dropDecision.reason()).isEqualTo("shadow_drop");
        }

        @Test
        @DisplayName("mode=off — gate 무동작")
        void modeOffPasses() {
            ReflectionTestUtils.setField(gate, "modeRaw", "off");
            ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

            List<LegalChunk> filtered = gate.filter(List.of(
                    new LegalChunk("A", "1", "t", "c", "2026-01-01", "", 0.01)
            ), RetrievalScoreMethod.WEIGHTED);

            assertThat(filtered).hasSize(1);
        }

        @Test
        @DisplayName("mode=invalid → startup fail-fast (currentMode 호출 시점)")
        void invalidModeThrows() {
            ReflectionTestUtils.setField(gate, "modeRaw", "bogus");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    gate.evaluate(new RetrievalScoreCandidate("a", RetrievalScoreMethod.WEIGHTED, 0.5)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bogus");
        }
    }
}
