# Phase P5.1 — Observability + 측정 인프라

## 메타
- 기간: ~3-4일 (Sprint 1A)
- 의존: Phase C-5, P4 기준선
- 마스터: [ai-rag-phase-p5-pipeline-upgrade-master.md](./ai-rag-phase-p5-pipeline-upgrade-master.md)

## 1. 목표와 비목표

### 목표
- Cohere chat/brief/classify/embed 호출의 token/cost/latency를 Prometheus에 노출
- `RagFeatureMode` enum 도입 + startup fail-fast 파싱
- Provider interface 4개 도입 (Cohere 구현체만 wrapping)
- `EmbeddingResult` record로 embed 토큰 정확값 plumbing
- `RetrievalScoreGate` shadow 모드 추가 (decision 기록만)
- PR Template + Grafana panel JSON 초안

### 비목표
- 신규 RAG 기능 활성화 (다음 phase)
- user-facing 동작 변경 (전부 shadow/observation)
- 토큰 cost 계산 정밀화 (estimated 단가 테이블 외부화로 시작)

## 2. 현재 코드 기준 진입점

| 클래스 | 역할 | 위치 |
|---|---|---|
| `CohereService` | chat/brief/classify 호출 | `src/main/java/org/example/shield/ai/application/CohereService.java` |
| `CohereClient` | HTTP 호출 + 응답 파싱 | `src/main/java/org/example/shield/ai/infrastructure/CohereClient.java` (~138, 220) |
| `CohereEmbedResponse` | embed 응답 DTO (Meta.BilledUnits.inputTokens 존재) | `src/main/java/org/example/shield/ai/infrastructure/CohereEmbedResponse.java:87-99` |
| `QueryEmbeddingService` | embed 호출 entry | `src/main/java/org/example/shield/ai/application/QueryEmbeddingService.java:35-38` |
| `RetrievalScoreGate` | filter 정책 (현재 enabled=false) | `src/main/java/org/example/shield/ai/application/RetrievalScoreGate.java` |
| `AiRagOperationalMetrics` | 중앙 metric 발행 | `src/main/java/org/example/shield/ai/infrastructure/AiRagOperationalMetrics.java` |
| `AiCallResult` | chat/brief 반환 (tokens 포함) | (record) |

## 3. 구현 순서

### Commit 1 — `RagFeatureMode` enum + fail-fast 파싱 (~0.5일)

**의존**: 없음 (다른 commit의 기반)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/config/RagFeatureMode.java`
- 신규: `src/main/java/org/example/shield/ai/config/RagFeatureModeConverter.java` (Spring `Converter<String, RagFeatureMode>`)
- 수정: `src/main/java/org/example/shield/ai/config/AiConfig.java` 또는 신규 config 클래스 — Converter 등록

**스켈레톤**:

```java
public enum RagFeatureMode {
    OFF, SHADOW, SAMPLED, ENFORCE;

