from __future__ import annotations

import math

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

        labeled_rows = [row for row in rows if labels.get(row.case_id)]
        if not labeled_rows:
            return {
                "matching_row_count": float(len(rows)),
                "labeled_row_count": 0.0,
                "label_coverage_rate": 0.0,
            }

        hit_at_1 = 0
        mrr_total = 0.0
        recall_at_3 = recall_at_5 = recall_at_10 = 0.0
        ndcg_at_5 = ndcg_at_10 = 0.0
        exact_specialist_recall_at_10 = 0.0
        hard_negative_intrusions = 0

        for row in labeled_rows:
            label = labels[row.case_id]
            lawyer_ids = [_lawyer_id(item) for item in row.ranked_lawyers]
            grades = [label.grade_of(lawyer_id) for lawyer_id in lawyer_ids]
            hit_at_1 += int(bool(grades) and grades[0] >= 2)
            first = next((idx + 1 for idx, grade in enumerate(grades) if grade >= 2), None)
            if first:
                mrr_total += 1.0 / first

            relevant_ids = {lawyer_id for lawyer_id, grade in label.relevance.items() if grade >= 2}
            exact_ids = {lawyer_id for lawyer_id, grade in label.relevance.items() if grade == 3}
            recall_at_3 += _recall_at_k(lawyer_ids, relevant_ids, 3)
            recall_at_5 += _recall_at_k(lawyer_ids, relevant_ids, 5)
            recall_at_10 += _recall_at_k(lawyer_ids, relevant_ids, 10)
            exact_specialist_recall_at_10 += _recall_at_k(lawyer_ids, exact_ids, 10)
            ndcg_at_5 += _ndcg_at_k(grades, list(label.relevance.values()), 5)
            ndcg_at_10 += _ndcg_at_k(grades, list(label.relevance.values()), 10)
            hard_negative_intrusions += int(any(
                label.has_label(lawyer_id) and label.grade_of(lawyer_id) == 0
                for lawyer_id in lawyer_ids[:5]
            ))

        count = len(labeled_rows)
        return {
            "matching_row_count": float(len(rows)),
            "labeled_row_count": float(count),
            "label_coverage_rate": _safe_div(count, len(rows)),
            "hit_at_1": _safe_div(hit_at_1, count),
            "recall_at_3": _safe_div(recall_at_3, count),
            "recall_at_5": _safe_div(recall_at_5, count),
            "recall_at_10": _safe_div(recall_at_10, count),
            "ndcg_at_5": _safe_div(ndcg_at_5, count),
            "ndcg_at_10": _safe_div(ndcg_at_10, count),
            "mrr": _safe_div(mrr_total, count),
            "exact_specialist_recall_at_10": _safe_div(exact_specialist_recall_at_10, count),
            "hard_negative_intrusion_rate": _safe_div(hard_negative_intrusions, count),
        }


def _safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def _lawyer_id(item: dict) -> str:
    return str(item.get("lawyerId") or item.get("lawyer_id") or "")


def _recall_at_k(ranked_ids: list[str], relevant_ids: set[str], k: int) -> float:
    if not relevant_ids:
        return 0.0
    return len(set(ranked_ids[:k]) & relevant_ids) / len(relevant_ids)


def _ndcg_at_k(grades: list[int], all_label_grades: list[int], k: int) -> float:
    dcg = _dcg(grades[:k])
    ideal = _dcg(sorted(all_label_grades, reverse=True)[:k])
    return _safe_div(dcg, ideal)


def _dcg(grades: list[int]) -> float:
    total = 0.0
    for idx, grade in enumerate(grades, start=1):
        total += (2**grade - 1) / math.log2(idx + 1)
    return total
