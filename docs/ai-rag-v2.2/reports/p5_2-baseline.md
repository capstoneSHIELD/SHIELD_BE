# Phase P5.2 — Baseline 측정 보고서

> **상태**: 인프라 baseline (메트릭 정의 + 평가셋 통계 + 구성 정보).
> **실제 Recall@5 / nDCG@5 / latency 수치**는 production 환경에서 [`OfflineQualityReportJob`](../../src/main/java/org/example/shield/ai/application/OfflineQualityReportJob.java) 실행 후 추가 기재 예정 (P5.2 Commit 5 후속).

## 1. 측정 모드 (production default)

| flag | 값 | 의미 |
|---|---|---|
| `AI_RAG_FUSION_MODE` | weighted | 3-way SQL 가중 합산 (vector 0.5 + BM25 0.3 + trigram 0.2) |
| `AI_RAG_RETRIEVAL_GATE_MODE` | off | gate 미작동 (P5.1 Commit 5에서 shadow 도입 예정) |
| `AI_RAG_RERANK_MODE` | off | rerank 미사용 |
| `AI_EMBEDDING_CACHE_MODE` | off | Cohere embed 매 호출 직접 (P5.3에서 Caffeine L1 도입) |
| `AI_RAG_INTENT_AWARE_MODE` | off | intent-aware routing 미작동 |
| `AI_RAG_CONTEXT_BUDGET_MODE` | off | budget 미작동 |

## 2. 평가셋 v1.6 통계

[eval/eval-set.v1.6.jsonl](../../eval/eval-set.v1.6.jsonl) — 50개 신규 항목 (P5.2 Commit 2):

| dialogue_intent | count | low_evidence | mixed_type | 비고 |
|---|---|---|---|---|
| greeting | 10 | all true | statute_only | "안녕하세요" 류 |
| irrelevant | 10 | all true | statute_only | "날씨 어때요?" 류 |
| change_topic | 10 | all false | statute_only | 도메인 전환 시 expected 보유 |
| ask_legal_advice (low) | 10 | all true | statute_only | "도와주세요" 류 모호 질문 |
| ask_legal_advice (mixed) | 10 | all false | mixed | 법령+판례 양쪽 expected |

(기존 v1.5 [eval/eval-set.v1.5.jsonl](../../eval/eval-set.v1.5.jsonl) 40건은 schema가 달라 RagEvalItem reader가 직접 읽지 않음. 별도 변환 작업은 후속 plan에서 결정)

## 3. P5.2 Commit별 산출물

| Commit | 산출물 | 검증 테스트 |
|---|---|---|
| Commit 1 | [RagEvalItem.java](../../src/main/java/org/example/shield/ai/dto/RagEvalItem.java) — v1.6 스키마 3개 신규 필드 | [RagEvalItemTest](../../src/test/java/org/example/shield/ai/dto/RagEvalItemTest.java) (5 tests) |
| Commit 2 | [RagEvalSetValidator.java:123-126](../../src/main/java/org/example/shield/ai/application/RagEvalSetValidator.java) low_evidence 허용 + v1.6 jsonl 50건 | [RagEvalSetValidatorTest](../../src/test/java/org/example/shield/ai/application/RagEvalSetValidatorTest.java) (5 tests) + [EvalSetV16IntegrationTest](../../src/test/java/org/example/shield/ai/application/EvalSetV16IntegrationTest.java) (4 tests) |
| Commit 3 | [CitationCoverageEvaluator.java](../../src/main/java/org/example/shield/ai/application/CitationCoverageEvaluator.java) — regex 기반 reference mention coverage | [CitationCoverageEvaluatorTest](../../src/test/java/org/example/shield/ai/application/CitationCoverageEvaluatorTest.java) (8 tests) |
| Commit 4 | [ConversationDeterministicSampler.java](../../src/main/java/org/example/shield/ai/util/ConversationDeterministicSampler.java) + [OutputComplianceShadowJudge.java](../../src/main/java/org/example/shield/ai/application/OutputComplianceShadowJudge.java) `evaluate(response, conversationId)` + PII masking | [Sampler](../../src/test/java/org/example/shield/ai/util/ConversationDeterministicSamplerTest.java) (9 tests) + [Judge](../../src/test/java/org/example/shield/ai/application/OutputComplianceShadowJudgeTest.java) (7 tests) |

