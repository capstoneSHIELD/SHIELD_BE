from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .models import ClassificationTurn, LawyerFixture, MatchingLabelSet
from .ontology import OntologyMapper


def read_jsonl(path: str | Path) -> list[dict]:
    p = Path(path)
    if not p.exists():
        return []
    rows: list[dict] = []
    for line in p.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def write_jsonl(path: str | Path, rows: list[dict]) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(
        "\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in rows) + ("\n" if rows else ""),
        encoding="utf-8",
    )


class DatasetRepository:
    def load_classification_turns(self, path: str | Path, dataset_path: str | Path | None = None) -> list[ClassificationTurn]:
        rows = read_jsonl(path)
        if not rows and dataset_path is not None:
            rows = read_jsonl(dataset_path)
        return [ClassificationTurn.from_json(row) for row in rows]


class LawyerCorpusRepository:
    def load_lawyer_rows(self, path: str | Path | None) -> list[dict[str, Any]]:
        if path is None:
            return []
        return read_jsonl(path)

    def load_lawyers(self, path: str | Path | None) -> list[LawyerFixture]:
        if path is None:
            return []
        return [LawyerFixture.from_json(row) for row in read_jsonl(path)]

    def load_matching_labels(self, path: str | Path | None) -> dict[str, MatchingLabelSet]:
        if path is None:
            return {}
        labels = [MatchingLabelSet.from_json(row) for row in read_jsonl(path)]
        return {label.case_id: label for label in labels}

    def upload_lawyers(self, client, path: str | Path | None, corpus_id: str = "lawyers-v1") -> dict[str, Any]:
        rows = self.load_lawyer_rows(path)
        return client.upload_lawyer_corpus(corpus_id, rows)


class LawyerCorpusValidator:
    def validate(
        self,
        lawyers: list[LawyerFixture],
        labels: dict[str, MatchingLabelSet],
        mapper: OntologyMapper,
        required_node_ids: list[str] | None = None,
    ) -> dict[str, Any]:
        lawyer_ids = {lawyer.lawyer_id for lawyer in lawyers if lawyer.lawyer_id}
        covered_nodes = {
            node_id
            for lawyer in lawyers
            for node_id in lawyer.practice_node_ids
            if node_id
        }
        unknown_nodes = sorted(node_id for node_id in covered_nodes if not mapper.snapshot.exists(node_id))
        referenced_lawyers = {
            lawyer_id
            for label in labels.values()
            for lawyer_id in label.relevance
            if lawyer_id
        }
        missing_label_lawyers = sorted(referenced_lawyers - lawyer_ids)
        required = {node_id for node_id in (required_node_ids or []) if node_id}
        missing_required_nodes = sorted(required - covered_nodes)
        return {
            "lawyer_count": len(lawyers),
            "lawyer_id_count": len(lawyer_ids),
            "coverage_node_count": len(covered_nodes),
            "coverage_by_l1": _count_by(lambda node_id: mapper.to_l1(node_id), covered_nodes),
            "coverage_by_l2": _count_by(lambda node_id: mapper.to_l2(node_id), covered_nodes),
            "unknown_practice_node_ids": unknown_nodes,
            "missing_label_lawyer_ids": missing_label_lawyers,
            "required_practice_node_count": len(required),
            "missing_required_practice_node_ids": missing_required_nodes,
            "valid": not unknown_nodes and not missing_label_lawyers and not missing_required_nodes,
        }


class DatasetBuilder:
    def build_classification_turns(self, case_rows: list[dict]) -> list[ClassificationTurn]:
        turns: list[ClassificationTurn] = []
        for row in case_rows:
            messages = row.get("messages", [])
            user_message_indexes = [
                idx for idx, msg in enumerate(messages)
                if str(msg.get("role", "")).upper() == "USER"
            ]
            if not user_message_indexes:
                turns.append(ClassificationTurn.from_json(row))
                continue
            for ordinal, message_index in enumerate(user_message_indexes, start=1):
                prefix = messages[:message_index + 1]
                turn_row = dict(row)
                case_id = str(row.get("id") or row.get("case_id"))
                turn_row["case_id"] = case_id
                turn_row["id"] = f"{case_id}-T{ordinal:02d}"
                turn_row["turn_index"] = ordinal
                turn_row["is_final_turn"] = ordinal == len(user_message_indexes)
                turn_row["messages"] = prefix[-4:]
                turns.append(ClassificationTurn.from_json(turn_row))
        return turns


def _count_by(key_fn, values: set[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        key = key_fn(value)
        if key:
            counts[key] = counts.get(key, 0) + 1
    return dict(sorted(counts.items()))
