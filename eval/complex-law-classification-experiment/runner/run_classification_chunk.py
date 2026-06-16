from __future__ import annotations

import argparse
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from shield_experiment.client import ExperimentClient
from shield_experiment.config import ExperimentConfig
from shield_experiment.dataset import DatasetRepository, read_jsonl, write_jsonl
from shield_experiment.modes import ClassificationModeRegistry
from shield_experiment.ontology import OntologyMapper, OntologySnapshot
from shield_experiment.pipeline import _classification_result_from_json, _experiment_max_workers
from shield_experiment.result_store import _raw_row


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run one classification mode chunk.")
    parser.add_argument("--config", required=True)
    parser.add_argument("--mode", required=True)
    parser.add_argument("--provider", default="openai")
    parser.add_argument("--start", type=int, default=0)
    parser.add_argument("--count", type=int, default=300)
    parser.add_argument("--resume-dir", required=True)
    parser.add_argument("--resume-modes", default="A_FULL")
    parser.add_argument("--combine", action="store_true")
    args = parser.parse_args(argv)

    config = ExperimentConfig.from_file(args.config)
    resume_dir = Path(args.resume_dir)
    if args.combine:
        _combine_chunks(resume_dir, args.provider, args.mode)
        return 0

    ontology = OntologySnapshot.load(config.ontology_snapshot_path)
    mapper = OntologyMapper(ontology)
    turns = DatasetRepository().load_classification_turns(
        config.classification_turns_path,
        dataset_path=config.dataset_path,
    )
    selected = turns[args.start: args.start + args.count]
    if not selected:
        return 0

    chunk_name = _chunk_name(args.provider, args.mode, args.start, args.start + len(selected) - 1)
    parsed_path = resume_dir / "parsed" / f"{chunk_name}.parsed.jsonl"
    raw_path = resume_dir / "raw" / f"{chunk_name}.jsonl"
    existing_rows = _load_chunk_results(parsed_path)
    existing_by_turn_id = {row.turn_id: row for row in existing_rows}
    if len(existing_by_turn_id) == len(selected):
        print(f"skip existing {chunk_name}")
        return 0

    remaining = [turn for turn in selected if turn.id not in existing_by_turn_id]
    previous_results = _load_previous_results(resume_dir, args.resume_modes)
    strategy = ClassificationModeRegistry().get(args.mode)
    client = ExperimentClient(
        config.base_url,
        dry_run=config.dry_run,
        experiment_access_token=config.experiment_access_token,
    )
    max_workers = _experiment_max_workers()

    if max_workers <= 1:
        rows_by_turn_id = dict(existing_by_turn_id)
        for turn in remaining:
            result = strategy.execute(turn, args.provider, client, mapper, previous_results)
            rows_by_turn_id[result.turn_id] = result
            _write_chunk(raw_path, parsed_path, selected, rows_by_turn_id)
    else:
        rows_by_turn_id = dict(existing_by_turn_id)
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {
                executor.submit(strategy.execute, turn, args.provider, client, mapper, previous_results): index
                for index, turn in enumerate(remaining)
            }
            for future in as_completed(futures):
                result = future.result()
                rows_by_turn_id[result.turn_id] = result
                _write_chunk(raw_path, parsed_path, selected, rows_by_turn_id)

    rows = [rows_by_turn_id[turn.id] for turn in selected if turn.id in rows_by_turn_id]
    _write_chunk(raw_path, parsed_path, selected, rows_by_turn_id)
    error_count = sum(1 for row in rows if row.error_type or row.error_message)
    print(f"wrote {chunk_name} rows={len(rows)} errors={error_count}")
    return 0 if error_count == 0 else 2


def _load_previous_results(resume_dir: Path, modes_csv: str):
    modes = {part.strip() for part in modes_csv.split(",") if part.strip()}
    results = {}
    for path in (resume_dir / "parsed").glob("*.parsed.jsonl"):
        if ".chunk-" in path.name:
            continue
        for row in read_jsonl(path):
            result = _classification_result_from_json(row)
            if modes and result.mode not in modes:
                continue
            results[(result.turn_id, result.provider, result.mode)] = result
    return results


def _load_chunk_results(parsed_path: Path):
    if not parsed_path.exists():
        return []
    return [_classification_result_from_json(row) for row in read_jsonl(parsed_path)]


def _write_chunk(raw_path: Path, parsed_path: Path, selected, rows_by_turn_id) -> None:
    rows = [rows_by_turn_id[turn.id] for turn in selected if turn.id in rows_by_turn_id]
    raw_path.parent.mkdir(parents=True, exist_ok=True)
    parsed_path.parent.mkdir(parents=True, exist_ok=True)
    write_jsonl(raw_path, [_raw_row(row) for row in rows])
    write_jsonl(parsed_path, [row.to_json() for row in rows])


def _combine_chunks(resume_dir: Path, provider: str, mode: str) -> None:
    raw_chunks = sorted((resume_dir / "raw").glob(f"{provider}_{mode}.chunk-*.jsonl"))
    parsed_chunks = sorted((resume_dir / "parsed").glob(f"{provider}_{mode}.chunk-*.parsed.jsonl"))
    if not raw_chunks or not parsed_chunks:
        raise FileNotFoundError(f"No chunks found for {provider}_{mode}")
    raw_rows = [row for path in raw_chunks for row in read_jsonl(path)]
    parsed_rows = [row for path in parsed_chunks for row in read_jsonl(path)]
    write_jsonl(resume_dir / "raw" / f"{provider}_{mode}.jsonl", raw_rows)
    write_jsonl(resume_dir / "parsed" / f"{provider}_{mode}.parsed.jsonl", parsed_rows)
    print(f"combined {provider}_{mode} raw={len(raw_rows)} parsed={len(parsed_rows)}")


def _chunk_name(provider: str, mode: str, start: int, end: int) -> str:
    return f"{provider}_{mode}.chunk-{start:04d}-{end:04d}"


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
