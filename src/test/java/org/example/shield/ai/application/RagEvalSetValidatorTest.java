package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.example.shield.ai.dto.RagEvalSetValidationResult;
import org.example.shield.ai.dto.RagRetrievalHint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvalSetValidatorTest {

    private final RagEvalSetValidator validator = new RagEvalSetValidator();

    @Test
    @DisplayName("validates schema, split counts, and double-label rate")
    void validate_schemaSplitAndDoubleLabels() {
        List<RagEvalItem> items = List.of(
                item("Q1", "dev", "ops_log", "reviewer-a"),
                item("Q1", "dev", "ops_log", "reviewer-b"),
                item("Q2", "calibration", "ops_log", "reviewer-a"),
                item("Q3", "holdout", "ops_log", "reviewer-a"));

        RagEvalSetValidationResult result = validator.validate(
                items,
                Map.of("dev", 2L, "calibration", 1L, "holdout", 1L),
                Map.of(),
                true);

        assertThat(result.valid()).isTrue();
        assertThat(result.splitCounts()).containsEntry("dev", 2L);
        assertThat(result.doubleLabeledItemCount()).isEqualTo(1);
        assertThat(result.doubleLabelRate()).isGreaterThanOrEqualTo(0.20);
    }

    @Test
    @DisplayName("holdout items cannot come directly from yaml evidence")
    void validate_holdoutLeakageSourceFails() {
        RagEvalSetValidationResult result = validator.validate(
                List.of(item("Q1", "holdout", "yaml_evidence", "reviewer-a")),
                Map.of("holdout", 1L),
                Map.of(),
                false);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure).contains("holdout source is leakage-prone"));
    }

    @Test
    @DisplayName("relevance grades must stay in 0..3 and expected documents are required")
    void validate_relevanceGradesAndExpectedDocs() {
        RagEvalItem invalid = new RagEvalItem(
                "Q1",
                "dev",
                "law-001-02-02",
                "부동산 거래",
                "부동산 임대차",
                "보증금 및 차임",
                "real_estate_lease",
                "보증금 반환",
                List.of("보증금"),
                List.of(),
                List.of(),
                List.of(),
                Map.of("case:2024다12345", 5),
                "baseline",
                "ops_log",
                "reviewer-a",
                "2026-05-19");

        RagEvalSetValidationResult result = validator.validate(
                List.of(invalid),
                Map.of("dev", 1L),
                Map.of(),
                false);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure).contains("no expected document reference"));
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure).contains("out-of-range relevance grade"));
    }

    @Test
    @DisplayName("holdout keyword exact hint reuse is reported as leakage warning")
    void validate_hintKeywordReuseWarns() {
        RagRetrievalHint hint = new RagRetrievalHint(
                "law-001-02-02",
                List.of("LSI249999"),
                List.of("group:leasing"),
                List.of("보증금"),
                List.of(),
                "v1",
                "2026-05-19",
                "2026-05-19T00:00:00",
                "ontology-v1",
                "mapping-v1",
                "generator-v1");

        RagEvalSetValidationResult result = validator.validate(
                List.of(item("Q1", "holdout", "ops_log", "reviewer-a")),
                Map.of("holdout", 1L),
                Map.of("law-001-02-02", hint),
                false);

        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("keyword exactly matches L3 hint term"));
    }

    private RagEvalItem item(String id, String split, String source, String reviewer) {
        return new RagEvalItem(
                id,
                split,
                "law-001-02-02",
                "부동산 거래",
                "부동산 임대차",
                "보증금 및 차임",
                "real_estate_lease",
                "보증금 반환",
                List.of("보증금"),
                List.of("law:민법:제618조"),
                List.of(new RagEvalLawRef("민법", "제618조")),
                List.of(),
                Map.of("law:민법:제618조", 3),
                "baseline",
                source,
                reviewer,
                "2026-05-19");
    }
}
