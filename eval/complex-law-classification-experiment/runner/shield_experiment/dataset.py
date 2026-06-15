from __future__ import annotations

import json
from pathlib import Path

from .models import ClassificationTurn, LawyerFixture, MatchingLabelSet


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
    def load_lawyers(self, path: str | Path | None) -> list[LawyerFixture]:
        if path is None:
            return []
        return [LawyerFixture.from_json(row) for row in read_jsonl(path)]

    def load_matching_labels(self, path: str | Path | None) -> dict[str, MatchingLabelSet]:
        if path is None:
            return {}
        labels = [MatchingLabelSet.from_json(row) for row in read_jsonl(path)]
        return {label.case_id: label for label in labels}


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
