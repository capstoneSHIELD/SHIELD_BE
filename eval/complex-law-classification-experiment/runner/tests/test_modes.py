from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.models import ClassificationResult, ClassificationTurn
from shield_experiment.modes import RuntimeScopeResolver
from shield_experiment.ontology import OntologyMapper, OntologySnapshot


class RuntimeScopeResolverTest(unittest.TestCase):
    def setUp(self) -> None:
        repo_root = Path(__file__).resolve().parents[4]
        self.mapper = OntologyMapper(
            OntologySnapshot.load(repo_root / "src/main/resources/ontology/legal-ontology-slim.json")
        )
        self.turn = ClassificationTurn(
            id="T1",
            case_id="CASE-1",
            conversation_id="CASE-1",
            turn_index=1,
            is_final_turn=True,
            benchmark_split="test",
            group="single",
            messages=[],
            gold_node_ids=["law-007-01-05"],
        )

    def test_runtime_scope_uses_full_result_prediction(self) -> None:
        full_result = self._result(["law-006-03-02"])

        scope = RuntimeScopeResolver().resolve(
            self.turn,
            "cohere",
            {("T1", "cohere", "A_FULL"): full_result},
            self.mapper,
        )

        self.assertEqual(scope, "law-006")

    def test_runtime_scope_does_not_fall_back_to_gold_labels(self) -> None:
        scope = RuntimeScopeResolver().resolve(self.turn, "cohere", {}, self.mapper)

        self.assertIsNone(scope)

    def _result(self, pred_node_ids: list[str]) -> ClassificationResult:
        return ClassificationResult(
            turn_id="T1",
            case_id="CASE-1",
            conversation_id="CASE-1",
            turn_index=1,
            is_final_turn=True,
            benchmark_split="test",
            group="single",
            provider="cohere",
            requested_provider="cohere",
            mode="A_FULL",
            input_domain=None,
            gold_node_ids=self.turn.gold_node_ids,
            gold_primary_node_id="law-007-01-05",
            expected_complex=False,
            pred_node_ids=pred_node_ids,
        )


if __name__ == "__main__":
    unittest.main()
