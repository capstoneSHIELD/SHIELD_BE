from __future__ import annotations

import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from .client import ExperimentClient
from .config import ExperimentConfig
from .dataset import DatasetBuilder, DatasetRepository, LawyerCorpusRepository, read_jsonl, write_jsonl
from .evaluator import ClassificationEvaluator, MatchingEvaluator
from .matching import CurrentServiceQueryBuilder, MatchingModeRegistry
from .modes import ClassificationModeRegistry
from .models import ClassificationResult, MatchingResult
from .ontology import OntologyMapper, OntologySnapshot
from .report import ReportWriter
from .result_store import JsonlResultSink


@dataclass(frozen=True)
class RunContext:
    run_id: str
    repo: str
    branch: str
    commit_sha: str
    output_dir: Path


class RunContextFactory:
    def create(self, config: ExperimentConfig) -> RunContext:
        branch = _git("rev-parse", "--abbrev-ref", "HEAD")
        commit = _git("rev-parse", "HEAD")
        short = commit[:8] if commit else "unknown"
        stamp = datetime.now(ZoneInfo("Asia/Seoul")).strftime("%Y-%m-%d_%H%M")
        safe_branch = _safe_path_component(branch or "unknown")
        run_id = f"{stamp}_{safe_branch}_{short}"
        return RunContext(
            run_id=run_id,
            repo="capstoneSHIELD/SHIELD_BE",
            branch=branch or "unknown",
            commit_sha=commit or "unknown",
            output_dir=config.output_root / run_id,
        )


class PreflightPipeline:
    def run(self, config: ExperimentConfig, client: ExperimentClient) -> None:
        required = [
            config.ontology_snapshot_path,
        ]
        for path in required:
            if not path.exists():
                raise FileNotFoundError(f"Required input file does not exist: {path}")
        if not config.classification_turns_path.exists() and not config.dataset_path.exists():
            raise FileNotFoundError(
                "Either classification_turns_path or dataset_path must exist: "
                f"{config.classification_turns_path}, {config.dataset_path}"
            )
        if config.matching_modes:
            if config.lawyer_corpus_path is None or not config.lawyer_corpus_path.exists():
                raise FileNotFoundError(f"lawyer_corpus_path is required for matching: {config.lawyer_corpus_path}")
            if config.matching_labels_path is None or not config.matching_labels_path.exists():
                raise FileNotFoundError(f"matching_labels_path is required for matching: {config.matching_labels_path}")
        client.preflight_providers(config.providers)


class ClassificationExperimentPipeline:
    def __init__(self, registry: ClassificationModeRegistry):
        self.registry = registry

    def run(
        self,
        config: ExperimentConfig,
        turns,
        client: ExperimentClient,
        mapper: OntologyMapper,
        sink: JsonlResultSink,
    ) -> dict[tuple[str, str, str], ClassificationResult]:
        results: dict[tuple[str, str, str], ClassificationResult] = {}
        for provider in config.providers:
            for mode in config.classification_modes:
                strategy = self.registry.get(mode)
                rows: list[ClassificationResult] = []
                for turn in turns:
                    result = strategy.execute(turn, provider, client, mapper, results)
                    results[(turn.id, provider, mode)] = result
                    rows.append(result)
                sink.write_classification_results(provider, mode, rows)
        return results


class MatchingExperimentPipeline:
    def __init__(self, registry: MatchingModeRegistry):
        self.registry = registry

    def run(
        self,
        config: ExperimentConfig,
        final_turns,
        classification_results: dict[tuple[str, str, str], ClassificationResult],
        client: ExperimentClient,
        mapper: OntologyMapper,
        sink: JsonlResultSink,
    ) -> dict[str, list[MatchingResult]]:
        if not config.matching_modes:
            return {}
        query_builder = CurrentServiceQueryBuilder(mapper)
        by_mode: dict[str, list[MatchingResult]] = {}
        for mode in config.matching_modes:
            strategy = self.registry.get(mode)
            rows: list[MatchingResult] = []
            for turn in final_turns:
                classification_result = classification_results.get(
                    (turn.id, config.selected_provider, config.selected_classification_mode)
                )
                rows.append(strategy.execute(
                    turn=turn,
                    provider=config.selected_provider,
                    classification_mode=config.selected_classification_mode,
                    classification_result=classification_result,
                    client=client,
                    query_builder=query_builder,
                    top_k=config.top_k,
                ))
            by_mode[mode] = rows
            sink.write_matching_results(mode, rows)
        return by_mode