    public static RagFeatureMode fromOrThrow(String value, String flagName) {
        if (value == null || value.isBlank()) return OFF;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Invalid mode '%s' for %s. Allowed: off|shadow|sampled|enforce"
                .formatted(value, flagName));
        }
    }
}
```

**테스트**:
- Unit: `RagFeatureModeTest` — 4개 정상 + invalid → throw + null/blank → OFF
- Integration: 잘못된 flag로 Spring context startup 시도 → fail-fast 확인

**완료 기준**:
- [ ] enum + Converter 등록
- [ ] invalid mode 값으로 Spring context 부팅 시 startup fail
- [ ] application.yml에서 모든 새 flag가 RagFeatureMode 타입으로 파싱됨

### Commit 2 — Provider interface 4개 + Cohere wrapping (~1일)

**의존**: 없음 (독립)

**파일**:
- 신규:
  - `src/main/java/org/example/shield/ai/provider/AiChatClient.java`
  - `src/main/java/org/example/shield/ai/provider/AiEmbeddingClient.java`
  - `src/main/java/org/example/shield/ai/provider/AiRerankClient.java` (Commit P5.4-1까지는 미구현 OK)
  - `src/main/java/org/example/shield/ai/provider/AiClassificationClient.java`
  - `src/main/java/org/example/shield/ai/provider/cohere/CohereChatClientAdapter.java`
  - `src/main/java/org/example/shield/ai/provider/cohere/CohereEmbeddingClientAdapter.java`
  - `src/main/java/org/example/shield/ai/provider/cohere/CohereClassificationClientAdapter.java`
- 수정: `CohereService`, `QueryEmbeddingService`, `IntentClassificationService` 진입점만 interface 의존으로 전환 (기존 `CohereClient`는 그대로 두고 adapter가 wrapping)

**스켈레톤** (예: AiEmbeddingClient):

```java
public interface AiEmbeddingClient {
    EmbeddingResult embedQuery(String model, String text);
    EmbeddingResult embedDocuments(String model, List<String> texts);
}
```

**원칙**:
- Cohere 응답 shape는 adapter 내부에서만. DTO는 provider-neutral
- `CohereClient`는 그대로, adapter만 신규 (low-risk wrapping)

**테스트**:
- Unit: 각 adapter가 `CohereClient`에 정확히 위임하는지 mock 테스트
- Integration: 기존 `CohereService` 동작이 변하지 않음 확인 (regression)

**완료 기준**:
- [ ] 4개 interface 정의
- [ ] Cohere adapter 3개 (Rerank는 P5.4-1)
- [ ] `CohereService`/`QueryEmbeddingService`/`IntentClassificationService` 가 interface 사용
- [ ] 기존 단위 테스트 통과

### Commit 3 — `EmbeddingResult` record + embed 토큰 plumbing (~0.5일)

**의존**: Commit 2 (provider interface 존재)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/provider/EmbeddingResult.java`
- 수정: `CohereClient.java:89-103` (embedQuery/embedDocuments 반환 타입), `:105-138` (callEmbed return)
- 수정: `CohereEmbeddingClientAdapter` — 반환 매핑
- 수정: `QueryEmbeddingService.java:35-38` — `float[]` → `EmbeddingResult` 사용
- 수정: `CivilLawIngestService` 호출처 — 토큰 로깅

**스켈레톤**:

```java
public record EmbeddingResult(
    String responseId,
    List<float[]> vectors,
    Integer inputTokens,   // Cohere meta.billed_units.input_tokens (정확값)
    long latencyMs
) {
    public float[] firstVector() {
        return vectors == null || vectors.isEmpty() ? null : vectors.get(0);
    }
}
```

`CohereClient.callEmbed`:

```java
return new EmbeddingResult(
    resp.getId(),
    resp.extractAllFloatVectors(),
    Optional.ofNullable(resp.getMeta())
        .map(CohereEmbedResponse.Meta::getBilledUnits)
        .map(CohereEmbedResponse.BilledUnits::getInputTokens)
        .orElse(null),
    latencyMs
);
```

**테스트**:
- Unit: `CohereClientTest` — meta.billed_units.input_tokens 파싱 검증 (mock JSON 응답)
- Integration: 평가셋 1회 실행 시 `EmbeddingResult.inputTokens` non-null

**완료 기준**:
- [ ] `embedQuery/embedDocuments` 반환이 `EmbeddingResult`
- [ ] `inputTokens` 필드가 응답에서 채워짐 (non-null)
- [ ] 호출처 일괄 업데이트, 컴파일 통과
- [ ] 기존 vector 결과는 동일 (regression 없음)

### Commit 4 — Cohere token/cost/latency metrics (~1일)

**의존**: Commit 3

**파일**:
- 수정: `AiRagOperationalMetrics.java` — 신규 메서드 4개 추가
- 수정: `CohereService.java` — chat (~92), brief (~114) AiCallResult 직후 emit
- 수정: `CohereEmbeddingClientAdapter` — embed 호출 후 emit
- 수정: `IntentClassificationService` — classify 호출 후 emit
- 수정: `application.yml` — 모델별 단가 테이블 (cohere.pricing.*) 외부화

**스켈레톤**:

