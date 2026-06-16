from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from .models import ClassificationTurn


@dataclass(frozen=True)
class IntentRouteRequest:
    provider: str
    mode: str
    domain: str | None
    turn: ClassificationTurn


class ExperimentClient:
    def __init__(self, base_url: str, dry_run: bool = False, timeout_seconds: int = 60):
        self.base_url = base_url.rstrip("/")
        self.dry_run = dry_run
        self.timeout_seconds = timeout_seconds

    def preflight_providers(self, providers: list[str]) -> dict[str, Any]:
        if self.dry_run:
            return {provider: {"available": True, "dry_run": True} for provider in providers}
        return self._post("/internal/experiments/intent-route/preflight", {"providers": providers})

    def upload_lawyer_corpus(self, corpus_id: str, lawyers: list[dict[str, Any]]) -> dict[str, Any]:
        if self.dry_run:
            coverage = {
                node_id
                for lawyer in lawyers
                for node_id in lawyer.get("practice_node_ids", [])
                if node_id
            }
            return {
                "corpusId": corpus_id,
                "acceptedCount": len(lawyers),
                "rejectedCount": 0,
                "rejectedLawyerIds": [],
                "coverageNodeCount": len(coverage),
                "dryRun": True,
            }
        return self._post(
            "/internal/experiments/lawyer-match/corpus",
            {"corpusId": corpus_id, "lawyers": lawyers},
        )

    def preflight_lawyer_match(self, payload: dict[str, Any]) -> dict[str, Any]:
        if self.dry_run:
            required = payload.get("requiredPracticeNodeIds") or []
            return {
                "corpusLoaded": True,
                "lawyerCount": 0,
                "coverageNodeCount": len(set(required)),
                "missingPracticeNodeCount": 0,
                "missingPracticeNodeIds": [],
                "currentServiceCompatible": True,
                "rebuiltQueryTextHash": (payload.get("query") or {}).get("queryTextHash"),
                "suppliedQueryTextHash": (payload.get("query") or {}).get("queryTextHash"),
                "hybridWeightsAccepted": True,
                "hybridMatchWeights": payload.get("hybridMatchWeights", {}),
                "errorType": None,
                "errorMessage": None,
                "dryRun": True,
            }
        return self._post("/internal/experiments/lawyer-match/preflight", payload)

    def intent_route(self, request: IntentRouteRequest) -> dict[str, Any]:
        if self.dry_run:
            pred_node_ids = request.turn.gold_node_ids[:]
            return {
                "provider": request.provider,
                "requestedProvider": request.provider,
                "mode": request.mode,
                "inputDomain": request.domain,
                "parsed": {
                    "matchedNodeIds": pred_node_ids,
                    "dialogueIntent": "ASK_LEGAL_ADVICE",
                    "intentConfidence": 1.0 if pred_node_ids else 0.0,
                    "caseType": {"l1": pred_node_ids[0] if pred_node_ids else None, "confidence": 1.0 if pred_node_ids else 0.0},
                    "retrievalQueries": [],
                },
                "parseSuccess": True,
                "schemaSuccess": True,
                "fallbackUsed": False,
                "errorType": None,
                "errorMessage": None,
            }
        payload = {
            "provider": request.provider,
            "mode": request.mode,
            "domain": request.domain,
            "messages": [message.to_json() for message in request.turn.messages],
            "includeRaw": True,
        }
        if request.turn.selected_node_ids:
            payload["selectedNodeIds"] = request.turn.selected_node_ids
            payload["selectedLabels"] = request.turn.selected_labels
        return self._post("/internal/experiments/intent-route", payload)

    def lawyer_match(self, payload: dict[str, Any]) -> dict[str, Any]:
        if self.dry_run:
            query = payload.get("query") or {}
            node_ids = list(query.get("inputNodeIds") or [])
            primary_node = node_ids[0] if node_ids else "no-label"
            lawyer_id = f"L-{primary_node.replace('law-', '')}-001" if node_ids else "L-CONTENT-ONLY-001"
            return {
                "caseId": payload.get("caseId"),
                "matchingMode": payload.get("matchingMode"),
                "currentServiceCompatible": True,
                "results": [
                    {
                        "rank": 1,
                        "lawyerId": lawyer_id,
                        "practiceNodeIds": node_ids[:1],
                        "tags": node_ids,
                        "score": 0.82 if node_ids else 0.35,
                        "scoreComponents": {
                            "cosine": 0.82 if node_ids else 0.35,
                            "fieldOverlap": None,
                            "keywordOverlap": None,
                            "hybridScore": None,
                        },
                    }
                ],
                "latencyMs": 1,
                "errorType": None,
                "errorMessage": None,
            }
        return self._post("/internal/experiments/lawyer-match", payload)

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        url = self.base_url + path
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_seconds) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} from {url}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Failed to call {url}: {exc}") from exc
