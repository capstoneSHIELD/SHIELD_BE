from __future__ import annotations

import math

from .models import ClassificationResult, MatchingLabelSet, MatchingResult
from .ontology import OntologyMapper


class ClassificationEvaluator:
    def __init__(self, mapper: OntologyMapper):
        self.mapper = mapper

    def evaluate(self, rows: list[ClassificationResult]) -> dict[str, float]:
        eligible = [r for r in rows if not r.fallback_used and r.error_type is None]
        total = len(rows)
        parse_success = sum(1 for r in rows if r.parse_success)
        schema_success = sum(1 for r in rows if r.schema_success)
        provider_fallback = sum(1 for r in rows if r.provider != r.requested_provider)

        tp = fp = fn = 0
        exact = 0
        complex_hits = complex_total = 0
        under = 0
        over = 0
        valid_nodes = 0
        predicted_nodes = 0
        primary_hits = 0
        path_aware_hits = 0
        partial_score_total = 0.0
        for row in eligible:
            gold = set(row.gold_node_ids)
            pred = set(row.pred_node_ids)
            tp += len(gold & pred)
            fp += len(pred - gold)
            fn += len(gold - pred)
            exact += int(gold == pred)
            predicted_nodes += len(row.pred_node_ids)
            valid_nodes += len(self.mapper.validate(row.pred_node_ids))
            primary_hits += int(self._primary_matches(row))
            path_aware_hits += int(self._path_aware_matches(row))
            partial_score_total += self._hierarchical_partial_score(row.pred_node_ids, row.gold_node_ids)
            if len(gold) >= 2:
                complex_total += len(gold)
                complex_hits += len(gold & pred)
                if len(pred) <= 1:
                    under += 1
            if len(pred) >= len(gold) + 2:
                over += 1

        precision = _safe_div(tp, tp + fp)
        recall = _safe_div(tp, tp + fn)
        f1 = _safe_div(2 * precision * recall, precision + recall)
        complex_cases = sum(1 for r in eligible if len(r.gold_node_ids) >= 2)
        return {
            "row_count": float(total),
            "eligible_count": float(len(eligible)),
            "parse_success_rate": _safe_div(parse_success, total),
            "schema_success_rate": _safe_div(schema_success, total),
            "provider_fallback_rate": _safe_div(provider_fallback, total),
            "fallback_rate": _safe_div(sum(1 for r in rows if r.fallback_used), total),
            "valid_node_rate": _safe_div(valid_nodes, predicted_nodes),
            "exact_set_match": _safe_div(exact, len(eligible)),
            "micro_precision": precision,
            "micro_recall": recall,
            "micro_f1": f1,
            "complex_recall": _safe_div(complex_hits, complex_total),
            "under_classification_rate": _safe_div(under, complex_cases),
            "over_classification_rate": _safe_div(over, len(eligible)),
            "primary_accuracy": _safe_div(primary_hits, len(eligible)),
            "path_aware_accuracy": _safe_div(path_aware_hits, len(eligible)),
            "hierarchical_partial_score": _safe_div(partial_score_total, len(eligible)),
            "latency_avg_ms": _average([r.latency_ms for r in eligible]),
            "tokens_input_avg": _average([r.tokens_in for r in eligible]),
            "tokens_output_avg": _average([r.tokens_out for r in eligible]),
        }

    def _primary_matches(self, row: ClassificationResult) -> bool:
        primary = row.gold_primary_node_id or (row.gold_node_ids[0] if row.gold_node_ids else None)
        if not primary:
            return False
        if row.pred_node_ids and row.pred_node_ids[0] == primary:
            return True
        parsed_case_type = (row.raw.get("parsed") or {}).get("caseType") or (row.raw.get("parsed") or {}).get("case_type")
        if not isinstance(parsed_case_type, dict):
            return False
        case_type_values = {
            str(parsed_case_type.get("l1") or ""),
            str(parsed_case_type.get("l2") or ""),
            str(parsed_case_type.get("l3") or ""),
        }
        return primary in case_type_values

    def _path_aware_matches(self, row: ClassificationResult) -> bool:
        primary = row.gold_primary_node_id or (row.gold_node_ids[0] if row.gold_node_ids else None)
        predicted = row.pred_node_ids[0] if row.pred_node_ids else None
        if not primary or not predicted:
            return False
        acceptable_nodes = {
            primary,
            self.mapper.to_l2(primary),
            self.mapper.to_l1(primary),
        }
        return predicted in acceptable_nodes

    def _hierarchical_partial_score(self, predicted: list[str], gold: list[str]) -> float:
        if not gold:
            return 0.0
        total = 0.0
        for gold_node in gold:
            if not predicted:
                continue
            total += max(self.mapper.hierarchy_score(pred_node, gold_node) for pred_node in predicted)
        return total / len(gold)


