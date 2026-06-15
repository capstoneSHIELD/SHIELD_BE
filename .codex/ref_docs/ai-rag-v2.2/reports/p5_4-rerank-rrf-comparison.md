# Phase P5.4 — Rerank × RRF 4-mode 비교 보고서

> **상태 (2026-05-26)**: 인프라·평가 절차 baseline. 실측 nDCG@5/MRR/Recall@5는 production DB + 평가셋 v1.6 1회 실행 후 추가 기재.

## 1. 측정 4 modes

본 phase는 검색 레이어의 4가지 조합을 동일 평가셋으로 비교한다.

| Mode | Fusion | Rerank | 운영 활성 가능? |
|---|---|---|---|
| **A. weighted** (baseline) | 가중합 (vector 0.5 + BM25 0.3 + trigram 0.2) | OFF | ✅ 현재 production 기본 |
| **B. rrf** | Reciprocal Rank Fusion (k=60) | OFF | ❌ offline only (`AI_RAG_RRF_OFFLINE_ENABLED=true` 가드) |
| **C. weighted + rerank** | A 결과를 Cohere rerank-v3.5로 top-N 재정렬 | ON (shadow / sampled / enforce) | ✅ sampled 활성 가능 |
| **D. rrf + rerank** | B 결과를 Cohere rerank-v3.5로 top-N 재정렬 | ON | ❌ offline only |

## 2. 측정 항목 (모드별)

### 2.1 검색 품질
- **nDCG@5** (statute / case / mixed split)
- **MRR**
- **Recall@5** (statute / case / mixed split)
- **expectedReferenceMentionRate** (P5.2 Commit 3, 평가셋 v1.6 mixed/low-evidence 항목)

### 2.2 지연
- **p50 retrieval latency** (검색만)
- **p95 retrieval latency**
- **end-to-end latency p50/p95** (검색 + rerank + context build)

### 2.3 비용
- **Cohere rerank API 비용** (rerank-v3.5: $2.00 per 1M input tokens, 보통 1 search unit / 호출)
- **추정 월 비용** (production 트래픽 기준)

### 2.4 안정성
- **Rerank fallback rate** (timeout / API error / circuit breaker)
- **회로 차단 발동 빈도**

## 3. 측정 절차

### 3.1 환경 준비

```bash
# 1) DB + pgvector 설정
$env:DB_URL = "jdbc:postgresql://localhost:5432/shield?stringtype=unspecified"
$env:DB_USERNAME = "shield"
$env:DB_PASSWORD = "..."

# 2) Cohere API key
$env:COHERE_API_KEY = "..."

# 3) Mode별 flag 조합
# A. weighted (baseline)
$env:AI_RAG_FUSION_MODE = "weighted"
$env:AI_RAG_RERANK_MODE = "off"

# B. rrf
$env:AI_RAG_RRF_OFFLINE_ENABLED = "true"
$env:AI_RAG_RERANK_MODE = "off"

# C. weighted + rerank
$env:AI_RAG_FUSION_MODE = "weighted"
$env:AI_RAG_RERANK_MODE = "enforce"

# D. rrf + rerank
$env:AI_RAG_RRF_OFFLINE_ENABLED = "true"
$env:AI_RAG_RERANK_MODE = "enforce"
```

### 3.2 평가 실행

평가셋: [eval/eval-set.v1.6.jsonl](../../eval/eval-set.v1.6.jsonl) — 50건 (5 카테고리 × 10).

```bash
# 각 mode별로 실행 (4회)
./gradlew bootRun --args="--spring.profiles.active=eval --eval.mode=weighted"
./gradlew bootRun --args="--spring.profiles.active=eval --eval.mode=rrf"
./gradlew bootRun --args="--spring.profiles.active=eval --eval.mode=weighted_rerank"
./gradlew bootRun --args="--spring.profiles.active=eval --eval.mode=rrf_rerank"
```

`OfflineQualityReportJob`이 mode별 보고서 생성 → `eval/reports/p5_4-{mode}.json`.

### 3.3 Rerank 모델 후보 비교 (선택)

본 plan은 `rerank-v3.5`를 primary로 사용. 추가 비교 후보:

| 모델 | 입력 단가 (USD/1M) | 지연 특성 | 비고 |
|---|---|---|---|
| `rerank-v3.5` | $2.00 | 평균 200ms | 본 plan baseline |
| `rerank-v4.0-fast` | (Cohere 가격 페이지 확인) | 더 빠름 (~150ms?) | 빠른 응답 필요 시 |
| `rerank-v4.0-pro` | 더 비쌈 | 정확도 ↑ | 품질 우선 시 |

3개 모델 × 4 mode = 12 조합. 비용 예산 따라 선택.

## 4. 실측 수치 (미완 — 추후 기재)

### 4.1 weighted (A, baseline)

```
nDCG@5 (mixed):  <pending>
nDCG@5 (statute): <pending>
nDCG@5 (case):    <pending>
MRR:              <pending>
Recall@5:         <pending>
expectedReferenceMentionRate: <pending>
p50 latency:      <pending ms>
p95 latency:      <pending ms>
```

### 4.2 rrf (B)
```
<pending>
```

### 4.3 weighted + rerank (C)
```
<pending>
Rerank fallback rate: <pending>
추정 월 비용:         <pending USD>
```

