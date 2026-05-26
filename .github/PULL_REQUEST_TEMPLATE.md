<!--
Phase P5 PR Template — AI Pipeline Upgrade
모든 P5.x commit은 본 템플릿을 사용한다. 마스터: docs/ai-rag-v2.2/phases/ai-rag-phase-p5-pipeline-upgrade-master.md
-->

## Summary
- **What changed**:
- **User-facing behavior**: (변화 없음 / shadow / sampled / enforced)
- **Default flag state**:

## Feature Flag
- **Flag name**: (예: `AI_RAG_RETRIEVAL_GATE_MODE`)
- **Mode**: `off` | `shadow` | `sampled` | `enforce`
- **Default**: (yaml 기본값)
- **Rollback method**: redeploy with flag override (no runtime config update available — Q1/Q2 참조)
- **Requires redeploy/restart**: yes (always — 회로 차단기로 보완)

## Evaluation
- **Baseline commit**: (직전 회귀 baseline commit hash)
- **Dataset version**: v1.6 (`eval/eval-set.v1.6.jsonl`)
- **nDCG@5**: <측정값>
- **MRR**: <측정값>
- **Recall@5**: <측정값>
- **Answer compliance** (if applicable):
- **Reference mention coverage** (if applicable):
- **p50 retrieval latency**:
- **p95 retrieval latency**:
- **Token input/output**: (예: 1.2M / 0.4M, 추정 cost $)
- **Estimated cost**:

## Safety
- **Fallback path**: (실패 시 동작)
- **Timeout**:
- **No-result behavior**:
- **Metric added**: (신규 metric name)
- **Auto-off condition**: (회로 차단기 조건)

## Rollout
- **Shadow plan**: (shadow 단계 기간/조건)
- **Sampling plan**: (conversation-deterministic, 비율)
- **Enforce criteria**: (enforce 진입 조건)
- **Monitoring window**: (관찰 기간)

## Checklist
- [ ] 해당 phase 문서의 완료 기준 모두 만족
- [ ] 단위 테스트 추가 / 갱신
- [ ] 회귀 가드 통과 (전체 테스트)
- [ ] PR Blocking Rules 위반 없음 (nDCG@5 drop, Recall@5 drop, p95 latency 등)
- [ ] PII 노출 없음 (로그 / 메트릭 tag / DB)
- [ ] 새 metric은 `shield.*` 네임스페이스 + 명확한 tag 정의