```java
public void recordCohereTokens(String model, String operation,
                                String direction, int tokens, boolean estimated) {
    Counter.builder("shield.ai.cohere.tokens")
        .tag("model", model)
        .tag("operation", operation)
        .tag("direction", direction)
        .tag("estimated", String.valueOf(estimated))
        .register(meterRegistry)
        .increment(tokens);
}
public void recordCohereEstimatedCost(String model, String operation, double amountUsd) {
    DistributionSummary.builder("shield.ai.cohere.cost.estimated.usd")
        .tag("model", model).tag("operation", operation)
        .register(meterRegistry).record(amountUsd);
}
public void recordCohereLatency(String model, String operation,
                                 Duration duration, String status) {
    Timer.builder("shield.ai.cohere.latency")
        .tag("model", model).tag("operation", operation).tag("status", status)
        .register(meterRegistry).record(duration);
}
```

`CohereService.chat` 후:

```java
AiCallResult<ChatParsedResponse> result = aiChatClient.chat(...);
metrics.recordCohereTokens(model, "chat", "input", result.tokensInput(), false);
metrics.recordCohereTokens(model, "chat", "output", result.tokensOutput(), false);
metrics.recordCohereEstimatedCost(model, "chat", estimateCost(model, result));
metrics.recordCohereLatency(model, "chat", Duration.ofMillis(result.latencyMs()), "success");
```

**메트릭 emit은 best-effort**: try-catch로 감싸 metric 실패가 request 실패를 유발하지 않음.

**Pricing 테이블 (application.yml)**:

```yaml
cohere:
  pricing:
    command-a-03-2025:
      input-per-million: 2.50
      output-per-million: 10.00
    command-r7b-12-2024:
      input-per-million: 0.0375
      output-per-million: 0.15
    embed-v4.0:
      input-per-million: 0.10
```

**테스트**:
- Unit: `AiRagOperationalMetricsTest` — 각 record 메서드 호출 후 MeterRegistry 검증
- Integration: 평가셋 1회 실행 후 `/actuator/prometheus`에 `shield_ai_cohere_tokens_total{model="command-a-03-2025",operation="chat",direction="output"}` non-zero
- Reconciliation: sum(tokens, direction=output, operation=chat) ≈ sum(Message.tokensOutput) (±1%)

**완료 기준**:
- [ ] 4개 record 메서드 구현
- [ ] chat/brief/classify/embed 4개 operation에서 emit
- [ ] `/actuator/prometheus`에 메트릭 노출
- [ ] reconciliation 테스트 통과 (±1%)
- [ ] metric 실패 시 request 정상 동작 (try-catch 단위 테스트)

### Commit 5 — `RetrievalScoreGate` shadow mode + `ConversationDeterministicSampler` (~1일)

**의존**: Commit 1 (`RagFeatureMode`)

**파일**:
- 수정: `RetrievalScoreGate.java` — mode 분기 추가, shadow 시 decision만 emit
- 신규: `src/main/java/org/example/shield/ai/util/ConversationDeterministicSampler.java`
- 수정: `application.yml` — `AI_RAG_RETRIEVAL_GATE_MODE=off`
- 수정: `AiRagOperationalMetrics.recordRetrievalGate` 메서드 (`mode` 태그 추가)

**스켈레톤** (Gate):

```java
public List<? extends RetrievedDocument> filter(
        List<? extends RetrievedDocument> candidates,
        RetrievalScoreMethod method) {
    if (mode == OFF) return candidates;
    var threshold = thresholdFor(method);
    var decisions = candidates.stream()
        .map(doc -> new GateDecision(doc, evaluate(doc, threshold)))
        .toList();
    decisions.forEach(d ->
        metrics.recordRetrievalGate(mode.name().toLowerCase(),
            d.passed() ? "pass" : "drop",
            method.name().toLowerCase(),
            documentTypeOf(d.doc())));
    if (mode == SHADOW) return candidates;   // 결정만 기록
    return decisions.stream().filter(GateDecision::passed)
        .map(GateDecision::doc).toList();
}
```

**스켈레톤** (Sampler):

