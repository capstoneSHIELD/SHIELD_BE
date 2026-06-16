from __future__ import annotations

import sys
import unittest
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shield_experiment.client import ExperimentClient, IntentRouteRequest
from shield_experiment.models import ClassificationTurn, Message


class ExperimentClientPayloadTest(unittest.TestCase):
    def test_intent_route_forwards_wrong_selected_labels(self) -> None:
        client = CapturingClient("http://localhost:8080")
        turn = ClassificationTurn(
            id="WRONG-X1-001-T02",
            case_id="WRONG-X1-001",
            conversation_id="WRONG-X1-001",
            turn_index=2,
            is_final_turn=False,
            benchmark_split="test",
            group="wrong_selected_cross_l1",
            messages=[Message("USER", "전세 보증금을 돌려받지 못했습니다.")],
            gold_node_ids=["law-007-01-05"],
            selected_node_ids=["law-002-04-02", "law-004-02-01"],
            selected_labels=[
                {"nodeId": "law-002-04-02", "l1": "이혼·위자료·재산분할"},
                {"nodeId": "law-004-02-01", "l1": "근로계약·해고·임금"},
            ],
        )

        client.intent_route(IntentRouteRequest("cohere", "A_FULL", None, turn))

        self.assertEqual(client.last_path, "/internal/experiments/intent-route")
        self.assertEqual(client.last_payload["selectedNodeIds"], ["law-002-04-02", "law-004-02-01"])
        self.assertEqual(client.last_payload["selectedLabels"][0]["nodeId"], "law-002-04-02")

    def test_client_forwards_experiment_access_token_header(self) -> None:
        client = CapturingClient("http://localhost:8080", experiment_access_token="secret")

        client.preflight_providers(["openai"])

        self.assertEqual(client.last_headers["X-SHIELD-EXPERIMENT-TOKEN"], "secret")


class CapturingClient(ExperimentClient):
    def __init__(self, base_url: str, experiment_access_token: str | None = None):
        super().__init__(base_url, dry_run=False, experiment_access_token=experiment_access_token)
        self.last_path: str | None = None
        self.last_payload: dict[str, Any] = {}
        self.last_headers: dict[str, str] = {}

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        self.last_path = path
        self.last_payload = payload
        if self.experiment_access_token:
            self.last_headers["X-SHIELD-EXPERIMENT-TOKEN"] = self.experiment_access_token
        return {
            "provider": payload.get("provider", "openai"),
            "requestedProvider": payload.get("provider", "openai"),
            "mode": payload.get("mode", "A_FULL"),
            "inputDomain": payload.get("domain"),
            "parsed": {"matchedNodeIds": ["law-007-01-05"]},
            "parseSuccess": True,
            "schemaSuccess": True,
            "fallbackUsed": False,
        }


if __name__ == "__main__":
    unittest.main()
