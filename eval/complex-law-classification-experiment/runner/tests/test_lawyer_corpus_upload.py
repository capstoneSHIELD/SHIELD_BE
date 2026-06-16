from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.client import ExperimentClient
from shield_experiment.dataset import LawyerCorpusRepository, write_jsonl


class LawyerCorpusUploadTest(unittest.TestCase):
    def test_upload_is_skipped_by_dry_run_client(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            corpus_path = Path(tmp) / "lawyers.jsonl"
            write_jsonl(
                corpus_path,
                [
                    {
                        "lawyer_id": "L-007-01-05-001",
                        "practice_node_ids": ["law-007-01-05"],
                        "tags": ["law-007-01-05"],
                        "verification_status": "VERIFIED",
                    }
                ],
            )

            response = LawyerCorpusRepository().upload_lawyers(
                ExperimentClient("http://127.0.0.1:1", dry_run=True),
                corpus_path,
                corpus_id="test-corpus",
            )

            self.assertEqual(response["corpusId"], "test-corpus")
            self.assertEqual(response["acceptedCount"], 1)
            self.assertEqual(response["coverageNodeCount"], 1)
            self.assertTrue(response["dryRun"])


if __name__ == "__main__":
    unittest.main()
