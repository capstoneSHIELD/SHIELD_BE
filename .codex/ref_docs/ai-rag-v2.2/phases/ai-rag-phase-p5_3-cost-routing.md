# Phase P5.3 — 비용/성능 Quick Wins

## 메타
- 기간: ~1주 (Sprint 2)
- 의존: P5.1 (mode enum, metric), P5.2 (평가셋 v1.6, baseline)
- 마스터: [ai-rag-phase-p5-pipeline-upgrade-master.md](./ai-rag-phase-p5-pipeline-upgrade-master.md)

## 1. 목표와 비목표

### 목표
- Caffeine L1 embedding cache 도입 (반복 평가/멀티턴 상담 비용 절감)
- Intent-aware retrieval shadow → **GREETING-only skip enforce**
- Context budget shadow 측정 (enforcement는 본 plan 제외)

### 비목표
- Redis L2 cache (멀티 인스턴스 전환 시 별도)
- IRRELEVANT skip enforce (GREETING 정밀도 확보 후 별도 PR)
- Context budget enforce (다음 plan)
- ASK_LEGAL_ADVICE / CHANGE_TOPIC skip (절대 금지)

## 2. 현재 코드 기준 진입점

| 클래스 | 위치 |
|---|---|
| `QueryEmbeddingService` | `src/main/java/org/example/shield/ai/application/QueryEmbeddingService.java` |
| `IntentAwareRetrievalPolicy` | `src/main/java/org/example/shield/ai/application/IntentAwareRetrievalPolicy.java:12,23-24` |
| `RagPipelineService` | `src/main/java/org/example/shield/ai/application/RagPipelineService.java:64-155` |
| `RagContextBuilder` | `src/main/java/org/example/shield/ai/application/RagContextBuilder.java:71-135` |
| `build.gradle` | Caffeine 의존성 추가 대상 |

## 3. 구현 순서

### Commit 1 — `EmbeddingCache` 인터페이스 + Noop 구현 + Caffeine 의존성 (~0.5일)

**의존**: P5.1 Commit 3 (EmbeddingResult)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/cache/EmbeddingCache.java` (interface)
- 신규: `src/main/java/org/example/shield/ai/cache/NoopEmbeddingCache.java`
- 신규: `src/main/java/org/example/shield/ai/cache/EmbeddingCacheConfig.java`
- 수정: `build.gradle` — `implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'`

**스켈레톤**:

```java
public interface EmbeddingCache {
    Optional<float[]> get(EmbeddingCacheKey key);
    void put(EmbeddingCacheKey key, float[] vector);
}

public record EmbeddingCacheKey(
    String provider, String model, String inputType,
    int dimension, String normalizeVersion, String contentHash
) {
    public String toCacheKey() {
        return "embed:%s:%s:%s:%d:%s:%s".formatted(
            provider, model, inputType, dimension, normalizeVersion, contentHash);
    }
}
```

**Config**:

```java
@ConditionalOnProperty(name="ai.embedding.cache.mode", havingValue="off", matchIfMissing=true)
@Bean public EmbeddingCache noopCache() { return new NoopEmbeddingCache(); }
```

**테스트**: Noop은 항상 empty 반환

**완료 기준**:
- [ ] 인터페이스 + Noop + key 정의
- [ ] Caffeine 의존성 추가
- [ ] 기본 모드 OFF → Noop bean 등록

### Commit 2 — `CaffeineEmbeddingCache` + `QueryEmbeddingService` 통합 + 메트릭 (~1.5일)

**의존**: Commit 1

**파일**:
- 신규: `src/main/java/org/example/shield/ai/cache/CaffeineEmbeddingCache.java`
- 수정: `EmbeddingCacheConfig` — mode=l1 분기
- 수정: `QueryEmbeddingService.java` — lookup 통합
- 수정: `AiRagOperationalMetrics.recordEmbeddingCache(model, outcome)`

**스켈레톤** (Caffeine):

```java
public class CaffeineEmbeddingCache implements EmbeddingCache {
    private final Cache<String, float[]> cache = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .build();

    @Override public Optional<float[]> get(EmbeddingCacheKey key) {
        return Optional.ofNullable(cache.getIfPresent(key.toCacheKey()));
    }
    @Override public void put(EmbeddingCacheKey key, float[] vector) {
        cache.put(key.toCacheKey(), vector);
    }
}
```

**QueryEmbeddingService 통합**:

```java
public float[] embedQuery(String query) {
    var key = new EmbeddingCacheKey("cohere", model, "search_query",
        dim, "v1", sha256(normalize(query)));
    return cache.get(key).map(v -> {
        metrics.recordEmbeddingCache(model, "hit");
        return v;
    }).orElseGet(() -> {
        metrics.recordEmbeddingCache(model, "miss");
        EmbeddingResult result = ragMetrics.timeCohereEmbed(
            () -> aiEmbeddingClient.embedQuery(model, query));
        float[] vec = result.firstVector();
        if (vec != null) cache.put(key, vec);
        return vec;
    });
}
```

**Normalize**: trim → collapse whitespace → lower-case (한글 영향 없음, 영문/숫자 정규화)

**Stampede 방지**: Caffeine `loadingCache` 또는 `ConcurrentHashMap<String, CompletableFuture<float[]>>`로 동시 호출 중복 제거

**테스트**:
- Unit: HIT/MISS, 만료, normalize 일관성
- Integration: 동일 query 2회 호출 시 2번째 HIT, vector 동일
- Stampede: 같은 key 동시 10개 호출 시 provider 1회만 호출
- Eval regression: nDCG@5 변화 ±0.005 이내

**완료 기준**:
- [ ] Caffeine 구현 동작
- [ ] `AI_EMBEDDING_CACHE_MODE=l1`에서 HIT 발생
- [ ] 메트릭 `shield.ai.embedding.cache{outcome=hit|miss}` 노출
- [ ] 5턴 시뮬 embed API ≥30% 감소
- [ ] 평가셋 회귀 없음

### Commit 3 — `IntentAwareRetrievalPolicy` shadow + 메트릭 (~1일)

**의존**: P5.1 Commit 1 (mode enum)

**파일**:
- 수정: `IntentAwareRetrievalPolicy.java` — `RagFeatureMode` 분기 추가
- 수정: `RagPipelineService.java:64-155` — shadow에서 결정만 로깅
- 수정: `AiRagOperationalMetrics` — `recordIntentRouting(mode, intent, decision, confidence_bucket)` 추가
- 수정: `application.yml` — `AI_RAG_INTENT_AWARE_MODE=off`

**Shadow 동작**: routing decision은 결정하지만 실제 retrieval 동작은 baseline 유지

**스켈레톤**:

```java
RetrievalStrategyDecision decision = policy.decide(intent, defaultTopK);
metrics.recordIntentRouting(mode.name(), intent.dialogueIntent().name(),
    decision.routeName(), bucketize(intent.intentConfidence()));
if (mode == OFF || mode == SHADOW) {
    return RetrievalStrategyDecision.baseline(defaultTopK, "shadow");
}
return decision;
```

**테스트**:
- Unit: 모든 mode/intent 조합
- Integration: shadow에서 retrieval 결과 변화 0, decision 메트릭 non-zero
- Eval: v1.6의 GREETING/IRRELEVANT/ASK_LEGAL_ADVICE 분포가 메트릭에 반영

**완료 기준**:
- [ ] Policy mode 분기 동작
- [ ] Shadow에서 user-facing 변화 0
- [ ] 메트릭 노출

### Commit 4 — GREETING-only skip enforce (~1일)

**의존**: Commit 3 + P5.2 평가셋

**파일**:
- 수정: `IntentAwareRetrievalPolicy.java` — `GREETING_SKIP_ENABLED` flag 분기
- 수정: `application.yml` — `AI_INTENT_ROUTER_ENABLE_GREETING_SKIP=true` (active commit)
- 수정: `AI_INTENT_ROUTER_GREETING_MIN_CONFIDENCE=0.90`

**Critical Constraint**: ASK_LEGAL_ADVICE / CHANGE_TOPIC는 절대 skip 안 됨

**스켈레톤** (decide 메서드 안):

```java
case GREETING -> {
    if (greetingSkipEnabled && confidence >= greetingMinConfidence) {
        return RetrievalStrategyDecision.skip(defaultTopK, "greeting_high_confidence");
    }
    yield RetrievalStrategyDecision.baseline(defaultTopK, "greeting_low_confidence");
}
case ASK_LEGAL_ADVICE -> RetrievalStrategyDecision.baseline(...);  // 절대 skip 없음
```

**테스트**:
- Unit: GREETING + confidence ≥ 0.90 → skip
- Unit: GREETING + confidence < 0.90 → baseline
- Unit: ASK_LEGAL_ADVICE + confidence 0.99 → baseline (절대 skip 안 됨)
- Eval: v1.6의 GREETING 10건 중 ≥ 9건 skip, ASK_LEGAL_ADVICE 0건 skip
- Eval: legal advice false-skip rate = 0%

