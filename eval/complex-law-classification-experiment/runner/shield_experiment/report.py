from __future__ import annotations

from pathlib import Path


class ReportWriter:
    def __init__(self, reports_dir: Path):
        self.reports_dir = reports_dir
        self.reports_dir.mkdir(parents=True, exist_ok=True)

    def write_metrics_summary(self, classification_metrics: dict[str, dict[str, float]]) -> None:
        lines = ["# Classification Metrics Summary", ""]
        for key, metrics in sorted(classification_metrics.items()):
            lines.append(f"## {key}")
            for name, value in sorted(metrics.items()):
                lines.append(f"- `{name}`: {value:.4f}")
            lines.append("")
        (self.reports_dir / "metrics-summary.md").write_text("\n".join(lines), encoding="utf-8")

    def write_matching_summary(self, matching_metrics: dict[str, dict[str, float]]) -> None:
        lines = ["# Matching Metrics Summary", ""]
        for key, metrics in sorted(matching_metrics.items()):
            lines.append(f"## {key}")
            for name, value in sorted(metrics.items()):
                lines.append(f"- `{name}`: {value:.4f}")
            lines.append("")
        (self.reports_dir / "matching-metrics-summary.md").write_text("\n".join(lines), encoding="utf-8")
