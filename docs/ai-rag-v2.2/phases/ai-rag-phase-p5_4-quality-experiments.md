# Phase P5.4 — 품질 실험 (Rerank + RRF)

## 메타
- 기간: ~1주 (Sprint 3)
- 의존: P5.1 (mode enum, metric), P5.2 (평가셋 v1.6), P5.3 (cache 안정화)
- 마스터: [ai-rag-phase-p5-pipeline-upgrade-master.md](./ai-rag-phase-p5-pipeline-upgrade-master.md)

## 1. 목표와 비목표

### 목표
- Cohere `rerank-v3.5` shadow → sampled 30% (Q6 적극적 패턴)
- Rerank API 회로 차단기 (실패율/지연/비용 임계)
- RRF offline comparison (4 modes: weighted, rrf, weighted+rerank, rrf+rerank)
- 최종 보고서 + Phase 3(provider A/B) 진입 결정

### 비목표
- RRF production enforcement (별도 plan, 본 plan은 offline only)
- Rerank enforce 모드 (sampled까지만 권장, enforce는 별도 결정)
- rerank-v4.0-fast/pro production 도입 (offline 비교만)

## 2. 현재 코드 기준 진입점

| 클래스 | 위치 |
|---|---|
| `RagPipelineService` | `src/main/java/org/example/shield/ai/application/RagPipelineService.java` |
| `PgLegalRetrievalService` | `src/main/java/org/example/shield/ai/infrastructure/PgLegalRetrievalService.java:220-223` |
| `RrfFusionService` | `src/main/java/org/example/shield/ai/application/RrfFusionService.java` (코드 존재, 통합 안 됨) |
| `LegalChunkJpaRepository` | `src/main/java/org/example/shield/ai/domain/LegalChunkJpaRepository.java:84-200` |
| `LegalCaseJpaRepository` | `src/main/java/org/example/shield/ai/domain/LegalCaseJpaRepository.java:86-141` |
| `AiRerankClient` (P5.1 신규 인터페이스) | `src/main/java/org/example/shield/ai/provider/AiRerankClient.java` |
| `AiRagRollbackPolicy` | (회로 차단기, 기존) |

## 3. 구현 순서

### Commit 1 — `CohereRerankClient` + `AiRerankClient` 구현 (~1일)

**의존**: P5.1 Commit 2 (interface)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/provider/cohere/CohereRerankClientAdapter.java`
- 신규: `src/main/java/org/example/shield/ai/infrastructure/CohereRerankRequest.java`
- 신규: `src/main/java/org/example/shield/ai/infrastructure/CohereRerankResponse.java`
- 수정: `application.yml` — `COHERE_RERANK_MODEL=rerank-v3.5`, `AI_RAG_RERANK_TIMEOUT_MS=2000`

**API**: `POST https://api.cohere.com/v2/rerank`

```json
{
  "model": "rerank-v3.5",
  "query": "...",
  "documents": ["...", "..."],
  "top_n": 5,
  "return_documents": false
}
```

**스켈레톤**:

```java
public interface AiRerankClient {
    RerankResult rerank(String model, String query, List<String> documents, int topN);
}

public record RerankResult(
    List<RerankedItem> items,    // (index, relevanceScore)
    long latencyMs,
    Integer inputTokens
) { }
```

**WebClient timeout 2s**, 응답 shape validation (`items` 누락 → invalid)

**테스트**:
- Unit: mock 응답 → 정확한 매핑
- Unit: timeout → throw + 호출처가 weighted fallback
- Integration: 실제 Cohere API 통합 (test profile, 작은 query)

**완료 기준**:
- [ ] `AiRerankClient` interface 정의
- [ ] Cohere 구현 + WebClient 통합
- [ ] timeout/invalid 응답 시 명확한 예외

### Commit 2 — `RerankingService` shadow mode (~1.5일)

**의존**: Commit 1

**파일**:
- 신규: `src/main/java/org/example/shield/ai/application/RerankingService.java`
- 수정: `RagPipelineService.java` — rerank 단계 추가
- 수정: `application.yml` — `AI_RAG_RERANK_MODE=off`, `AI_RAG_RERANK_CANDIDATE_N=20`, `AI_RAG_RERANK_TOP_N=5`
- 수정: `AiRagOperationalMetrics` — `recordRerankLatency`, `recordRerankFallback` 추가

**스켈레톤**:

```java
public List<RetrievedDocument> rerank(String query, List<RetrievedDocument> candidates,
                                       int topN, String conversationId) {
    if (mode == OFF) return candidates.stream().limit(topN).toList();

    boolean shouldExecute = (mode == SHADOW)
        || (mode == SAMPLED && sampler.shouldApply(conversationId, samplingRate));
    if (!shouldExecute) return candidates.stream().limit(topN).toList();

    try {
        Timer.Sample t = Timer.start();
        List<String> docs = candidates.stream().map(this::extractText).toList();
        RerankResult rr = aiRerankClient.rerank(model, query, docs, candidates.size());
        metrics.recordRerankLatency(model, t.stop(), "success");

        var reranked = rr.items().stream()
            .map(item -> withRerankScore(candidates.get(item.index()), item.relevanceScore()))
            .limit(topN).toList();

        return (mode == SHADOW) ? candidates.stream().limit(topN).toList() : reranked;
    } catch (Exception e) {
        metrics.recordRerankFallback(reasonOf(e));
        return candidates.stream().limit(topN).toList();
    }
}
```

**Shadow vs Sampled**:
- SHADOW: rerank 실행하고 score 기록만, 사용자엔 weighted topN
- SAMPLED: 일부 conversation만 reranked topN 사용

**테스트**:
- Unit: 4개 mode 분기
- Unit: rerank fail → weighted fallback
- Integration: shadow에서 metric에 rerank 점수, 사용자엔 weighted topN

**완료 기준**:
- [ ] mode 분기 + sampling deterministic
- [ ] fail/timeout 시 weighted fallback
- [ ] shadow에서 사용자 동작 변화 0
- [ ] rerank latency / fallback 메트릭 노출

### Commit 3 — Sampled 30% + Auto-OFF 회로 차단기 (~1일)

**의존**: Commit 2

**파일**:
- 수정: `application.yml` — `AI_RAG_RERANK_MODE=sampled`, `AI_RAG_RERANK_SAMPLING_RATE=0.3`
- 수정: `AiRagRollbackPolicy` — rerank fallback rate / p95 latency / 월 비용 임계 추가
- 수정: `AiRagOperationalMetrics` — `recordRerankCostEstimate(model, tokens)` 추가
- 신규: `src/main/java/org/example/shield/ai/safety/RerankCircuitBreaker.java`

**Auto-OFF 규칙**:
- fallback rate > 5% (5분 window) → `AI_RAG_RERANK_MODE=off` 동적 변경 시도
  - Q1/Q2 결과로 runtime change 불가 → **Bean 내부 atomic flag로 응답 흐름에서 by-pass** (Bean은 그대로 두고 logical OFF)
- p95 latency > 8s → `AI_RAG_RERANK_SAMPLING_RATE=0` (logical, 같은 방식)
- 월 비용 추정 > 임계 → 동일 logical OFF + Slack/이메일 알림

**스켈레톤** (CircuitBreaker):

```java
public class RerankCircuitBreaker {
    private final AtomicBoolean logicalOff = new AtomicBoolean(false);
    private final SlidingWindow<Boolean> fallbacks = new SlidingWindow<>(Duration.ofMinutes(5));

    public void recordResult(boolean fallback) {
        fallbacks.add(fallback);
        double rate = fallbacks.mean();   // boolean → 0/1
        if (rate > 0.05 && fallbacks.size() > 20) {
            if (logicalOff.compareAndSet(false, true)) {
                log.error("Rerank circuit breaker tripped (fallback rate={}). Logical OFF.", rate);
                alertingService.notify("rerank_circuit_breaker", rate);
            }
        }
    }
    public boolean isLogicalOff() { return logicalOff.get(); }
}
```

**RerankingService 사용**:

```java
if (circuitBreaker.isLogicalOff()) return weightedFallback(candidates, topN);
```

**테스트**:
- Unit: fallback rate 증가 → trip
- Unit: trip 후 5분 cool-down → reset
- Integration: synthetic 5% 실패 주입 → 회로 차단 발동

**완료 기준**:
- [ ] sampling 30% (deterministic by conversationId)
- [ ] 회로 차단기 동작
- [ ] trip 시 알림 발송 메커니즘
- [ ] runtime 변경 불가 제약 우회 (logical OFF)

### Commit 4 — RRF path-specific repository 메서드 (~1일)

**의존**: 없음 (P2-R2 offline)

**파일**:
- 수정: `LegalChunkJpaRepository.java` — `searchVectorOnly`, `searchBm25Only`, `searchTrigramOnly` 추가
- 수정: `LegalCaseJpaRepository.java` — 동일

**SQL 스켈레톤** (vector only):

```sql
SELECT id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS score
  FROM legal_chunks
 WHERE ... AND embedding IS NOT NULL
   AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
         OR category_ids && CAST(:categoryIds AS text[]) )
 ORDER BY embedding <=> CAST(:queryVector AS vector)
 LIMIT :topK
```

**HNSW ef_search 일관성**: 세 메서드 모두 `SET LOCAL hnsw.ef_search` 동일 값 (또는 별도 설정 적용)

**테스트**:
- Unit: 각 메서드의 SQL이 정상 ranked list 반환
- Integration: 동일 query에 대해 weighted SQL의 분해 검증 (세 ranked list union → weighted과 유사)