**완료 기준**:
- [ ] GREETING + high confidence skip
- [ ] ASK_LEGAL_ADVICE 절대 skip 안 됨 (false-skip rate = 0)
- [ ] GREETING skip 정밀도 ≥ 95% (v1.6 평가셋)
- [ ] Recall@5 회귀 없음 (legal advice 케이스에서)

### Commit 5 — `RagContextBuilder` budget shadow (~1.5일)

**의존**: P5.1 Commit 1, P5.2 평가셋

**파일**:
- 수정: `RagContextBuilder.java` — budget-aware overload + shadow 동작
- 수정: `RagPipelineService` — budget 메서드 호출 결정
- 수정: `AiRagOperationalMetrics` — `recordContextBudget(kind, action, reason, estimatedTokens)` 추가
- 수정: `application.yml` — `AI_RAG_CONTEXT_BUDGET_MODE=off`, `AI_RAG_CONTEXT_BUDGET_TOKENS=2000`

**스켈레톤**:

```java
public String build(MixedRetrievalResult mixed, String intentSummary) {
    return build(mixed, intentSummary, Integer.MAX_VALUE);   // 기존 시그니처 유지
}

public String build(MixedRetrievalResult mixed, String intentSummary, int tokenBudget) {
    if (budgetMode == OFF) {
        return build(mixed, intentSummary);   // budget 무시
    }
    BudgetPlan plan = planTrimAndDrop(mixed, tokenBudget);
    metrics.recordContextBudget("statute", "trimmed", "budget", plan.statuteTrimTokens());
    metrics.recordContextBudget("statute", "dropped", "budget", plan.statuteDropCount());
    metrics.recordContextBudget("case", "trimmed", "budget", plan.caseTrimTokens());
    metrics.recordContextBudget("case", "dropped", "budget", plan.caseDropCount());
    metrics.recordContextBudget("total", "estimated", "tokens", plan.estimatedTokens());
    if (budgetMode == SHADOW) {
        return build(mixed, intentSummary);   // 계산만 기록, 결과는 원본
    }
    // ENFORCE는 본 plan 범위 밖
    throw new UnsupportedOperationException("Budget ENFORCE is out of scope for P5.3");
}
```

**원칙** (enforce 시 적용될 — 본 plan은 shadow까지):
- citation metadata는 절대 제거 금지
- statute/case 모두 있을 때 minimum 1개씩 보존
- chunk body trim 우선, 전체 drop은 마지막

**테스트**:
- Unit: shadow에서 결과 텍스트가 baseline과 동일
- Unit: 큰 input에서 plan이 적절히 trim/drop 수치 계산
- Eval: shadow 모드에서 nDCG/MRR/Recall 변화 0

**완료 기준**:
- [ ] Budget shadow에서 user-facing 변화 0
- [ ] trim/drop 메트릭 노출
- [ ] enforce 호출 시 UnsupportedOperationException (의도적 미구현)

## 4. 인터페이스/API 변경

| 인터페이스 | 변경 | BC |
|---|---|---|
| `EmbeddingCache` | 신규 | N/A |
| `EmbeddingCacheKey` | 신규 | N/A |
| `RagContextBuilder.build()` | 오버로드 추가 | BC 유지 |
| `IntentAwareRetrievalPolicy` | mode 분기 | BC 유지 (OFF default) |

## 5. 테스트 계획

- Unit per commit
- Eval regression: v1.6 평가셋에서 nDCG@5 ±0.005, Recall@5 -1%p 이내
- Cost: 5턴 시뮬 embed API ≥30% 감소
- Safety: ASK_LEGAL_ADVICE false-skip = 0

## 6. 완료 기준

- [ ] Caffeine cache HIT 동작
- [ ] GREETING-only skip enforce + 정밀도 ≥ 95%
- [ ] Context budget shadow 메트릭 수집
- [ ] 모든 회귀 가드 통과

## 7. Rollback / Feature Flag

- `AI_EMBEDDING_CACHE_MODE=off` → Noop bean
- `AI_RAG_INTENT_AWARE_MODE=off` → baseline routing
- `AI_INTENT_ROUTER_ENABLE_GREETING_SKIP=false` → skip 비활성
- `AI_RAG_CONTEXT_BUDGET_MODE=off` → budget 미동작
- 비상 시: yml 변경 → 재배포
