from __future__ import annotations

import hashlib
from abc import ABC, abstractmethod
from typing import Any

from .client import ExperimentClient
from .models import ClassificationResult, ClassificationTurn, MatchingResult
from .ontology import OntologyMapper


class CurrentServiceQueryBuilder:
    def __init__(self, mapper: OntologyMapper):
        self.mapper = mapper

    def build_query(self, turn: ClassificationTurn, node_ids: list[str], label_source: str) -> dict:
        content = "\n".join(message.content for message in turn.messages if message.role.upper() == "USER")
        domains = [self.mapper.to_l1(node_id) for node_id in node_ids if self.mapper.to_l1(node_id)]
        sub_domains = [self.mapper.to_l2(node_id) for node_id in node_ids if self.mapper.to_l2(node_id)]
        query_text = self._build_query_text(content, domains, sub_domains, node_ids)
        return {
            "briefContent": content,
            "inputNodeIds": node_ids,
            "labelSource": label_source,
            "domains": domains,
            "subDomains": sub_domains,
            "tags": node_ids,
            "queryText": query_text,
            "queryTextHash": self.hash_query_text(query_text),
        }

    @staticmethod
    def hash_query_text(query_text: str) -> str:
        digest = hashlib.sha256(query_text.encode("utf-8")).hexdigest()
        return f"sha256:{digest}"

    @staticmethod
    def _build_query_text(content: str, domains: list[str], sub_domains: list[str], tags: list[str]) -> str:
        sections: list[str] = []
        _append_repeated_section(sections, "[전문 분야]", domains, 3)
        _append_repeated_section(sections, "[세부 분야]", sub_domains, 2)
        _append_repeated_section(sections, "[태그]", tags, 1)
        if content and content.strip():
            sections.append("[자기소개]\n" + content.strip())
        return "\n".join(sections)


