# AI/RAG Phase P4 Implementation: RAG Quality Loop

상위 문서: `docs/ai-rag-upgrade-plan-v2.2.md`  
Phase: P4  
목표 기간: 4~6주  
코드 변경 범위: weighted hybrid baseline 유지, RRF/rerank 비교, calibrated retrieval gate, output compliance shadow judge

---

## 1. 목표와 비목표

### 목표

- 현재 weighted hybrid retrieval을 baseline으로 고정한다.
- RRF fusion을 feature flag 뒤에 추가하고 eval set으로 비교한다.
- Cohere rerank 조합을 기존 benchmark와 같은 방식으로 비교한다.
- retrieval score gate는 고정 threshold가 아니라 calibration 후 적용한다.
- output compliance LLM judge는 먼저 offline/shadow로만 실행한다.
- 반복 질문, slot 누락, legal judgment leak을 오프라인 품질 리포트로 측정한다.
- eval set 구성과 분기별 갱신 전략을 정의한다.
- offline 평가 에이전트의 입출력 schema와 저장 위치를 정의한다.
- output compliance judge의 비용·지연·개인정보 기준을 명시한다.

### 비목표

- `score < 0.35` 같은 고정 threshold를 즉시 운영 적용하지 않는다.
- 런타임 retrieval agent나 multi-agent loop를 만들지 않는다.
- P2/P3 feature가 불안정한 상태에서 intent-aware retrieval을 강제 적용하지 않는다.

---

## 2. 현재 코드 기준 진입점

- `PgLegalRetrievalService`: 현재 법령/판례 retrieval orchestration의 중심이다.
- `LegalChunkJpaRepository`: pgvector, BM25, trigram retrieval SQL이 있다.
- `RagContextBuilder`: retrieval result를 Cohere context로 만든다.
- `RagMetrics`: retrieval score/usage metric 관련 코드다.
- `eval/`과 `docs/phase-c5-rerank.md`: 기존 RAG benchmark 기준선이다.
- P2 `IntentRouterResponse`: intent-aware retrieval 입력으로 사용한다.

먼저 읽을 테스트:

- `PgLegalRetrievalServiceTest`
- `RagContextBuilderTest`
- `RagMetricsTest`
- 기존 eval script와 `docs/phase-c5-rerank.md`

---

## 3. 구현 순서

### Commit 1. Baseline metric 고정

1. 현재 weighted hybrid retrieval 결과를 baseline으로 기록한다.
2. 동일 eval set에서 Recall@5, MRR, nDCG@5, latency, cost를 산출한다.
3. baseline 문서는 `docs/ai-rag-phase-p4-baseline.md`로 저장한다.
4. baseline 생성은 코드 변경과 분리해 재현 가능한 command를 문서화한다.

Eval set 구성:

- 최초 eval set은 최근 3개월 상담 로그에서 retrieval 실패 케이스 60건, 성공 케이스 60건, 신규/저빈도 도메인 30건을 샘플링해 150건으로 시작한다.
- 각 문항은 query, expected law/case chunk id, domain, failure_type, labeler, created_at을 가진다.
- 분기별로 갱신하며, 새로운 L2 도메인 추가, retrieval 실패 유형 변화, 법령/판례 데이터 업데이트가 있으면 임시 갱신한다.
- 기존 eval set의 70%는 회귀 비교를 위해 유지하고 30%만 교체한다.

### Commit 2. RRF fusion feature 추가

1. RRF fusion SQL 또는 service-level merger를 추가한다.
2. `app.ai.rag.fusion-mode=weighted|rrf` config를 둔다.
3. 기본값은 `weighted`다.
4. RRF는 운영 기본값으로 켜지 않는다.

RRF 원칙:

```text
rrf_score = 1 / (k + rank)
default k = 60
```

### Commit 3. Rerank 조합 비교

1. 기존 Cohere rerank benchmark 흐름을 재사용한다.
2. 조합은 최소 4개를 비교한다.
   - weighted only
   - weighted + rerank
   - rrf only
   - rrf + rerank
