from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.corpus_generator import SyntheticLawyerCorpusGenerator
from shield_experiment.dataset import read_jsonl
from shield_experiment.ontology import OntologySnapshot


class SyntheticLawyerCorpusGeneratorTest(unittest.TestCase):
    def test_seed_reproducibility_fake_id_and_ontology_coverage(self) -> None:
        repo_root = Path(__file__).resolve().parents[4]
        ontology = OntologySnapshot.load(repo_root / "src/main/resources/ontology/legal-ontology-slim.json")

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            config_path = tmp_path / "generator.yaml"
            config_path.write_text(
                "\n".join([
                    "seed: 123",
                    "specialists_per_l3: 1",
                    "adjacent_per_l3: 0",
                    "cross_domain_count: 1",
                    "hard_negative_count: 1",
                    "max_l3_nodes: 3",
                ]),
                encoding="utf-8",
            )
            first_path = tmp_path / "lawyers-first.jsonl"
            second_path = tmp_path / "lawyers-second.jsonl"

            SyntheticLawyerCorpusGenerator().generate(config_path, ontology, first_path)
            SyntheticLawyerCorpusGenerator().generate(config_path, ontology, second_path)

            first = read_jsonl(first_path)
            second = read_jsonl(second_path)
            self.assertEqual(first, second)
            self.assertTrue(all(row["lawyer_id"].startswith("L-") for row in first))
            covered = {
                node_id
                for row in first
                for node_id in row["practice_node_ids"]
            }
            expected_l3 = ["law-001-01-01", "law-001-01-02", "law-001-01-03"]
            self.assertTrue(set(expected_l3).issubset(covered))
            first_specialist = next(row for row in first if row["practice_node_ids"] == ["law-001-01-01"])
            self.assertEqual(first_specialist["domains"], ["부동산 거래"])
            self.assertEqual(first_specialist["sub_domains"], ["부동산 매매"])
            self.assertIn("계약 체결 및 효력", first_specialist["tags"])
            self.assertIn("[전문 분야]\n부동산 거래. 부동산 거래. 부동산 거래", first_specialist["embedding_text"])


if __name__ == "__main__":
    unittest.main()