### 4.4 rrf + rerank (D)
```
<pending>
```

## 5. Decision Tree

### 5.1 Rerank 활성화 (mode C, weighted+rerank)
- **활성 조건**: nDCG@5 ≥ baseline + **0.05** AND p95 latency ≤ 8s AND fallback rate < 1%
- **조건 충족** → sampled 30% (Q6 적극적 권장) 시작, 1주 운영 후 enforce 검토
- **조건 미달** → mode=off 유지, 모델 변경 또는 prompt 튜닝 후 재시도

### 5.2 RRF 활성화 (mode B, fusion)
- **활성 조건**: nDCG@5 / MRR / Recall@5 **모두** baseline 대비 +0.02 이상
- **조건 충족** → 별도 plan에서 production fusion 분기 도입 검토 (현재는 offline only)
- **조건 미달 (±0.02 이내)** → weighted 유지 (1회 SQL이 latency 유리)
- 핵심: RRF는 가중합 outlier 회피 효과가 한국어 법률 도메인에 유의미한지 평가

### 5.3 Rerank + RRF 결합 (mode D)
- 두 항목 모두 우위일 때만 의미. C와 D 비교에서 D 우위면 RRF + rerank 조합 검토.
- 현실적으로 RRF 단독이 weighted를 명확히 이기지 못하면 D도 production 검토 보류.

## 6. PR Blocking Rules 활성화

본 phase 완료 시 다음 회귀 가드 활성화:
```
nDCG@5 drop > 0.02 → block
Recall@5 drop > 1%p → block
p95 RAG latency > 8s → block or shadow only
Rerank fallback rate > 5% → force rerank mode=off (회로 차단기 자동)
```

## 7. 후속 작업

- **rerank-v4.0-fast/pro** 비교 (시간/예산 여유 시)
- **운영 sampling rate**: shadow → 5% → 10% → 30% (Q6) → enforce
- **회로 차단기 임계값 튜닝**: 1주 운영 후 fallback rate 분포 보고 조정
- [P5.5 HyperCLOVA Judge](../phases/ai-rag-phase-p5_5-hyperclova-hybrid.md)와 병행 진행 가능

## 8. 산출물 일람 (P5.4 5/5 commits)

| Commit | 신규/수정 | 신규 테스트 |
|---|---|---|
| **C1** Cohere Rerank Client + Adapter | [CohereRerankClient](../../src/main/java/org/example/shield/ai/infrastructure/CohereRerankClient.java), [CohereRerankClientAdapter](../../src/main/java/org/example/shield/ai/provider/cohere/CohereRerankClientAdapter.java), Request/Response DTO | 3 |
| **C2** RerankingService shadow mode + RagPipelineService 통합 | [RerankingService](../../src/main/java/org/example/shield/ai/application/RerankingService.java), [RagPipelineService](../../src/main/java/org/example/shield/ai/application/RagPipelineService.java) rerank 통합 | 13 |
| **C3** Sampled 30% + Auto-OFF 회로 차단기 | [RerankCircuitBreaker](../../src/main/java/org/example/shield/ai/safety/RerankCircuitBreaker.java), Reranking 통합 | 7 + 2 |
| **C4** RRF path-specific repository | [LegalChunkJpaRepository](../../src/main/java/org/example/shield/ai/domain/LegalChunkJpaRepository.java) + [LegalCaseJpaRepository](../../src/main/java/org/example/shield/ai/domain/LegalCaseJpaRepository.java) — `searchVectorOnly` / `searchBm25Only` / `searchTrigramOnly` 각 2개씩 | (DB 통합 필요) |
| **C5** RagPipelineService RRF 분기 (offline only) | [OfflineRrfRetrievalService](../../src/main/java/org/example/shield/ai/application/OfflineRrfRetrievalService.java) — production guard 포함 | 4 |
| **C6** 본 보고서 + 최종 결정 의사결정 프레임워크 | — | — |

### 신규 메트릭 (P5.4)
- `shield.ai.rerank.latency{model, status}` (Timer)
- `shield.ai.rerank.fallback{reason}` (Counter)
- `shield.ai.rerank.outcome{mode, outcome=skipped/shadow_executed/applied/fallback/circuit_open}` (Counter)

### 새 Feature Flag (P5.4)
```
AI_RAG_RERANK_MODE=off                        # off | shadow | sampled | enforce
AI_RAG_RERANK_SAMPLING_RATE=0.0               # Q6 권장 0.30 (sampled mode)
AI_RAG_RERANK_CANDIDATE_N=20
AI_RAG_RERANK_TOP_N=5
AI_RAG_RERANK_CB_THRESHOLD=0.05               # circuit breaker fallback rate
AI_RAG_RERANK_CB_MIN_SAMPLES=20
AI_RAG_RERANK_CB_WINDOW_MINUTES=5
AI_RAG_RRF_OFFLINE_ENABLED=false              # production 절대 활성 금지
AI_RAG_RRF_OFFLINE_CANDIDATE_N=40
COHERE_RERANK_MODEL=rerank-v3.5
```

---

**다음 단계**: 실측 수치 채우기 → §5 Decision Tree 적용 → 운영 활성화 또는 후속 plan으로 분리.
