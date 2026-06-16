from __future__ import annotations

import csv
from pathlib import Path

from .models import ClassificationResult, MatchingLabelSet, MatchingResult


class ReportWriter:
    def __init__(self, reports_dir: Path):
        self.reports_dir = reports_dir
        self.reports_dir.mkdir(parents=True, exist_ok=True)

    def write_metrics_summary(self, classification_metrics: dict[str, dict[str, float]]) -> None:
        lines = ["# Classification Metrics Summary", ""]
        for key, metrics in sorted(classification_metrics.items()):
            lines.append(f"## {key}")
            for name, value in sorted(metrics.items()):
                lines.append(f"- `{name}`: {value:.4f}")
            lines.append("")
        (self.reports_dir / "metrics-summary.md").write_text("\n".join(lines), encoding="utf-8")

    def write_matching_summary(self, matching_metrics: dict[str, dict[str, float]]) -> None:
        lines = ["# Matching Metrics Summary", ""]
        for key, metrics in sorted(matching_metrics.items()):
            lines.append(f"## {key}")
            for name, value in sorted(metrics.items()):
                lines.append(f"- `{name}`: {value:.4f}")
            lines.append("")
        (self.reports_dir / "matching-metrics-summary.md").write_text("\n".join(lines), encoding="utf-8")
        self.write_current_service_baseline(matching_metrics)
        self.write_cosine_vs_hybrid(matching_metrics)

    def write_current_service_baseline(self, matching_metrics: dict[str, dict[str, float]]) -> None:
        baseline = matching_metrics.get("PREDICTED_LABELS_COSINE_ONLY", {})
        lines = [
            "# Current Service Baseline",
            "",
            "`PREDICTED_LABELS_COSINE_ONLY` is the current-service compatible baseline.",
            "",
        ]
        if not baseline:
            lines.append("No baseline rows were produced in this run.")
        else:
            for name in [
                "matching_row_count",
                "eligible_count",
                "hit_at_1",
                "recall_at_10",
                "ndcg_at_10",
                "mrr",
                "hard_negative_intrusion_rate",
            ]:
                lines.append(f"- `{name}`: {baseline.get(name, 0.0):.4f}")
        (self.reports_dir / "current-service-baseline.md").write_text("\n".join(lines), encoding="utf-8")

    def write_cosine_vs_hybrid(self, matching_metrics: dict[str, dict[str, float]]) -> None:
        predicted_cosine = matching_metrics.get("PREDICTED_LABELS_COSINE_ONLY", {})
        predicted_hybrid = matching_metrics.get("PREDICTED_LABELS_HYBRID_MATCH", {})
        oracle_cosine = matching_metrics.get("ORACLE_LABELS_COSINE_ONLY", {})
        oracle_hybrid = matching_metrics.get("ORACLE_LABELS_HYBRID_MATCH", {})
        lines = [
            "# Cosine vs Hybrid Matching",
            "",
            "| delta | value |",
            "|---|---:|",
            f"| hybrid_recovery_ndcg_at_10 | {_delta(predicted_hybrid, predicted_cosine, 'ndcg_at_10'):.4f} |",
            f"| classification_induced_drop_ndcg_at_10 | {_delta(oracle_cosine, predicted_cosine, 'ndcg_at_10'):.4f} |",
            f"| oracle_hybrid_vs_oracle_cosine_ndcg_at_10 | {_delta(oracle_hybrid, oracle_cosine, 'ndcg_at_10'):.4f} |",
            f"| predicted_hybrid_vs_predicted_cosine_recall_at_10 | {_delta(predicted_hybrid, predicted_cosine, 'recall_at_10'):.4f} |",
        ]
        (self.reports_dir / "cosine-vs-hybrid-matching.md").write_text("\n".join(lines), encoding="utf-8")

    def write_benchmark_validity_check(
        self,
        preflight_summary: dict,
        classification_metrics: dict[str, dict[str, float]],
        matching_metrics: dict[str, dict[str, float]],
    ) -> None:
        lines = ["# Benchmark Validity Check", ""]
        turn_report = preflight_summary.get("classification_turns") or {}
        lines.extend([
            "## Preflight",
            f"- `classification_turn_rows`: {turn_report.get('row_count', 0)}",
            f"- `classification_turn_errors`: {turn_report.get('error_count', 0)}",
            f"- `provider_preflight`: `{preflight_summary.get('providers', {})}`",
        ])
        adapter = preflight_summary.get("lawyer_match_adapter") or {}
        if adapter:
            lines.extend([
                f"- `lawyer_match_corpus_loaded`: {adapter.get('corpusLoaded')}",
                f"- `lawyer_match_current_service_compatible`: {adapter.get('currentServiceCompatible')}",
                f"- `lawyer_match_hybrid_weights_accepted`: {adapter.get('hybridWeightsAccepted')}",
                f"- `lawyer_match_error_type`: {adapter.get('errorType')}",
            ])
        lines.extend(["", "## Metric Guards"])
        for key, metrics in sorted(classification_metrics.items()):
            lines.append(
                f"- `{key}` fallback={metrics.get('fallback_rate', 0.0):.4f}, "
                f"provider_fallback={metrics.get('provider_fallback_rate', 0.0):.4f}, "
                f"valid_node={metrics.get('valid_node_rate', 0.0):.4f}"
            )
        for key, metrics in sorted(matching_metrics.items()):
            lines.append(
                f"- `{key}` error={metrics.get('error_rate', 0.0):.4f}, "
                f"eligible={metrics.get('eligible_count', 0.0):.0f}"
            )
        (self.reports_dir / "benchmark-validity-check.md").write_text("\n".join(lines), encoding="utf-8")

    def write_corpus_coverage_report(self, preflight_summary: dict) -> None:
        corpus = preflight_summary.get("lawyer_corpus") or {}
        lines = ["# Corpus Coverage Report", ""]
        if not corpus:
            lines.append("No matching corpus was required in this run.")
        else:
            lines.extend([
                f"- `lawyer_count`: {corpus.get('lawyer_count', 0)}",
                f"- `coverage_node_count`: {corpus.get('coverage_node_count', 0)}",
                f"- `required_practice_node_count`: {corpus.get('required_practice_node_count', 0)}",
                f"- `missing_required_practice_node_ids`: `{corpus.get('missing_required_practice_node_ids', [])}`",
                f"- `unknown_practice_node_ids`: `{corpus.get('unknown_practice_node_ids', [])}`",
                f"- `missing_label_lawyer_ids`: `{corpus.get('missing_label_lawyer_ids', [])}`",
                "",
                "## Coverage by L1",
            ])
            for node_id, count in (corpus.get("coverage_by_l1") or {}).items():
                lines.append(f"- `{node_id}`: {count}")
            lines.extend(["", "## Coverage by L2"])
            for node_id, count in (corpus.get("coverage_by_l2") or {}).items():
                lines.append(f"- `{node_id}`: {count}")
        (self.reports_dir / "corpus-coverage-report.md").write_text("\n".join(lines), encoding="utf-8")

    def write_failure_cases(
        self,
        classification_results: dict[tuple[str, str, str], ClassificationResult],
        matching_results: dict[str, list[MatchingResult]],
        labels: dict[str, MatchingLabelSet],
    ) -> None:
        lines = ["# Failure Cases", "", "## Classification"]
        failures = 0
        for result in classification_results.values():
            tags = _classification_failure_tags(result)
            if not tags:
                continue
            failures += 1
            lines.append(
                f"- `{result.turn_id}` `{result.provider}` `{result.mode}` "
                f"case=`{result.case_id}` tags=`{','.join(tags)}` error=`{result.error_type}`"
            )
        if failures == 0:
            lines.append("No classification failures were tagged.")

        lines.extend(["", "## Matching"])
        matching_failures = 0
        for mode, rows in sorted(matching_results.items()):
            for row in rows:
                tags = _matching_failure_tags(row, labels.get(row.case_id))
                if not tags:
                    continue
                matching_failures += 1
                lines.append(
                    f"- `{row.case_id}` `{mode}` tags=`{','.join(tags)}` "
                    f"error=`{row.error_type}` top_k={row.top_k}"
                )
        if matching_failures == 0:
            lines.append("No matching failures were tagged.")
        (self.reports_dir / "failure-cases.md").write_text("\n".join(lines), encoding="utf-8")

    def write_confusion_by_l1(self, classification_results, mapper) -> None:
        counts: dict[tuple[str, str, str, str], int] = {}
        for result in classification_results.values():
            gold = mapper.to_l1(result.gold_primary_node_id or (result.gold_node_ids[0] if result.gold_node_ids else None))
            pred = mapper.to_l1(result.pred_node_ids[0] if result.pred_node_ids else None)
            key = (result.provider, result.mode, gold or "(none)", pred or "(none)")
            counts[key] = counts.get(key, 0) + 1
        with (self.reports_dir / "confusion-by-l1.csv").open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow(["provider", "mode", "gold_l1", "pred_l1", "count"])
            for (provider, mode, gold, pred), count in sorted(counts.items()):
                writer.writerow([provider, mode, gold, pred, count])

    def write_scoped_ontology_loss(
        self,
        classification_results: dict[tuple[str, str, str], ClassificationResult],
    ) -> None:
        by_turn_provider: dict[tuple[str, str], dict[str, ClassificationResult]] = {}
        for (_, provider, mode), result in classification_results.items():
            by_turn_provider.setdefault((result.turn_id, provider), {})[mode] = result
        lines = ["# Scoped Ontology Loss", ""]
        loss_count = 0
        for (turn_id, provider), by_mode in sorted(by_turn_provider.items()):
            full = by_mode.get("A_FULL")
            if not full:
                continue
            full_hits = set(full.pred_node_ids) & set(full.gold_node_ids)
            if not full_hits:
                continue
            for mode in ["B_SCOPED_GOLD", "B_SCOPED_RUNTIME", "C_HYBRID_RUNTIME"]:
                scoped = by_mode.get(mode)
                if scoped and not (set(scoped.pred_node_ids) & set(scoped.gold_node_ids)):
                    loss_count += 1
                    lines.append(
                        f"- `{turn_id}` provider=`{provider}` mode=`{mode}` "
                        f"full_hits=`{sorted(full_hits)}` scoped_pred=`{scoped.pred_node_ids}`"
                    )
        if loss_count == 0:
            lines.append("No scoped ontology loss was detected by this coarse rule.")
        (self.reports_dir / "scoped-ontology-loss.md").write_text("\n".join(lines), encoding="utf-8")

    def write_classification_to_matching_loss(self, matching_metrics: dict[str, dict[str, float]]) -> None:
        predicted = matching_metrics.get("PREDICTED_LABELS_COSINE_ONLY", {})
        oracle = matching_metrics.get("ORACLE_LABELS_COSINE_ONLY", {})
        lines = [
            "# Classification to Matching Loss",
            "",
            "| metric | oracle - predicted |",
            "|---|---:|",
        ]
        for metric in ["hit_at_1", "recall_at_10", "ndcg_at_10", "mrr"]:
            lines.append(f"| {metric} | {_delta(oracle, predicted, metric):.4f} |")
        (self.reports_dir / "classification-to-matching-loss.md").write_text("\n".join(lines), encoding="utf-8")


