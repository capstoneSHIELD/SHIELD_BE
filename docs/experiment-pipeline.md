# Experiment Pipeline

이 문서는 SHIELD BE의 local/test 전용 실험 파이프라인 구현 범위를 기록한다. `/internal/experiments/*` 경로는 공개 운영 API가 아니며, benchmark runner가 현재 분류/매칭 경로를 재현하고 지표를 산출하기 위한 내부 adapter다.

## Scope

- 목표 범위는 Benchmark v1이다.
- 300개 본셋 작성은 별도 데이터 작성 작업으로 분리한다.
- 변호사 매칭 실험은 운영 DB를 사용하지 않고 runner가 업로드한 synthetic corpus만 사용한다.
- hybrid matching은 runner-side 비교군이며 운영 매칭 점수식으로 간주하지 않는다.

## Internal Endpoints

`local` 또는 `test` profile에서만 등록된다.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/experiments/intent-route/preflight` | provider 등록 여부 확인 |
| `POST` | `/internal/experiments/intent-route` | BE intent classifier 경로 호출 및 raw/parsed 결과 반환 |
| `POST` | `/internal/experiments/lawyer-match/corpus` | `lawyers-v1.jsonl` synthetic corpus를 in-memory store에 적재 |
| `POST` | `/internal/experiments/lawyer-match/preflight` | corpus coverage, query hash compatibility, hybrid weight 고정 여부 확인 |
| `POST` | `/internal/experiments/lawyer-match` | in-memory synthetic corpus cosine 후보 반환 |

## Current-Service Compatibility

`lawyer-match` adapter는 `LawyerEmbeddingTextBuilder`로 query text를 재생성하고 runner가 보낸 `queryTextHash`와 비교한다. 이 값이 맞아야 `PREDICTED_LABELS_COSINE_ONLY`를 current-service baseline으로 해석할 수 있다.

실험 adapter의 cosine score는 local/test synthetic corpus를 고정 차원 해시 벡터로 변환해 계산한다. 외부 embedding provider나 운영 `lawyer_embeddings` 테이블은 사용하지 않는다.

`intent-route` adapter는 parser 단계에서 `matched_node_ids` ancestor chain을 정규화한다. 예를 들어 `law-004`, `law-004-04`, `law-004-04-04`가 함께 오면 가장 구체적인 node 하나만 남긴다.

classification summary는 strict `exact_set_match` 외에 `path_aware_accuracy`도 제공한다. 이 지표는 첫 predicted node가 gold leaf 또는 같은 ontology path의 ancestor(L2/L1)면 정답으로 본다.

## Runner Outputs

runner는 각 실행마다 `eval/complex-law-classification-experiment/output/{run_id}` 아래에 raw/parsed/matching JSONL과 아래 리포트를 생성한다.

- `metrics-summary.md`
- `matching-metrics-summary.md`
- `benchmark-validity-check.md`
- `current-service-baseline.md`
- `corpus-coverage-report.md`
- `failure-cases.md`
- `confusion-by-l1.csv`
- `scoped-ontology-loss.md`
- `cosine-vs-hybrid-matching.md`
- `classification-to-matching-loss.md`