## 4. 활성화된 메트릭 (P5.1 Commit 4 + P5.2)

### Prometheus exposure
- `shield.ai.cohere.tokens{model, operation, direction, estimated}` (Counter, P5.1 Commit 4)
- `shield.ai.cohere.cost.estimated.usd{model, operation}` (DistributionSummary, P5.1 Commit 4)
- `shield.ai.cohere.latency{model, operation, status}` (Timer, P5.1 Commit 4)
- `shield.rag.retrieval_gate{method, outcome}` (Counter, 기존)
- `shield.ai.output_judge.shadow{outcome}` (Counter, 기존)
- `shield.rag.cohere.embed{outcome}` (Timer, 기존)
- `shield.rag.classify{outcome}` (Timer, 기존)

### 정확값 vs 추정값
- chat/brief/classify input/output tokens: **정확값** (`meta.billed_units` 파싱, `estimated=false`)
- embed input tokens: **정확값** (P5.1 Commit 3 plumbing 완료, `estimated=false`)
- embed output tokens: **N/A** (Cohere embed API는 output token 없음)
- estimated cost: **추정값** (단가표 코드 내 정적, [CohereCostCalculator](../../src/main/java/org/example/shield/ai/infrastructure/CohereCostCalculator.java) 참고)

## 5. PR Blocking Rules — 현 상태

| 규칙 | 활성 | 비고 |
|---|---|---|
| nDCG@5 drop > 0.02 → block | ✅ | RagBaselineEvaluator에 이미 존재 |
| Recall@5 drop > 1%p → block | ✅ | RagBaselineEvaluator에 이미 존재 |
| **reference mention coverage drop > 5%p** → block | ⚠️ | P5.2 Commit 3 evaluator 완성, **baseline 수치 미측정 (DB 필요)** |
| **answer compliance pass rate drop > 3%p** → block | ⚠️ | shadow judge sampling rate 0.1 활성 1주 후 baseline 확정 예정 |
| legal advice false-skip rate > 1% → block | ⏸️ | P5.3 GREETING enforce 단계에서 활성 |
| p95 RAG latency > 8s → block | ✅ | 이미 측정 가능 |
| rerank fallback rate > 5% → force off | ⏸️ | P5.4 rerank 도입 후 활성 |

## 6. 다음 단계 (Sprint 2 = P5.3)

1. **embedding cache (Caffeine L1)** — 동일 query 반복 시 hit
2. **intent-aware retrieval shadow → GREETING-only enforce**
3. **context budget shadow**

이때 본 baseline 수치 (nDCG@5, MRR, Recall@5, reference mention coverage)가 회귀 가드로 활용됨.

## 7. 실측 baseline 수치 (미완)

> 다음 항목은 production DB + 평가셋 1회 실행 후 채워질 예정.

```
nDCG@5 (mixed): <pending>
nDCG@5 (statute): <pending>
nDCG@5 (case): <pending>
MRR: <pending>
Recall@5 (mixed): <pending>
Recall@5 (statute): <pending>
Recall@5 (case): <pending>
expectedReferenceMentionRate (avg): <pending>
p50 retrieval latency: <pending ms>
p95 retrieval latency: <pending ms>
p50 end-to-end latency: <pending ms>
p95 end-to-end latency: <pending ms>
```

실측 절차: `OfflineQualityReportJob` 호출 → `eval/reports/p5_2-baseline.json` 자동 생성 → 본 보고서 7항 갱신.

---

**작성**: P5.2 Commit 5 (2026-05-26)
**다음 보고서**: P5.3 종료 시 — embedding cache hit rate, GREETING skip 정밀도, context budget shadow trim/drop 추세 포함