```java
public class ConversationDeterministicSampler {
    public static boolean shouldApply(String conversationId, double rate) {
        if (rate <= 0.0) return false;
        if (rate >= 1.0) return true;
        int hash = Math.floorMod(Hashing.murmur3_32_fixed()
            .hashString(conversationId, StandardCharsets.UTF_8).asInt(), 100);
        return hash < (rate * 100);
    }
}
```

**테스트**:
- Unit: `RetrievalScoreGateTest` — OFF/SHADOW/ENFORCE 분기 테스트
- Unit: `ConversationDeterministicSamplerTest` — 같은 conversationId는 같은 결과, 분포가 rate에 근접 (10000 conv × rate=0.3 → 2800~3200)
- Eval: shadow 모드에서 retrieval 결과가 변하지 않음 (regression)

**완료 기준**:
- [ ] Gate shadow 모드에서 decision 메트릭만 기록
- [ ] Gate enforce 모드에서 threshold 미만 필터링
- [ ] `ConversationDeterministicSampler` 분포 검증 통과
- [ ] 평가셋 shadow 실행 시 Recall@5 / nDCG@5 변화 0

### Commit 6 — PR Template + Grafana panel JSON + Prometheus snippets (~0.5일)

**의존**: Commit 4 (메트릭 노출)

**파일**:
- 신규: `.github/PULL_REQUEST_TEMPLATE.md`
- 신규: `docs/ai-rag-v2.2/dashboards/grafana-p5-observability.json`
- 신규: `docs/ai-rag-v2.2/prometheus-queries-p5.md`

**PR Template 핵심**: Summary / Feature Flag (mode + rollback "redeploy required") / Evaluation / Safety / Rollout 섹션

**Grafana panels**:
- Cohere tokens by model/operation/direction
- Estimated cost by model/operation
- p50/p95 latency by operation
- Retrieval gate decision rate (mode/decision/document_type)

**완료 기준**:
- [ ] PR template 존재 + 새 PR에서 자동 적용
- [ ] Grafana dashboard JSON import 가능
- [ ] Prometheus query 예시 6개 이상

## 4. 인터페이스/API 변경

| 인터페이스 | 변경 | BC |
|---|---|---|
| `AiChatClient`/`AiEmbeddingClient`/`AiRerankClient`/`AiClassificationClient` | 신규 | N/A |
| `EmbeddingResult` | 신규 record | N/A |
| `CohereClient.embedQuery/embedDocuments` | `float[]` → `EmbeddingResult` 반환 | Breaking (호출처 모두 업데이트) |
| `RetrievalScoreGate.filter()` | mode 분기 추가, 시그니처 동일 | BC 유지 |
| `RagFeatureMode` | 신규 enum | N/A |
| `AiRagOperationalMetrics` | 신규 메서드 추가 | BC 유지 |

## 5. 테스트 계획

- **Unit**: 각 commit에 명시
- **Integration**: 평가셋 1회 (40건 v1.5) 실행 + `/actuator/prometheus` curl
- **Regression**: 기존 chat/embed/classify 단위 테스트 전체 통과
- **Token reconciliation**: sum(tokens output) ≈ sum(Message.tokensOutput) (±1%)

## 6. 완료 기준

- [ ] Commit 1-6 모두 머지
- [ ] `/actuator/prometheus`에 `shield.ai.cohere.tokens`, `shield.ai.cohere.cost.estimated.usd`, `shield.ai.cohere.latency`, `shield.rag.retrieval_gate{mode=shadow}` 노출
- [ ] Invalid mode flag로 Spring context 부팅 시 fail-fast
- [ ] Provider interface 4개 정의 + Cohere adapter 3개 (Rerank 제외)
- [ ] Embed `inputTokens` 정확값 수집
- [ ] Retrieval gate shadow에서 사용자 동작 변화 0
- [ ] Grafana dashboard import 동작
- [ ] PR template 활성

## 7. Rollback / Feature Flag

- `COHERE_TOKEN_METRICS_ENABLED=true` (기본 ON, 관측 전용, OFF 시 metric만 끔)
- `AI_RAG_RETRIEVAL_GATE_MODE=off` (기본 OFF)
- 비상 시: yml flag 변경 → 재배포 (~10분). 회로 차단기는 P5.4에서 도입.
