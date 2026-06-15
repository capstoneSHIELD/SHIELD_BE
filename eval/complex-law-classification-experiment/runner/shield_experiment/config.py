from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class ExperimentConfig:
    dataset_path: Path
    classification_turns_path: Path
    ontology_snapshot_path: Path
    lawyer_corpus_path: Path | None
    matching_labels_path: Path | None
    output_root: Path
    base_url: str
    providers: list[str] = field(default_factory=lambda: ["cohere"])
    classification_modes: list[str] = field(default_factory=lambda: ["A_FULL"])
    matching_modes: list[str] = field(default_factory=list)
    selected_classification_mode: str = "C_HYBRID_RUNTIME"
    selected_provider: str = "cohere"
    dry_run: bool = False
    top_k: int = 10
    production_group_weights: dict[str, float] = field(default_factory=dict)
    hybrid_match_weights: dict[str, float] = field(default_factory=lambda: {
        "cosine": 0.60,
        "fieldOverlap": 0.25,
        "keywordOverlap": 0.15,
    })

    @staticmethod
    def from_file(path: str | Path) -> "ExperimentConfig":
        config_path = Path(path)
        data = _load_config(config_path)
        base = config_path.parent

        def resolve(key: str, default: str | None = None) -> Path | None:
            value = data.get(key, default)
            if value is None:
                return None
            p = Path(str(value))
            return p if p.is_absolute() else (base / p).resolve()

        return ExperimentConfig(
            dataset_path=resolve("dataset_path", "../input/dataset-v1.jsonl"),
            classification_turns_path=resolve(
                "classification_turns_path", "../input/classification-turns-v1.jsonl"
            ),
            ontology_snapshot_path=resolve(
                "ontology_snapshot_path", "../input/legal-ontology-slim.snapshot.json"
            ),
            lawyer_corpus_path=resolve("lawyer_corpus_path", "../input/lawyers-v1.jsonl"),
            matching_labels_path=resolve("matching_labels_path", "../input/matching-labels-v1.jsonl"),
            output_root=resolve("output_root", "../output"),
            base_url=str(data.get("base_url", "http://localhost:8080")),
            providers=list(data.get("providers", ["cohere"])),
            classification_modes=list(data.get("classification_modes", ["A_FULL"])),
            matching_modes=list(data.get("matching_modes", [])),
            selected_classification_mode=str(data.get("selected_classification_mode", "C_HYBRID_RUNTIME")),
            selected_provider=str(data.get("selected_provider", "cohere")),
            dry_run=bool(data.get("dry_run", False)),
            top_k=int(data.get("top_k", 10)),
            production_group_weights=dict(data.get("production_group_weights", {})),
            hybrid_match_weights=dict(data.get("hybrid_match_weights", {
                "cosine": 0.60,
                "fieldOverlap": 0.25,
                "keywordOverlap": 0.15,
            })),
        )


def _load_config(path: Path) -> dict[str, Any]:
    raw = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        return json.loads(raw)
    if path.suffix.lower() in {".yaml", ".yml"}:
        try:
            import yaml  # type: ignore
        except ImportError as exc:
            raise RuntimeError(
                "YAML config requires PyYAML. Use config.example.json or install PyYAML."
            ) from exc
        loaded = yaml.safe_load(raw)
        return loaded or {}
    raise ValueError(f"Unsupported config file: {path}")
