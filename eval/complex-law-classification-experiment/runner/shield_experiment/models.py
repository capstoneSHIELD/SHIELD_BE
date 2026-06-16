from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


JsonObject = dict[str, Any]


@dataclass(frozen=True)
class Message:
    role: str
    content: str

    @staticmethod
    def from_json(data: JsonObject) -> "Message":
        return Message(role=str(data.get("role", "")), content=str(data.get("content", "")))

    def to_json(self) -> JsonObject:
        return {"role": self.role, "content": self.content}


@dataclass(frozen=True)
class ClassificationTurn:
    id: str
    case_id: str
    conversation_id: str
    turn_index: int
    is_final_turn: bool
    benchmark_split: str
    group: str
    messages: list[Message]
    gold_node_ids: list[str]
    gold_primary_node_id: str | None = None
    expected_complex: bool = False
    matching_label_set_id: str | None = None
    selected_node_ids: list[str] = field(default_factory=list)
    selected_labels: list[JsonObject] = field(default_factory=list)
    evaluation_target: bool = True

    @staticmethod
    def from_json(data: JsonObject) -> "ClassificationTurn":
        case_id = str(data.get("case_id") or data.get("id"))
        turn_index = int(data.get("turn_index", 1))
        row_id = str(data.get("id") or f"{case_id}-T{turn_index:02d}")
        return ClassificationTurn(
            id=row_id,
            case_id=case_id,
            conversation_id=str(data.get("conversation_id") or case_id),
            turn_index=turn_index,
            is_final_turn=bool(data.get("is_final_turn", True)),
            benchmark_split=str(data.get("benchmark_split", "test")),
            group=str(data.get("group", "")),
            messages=[Message.from_json(m) for m in data.get("messages", [])],
            gold_node_ids=[str(x) for x in data.get("gold_node_ids", [])],
            gold_primary_node_id=data.get("gold_primary_node_id"),
            expected_complex=bool(data.get("expected_complex", False)),
            matching_label_set_id=data.get("matching_label_set_id"),
            selected_node_ids=[str(x) for x in data.get("selected_node_ids", [])],
            selected_labels=[
                dict(label)
                for label in data.get("selected_labels", [])
                if isinstance(label, dict)
            ],
            evaluation_target=bool(data.get("evaluation_target", True)),
        )

    def to_json(self) -> JsonObject:
        return {
            "id": self.id,
            "case_id": self.case_id,
            "conversation_id": self.conversation_id,
            "turn_index": self.turn_index,
            "is_final_turn": self.is_final_turn,
            "benchmark_split": self.benchmark_split,
            "group": self.group,
            "messages": [m.to_json() for m in self.messages],
            "gold_node_ids": self.gold_node_ids,
            "gold_primary_node_id": self.gold_primary_node_id,
            "expected_complex": self.expected_complex,
            "matching_label_set_id": self.matching_label_set_id,
            "selected_node_ids": self.selected_node_ids,
            "selected_labels": self.selected_labels,
            "evaluation_target": self.evaluation_target,
        }


@dataclass(frozen=True)
class ClassificationResult:
    turn_id: str
    case_id: str
    conversation_id: str
    turn_index: int
    is_final_turn: bool
    benchmark_split: str
    group: str
    provider: str
    requested_provider: str
    mode: str
    input_domain: str | None
    gold_node_ids: list[str]
    gold_primary_node_id: str | None
    expected_complex: bool
    pred_node_ids: list[str]
    selected_node_ids: list[str] = field(default_factory=list)
    selected_labels: list[JsonObject] = field(default_factory=list)
    evaluation_target: bool = True
    raw: JsonObject = field(default_factory=dict)
    parse_success: bool = True
    schema_success: bool = True
    fallback_used: bool = False
    error_type: str | None = None
    error_message: str | None = None
    latency_ms: int | None = None
    tokens_in: int | None = None
    tokens_out: int | None = None

    def to_json(self) -> JsonObject:
        return {
            "turn_id": self.turn_id,
            "case_id": self.case_id,
            "conversation_id": self.conversation_id,
            "turn_index": self.turn_index,
            "is_final_turn": self.is_final_turn,
            "benchmark_split": self.benchmark_split,
            "group": self.group,
            "provider": self.provider,
            "requested_provider": self.requested_provider,
            "mode": self.mode,
            "input_domain": self.input_domain,
            "gold_node_ids": self.gold_node_ids,
            "gold_primary_node_id": self.gold_primary_node_id,
            "expected_complex": self.expected_complex,
            "pred_node_ids": self.pred_node_ids,
            "selected_node_ids": self.selected_node_ids,
            "selected_labels": self.selected_labels,
            "evaluation_target": self.evaluation_target,
            "parse_success": self.parse_success,
            "schema_success": self.schema_success,
            "fallback_used": self.fallback_used,
            "error_type": self.error_type,
            "error_message": self.error_message,
            "latency_ms": self.latency_ms,
            "tokens_in": self.tokens_in,
            "tokens_out": self.tokens_out,
            "raw": self.raw,
        }


@dataclass(frozen=True)
class LawyerFixture:
    lawyer_id: str
    practice_node_ids: list[str]
    tags: list[str]
    embedding_text: str = ""

    @staticmethod
    def from_json(data: JsonObject) -> "LawyerFixture":
        practice = list(data.get("practice_node_ids", []))
        secondary = list(data.get("secondary_node_ids", []))
        return LawyerFixture(
            lawyer_id=str(data.get("lawyer_id", "")),
            practice_node_ids=[str(x) for x in [*practice, *secondary]],
            tags=[str(x) for x in data.get("tags", [])],
            embedding_text=str(data.get("embedding_text", "")),
        )


@dataclass(frozen=True)
class MatchingLabelSet:
    label_set_id: str
    case_id: str
    relevance: dict[str, int]

    @staticmethod
    def from_json(data: JsonObject) -> "MatchingLabelSet":
        relevance = {
            str(item.get("lawyer_id")): int(item.get("grade", 0))
            for item in data.get("relevance", [])
        }
        return MatchingLabelSet(
            label_set_id=str(data.get("label_set_id", "")),
            case_id=str(data.get("case_id", "")),
            relevance=relevance,
        )

    def grade_of(self, lawyer_id: str) -> int:
        return self.relevance.get(lawyer_id, 0)

    def has_label(self, lawyer_id: str) -> bool:
        return lawyer_id in self.relevance


@dataclass(frozen=True)
class MatchingResult:
    case_id: str
    provider: str
    classification_mode: str
    matching_mode: str
    label_source: str
    input_node_ids: list[str]
    ranked_lawyers: list[JsonObject]
    top_k: int
    current_service_compatible: bool = False
    query_text_hash: str | None = None
    latency_ms: int | None = None
    error_type: str | None = None
    error_message: str | None = None

    def to_json(self) -> JsonObject:
        return {
            "case_id": self.case_id,
            "provider": self.provider,
            "classification_mode": self.classification_mode,
            "matching_mode": self.matching_mode,
            "label_source": self.label_source,
            "input_node_ids": self.input_node_ids,
            "ranked_lawyers": self.ranked_lawyers,
            "top_k": self.top_k,
            "current_service_compatible": self.current_service_compatible,
            "query_text_hash": self.query_text_hash,
            "latency_ms": self.latency_ms,
            "error_type": self.error_type,
            "error_message": self.error_message,
        }
