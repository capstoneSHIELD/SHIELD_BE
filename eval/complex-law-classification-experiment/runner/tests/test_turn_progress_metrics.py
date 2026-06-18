from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.models import ClassificationResult
from shield_experiment.ontology import OntologyMapper, OntologySnapshot
from shield_experiment.pipeline import EvaluationPipeline


class TurnProgressMetricsTest(unittest.TestCase):
    def test_classification_metrics_are_grouped_by_turn_index(self) -> None:
        repo_root = Path(__file__).resolve().parents[4]
        mapper = OntologyMapper(
            OntologySnapshot.load(repo_root / "src/main/resources/ontology/legal-ontology-slim.json")
        )
        results = {
            ("CASE-1-T02", "cohere", "A_FULL"): _result("CASE-1-T02", 2, ["law-007-01-05"]),
            ("CASE-2-T02", "cohere", "A_FULL"): _result("CASE-2-T02", 2, []),
            ("CASE-1-T03", "cohere", "A_FULL"): _result("CASE-1-T03", 3, ["law-007-01-05"]),
            ("CASE-2-T03", "cohere", "A_FULL"): _result("CASE-2-T03", 3, ["law-007-01-05"]),
        }

        _, _, by_turn = EvaluationPipeline().run(results, {}, {}, mapper)

        self.assertEqual(by_turn["cohere_A_FULL"][2]["exact_set_match"], 0.5)
        self.assertEqual(by_turn["cohere_A_FULL"][2]["path_aware_accuracy"], 0.5)
        self.assertEqual(by_turn["cohere_A_FULL"][3]["exact_set_match"], 1.0)
        self.assertEqual(by_turn["cohere_A_FULL"][3]["path_aware_accuracy"], 1.0)


def _result(turn_id: str, turn_index: int, pred_node_ids: list[str]) -> ClassificationResult:
    return ClassificationResult(
        turn_id=turn_id,
        case_id=turn_id.rsplit("-", 1)[0],
        conversation_id=turn_id.rsplit("-", 1)[0],
        turn_index=turn_index,
        is_final_turn=turn_index == 3,
        benchmark_split="test",
        group="wrong_selected_cross_l1",
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