class MatchingEvaluator:
    def evaluate(self, rows: list[MatchingResult], labels: dict[str, MatchingLabelSet]) -> dict[str, float]:
        if not rows:
            return {"matching_row_count": 0.0}
        eligible = [row for row in rows if row.error_type is None]
        if not eligible:
            return {
                "matching_row_count": float(len(rows)),
                "eligible_count": 0.0,
                "error_rate": 1.0,
            }
        hit_at_1 = 0
        recall_at_3 = 0.0
        recall_at_5 = 0.0
        recall_at_10 = 0.0
        ndcg_at_5 = 0.0
        ndcg_at_10 = 0.0
        mrr_total = 0.0
        exact_specialist_recall_at_10 = 0.0
        hard_negative_intrusion = 0
        labeled_rows = 0
        for row in eligible:
            label = labels.get(row.case_id)
            if not label:
                continue
            labeled_rows += 1
            grades = [
                label.grade_of(str(item.get("lawyerId") or item.get("lawyer_id")))
                for item in row.ranked_lawyers
            ]
            hit_at_1 += int(bool(grades) and grades[0] >= 2)
            first = next((idx + 1 for idx, grade in enumerate(grades) if grade >= 2), None)
            if first:
                mrr_total += 1.0 / first
            recall_at_3 += _recall_at_k(grades, label, 3)
            recall_at_5 += _recall_at_k(grades, label, 5)
            recall_at_10 += _recall_at_k(grades, label, 10)
            ndcg_at_5 += _ndcg_at_k(grades, label, 5)
            ndcg_at_10 += _ndcg_at_k(grades, label, 10)
            exact_specialist_recall_at_10 += _exact_specialist_recall_at_k(grades, label, 10)
            hard_negative_intrusion += int(any(grade == 0 for grade in grades[:5]))
        return {
            "matching_row_count": float(len(rows)),
            "eligible_count": float(len(eligible)),
            "labeled_count": float(labeled_rows),
            "error_rate": _safe_div(len(rows) - len(eligible), len(rows)),
            "hit_at_1": _safe_div(hit_at_1, labeled_rows),
            "recall_at_3": _safe_div(recall_at_3, labeled_rows),
            "recall_at_5": _safe_div(recall_at_5, labeled_rows),
            "recall_at_10": _safe_div(recall_at_10, labeled_rows),
            "ndcg_at_5": _safe_div(ndcg_at_5, labeled_rows),
            "ndcg_at_10": _safe_div(ndcg_at_10, labeled_rows),
            "mrr": _safe_div(mrr_total, labeled_rows),
            "exact_specialist_recall_at_10": _safe_div(exact_specialist_recall_at_10, labeled_rows),
            "hard_negative_intrusion_rate": _safe_div(hard_negative_intrusion, labeled_rows),
            "latency_avg_ms": _average([r.latency_ms for r in eligible]),
        }


def _safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def _average(values: list[int | None]) -> float:
    numeric = [value for value in values if value is not None]
    return _safe_div(sum(numeric), len(numeric))


def _recall_at_k(grades: list[int], label: MatchingLabelSet, k: int) -> float:
    relevant_total = sum(1 for grade in label.relevance.values() if grade >= 2)
    if relevant_total == 0:
        return 0.0
    return sum(1 for grade in grades[:k] if grade >= 2) / relevant_total


def _exact_specialist_recall_at_k(grades: list[int], label: MatchingLabelSet, k: int) -> float:
    specialist_total = sum(1 for grade in label.relevance.values() if grade == 3)
    if specialist_total == 0:
        return 0.0
    return sum(1 for grade in grades[:k] if grade == 3) / specialist_total


def _ndcg_at_k(grades: list[int], label: MatchingLabelSet, k: int) -> float:
    ideal = sorted(label.relevance.values(), reverse=True)
    ideal_dcg = _dcg(ideal[:k])
    if ideal_dcg == 0:
        return 0.0
    return _dcg(grades[:k]) / ideal_dcg


def _dcg(grades: list[int]) -> float:
    return sum((2 ** grade - 1) / math.log2(index + 2) for index, grade in enumerate(grades))