class HybridMatchScorer:
    def __init__(self, weights: dict[str, float], mapper: OntologyMapper):
        self.weights = weights
        self.mapper = mapper

    def score(self, cosine: float, field_overlap: float, keyword_overlap: float) -> float:
        return (
            self.weights.get("cosine", 0.60) * cosine
            + self.weights.get("fieldOverlap", 0.25) * field_overlap
            + self.weights.get("keywordOverlap", 0.15) * keyword_overlap
        )

    def field_overlap(self, case_nodes: list[str], lawyer_nodes: list[str]) -> float:
        if not case_nodes or not lawyer_nodes:
            return 0.0
        return max(
            self.mapper.hierarchy_score(lawyer_node, case_node)
            for case_node in case_nodes
            for lawyer_node in lawyer_nodes
        )

    def keyword_overlap(self, case_keywords: list[str], lawyer_keywords: list[str]) -> float:
        case_set = {keyword for keyword in case_keywords if keyword}
        lawyer_set = {keyword for keyword in lawyer_keywords if keyword}
        if not case_set or not lawyer_set:
            return 0.0
        return len(case_set & lawyer_set) / min(len(case_set), len(lawyer_set))


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

    @property
    def current_service_compatible(self) -> bool:
        return True

    def gateway_mode(self) -> str:
        return self.mode_name

    def execute(
        self,
        turn: ClassificationTurn,
        provider: str,
        classification_mode: str,
        classification_result: ClassificationResult | None,
        client: ExperimentClient,
        query_builder: CurrentServiceQueryBuilder,
        top_k: int,
        mapper: OntologyMapper,
        hybrid_match_weights: dict[str, float],
    ) -> MatchingResult:
        nodes = self.input_nodes(turn, classification_result)
        query = query_builder.build_query(turn, nodes, self.label_source)
        payload = {
            "caseId": turn.case_id,
            "matchingMode": self.gateway_mode(),
            "classificationMode": classification_mode,
            "topK": top_k,
            "query": query,
        }
        raw = client.lawyer_match(payload)
        ranked_lawyers = self.normalize_ranked_lawyers(raw.get("results", []))
        if not self.current_service_compatible:
            ranked_lawyers = self.rerank_hybrid(
                ranked_lawyers,
                nodes,
                query,
                mapper,
                hybrid_match_weights,
            )
        return MatchingResult(
            case_id=turn.case_id,
            provider=provider,
            classification_mode=classification_mode,
            matching_mode=self.mode_name,
            label_source=self.label_source,
            input_node_ids=nodes,
            ranked_lawyers=ranked_lawyers[:top_k],
            top_k=top_k,
            current_service_compatible=bool(raw.get("currentServiceCompatible", True)) and self.current_service_compatible,
            query_text_hash=query.get("queryTextHash"),
            latency_ms=raw.get("latencyMs"),
            error_type=raw.get("errorType"),
            error_message=raw.get("errorMessage"),
        )

    def normalize_ranked_lawyers(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        normalized: list[dict[str, Any]] = []
        for index, row in enumerate(rows, start=1):
            item = dict(row)
            item["rank"] = int(item.get("rank") or index)
            item["lawyer_id"] = str(item.get("lawyer_id") or item.get("lawyerId") or "")
            item["practice_node_ids"] = list(item.get("practice_node_ids") or item.get("practiceNodeIds") or [])
            item["tags"] = list(item.get("tags") or item.get("matchedKeywords") or item.get("matched_keywords") or [])
            item["score"] = _float_or_zero(item.get("score"))
            item["score_components"] = _normalize_score_components(item.get("score_components") or item.get("scoreComponents"))
            if item["score_components"].get("cosine") is None:
                item["score_components"]["cosine"] = item["score"]
            normalized.append(item)
        return normalized

    def rerank_hybrid(
        self,
        rows: list[dict[str, Any]],
        case_nodes: list[str],
        query: dict[str, Any],
        mapper: OntologyMapper,
        hybrid_match_weights: dict[str, float],
    ) -> list[dict[str, Any]]:
        scorer = HybridMatchScorer(hybrid_match_weights, mapper)
        case_keywords = [*case_nodes, *list(query.get("tags", []))]
        reranked: list[dict[str, Any]] = []
        for row in rows:
            components = dict(row.get("score_components") or {})
            cosine = _float_or_zero(components.get("cosine"))
            field_overlap = scorer.field_overlap(case_nodes, list(row.get("practice_node_ids", [])))
            keyword_overlap = scorer.keyword_overlap(case_keywords, [
                *list(row.get("tags", [])),
                *list(row.get("practice_node_ids", [])),
            ])
            hybrid_score = scorer.score(cosine, field_overlap, keyword_overlap)
            updated = dict(row)
            updated["score"] = hybrid_score
            updated["score_components"] = {
                **components,
                "cosine": cosine,
                "field_overlap": field_overlap,
                "keyword_overlap": keyword_overlap,
                "hybrid_score": hybrid_score,
            }
            reranked.append(updated)
        reranked.sort(key=lambda item: item.get("score", 0.0), reverse=True)
        for index, row in enumerate(reranked, start=1):
            row["rank"] = index
        return reranked


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

    @property
    def current_service_compatible(self) -> bool:
        return False

    def gateway_mode(self) -> str:
        return "PREDICTED_LABELS_COSINE_ONLY"


class OracleLabelsHybridMatch(OracleLabelsCosineOnly):
    @property
    def mode_name(self) -> str:
        return "ORACLE_LABELS_HYBRID_MATCH"

    @property
    def current_service_compatible(self) -> bool:
        return False

    def gateway_mode(self) -> str:
        return "ORACLE_LABELS_COSINE_ONLY"


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


def _float_or_zero(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _normalize_score_components(value: Any) -> dict[str, float | None]:
    if not isinstance(value, dict):
        return {"cosine": None, "field_overlap": None, "keyword_overlap": None, "hybrid_score": None}
    return {
        "cosine": _float_or_none(value.get("cosine")),
        "field_overlap": _float_or_none(value.get("field_overlap", value.get("fieldOverlap"))),
        "keyword_overlap": _float_or_none(value.get("keyword_overlap", value.get("keywordOverlap"))),
        "hybrid_score": _float_or_none(value.get("hybrid_score", value.get("hybridScore"))),
    }


def _float_or_none(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _append_repeated_section(sections: list[str], header: str, values: list[str], repeat: int) -> None:
    cleaned = [value.strip() for value in values if value and value.strip()]
    if not cleaned:
        return
    joined = ". ".join(cleaned)
    sections.append(header + "\n" + ". ".join(joined for _ in range(repeat)))
