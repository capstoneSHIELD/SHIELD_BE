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

    def intent_route(self, request: IntentRouteRequest) -> dict[str, Any]:
        if self.dry_run:
            return {
                "provider": request.provider,
                "requestedProvider": request.provider,
                "mode": request.mode,
                "inputDomain": request.domain,
                "parsed": {
                    "matchedNodeIds": [],
                    "dialogueIntent": "ASK_LEGAL_ADVICE",
                    "intentConfidence": 0.0,
                    "caseType": {"confidence": 0.0},
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
        return self._post("/internal/experiments/intent-route", payload)

    def lawyer_match(self, payload: dict[str, Any]) -> dict[str, Any]:
        if self.dry_run:
            return {
                "caseId": payload.get("caseId"),
                "matchingMode": payload.get("matchingMode"),
                "results": [],
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
