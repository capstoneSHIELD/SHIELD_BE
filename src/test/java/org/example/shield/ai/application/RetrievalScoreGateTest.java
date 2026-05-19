package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.RetrievalScoreCandidate;
import org.example.shield.ai.dto.RetrievalScoreGateDecision;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalScoreGateTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RetrievalScoreGate gate = new RetrievalScoreGate(new RagMetrics(registry));

    @Test
    @DisplayName("disabled gate passes every candidate")
    void evaluate_disabledPasses() {
        ReflectionTestUtils.setField(gate, "enabled", false);

        RetrievalScoreGateDecision decision = gate.evaluate(
                new RetrievalScoreCandidate("law-a", RetrievalScoreMethod.WEIGHTED, 0.01));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("disabled");
    }

    @Test
    @DisplayName("enabled gate without calibrated threshold still passes")
    void evaluate_uncalibratedPasses() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "weightedThreshold", "");

        RetrievalScoreGateDecision decision = gate.evaluate(
                new RetrievalScoreCandidate("law-a", RetrievalScoreMethod.WEIGHTED, 0.01));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("uncalibrated");
    }

    @Test
    @DisplayName("enabled gate drops documents below calibrated threshold")
    void filter_dropsBelowThreshold() {
        ReflectionTestUtils.setField(gate, "enabled", true);
        ReflectionTestUtils.setField(gate, "weightedThreshold", "0.35");

        List<LegalChunk> filtered = gate.filter(List.of(
                new LegalChunk("A", "1", "title", "content", "2026-01-01", "", 0.50),
                new LegalChunk("B", "2", "title", "content", "2026-01-01", "", 0.20)
        ), RetrievalScoreMethod.WEIGHTED);

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).lawName()).isEqualTo("A");
        assertThat(registry.counter(
                RagMetrics.METRIC_RETRIEVAL_GATE,
                "method", "weighted",
                "outcome", "dropped").count()).isEqualTo(1.0);
    }
}
