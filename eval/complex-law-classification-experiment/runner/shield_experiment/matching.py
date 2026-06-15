from __future__ import annotations

from abc import ABC, abstractmethod

from .client import ExperimentClient
from .models import ClassificationResult, ClassificationTurn, MatchingResult
from .ontology import OntologyMapper


class CurrentServiceQueryBuilder:
    def __init__(self, mapper: OntologyMapper):
        self.mapper = mapper

    def build_query(self, turn: ClassificationTurn, node_ids: list[str], label_source: str) -> dict:
        content = "\n".join(message.content for message in turn.messages if message.role.upper() == "USER")
        return {
            "briefContent": content,
            "inputNodeIds": node_ids,
            "labelSource": label_source,
            "domains": [self.mapper.to_l1(node_id) for node_id in node_ids if self.mapper.to_l1(node_id)],
            "subDomains": [self.mapper.to_l2(node_id) for node_id in node_ids if self.mapper.to_l2(node_id)],
            "tags": node_ids,
        }


class HybridMatchScorer:
    def __init__(self, weights: dict[str, float]):
        self.weights = weights

    def score(self, cosine: float, field_overlap: float, keyword_overlap: float) -> float:
        return (
            self.weights.get("cosine", 0.60) * cosine
            + self.weights.get("fieldOverlap", 0.25) * field_overlap
            + self.weights.get("keywordOverlap", 0.15) * keyword_overlap
        )


class MatchingModeStrategy(ABC):
    @property
    @abstractmethod
    def mode_name(self) -> str:
        raise NotImplementedError

    @property
    @abstractmethod
    def label_source(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def input_nodes(self, turn: ClassificationTurn, classification_result: ClassificationResult | None) -> list[str]:
        raise NotImplementedError

    def execute(
        self,
        turn: ClassificationTurn,
        provider: str,
        classification_mode: str,
        classification_result: ClassificationResult | None,
        client: ExperimentClient,
        query_builder: CurrentServiceQueryBuilder,
        top_k: int,
    ) -> MatchingResult:
        nodes = self.input_nodes(turn, classification_result)
        payload = {
            "caseId": turn.case_id,
            "matchingMode": self.mode_name,
            "classificationMode": classification_mode,
            "topK": top_k,
            "query": query_builder.build_query(turn, nodes, self.label_source),
        }
        raw = client.lawyer_match(payload)
        return MatchingResult(
            case_id=turn.case_id,
            provider=provider,
            classification_mode=classification_mode,
            matching_mode=self.mode_name,
            label_source=self.label_source,
            input_node_ids=nodes,
            ranked_lawyers=list(raw.get("results", [])),
            error_type=raw.get("errorType"),
            error_message=raw.get("errorMessage"),
        )


class PredictedLabelsCosineOnly(MatchingModeStrategy):
    @property
    def mode_name(self) -> str:
        return "PREDICTED_LABELS_COSINE_ONLY"

    @property
    def label_source(self) -> str:
        return "predicted"

    def input_nodes(self, turn, classification_result):
        return classification_result.pred_node_ids if classification_result else []


class OracleLabelsCosineOnly(MatchingModeStrategy):
    @property
    def mode_name(self) -> str:
        return "ORACLE_LABELS_COSINE_ONLY"

    @property
    def label_source(self) -> str:
        return "oracle"

    def input_nodes(self, turn, classification_result):
        return turn.gold_node_ids


class PredictedLabelsHybridMatch(PredictedLabelsCosineOnly):
    @property
    def mode_name(self) -> str:
        return "PREDICTED_LABELS_HYBRID_MATCH"


class OracleLabelsHybridMatch(OracleLabelsCosineOnly):
    @property
    def mode_name(self) -> str:
        return "ORACLE_LABELS_HYBRID_MATCH"


class NoLabelCosineOnly(MatchingModeStrategy):
    @property
    def mode_name(self) -> str:
        return "NO_LABEL_COSINE_ONLY"

    @property
    def label_source(self) -> str:
        return "none"

    def input_nodes(self, turn, classification_result):
        return []


class MatchingModeRegistry:
    def __init__(self):
        strategies: list[MatchingModeStrategy] = [
            PredictedLabelsCosineOnly(),
            OracleLabelsCosineOnly(),
            PredictedLabelsHybridMatch(),
            OracleLabelsHybridMatch(),
            NoLabelCosineOnly(),
        ]
        self._strategies = {strategy.mode_name: strategy for strategy in strategies}

    def get(self, mode_name: str) -> MatchingModeStrategy:
        try:
            return self._strategies[mode_name]
        except KeyError as exc:
            raise ValueError(f"Unknown matching mode: {mode_name}") from exc
