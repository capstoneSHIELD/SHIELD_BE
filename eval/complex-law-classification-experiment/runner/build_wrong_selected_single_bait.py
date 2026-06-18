from __future__ import annotations

import argparse
import json
from pathlib import Path

from shield_experiment.ontology import OntologyMapper, OntologySnapshot


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build a softened wrong-selected benchmark by keeping only one "
            "cross-L1 selected label per final-turn row."
        )
    )
    parser.add_argument("--input", required=True, help="Source JSONL path")
    parser.add_argument("--output", required=True, help="Output JSONL path")
    parser.add_argument(
        "--ontology",
        default="../../../src/main/resources/ontology/legal-ontology-slim.json",
        help="Ontology snapshot path for validation",
    )
    return parser.parse_args()


def load_rows(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, start=1):
            text = raw.strip()
            if not text:
                continue
            try:
                data = json.loads(text)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exc}") from exc
            if not isinstance(data, dict):
                raise ValueError(f"{path}:{line_number}: row must be a JSON object")
            rows.append(data)
    return rows


def soften_row(row: dict, mapper: OntologyMapper) -> dict:
    selected_labels = [label for label in row.get("selected_labels", []) if isinstance(label, dict)]
    if not selected_labels:
        raise ValueError(f"{row.get('id')}: selected_labels is empty")

    kept_label = dict(selected_labels[0])
    kept_node_id = str(kept_label.get("nodeId") or "")
    if not kept_node_id:
        raise ValueError(f"{row.get('id')}: selected label is missing nodeId")

    gold_node_ids = [str(node_id) for node_id in row.get("gold_node_ids", [])]
    kept_l1 = mapper.to_l1(kept_node_id)
    gold_l1 = {mapper.to_l1(node_id) for node_id in gold_node_ids}
    if kept_l1 and kept_l1 in gold_l1:
        raise ValueError(f"{row.get('id')}: kept selected label overlaps gold L1")

    softened = dict(row)
    softened["benchmark_split"] = "wrong-selected-single-bait-final-turns.v1"
    softened["selected_labels"] = [kept_label]
    softened["selected_node_ids"] = [kept_node_id]
    return softened


def write_rows(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False))
            handle.write("\n")


def main() -> None:
    args = parse_args()
    script_dir = Path(__file__).resolve().parent
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    ontology_arg = Path(args.ontology)
    ontology_path = ontology_arg if ontology_arg.is_absolute() else (script_dir / ontology_arg).resolve()

    mapper = OntologyMapper(OntologySnapshot.load(ontology_path))
    rows = load_rows(input_path)
    softened = [soften_row(row, mapper) for row in rows]
    write_rows(output_path, softened)

    print(
        json.dumps(
            {
                "input_path": str(input_path),
                "output_path": str(output_path),
                "row_count": len(softened),
                "selected_labels_per_row": 1,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