class EvaluationPipeline:
    def run(
        self,
        classification_results: dict[tuple[str, str, str], ClassificationResult],
        matching_results: dict[str, list[MatchingResult]],
        labels,
    ) -> tuple[dict[str, dict[str, float]], dict[str, dict[str, float]]]:
        classification_evaluator = ClassificationEvaluator()
        matching_evaluator = MatchingEvaluator()
        by_classification_key: dict[str, list[ClassificationResult]] = {}
        for (_, provider, mode), result in classification_results.items():
            by_classification_key.setdefault(f"{provider}_{mode}", []).append(result)
        classification_metrics = {
            key: classification_evaluator.evaluate(rows)
            for key, rows in by_classification_key.items()
        }
        matching_metrics = {
            key: matching_evaluator.evaluate(rows, labels)
            for key, rows in matching_results.items()
        }
        return classification_metrics, matching_metrics


class ReportingPipeline:
    def run(self, run_dir: Path, classification_metrics, matching_metrics) -> None:
        writer = ReportWriter(run_dir / "reports")
        writer.write_metrics_summary(classification_metrics)
        writer.write_matching_summary(matching_metrics)


class ExperimentPipelineFacade:
    def __init__(self):
        self.dataset_repository = DatasetRepository()
        self.lawyer_repository = LawyerCorpusRepository()
        self.classification_pipeline = ClassificationExperimentPipeline(ClassificationModeRegistry())
        self.matching_pipeline = MatchingExperimentPipeline(MatchingModeRegistry())
        self.evaluation_pipeline = EvaluationPipeline()
        self.reporting_pipeline = ReportingPipeline()

    def run(self, config: ExperimentConfig) -> RunContext:
        context = RunContextFactory().create(config)
        client = ExperimentClient(config.base_url, dry_run=config.dry_run)
        PreflightPipeline().run(config, client)

        ontology = OntologySnapshot.load(config.ontology_snapshot_path)
        mapper = OntologyMapper(ontology)
        turns = self._load_or_build_turns(config)
        final_turns = [turn for turn in turns if turn.is_final_turn]

        sink = JsonlResultSink(context.output_dir)
        sink.write_run_meta(self._run_meta(config, context))

        classification_results = self.classification_pipeline.run(config, turns, client, mapper, sink)
        labels = self.lawyer_repository.load_matching_labels(config.matching_labels_path)
        matching_results = self.matching_pipeline.run(
            config, final_turns, classification_results, client, mapper, sink
        )
        classification_metrics, matching_metrics = self.evaluation_pipeline.run(
            classification_results, matching_results, labels
        )
        self.reporting_pipeline.run(context.output_dir, classification_metrics, matching_metrics)
        return context

    def _load_or_build_turns(self, config: ExperimentConfig):
        turns = self.dataset_repository.load_classification_turns(
            config.classification_turns_path,
            dataset_path=config.dataset_path,
        )
        if config.classification_turns_path.exists():
            return turns
        case_rows = read_jsonl(config.dataset_path)
        built = DatasetBuilder().build_classification_turns(case_rows)
        write_jsonl(config.classification_turns_path, [turn.to_json() for turn in built])
        return built

    @staticmethod
    def _run_meta(config: ExperimentConfig, context: RunContext) -> dict:
        return {
            "run_id": context.run_id,
            "repo": context.repo,
            "branch": context.branch,
            "commit_sha": context.commit_sha,
            "dataset_path": str(config.dataset_path),
            "classification_turns_path": str(config.classification_turns_path),
            "ontology_snapshot_path": str(config.ontology_snapshot_path),
            "lawyer_corpus_path": str(config.lawyer_corpus_path) if config.lawyer_corpus_path else None,
            "matching_labels_path": str(config.matching_labels_path) if config.matching_labels_path else None,
            "providers": config.providers,
            "classification_modes": config.classification_modes,
            "matching_modes": config.matching_modes,
            "selected_provider": config.selected_provider,
            "selected_classification_mode": config.selected_classification_mode,
            "dry_run": config.dry_run,
            "production_group_weights": config.production_group_weights,
            "hybrid_match_weights": config.hybrid_match_weights,
        }


def _git(*args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


def _safe_path_component(value: str) -> str:
    safe = []
    for char in value:
        safe.append(char if char.isalnum() or char in "._-" else "_")
    return "".join(safe).strip("._") or "unknown"
