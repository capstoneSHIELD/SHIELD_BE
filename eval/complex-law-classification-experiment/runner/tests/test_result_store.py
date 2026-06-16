from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.models import ClassificationResult
from shield_experiment.result_store import JsonlResultSink


class JsonlResultSinkTest(unittest.TestCase):
    def test_classification_results_write_raw_and_parsed_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sink = JsonlResultSink(Path(tmp))
            result = ClassificationResult(
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
                gold_node_ids=["law-007-01-05"],
                gold_primary_node_id="law-007-01-05",
                expected_complex=False,
                pred_node_ids=["law-007-01-05"],
                raw={"rawJson": "{\"ok\":true}", "responseId": "r1"},
            )

            sink.write_classification_results("cohere", "A_FULL", [result])

            raw_rows = [
                json.loads(line)
                for line in (Path(tmp) / "raw/cohere_A_FULL.jsonl").read_text(encoding="utf-8").splitlines()
            ]
            parsed_rows = [
                json.loads(line)
                for line in (Path(tmp) / "parsed/cohere_A_FULL.parsed.jsonl").read_text(encoding="utf-8").splitlines()
            ]
            self.assertEqual(raw_rows[0]["raw"]["responseId"], "r1")
            self.assertEqual(parsed_rows[0]["pred_node_ids"], ["law-007-01-05"])


if __name__ == "__main__":
    unittest.main()