**완료 기준**:
- [ ] 3개 path-specific 메서드 (statute + case)
- [ ] 기존 `search3Way` SQL 변경 없음 (BC)
- [ ] HNSW 세션 설정 일관성

### Commit 5 — `RagPipelineService` RRF 분기 (offline only) (~1일)

**의존**: Commit 4

**파일**:
- 수정: `RagPipelineService.java` — `fusionMode` 분기
- 수정: `application.yml` — `AI_RAG_FUSION_MODE=weighted` (production은 weighted 고정)

**스켈레톤**:

```java
MixedRetrievalResult retrieved;
switch (fusionMode) {
    case WEIGHTED -> retrieved = pgLegalRetrievalService.retrieveMixed(query, ...);
    case RRF -> {
        var rankedLists = pgLegalRetrievalService.retrievePathSpecific(query, ...);
        retrieved = rrfFusionService.fuse(rankedLists, topK);
    }
}
```

**Production guard**: production은 weighted 유지. RRF는 offline `OfflineQualityReportJob` 에서만 호출 (별도 flag `AI_RAG_RRF_OFFLINE_ENABLED=false`로 production 차단)

**테스트**:
- Unit: 두 모드의 결과 형식 동일 (BC)
- Integration: RRF offline 실행 시 정상 결과
- Eval: 두 모드 모두 평가셋 1회 실행 완료

**완료 기준**:
- [ ] 두 fusion 모드 분기 동작
- [ ] Production 기본값 = weighted
- [ ] RRF는 offline에서만 실행 가능

### Commit 6 — 4-mode offline comparison report + 최종 결정 (~1.5일)

**의존**: Commit 1-5 + P5.2 평가셋

**파일**:
- 신규: `docs/ai-rag-v2.2/reports/p5_4-rerank-rrf-comparison.md`
- 신규: `eval/reports/p5_4-comparison.json` (자동 생성)
- 신규: `docs/ai-rag-v2.2/reports/p5_4-final-decision.md`

**측정 4 modes**:
1. weighted (baseline)
2. rrf
3. weighted + rerank
4. rrf + rerank

**측정 항목** (모드별):
- nDCG@5, MRR, Recall@5 (statute/case/mixed split)
- expectedReferenceMentionRate
- p50/p95 retrieval latency
- p50/p95 end-to-end latency
- 추정 비용 (Cohere rerank API tokens)
- Rerank-v4.0-fast/pro도 같은 evaluation 추가 (offline)

**Decision Tree**:
- Rerank가 nDCG@5 ≥ +0.05 + p95 ≤ 8s + 비용 허용 → sampled 30% 유지, 별도 plan에서 enforce 검토
- 미달 → mode=off
- RRF가 모든 메트릭 +0.02 이상 → 별도 plan에서 production 통합
- ±0.02 이내 → weighted 유지

**완료 기준**:
- [ ] 4-mode 비교 보고서
- [ ] Rerank 모델 3종 비교 (v3.5 / v4.0-fast / v4.0-pro)
- [ ] 최종 결정 문서 (sampled 유지/off/다음 단계 일정)
- [ ] PR Blocking Rules의 모든 가드 통과

## 4. 인터페이스/API 변경

| 인터페이스 | 변경 | BC |
|---|---|---|
| `AiRerankClient` | 신규 | N/A |
| `RerankingService` | 신규 | N/A |
| `RerankCircuitBreaker` | 신규 | N/A |
| `LegalChunkJpaRepository` | path-specific 메서드 3개 추가 | BC 유지 |
| `RagPipelineService` | fusion 분기 추가 | BC 유지 |
| `RetrievedDocument` | `rerankScore()` default method 추가 | BC 유지 |

## 5. 테스트 계획

- Unit per commit
- Integration: rerank 실제 호출 1회 (test profile, 작은 query)
- Eval: 4-mode × {nDCG, MRR, Recall, latency, cost}
- Safety: 회로 차단기 fallback 테스트, ASK_LEGAL_ADVICE 절대 skip 안 됨 검증

## 6. 완료 기준

- [ ] Rerank shadow → sampled 30% 동작
- [ ] 회로 차단기 trip 동작
- [ ] RRF 분기 offline 동작
- [ ] 4-mode 비교 보고서
- [ ] 최종 결정 문서
- [ ] p95 latency ≤ 8s, fallback rate < 1%

## 7. Rollback / Feature Flag

- `AI_RAG_RERANK_MODE=off` → mode 자체 OFF
- `AI_RAG_RERANK_SAMPLING_RATE=0.0` → 실행 안 함
- `AI_RAG_FUSION_MODE=weighted` → production 보호
- Circuit breaker는 자동 OFF 메커니즘
- 비상 시: yml 변경 → 재배포 OR atomic logical OFF (runtime change 우회)
