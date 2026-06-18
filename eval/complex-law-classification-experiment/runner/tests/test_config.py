from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.config import ExperimentConfig


class ExperimentConfigTest(unittest.TestCase):
    def test_from_file_uses_env_token_when_config_value_is_null(self) -> None:
        previous = os.environ.get("SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN")
        os.environ["SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN"] = "env-secret"

        try:
            with tempfile.TemporaryDirectory() as tmpdir:
                config_path = Path(tmpdir) / "config.json"
                config_path.write_text(
                    json.dumps(
                        {
                            "base_url": "http://127.0.0.1:18080",
                            "experiment_access_token": None,
                            "dataset_path": "dataset.jsonl",
                            "classification_turns_path": "turns.jsonl",
                            "ontology_snapshot_path": "ontology.json",
                            "output_root": "output",
                        }
                    ),
                    encoding="utf-8",
                )

                config = ExperimentConfig.from_file(config_path)

            self.assertEqual(config.experiment_access_token, "env-secret")
        finally:
            if previous is None:
                os.environ.pop("SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN", None)
            else:
                os.environ["SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN"] = previous


if __name__ == "__main__":
    unittest.main()
