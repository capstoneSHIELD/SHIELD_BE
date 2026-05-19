package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.example.shield.ai.dto.RetrievedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagBaselineEvaluatorTest {

    private final RagBaselineEvaluator evaluator = new RagBaselineEvaluator();

    @Test
    @DisplayName("baseline evaluator calculates recall, MRR, nDCG, and latency percentiles")
    void evaluateMetrics() {
        RagEvalItem q1 = item("V22-Q001", "law-civil:제618조");
        RagEvalItem q2 = item("V22-Q002", "law-civil:제398조");
        Map<String, List<RetrievedDocument>> results = Map.of(
                "V22-Q001", List.of(law("law-civil", "제618조", 0.9)),
                "V22-Q002", List.of(law("law-civil", "제105조", 0.9), law("law-civil", "제398조", 0.8))
        );

        RagBaselineEvaluationResult result = evaluator.evaluate(
                List.of(q1, q2),
                results,
                Map.of("V22-Q001", 100L, "V22-Q002", 300L),
                "weighted");

        assertThat(result.queryCount()).isEqualTo(2);
        assertThat(result.recallAt5()).isEqualTo(1.0);
        assertThat(result.mixedRecallAt5()).isEqualTo(1.0);
        assertThat(result.statuteRecallAt5()).isEqualTo(1.0);
        assertThat(result.mrr()).isEqualTo(0.75);
        assertThat(result.ndcgAt5()).isGreaterThan(0.8);
        assertThat(result.latencyP50Ms()).isEqualTo(100.0);
        assertThat(result.latencyP95Ms()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("empty results produce zero metrics and serializable markdown report")
    void emptyResults() {
        RagBaselineEvaluationResult result = evaluator.evaluate(
                List.of(item("V22-Q001", "law-civil:제618조")),
                Map.of("V22-Q001", List.of()),
                Map.of(),
                "weighted");
        RagBaselineReportWriter writer = new RagBaselineReportWriter(new ObjectMapper().findAndRegisterModules());

        assertThat(result.recallAt5()).isZero();
        assertThat(result.mrr()).isZero();
        assertThat(result.ndcgAt5()).isZero();
        assertThat(writer.toMarkdown(result)).contains("Mixed Recall@5").contains("0.0000");
    }

    @Test
    @DisplayName("baseline evaluator separates statute/case recall and split metrics")
    void evaluateSeparatedRecallAndSplits() {
        RagEvalItem statute = new RagEvalItem(
                "V22-Q001",
                "dev",
                "law-001-02-02",
                "부동산 거래",
                "부동산 임대차",
                "보증금 및 차임",
                "real_estate_lease",
                "보증금 반환",
                List.of("보증금"),
                List.of(),
                List.of(new RagEvalLawRef("민법", "제618조")),
                List.of("law:민법:제618조"),
                Map.of("law:민법:제618조", 3),
                "baseline",
                "ops_log",
                "reviewer-a",
                "2026-05-19");
        RagEvalItem precedent = new RagEvalItem(
                "V22-Q002",
                "holdout",
                "law-001-02-02",
                "부동산 거래",
                "부동산 임대차",
                "보증금 및 차임",
                "real_estate_lease",
                "보증금 반환 판례",
                List.of("보증금"),
                List.of(),
                List.of(),
                List.of("case:2024다12345"),
                Map.of("case:2024다12345", 2),
                "baseline",
                "ops_log",
                "reviewer-a",
                "2026-05-19");

        RagBaselineEvaluationResult result = evaluator.evaluate(
                List.of(statute, precedent),
                Map.of(
                        "V22-Q001", List.of(law("민법", "제618조", 0.9)),
                        "V22-Q002", List.of(caseDoc("2024다12345", 0.8))),
                Map.of("V22-Q001", 100L, "V22-Q002", 200L),
                "weighted");

        assertThat(result.statuteQueryCount()).isEqualTo(1);
        assertThat(result.caseQueryCount()).isEqualTo(1);
        assertThat(result.statuteRecallAt5()).isEqualTo(1.0);
        assertThat(result.caseRecallAt5()).isEqualTo(1.0);
        assertThat(result.gradedNdcgQueryCount()).isEqualTo(2);
        assertThat(result.splitMetrics()).containsKeys("dev", "holdout");
    }

    private RagEvalItem item(String id, String expectedChunkId) {
        return new RagEvalItem(
                id,
                "real_estate_lease",
                "query",
                List.of(expectedChunkId),
                List.of(new RagEvalLawRef("law-civil", expectedChunkId.substring(expectedChunkId.indexOf(':') + 1))),
                "baseline",
                "unit",
                "dev");
    }

    private LegalChunk law(String lawName, String articleNo, double score) {
        return new LegalChunk(lawName, articleNo, "", "", "", "", score);
    }

    private Precedent caseDoc(String caseNo, double score) {
        return new Precedent(caseNo, "대법원", "", "2024-01-01", "민사", "", "", "", score);
    }
}
