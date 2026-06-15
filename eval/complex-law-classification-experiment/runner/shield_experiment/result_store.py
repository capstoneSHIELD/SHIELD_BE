from __future__ import annotations

import json
from pathlib import Path

from .models import ClassificationResult, MatchingResult


class JsonlResultSink:
    def __init__(self, run_dir: Path):
        self.run_dir = run_dir
        self.raw_dir = run_dir / "raw"
        self.parsed_dir = run_dir / "parsed"
        self.matching_dir = run_dir / "matching"
        self.run_dir.mkdir(parents=True, exist_ok=True)
        for directory in [self.raw_dir, self.parsed_dir, self.matching_dir, run_dir / "reports"]:
            directory.mkdir(exist_ok=True)

    def write_run_meta(self, meta: dict) -> None:
        (self.run_dir / "run-meta.json").write_text(
            json.dumps(meta, ensure_ascii=False, indent=2, sort_keys=True),
            encoding="utf-8",
        )

    def write_classification_results(self, provider: str, mode: str, rows: list[ClassificationResult]) -> None:
        self._write_jsonl(self.parsed_dir / f"{provider}_{mode}.parsed.jsonl", [row.to_json() for row in rows])

    def write_matching_results(self, mode: str, rows: list[MatchingResult]) -> None:
        self._write_jsonl(self.matching_dir / f"{mode.lower()}.jsonl", [row.to_json() for row in rows])

    @staticmethod
    def _write_jsonl(path: Path, rows: list[dict]) -> None:
        path.write_text(
            "\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in rows)
            + ("\n" if rows else ""),
            encoding="utf-8",
        )
