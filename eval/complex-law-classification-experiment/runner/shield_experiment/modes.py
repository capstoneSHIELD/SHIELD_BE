from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from .client import ExperimentClient, IntentRouteRequest
from .models import ClassificationResult, ClassificationTurn
from .ontology import OntologyMapper


class ClassificationModeStrategy(ABC):
    @property
    @abstractmethod
    def mode_name(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def execute(
        self,
        turn: ClassificationTurn,
        provider: str,
        client: ExperimentClient,
        mapper: OntologyMapper,
        previous_results: dict[tuple[str, str, str], ClassificationResult],
    ) -> ClassificationResult:
        raise NotImplementedError

    def _call(
        self,
        turn: ClassificationTurn,
        provider: str,
        client: ExperimentClient,
        domain: str | None,
    ) -> ClassificationResult:
        raw = client.intent_route(IntentRouteRequest(provider, self.mode_name, domain, turn))
        parsed = raw.get("parsed") or {}
        pred_node_ids = _extract_node_ids(parsed)
        return ClassificationResult(
            turn_id=turn.id,
            case_id=turn.case_id,
            provider=str(raw.get("provider") or provider),
            requested_provider=str(raw.get("requestedProvider") or provider),
            mode=self.mode_name,
            input_domain=raw.get("inputDomain", domain),
            gold_node_ids=turn.gold_node_ids,
            pred_node_ids=pred_node_ids,
            raw=raw,
            parse_success=bool(raw.get("parseSuccess", True)),
            schema_success=bool(raw.get("schemaSuccess", True)),
            fallback_used=bool(raw.get("fallbackUsed", False)),
            error_type=raw.get("errorType"),
            error_message=raw.get("errorMessage"),
            latency_ms=raw.get("latencyMs"),
            tokens_in=raw.get("tokensInput"),
            tokens_out=raw.get("tokensOutput"),
        )


class AFullClassificationStrategy(ClassificationModeStrategy):
    @property
    def mode_name(self) -> str:
        return "A_FULL"

    def execute(self, turn, provider, client, mapper, previous_results):
        return self._call(turn, provider, client, None)


class BScopedGoldStrategy(ClassificationModeStrategy):
    @property
    def mode_name(self) -> str:
        return "B_SCOPED_GOLD"

    def execute(self, turn, provider, client, mapper, previous_results):
        domain = mapper.to_l1(turn.gold_node_ids[0]) if turn.gold_node_ids else None
        return self._call(turn, provider, client, domain)


class RuntimeScopeResolver:
    def resolve(
        self,
        turn: ClassificationTurn,
        provider: str,
        previous_results: dict[tuple[str, str, str], ClassificationResult],
        mapper: OntologyMapper,
    ) -> str | None:
        full_result = previous_results.get((turn.id, provider, "A_FULL"))
        if full_result and full_result.pred_node_ids:
            return mapper.to_l1(full_result.pred_node_ids[0])
        if turn.gold_node_ids:
            return mapper.to_l1(turn.gold_node_ids[0])
        return None


class BScopedRuntimeStrategy(ClassificationModeStrategy):
    def __init__(self, runtime_scope_resolver: RuntimeScopeResolver | None = None):
        self.runtime_scope_resolver = runtime_scope_resolver or RuntimeScopeResolver()

    @property
    def mode_name(self) -> str:
        return "B_SCOPED_RUNTIME"

    def execute(self, turn, provider, client, mapper, previous_results):
        domain = self.runtime_scope_resolver.resolve(turn, provider, previous_results, mapper)
        return self._call(turn, provider, client, domain)


class HybridClassificationPolicy:
    cross_domain_triggers = {
        "가압류", "가처분", "지급명령", "보증인", "손해배상", "위자료",
        "임금", "해고", "상속", "지분", "주식", "영업비밀", "회생", "파산",
    }

    def should_rerun_full(self, scoped_result: ClassificationResult, turn: ClassificationTurn) -> bool:
        if not scoped_result.pred_node_ids:
            return True
        if len(scoped_result.pred_node_ids) == 1 and self._has_cross_domain_trigger(turn):
            return True
        return any(_node_depth(node_id) < 4 for node_id in scoped_result.pred_node_ids)

    def choose(self, scoped_result: ClassificationResult, full_result: ClassificationResult) -> ClassificationResult:
        if len(full_result.pred_node_ids) > len(scoped_result.pred_node_ids):
            return full_result
        return scoped_result

    def _has_cross_domain_trigger(self, turn: ClassificationTurn) -> bool:
        text = " ".join(message.content for message in turn.messages)
        return any(trigger in text for trigger in self.cross_domain_triggers)


class CHybridRuntimeStrategy(ClassificationModeStrategy):
    def __init__(
        self,
        runtime_scope_resolver: RuntimeScopeResolver | None = None,
        policy: HybridClassificationPolicy | None = None,
    ):
        self.runtime_scope_resolver = runtime_scope_resolver or RuntimeScopeResolver()
        self.policy = policy or HybridClassificationPolicy()

    @property
    def mode_name(self) -> str:
        return "C_HYBRID_RUNTIME"

    def execute(self, turn, provider, client, mapper, previous_results):
        domain = self.runtime_scope_resolver.resolve(turn, provider, previous_results, mapper)
        scoped = self._call(turn, provider, client, domain)
        if not self.policy.should_rerun_full(scoped, turn):
            return scoped
        full = AFullClassificationStrategy()._call(turn, provider, client, None)
        chosen = self.policy.choose(scoped, full)
        return ClassificationResult(
            **{**chosen.to_json(), "mode": self.mode_name, "raw": {
                "scoped": scoped.to_json(),
                "full": full.to_json(),
                "chosen": chosen.mode,
            }}
        )


class ClassificationModeRegistry:
    def __init__(self):
        strategies: list[ClassificationModeStrategy] = [
            AFullClassificationStrategy(),
            BScopedGoldStrategy(),
            BScopedRuntimeStrategy(),
            CHybridRuntimeStrategy(),
        ]
        self._strategies = {strategy.mode_name: strategy for strategy in strategies}

    def get(self, mode_name: str) -> ClassificationModeStrategy:
        try:
            return self._strategies[mode_name]
        except KeyError as exc:
            raise ValueError(f"Unknown classification mode: {mode_name}") from exc


def _extract_node_ids(parsed: dict[str, Any]) -> list[str]:
    ids = parsed.get("matchedNodeIds")
    if ids is None:
        ids = parsed.get("matched_node_ids")
    if not ids:
        return []
    return [str(node_id) for node_id in ids if str(node_id)]


def _node_depth(node_id: str) -> int:
    return len(node_id.split("-"))
