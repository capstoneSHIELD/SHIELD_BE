from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.matching import CurrentServiceQueryBuilder
from shield_experiment.models import ClassificationTurn, Message
from shield_experiment.ontology import OntologyMapper, OntologySnapshot


class CurrentServiceQueryBuilderTest(unittest.TestCase):
    def test_query_text_mirrors_lawyer_embedding_text_builder_template(self) -> None:
        repo_root = Path(__file__).resolve().parents[4]
        mapper = OntologyMapper(
            OntologySnapshot.load(repo_root / "src/main/resources/ontology/legal-ontology-slim.json")
        )
        turn = ClassificationTurn(
            id="T1",
            case_id="CASE-1",
            conversation_id="CASE-1",
            turn_index=1,
            is_final_turn=True,
            benchmark_split="test",
            group="single",
            messages=[Message("USER", "보증금을 돌려받지 못했습니다.")],
            gold_node_ids=["law-007-01-05", "law-007-03-03"],
        )

        query = CurrentServiceQueryBuilder(mapper).build_query(
            turn,
            ["law-007-01-05", "law-007-03-03"],
            "oracle",
        )

        self.assertEqual(query["domains"], ["임대차보호"])
        self.assertEqual(query["subDomains"], ["주택임대차보호", "임차인 보호 절차"])
        self.assertEqual(query["tags"], ["보증금 반환 및 회수", "보증금 반환 소송"])
        self.assertIn("[전문 분야]\n임대차보호. 임대차보호. 임대차보호", query["queryText"])
        self.assertIn(
            "[세부 분야]\n주택임대차보호. 임차인 보호 절차. 주택임대차보호. 임차인 보호 절차",
            query["queryText"],
        )
        self.assertIn("[자기소개]\n보증금을 돌려받지 못했습니다.", query["queryText"])
        self.assertEqual(
            query["queryTextHash"],
            CurrentServiceQueryBuilder.hash_query_text(query["queryText"]),
        )


if __name__ == "__main__":
    unittest.main()