3. 각 조합의 latency와 API cost를 함께 기록한다.

### Commit 4. Score distribution 수집

1. retrieval method별 score distribution을 로그 또는 eval artifact로 저장한다.
2. weighted score, RRF score, rerank score를 서로 다른 scale로 취급한다.
3. domain별 false drop rate를 측정한다.
4. threshold 후보는 percentile 또는 rerank score 기준으로 산출한다.

### Commit 5. Calibrated retrieval gate 추가

1. `app.ai.rag.retrieval-gate.enabled=false`를 기본값으로 둔다.
2. method별 threshold config를 둔다.
3. gate가 문서를 drop할 때 method, score, threshold, query id를 metric으로 남긴다.
4. 운영 적용 전 eval에서 false drop rate 허용 기준을 충족해야 한다.

### Commit 6. Intent-aware retrieval 추가

1. P2 intent router가 안정화된 뒤 활성화한다.
2. `ASK_LEGAL_ADVICE`, `GREETING`, `IRRELEVANT`는 RAG skip 대상이다.
3. `CHANGE_TOPIC`은 topK를 늘리고, high-confidence `PROVIDE_INFO`는 topK를 줄인다.
4. 기본값은 기존 retrieval strategy다.

Intent confidence fallback:

| intentConfidence | 전략 |
|---:|---|
| >= 0.85 | intent별 최적화 전략 적용 |
| 0.65 ~ 0.85 | topK는 기존값 유지, skip은 `ASK_LEGAL_ADVICE`만 허용 |
| < 0.65 | intent-aware strategy 미적용, 기본 weighted hybrid retrieval 유지 |

slot confidence가 낮거나 `topicChanged=true`이면 topK 축소를 금지한다.

### Commit 7. Output compliance shadow judge

1. deterministic `GuardrailFilter`는 계속 1차 gate로 사용한다.
2. LLM judge는 offline/shadow로만 시작한다.
3. sampling rate는 config로 두되 운영 blocking에는 사용하지 않는다.
4. judge 결과는 legal judgment leak rate 리포트에만 반영한다.

활성화 기준:

- p95 latency 증가폭이 200ms 이내여야 한다.
- output judge 비용은 전체 LLM 비용의 10% 이내여야 한다.
- 외부 LLM judge로 보낼 text는 이름, 전화번호, 이메일, 주소, 주민등록번호 패턴을 마스킹한다.
- shadow 2주 동안 legal leak false negative가 deterministic guardrail보다 낮아야 한다.

### Commit 8. Offline quality report schema 추가

입력:

- `consultations`: id, user id hash, created/updated time, domain fields
- `messages`: consultation id, role, sanitized content, created time
- P1.5/P3 slot state: missing/collected/pending slot snapshot
- guardrail/output judge logs

출력 파일:

- JSONL 기본, CSV summary 추가
- 저장 위치: `eval/reports/ai-rag-quality-YYYY-MM-DD.jsonl`
- summary 문서: `docs/ai-rag-quality-report-YYYY-MM-DD.md`

JSONL 필수 필드:

```json
{
  "consultation_id": "uuid",
  "domain": "부동산 거래",
  "repeat_question_count": 0,
  "missing_slots": ["lease_end_date"],
  "legal_leak_expressions": [],
  "retrieval_failure_type": "keyword_mismatch",
  "dynamic_to_static_candidates": [],
  "review_required": false
}
```

알림:

- `review_required=true` 건수와 legal leak 후보가 1건 이상이면 Slack 또는 이메일로 요약을 보낸다.
- 알림 연동이 없으면 summary 문서 생성까지만 P4 완료 기준으로 삼는다.

---

## 4. 인터페이스/API 변경

- Config:
  - `app.ai.rag.fusion-mode=weighted|rrf`
  - `app.ai.rag.rrf-k=60`
  - `app.ai.rag.retrieval-gate.enabled=false`
  - `app.ai.rag.retrieval-gate.weighted-threshold`
  - `app.ai.rag.retrieval-gate.rrf-threshold`
  - `app.ai.rag.retrieval-gate.rerank-threshold`
  - `app.ai.output-judge.shadow-enabled=false`
  - `app.ai.output-judge.sampling-rate=0.0`
  - `app.ai.output-judge.max-p95-latency-increase-ms=200`
  - `app.ai.output-judge.max-cost-ratio=0.10`
