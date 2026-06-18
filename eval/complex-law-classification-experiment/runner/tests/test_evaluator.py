from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.evaluator import ClassificationEvaluator, MatchingEvaluator
from shield_experiment.models import ClassificationResult, MatchingLabelSet, MatchingResult
from shield_experiment.ontology import OntologyMapper, OntologySnapshot
from shield_experiment.report import _matching_failure_tags


class ClassificationEvaluatorTest(unittest.TestCase):
    def test_path_aware_accuracy_accepts_same_path_ancestors(self) -> None:
        repo_root = Path(__file__).resolve().parents[4]
        mapper = OntologyMapper(
            OntologySnapshot.load(repo_root / "src/main/resources/ontology/legal-ontology-slim.json")
        )
        evaluator = ClassificationEvaluator(mapper)
        rows = [
            _classification_result("CASE-1-T03", ["law-007-01-05"]),
            _classification_result("CASE-2-T03", ["law-007-01"]),
            _classification_result("CASE-3-T03", ["law-004-02-01"]),
        ]

        metrics = evaluator.evaluate(rows)

        self.assertAlmostEqual(metrics["exact_set_match"], 1 / 3)
        self.assertAlmostEqual(metrics["primary_accuracy"], 1 / 3)
        self.assertAlmostEqual(metrics["path_aware_accuracy"], 2 / 3)


class MatchingEvaluatorTest(unittest.TestCase):
    def test_recall_ndcg_mrr_and_failure_tags(self) -> None:
        label = MatchingLabelSet(
            label_set_id="MLS-1",
            case_id="CASE-1",
            relevance={"L-SP": 3, "L-REL": 2, "L-HN": 0},
        )
        row = MatchingResult(
            case_id="CASE-1",
            provider="cohere",
            classification_mode="C_HYBRID_RUNTIME",
            matching_mode="PREDICTED_LABELS_COSINE_ONLY",
            label_source="predicted",
            input_node_ids=["law-007-01-05"],
            ranked_lawyers=[
                {"lawyerId": "L-SP"},
                {"lawyerId": "L-REL"},
                {"lawyerId": "L-HN"},
            ],
            top_k=3,
        )

        metrics = MatchingEvaluator().evaluate([row], {"CASE-1": label})

        self.assertEqual(metrics["hit_at_1"], 1.0)
        self.assertEqual(metrics["recall_at_3"], 1.0)
        self.assertEqual(metrics["ndcg_at_5"], 1.0)
        self.assertEqual(metrics["mrr"], 1.0)
        self.assertEqual(metrics["hard_negative_intrusion_rate"], 1.0)
        self.assertEqual(_matching_failure_tags(row, label), ["matching_hard_negative_top_rank"])


def _classification_result(turn_id: str, pred_node_ids: list[str]) -> ClassificationResult:
    return ClassificationResult(
        turn_id=turn_id,
        case_id=turn_id.rsplit("-", 1)[0],
        conversation_id=turn_id.rsplit("-", 1)[0],
        turn_index=3,
        is_final_turn=True,
        benchmark_split="test",
        group="wrong_selected_single_bait",
        provider="cohere",
        requested_provider="cohere",
        mode="A_FULL",
        input_domain=None,
        gold_node_ids=["law-007-01-05"],
        gold_primary_node_id="law-007-01-05",
        expected_complex=False,
        pred_node_ids=pred_node_ids,
    )


if __name__ == "__main__":
    unittest.main()
