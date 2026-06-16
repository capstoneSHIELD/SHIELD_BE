from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.evaluator import MatchingEvaluator
from shield_experiment.models import MatchingLabelSet, MatchingResult
from shield_experiment.report import _matching_failure_tags


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


if __name__ == "__main__":
    unittest.main()