- Internal services:
  - `RagFusionMode`
  - `RrfFusionService` or repository query method
  - `RetrievalScoreCalibrator`
  - `OutputComplianceShadowJudge`
  - `OfflineQualityReportJob`
- External API:
  - 변경 없음
- DB:
  - 필수 schema 변경 없음
  - eval artifacts는 파일 또는 별도 운영 metric backend에 저장한다.

---

## 5. 테스트 계획

### Unit tests

- 신규 `RrfFusionServiceTest`
  - rank 기반 RRF score 계산을 검증한다.
  - duplicate chunk id가 sum score로 merge되는지 확인한다.
- 신규 `RetrievalScoreGateTest`
  - disabled일 때 drop하지 않는다.
  - method별 threshold가 독립 적용된다.
  - drop metric payload가 생성된다.
- 신규 `IntentAwareRetrieverTest`
  - intent별 topK/skip strategy를 검증한다.
- 신규 `OutputComplianceShadowJudgeTest`
  - shadow mode에서는 운영 응답을 block하지 않는다.

### Eval tests

- weighted baseline, RRF, rerank 조합별 Recall@5, MRR, nDCG@5를 산출한다.
- latency p50/p95와 rerank API cost를 함께 기록한다.
- score gate 후보별 false drop rate를 산출한다.
- eval set 갱신 스크립트가 기존 set 70%를 유지하고 신규 30%를 교체하는지 dry-run으로 확인한다.
- intentConfidence 구간별 retrieval fallback이 expected topK/skip policy를 따르는지 확인한다.
- offline quality report JSONL 필수 필드가 모두 채워지는지 schema test를 추가한다.

### Regression tests

- 기본 config에서는 기존 weighted retrieval 결과가 유지된다.
- RRF flag가 꺼져 있으면 기존 SQL path가 사용된다.
- output judge가 꺼져 있으면 guardrail 외 응답 경로가 바뀌지 않는다.

---

## 6. 완료 기준

- [ ] weighted hybrid baseline 문서가 생성된다.
- [ ] 최초 eval set 150건 구성과 분기별 갱신 기준이 문서화된다.
- [ ] RRF는 feature flag 뒤에 있고 기본값은 꺼져 있다.
- [ ] weighted/RRF/rerank 조합 비교 리포트가 있다.
- [ ] retrieval gate는 calibration 결과 없이 운영 활성화되지 않는다.
- [ ] `score < 0.35` 고정 threshold를 사용하지 않는다.
- [ ] intentConfidence < 0.65에서는 intent-aware retrieval이 적용되지 않는다.
- [ ] intent-aware retrieval은 P2 안정화 이후 flag로만 켤 수 있다.
- [ ] offline quality report의 입력 테이블과 JSONL 출력 schema가 고정된다.
- [ ] output judge는 p95 +200ms, 비용 +10%, 개인정보 마스킹 기준을 통과해야 운영 검토 대상이 된다.
- [ ] output LLM judge는 shadow 결과만 남기고 운영 blocking을 하지 않는다.

---

## 7. Rollback / Feature Flag

- `app.ai.rag.fusion-mode=weighted`로 즉시 기존 retrieval로 복귀한다.
- `app.ai.rag.retrieval-gate.enabled=false`로 gate를 끈다.
- `app.ai.output-judge.shadow-enabled=false`로 LLM judge를 끈다.
- RRF/rerank 실험 결과가 baseline보다 낮으면 운영 적용하지 않고 eval artifact만 보존한다.
- 즉시 rollback 기준은 retrieval gate false drop rate > 2%, Recall@5 baseline 대비 -2%p, output judge p95 latency +200ms 초과, 또는 output judge 비용이 전체 LLM 비용의 10%를 초과하는 경우다.
