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
- classification metrics with valid node, fallback, primary, path-aware, partial, and token/latency fields
- matching metrics with Recall@3/5/10, nDCG@5/10, MRR, exact specialist recall, and hard-negative intrusion rate
- markdown summary reports, current-service baseline report, and cosine-vs-hybrid delta report
- benchmark validity, corpus coverage, failure case, L1 confusion, scoped loss, and classification-to-matching loss reports
- dry-run mode for pipeline validation without backend calls
- BE `POST /internal/experiments/intent-route` adapter behind an explicit enable flag and token
- BE `POST /internal/experiments/lawyer-match` adapter behind an explicit enable flag and token
- synthetic lawyer corpus generator driven by `lawyer-corpus-generator-config.yaml`
- wrong-selected testcase directory loader for `src/test/testcases/wrong/wrong-x1-*.json`
- selected-label metadata forwarding to the intent-route adapter payload
- turn-index classification progress report for measuring 2~10 turn improvement

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

`config.example.json` uses `dry_run=true`, so it validates the runner flow without calling the backend. For real classification and matching runs, set `dry_run=false` and start SHIELD BE with `shield.experiment.adapter.enabled=true`.

For the wrong-selected cross-L1 benchmark, use:

```powershell
cd C:\SHIELD_BE
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.wrong-selected.example.json
```

`config.wrong-selected.example.json` uses `dry_run=false`, so start SHIELD BE with `shield.experiment.adapter.enabled=true` before running it. It reads `src/test/testcases/wrong/wrong-x1-*.json`, converts each evaluation turn to normalized classification turns, and writes the normalized JSONL cache to `eval/complex-law-classification-experiment/input/wrong-selected-classification-turns-v1.jsonl`.

For AWS runs, deploy the backend with both:

```text
SHIELD_EXPERIMENT_ADAPTER_ENABLED=true
SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN=<long random token>
```

Then set the same token in the runner environment and use the AWS config:

```powershell
$env:SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN="<same token>"
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.wrong-selected.aws.example.json
```

The runner sends this value as `X-SHIELD-EXPERIMENT-TOKEN`. Do not commit the real token into config files.

For lighter wrong-selected trap runs, first build the single-bait final-turn input and then run the dedicated AWS config:

```powershell
cd C:\SHIELD_BE
python .\eval\complex-law-classification-experiment\runner\build_wrong_selected_single_bait.py --input .\eval\complex-law-classification-experiment\input\wrong-selected-final-turns-v1.jsonl --output .\eval\complex-law-classification-experiment\input\wrong-selected-single-bait-final-turns-v1.jsonl
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.wrong-selected.single-bait.aws.json
```

This variant keeps the same 30 final-turn rows as the relaxed run, but reduces the user-supplied wrong labels from two cross-L1 distractors to one cross-L1 distractor per row.

For the expanded 300-case final-turn wrong-selected run, build the final-turn JSONL from `src/test/testcases/wrong` and use the dedicated AWS config:

```powershell
cd C:\SHIELD_BE
python .\eval\complex-law-classification-experiment\runner\build_wrong_selected_final_turns.py --input-dir .\src\test\testcases\wrong --output .\eval\complex-law-classification-experiment\input\wrong-selected-300-final-turns-v1.jsonl
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.wrong-selected.300-final.aws.json
```

This variant evaluates one final turn per testcase, so 300 testcase files produce 300 classification rows.

To validate the runner flow without backend or LLM calls, use:

```powershell
cd C:\SHIELD_BE
python .\eval\complex-law-classification-experiment\runner\run_experiment.py --config .\eval\complex-law-classification-experiment\runner\config.wrong-selected.dry-run.example.json
```

The dry-run config copies gold labels into predictions. Use it only for pipeline validation, not for model performance measurement.

For this benchmark, `classification_history_window` is `null` so turn 2~10 receive the cumulative user utterance history. `B_SCOPED_GOLD` is intentionally excluded because it scopes by the gold L1 and leaks the answer. The generated report `classification-turn-progress.md` groups classification metrics by `turn_index`.

`path_aware_accuracy` treats the first predicted node as correct when it matches the gold leaf or one of that gold leaf's ancestors on the same ontology path.

The backend intent-route adapter accepts `selectedNodeIds` and `selectedLabels` from the runner. It includes them in the experiment prompt as user-supplied metadata, while instructing the classifier to prefer conversation facts over selected areas. The experiment route uses the full supplied message history by default; pass `historyWindowMessages` only when an explicit backend-side truncation window is desired.

Current backend adapter status:

- `POST /internal/experiments/intent-route/preflight`
- `POST /internal/experiments/intent-route`
- `POST /internal/experiments/lawyer-match/corpus`
- `POST /internal/experiments/lawyer-match/preflight`
- `POST /internal/experiments/lawyer-match`

The matching adapter uses only the runner-uploaded synthetic corpus. It does not read production lawyer tables or embeddings.

## Matching Corpus Flow

When `matching_modes` is non-empty, preflight validates:

- required classification turn fields
- ontology node ids in gold labels
- synthetic lawyer practice node coverage
- matching label lawyer ids
- BE lawyer-match adapter corpus/query/weight compatibility

With `dry_run=true`, corpus upload is skipped. With `dry_run=false`, the runner uploads `lawyers-v1.jsonl` to `/internal/experiments/lawyer-match/corpus` before calling the adapter preflight.

If `lawyer_corpus_path` does not exist and `lawyer_corpus_generator_config_path` is set, the runner generates a deterministic synthetic corpus before preflight. The generator creates fake benchmark profiles only; it must not be used to model real lawyers or personal data.
