# AI Complex Law Classification Experiment Runner

This runner is the first implementation slice of the benchmark design in:

- `C:\SHIELD_BE\.codex\ref_docs\rag-evals\ai-complex-law-classification-experiment.md`
- `C:\SHIELD_BE\.codex\ref_docs\rag-evals\ai-complex-law-classification-experiment-diagrams\pipeline-class-diagram.md`

## Scope

Implemented in this slice:

- OOP runner structure based on the class diagram
- classification mode strategies
- matching mode strategies
- ontology validation/mapping helper
- JSONL result sink
- basic classification and matching metrics
- markdown summary reports
- dry-run mode for pipeline validation before BE adapters exist
- local/test BE intent-route adapter support via `POST /internal/experiments/intent-route`

Not implemented yet:

- BE `/internal/experiments/lawyer-match` adapter
- synthetic lawyer corpus generator
- local synthetic lawyer corpus loading into a test schema

## Run

```powershell
cd C:\SHIELD_BE\eval\complex-law-classification-experiment\runner
python .\run_experiment.py --config .\config.example.json
```

Restricted shells may prefer invoking the same config from the repository root:

```powershell
cd C:\SHIELD_BE
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.example.json
```

`config.example.json` uses `dry_run=true`, so it validates the runner flow without calling the backend. For real runs, set `dry_run=false` and start SHIELD BE with local/test experiment adapters enabled.

The intent adapter is available only under the `local` or `test` Spring profile:

```text
POST /internal/experiments/intent-route/preflight
POST /internal/experiments/intent-route
```
