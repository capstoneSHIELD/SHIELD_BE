from __future__ import annotations

from .models import ClassificationResult, MatchingLabelSet, MatchingResult


class ClassificationEvaluator:
    def evaluate(self, rows: list[ClassificationResult]) -> dict[str, float]:
        eligible = [r for r in rows if not r.fallback_used and r.error_type is None]
        total = len(rows)
        parse_success = sum(1 for r in rows if r.parse_success)
        schema_success = sum(1 for r in rows if r.schema_success)

        tp = fp = fn = 0
        exact = 0
        complex_hits = complex_total = 0
        under = 0
        for row in eligible:
            gold = set(row.gold_node_ids)
            pred = set(row.pred_node_ids)
            tp += len(gold & pred)
            fp += len(pred - gold)
            fn += len(gold - pred)
            exact += int(gold == pred)
            if len(gold) >= 2:
                complex_total += len(gold)
                complex_hits += len(gold & pred)
                if len(pred) <= 1:
                    under += 1

        precision = _safe_div(tp, tp + fp)
        recall = _safe_div(tp, tp + fn)
        f1 = _safe_div(2 * precision * recall, precision + recall)
        complex_cases = sum(1 for r in eligible if len(r.gold_node_ids) >= 2)
        return {
            "row_count": float(total),
            "eligible_count": float(len(eligible)),
            "parse_success_rate": _safe_div(parse_success, total),
            "schema_success_rate": _safe_div(schema_success, total),
            "exact_set_match": _safe_div(exact, len(eligible)),
            "micro_precision": precision,
            "micro_recall": recall,
            "micro_f1": f1,
            "complex_recall": _safe_div(complex_hits, complex_total),
            "under_classification_rate": _safe_div(under, complex_cases),
        }


class MatchingEvaluator:
    def evaluate(self, rows: list[MatchingResult], labels: dict[str, MatchingLabelSet]) -> dict[str, float]:
        if not rows:
            return {"matching_row_count": 0.0}
        hit_at_1 = 0
        mrr_total = 0.0
        for row in rows:
            label = labels.get(row.case_id)
            grades = [label.grade_of(str(item.get("lawyerId") or item.get("lawyer_id"))) if label else 0
                      for item in row.ranked_lawyers]
            hit_at_1 += int(bool(grades) and grades[0] >= 2)
            first = next((idx + 1 for idx, grade in enumerate(grades) if grade >= 2), None)
            if first:
                mrr_total += 1.0 / first
        return {
            "matching_row_count": float(len(rows)),
            "hit_at_1": _safe_div(hit_at_1, len(rows)),
            "mrr": _safe_div(mrr_total, len(rows)),
        }


def _safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator
