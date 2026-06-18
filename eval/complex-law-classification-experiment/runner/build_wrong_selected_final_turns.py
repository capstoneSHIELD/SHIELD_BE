from __future__ import annotations

import argparse
import json
from pathlib import Path

from shield_experiment.dataset import DatasetRepository, write_jsonl


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a final-turn-only JSONL from wrong-selected testcase JSON files."
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("src/test/testcases/wrong"),
        help="Directory containing wrong-x1-*.json testcase files.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("eval/complex-law-classification-experiment/input/wrong-selected-300-final-turns-v1.jsonl"),
        help="Output JSONL path.",
    )
    parser.add_argument(
        "--benchmark-split",
        default="wrong-selected-300-final-turns.v1",
        help="benchmark_split value to write into output rows.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_dir = args.input_dir.resolve()
    output_path = args.output.resolve()

    turns = DatasetRepository().load_wrong_selected_turns(input_dir, history_window=None)
    final_turns = []
    for turn in turns:
        if not turn.is_final_turn:
            continue
        row = turn.to_json()
        row["benchmark_split"] = args.benchmark_split
        final_turns.append(row)

    write_jsonl(output_path, final_turns)
    print(
        json.dumps(
            {
                "input_dir": str(input_dir),
                "output_path": str(output_path),
                "row_count": len(final_turns),
                "case_count": len({row["case_id"] for row in final_turns}),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