def _delta(left: dict[str, float], right: dict[str, float], key: str) -> float:
    return left.get(key, 0.0) - right.get(key, 0.0)


def _classification_failure_tags(result: ClassificationResult) -> list[str]:
    tags: list[str] = []
    if result.error_type:
        tags.append(result.error_type)
    if result.fallback_used:
        tags.append("provider_fallback" if result.provider != result.requested_provider else "fallback_used")
    if not result.parse_success:
        tags.append("parse_failure")
    if not result.schema_success:
        tags.append("schema_failure")
    gold = set(result.gold_node_ids)
    pred = set(result.pred_node_ids)
    if gold and pred and not (gold & pred):
        tags.append("wrong_label_set")
    if len(gold) >= 2 and len(pred) <= 1:
        tags.append("missed_secondary_area")
    return sorted(set(tags))


def _matching_failure_tags(row: MatchingResult, label: MatchingLabelSet | None) -> list[str]:
    tags: list[str] = []
    if row.error_type:
        tags.append(row.error_type)
    if not label:
        return sorted(set(tags))
    grades = [
        label.grade_of(str(item.get("lawyerId") or item.get("lawyer_id")))
        for item in row.ranked_lawyers
    ]
    if not any(grade >= 3 for grade in grades[: row.top_k]):
        tags.append("matching_missed_exact_specialist")
    if any(grade == 0 for grade in grades[:3]):
        tags.append("matching_hard_negative_top_rank")
    if row.matching_mode.startswith("PREDICTED") and not any(grade >= 2 for grade in grades[: row.top_k]):
        tags.append("classification_to_matching_loss")
    return sorted(set(tags))
