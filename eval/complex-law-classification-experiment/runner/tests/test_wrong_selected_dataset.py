from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.dataset import DatasetRepository


class WrongSelectedDatasetRepositoryTest(unittest.TestCase):
    def test_loads_wrong_selected_case_as_evaluation_turns_with_cumulative_messages(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "wrong-x1-001.json"
            path.write_text(json.dumps(_case_json(), ensure_ascii=False), encoding="utf-8")

            turns = DatasetRepository().load_wrong_selected_turns(Path(tmp), history_window=None)

            self.assertEqual([turn.turn_index for turn in turns], [2, 3])
            self.assertEqual(turns[0].id, "WRONG-X1-TEST-T02")
            self.assertEqual(turns[0].gold_node_ids, ["law-007-01-05"])
            self.assertEqual(turns[0].selected_node_ids, ["law-002-04-02", "law-004-02-01"])
            self.assertEqual(len(turns[0].messages), 2)
            self.assertEqual(len(turns[1].messages), 3)
            self.assertTrue(turns[1].is_final_turn)

    def test_history_window_can_limit_wrong_selected_messages(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "wrong-x1-001.json"
            path.write_text(json.dumps(_case_json(), ensure_ascii=False), encoding="utf-8")

            turns = DatasetRepository().load_wrong_selected_turns(Path(tmp), history_window=1)

            self.assertEqual(len(turns[0].messages), 1)
            self.assertEqual(turns[0].messages[0].content, "전세 보증금을 돌려받지 못했습니다.")


def _case_json() -> dict:
    return {
        "schemaVersion": "wrong-selected-cross-l1-testcases.v1",
        "case": {
            "caseId": "WRONG-X1-TEST",
            "group": "wrong_selected_cross_l1",
            "selectedLabels": [
                {"nodeId": "law-002-04-02", "l1": "이혼·위자료·재산분할"},
                {"nodeId": "law-004-02-01", "l1": "근로계약·해고·임금"},
            ],
            "goldLabels": [
                {"nodeId": "law-007-01-05", "l1": "임대차보호"},
            ],
            "turns": [
                {
                    "turnIndex": 1,
                    "userInput": "상담받고 싶은 일이 있습니다.",
                    "observableGoldNodeIds": [],
                    "evaluationTarget": False,
                },
                {
                    "turnIndex": 2,
                    "userInput": "전세 보증금을 돌려받지 못했습니다.",
                    "observableGoldNodeIds": ["law-007-01-05"],
                    "evaluationTarget": True,
                },
                {
                    "turnIndex": 3,
                    "userInput": "계약 만료 후 집주인이 연락을 피합니다.",
                    "observableGoldNodeIds": ["law-007-01-05"],
                    "evaluationTarget": True,
                },
            ],
        },
    }


if __name__ == "__main__":
    unittest.main()
