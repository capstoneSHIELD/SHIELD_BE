from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class OntologyNode:
    node_id: str
    name: str
    parent_id: str | None


class OntologySnapshot:
    def __init__(self, nodes: dict[str, OntologyNode]):
        self.nodes = nodes

    @staticmethod
    def load(path: str | Path) -> "OntologySnapshot":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        nodes: dict[str, OntologyNode] = {}

        def visit(raw: dict[str, Any], parent_id: str | None) -> None:
            node_id = str(raw.get("id", ""))
            if node_id:
                nodes[node_id] = OntologyNode(
                    node_id=node_id,
                    name=str(raw.get("name", "")),
                    parent_id=parent_id,
                )
            for child in raw.get("c", []) or raw.get("children", []):
                if isinstance(child, dict):
                    visit(child, node_id or parent_id)

        if isinstance(data, dict):
            visit(data, None)
        return OntologySnapshot(nodes)

    def exists(self, node_id: str) -> bool:
        return node_id in self.nodes

    def parent_of(self, node_id: str) -> str | None:
        node = self.nodes.get(node_id)
        if node:
            return node.parent_id
        parts = node_id.split("-")
        if len(parts) > 2:
            return "-".join(parts[:-1])
        return None


class OntologyMapper:
    def __init__(self, snapshot: OntologySnapshot):
        self.snapshot = snapshot

    def validate(self, node_ids: list[str]) -> list[str]:
        return [node_id for node_id in node_ids if self.snapshot.exists(node_id)]

    def to_l1(self, node_id: str | None) -> str | None:
        if not node_id:
            return None
        parts = node_id.split("-")
        if len(parts) >= 2:
            return "-".join(parts[:2])
        return node_id

    def to_l2(self, node_id: str | None) -> str | None:
        if not node_id:
            return None
        parts = node_id.split("-")
        if len(parts) >= 3:
            return "-".join(parts[:3])
        return node_id

    def hierarchy_score(self, pred: str, gold: str) -> float:
        if pred == gold:
            return 1.0
        if self.to_l2(pred) == self.to_l2(gold):
            return 0.7
        if self.to_l1(pred) == self.to_l1(gold):
            return 0.4
        return 0.0
