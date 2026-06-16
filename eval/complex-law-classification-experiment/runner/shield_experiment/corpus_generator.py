from __future__ import annotations

import json
import random
from pathlib import Path
from typing import Any

from .dataset import write_jsonl
from .ontology import OntologySnapshot


class SyntheticLawyerCorpusGenerator:
    def generate(self, config_path: str | Path, ontology: OntologySnapshot, output_path: str | Path) -> list[dict[str, Any]]:
        config = _load_mapping(Path(config_path))
        seed = int(config.get("seed", 20260615))
        rng = random.Random(seed)
        l3_nodes = _l3_leaf_nodes(ontology)
        max_l3_nodes = int(config.get("max_l3_nodes", len(l3_nodes)))
        l3_nodes = l3_nodes[:max_l3_nodes]
        rows: list[dict[str, Any]] = []

        for node_id in l3_nodes:
            for index in range(int(config.get("specialists_per_l3", 1))):
                rows.append(_lawyer_row("SP", node_id, [node_id], ontology, index))
            for index in range(int(config.get("adjacent_per_l3", 1))):
                adjacent = _adjacent_l3(node_id, l3_nodes) or node_id
                rows.append(_lawyer_row("AD", node_id, [adjacent], ontology, index))

        for index in range(int(config.get("cross_domain_count", 0))):
            left, right = _different_l1_pair(rng, l3_nodes)
            rows.append(_lawyer_row("CD", left, [left, right], ontology, index))

        for index in range(int(config.get("hard_negative_count", 0))):
            target = rng.choice(l3_nodes)
            negative = _same_l1_different_l2(rng, target, l3_nodes) or rng.choice(l3_nodes)
            rows.append(_lawyer_row("HN", target, [negative], ontology, index, misleading_tag=target))

        write_jsonl(output_path, rows)
        return rows


def _lawyer_row(
    kind: str,
    anchor_node_id: str,
    practice_node_ids: list[str],
    ontology: OntologySnapshot,
    index: int,
    misleading_tag: str | None = None,
) -> dict[str, Any]:
    primary = practice_node_ids[0] if practice_node_ids else anchor_node_id
    l1_values = sorted({
        name
        for node_id in practice_node_ids
        if (name := _path_name_at(ontology, node_id, 0))
    })
    l2_values = sorted({
        name
        for node_id in practice_node_ids
        if (name := _path_name_at(ontology, node_id, 1))
    })
    tags = [
        *[
            name
            for node_id in practice_node_ids
            if (name := _path_name_at(ontology, node_id, 2) or _name_of(ontology, node_id))
        ],
        *(misleading_tag and [_path_name_at(ontology, misleading_tag, 2) or misleading_tag] or []),
        f"synthetic-{kind.lower()}",
    ]
    bio = (
        f"Synthetic {kind} profile for {_name_of(ontology, anchor_node_id) or anchor_node_id}. "
        "This is generated benchmark data and does not describe a real person."
    )
    lawyer_id = f"L-{anchor_node_id.replace('law-', '')}-{kind}-{index + 1:03d}"
    return {
        "lawyer_id": lawyer_id,
        "display_name": f"Synthetic Lawyer {lawyer_id}",
        "bar_number_fake": f"TEST-2026-{lawyer_id.replace('-', '')}",
        "verification_status": "VERIFIED",
        "practice_node_ids": practice_node_ids,
        "primary_node_id": primary,
        "secondary_node_ids": practice_node_ids[1:],
        "domains": l1_values,
        "sub_domains": l2_values,
        "tags": tags,
        "experience_years": 3 + (index % 12),
        "case_count": 20 + index,
        "region": "synthetic-region",
        "bio": bio,
        "embedding_text": _embedding_text(l1_values, l2_values, tags, bio),
        "synthetic_profile_type": kind,
    }


def _embedding_text(domains: list[str], sub_domains: list[str], tags: list[str], bio: str) -> str:
    sections: list[str] = []
    _append_repeated_section(sections, "[전문 분야]", domains, 3)
    _append_repeated_section(sections, "[세부 분야]", sub_domains, 2)
    _append_repeated_section(sections, "[태그]", tags, 1)
    if bio.strip():
        sections.append("[자기소개]\n" + bio.strip())
    return "\n".join(sections)


def _append_repeated_section(sections: list[str], header: str, values: list[str], repeat: int) -> None:
    cleaned = [value.strip() for value in values if value and value.strip()]
    if not cleaned:
        return
    joined = ". ".join(cleaned)
    sections.append(header + "\n" + ". ".join(joined for _ in range(repeat)))


def _l3_leaf_nodes(ontology: OntologySnapshot) -> list[str]:
    parents = {node.parent_id for node in ontology.nodes.values() if node.parent_id}
    return sorted(
        node_id
        for node_id in ontology.nodes
        if node_id.startswith("law-") and node_id not in parents and len(node_id.split("-")) >= 4
    )


def _adjacent_l3(node_id: str, nodes: list[str]) -> str | None:
    l2 = _l2(node_id)
    for candidate in nodes:
        if candidate != node_id and _l2(candidate) == l2:
            return candidate
    return None


def _different_l1_pair(rng: random.Random, nodes: list[str]) -> tuple[str, str]:
    left = rng.choice(nodes)
    candidates = [node_id for node_id in nodes if _l1(node_id) != _l1(left)]
    right = rng.choice(candidates or nodes)
    return left, right


def _same_l1_different_l2(rng: random.Random, node_id: str, nodes: list[str]) -> str | None:
    candidates = [
        candidate
        for candidate in nodes
        if _l1(candidate) == _l1(node_id) and _l2(candidate) != _l2(node_id)
    ]
    return rng.choice(candidates) if candidates else None


def _l1(node_id: str) -> str | None:
    parts = node_id.split("-")
    return "-".join(parts[:2]) if len(parts) >= 2 else None


def _l2(node_id: str) -> str | None:
    parts = node_id.split("-")
    return "-".join(parts[:3]) if len(parts) >= 3 else None


def _name_of(ontology: OntologySnapshot, node_id: str | None) -> str | None:
    if not node_id:
        return None
    node = ontology.nodes.get(node_id)
    return node.name if node else None


def _path_name_at(ontology: OntologySnapshot, node_id: str | None, index: int) -> str | None:
    if not node_id:
        return None
    ids: list[str] = []
    current = node_id
    while current and current in ontology.nodes:
        ids.append(current)
        current = ontology.parent_of(current)
    names = [
        ontology.nodes[path_id].name
        for path_id in reversed(ids)
        if path_id in ontology.nodes and ontology.nodes[path_id].name != "법률"
    ]
    return names[index] if len(names) > index else None


def _load_mapping(path: Path) -> dict[str, Any]:
    raw = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        return json.loads(raw)
    if path.suffix.lower() in {".yaml", ".yml"}:
        try:
            import yaml  # type: ignore
        except ImportError:
            return _load_simple_yaml(raw)
        return yaml.safe_load(raw) or {}
    raise ValueError(f"Unsupported corpus generator config file: {path}")


def _load_simple_yaml(raw: str) -> dict[str, Any]:
    data: dict[str, Any] = {}
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        value = value.strip().strip("\"'")
        if value.lower() in {"true", "false"}:
            data[key.strip()] = value.lower() == "true"
        else:
            try:
                data[key.strip()] = int(value)
            except ValueError:
                data[key.strip()] = value
    return data
