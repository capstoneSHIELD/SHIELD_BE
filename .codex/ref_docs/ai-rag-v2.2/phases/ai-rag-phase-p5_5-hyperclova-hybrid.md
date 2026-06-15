# Phase P5.5 — Cohere × HyperCLOVA X 하이브리드 (한국 법률 생성·판정 레이어)

> **상태 (2026-05-26 신설)**: 본 phase는 provider 교체가 아닌 **역할 분리** 전략 문서.
> 분석 근거: 3개 외부 모델(GPT-5.5 Thinking / Claude Opus 4.7 Thinking 등) 공동 평가 (사용자 조사).
> P5.4 이후 또는 병행 진행 가능. 본 phase는 **구현 plan이 아니라 의사결정 기록 + 단계별 실행 순서 가이드**.

## 메타

- 기간: 2~3주 (Judge shadow 1주 + Chat/Brief shadow 1~2주)
- 의존: P5.1 완료 (provider interface 4개), P5.2 완료 (PII masker + Sampler + baseline 인프라)
- 마스터: [ai-rag-phase-p5-pipeline-upgrade-master.md](./ai-rag-phase-p5-pipeline-upgrade-master.md)
- 후속: P5.4 (Rerank + RRF)와 병행 가능 — 본 phase는 Chat/Brief/Judge 레이어, P5.4는 RAG 검색 레이어

## 1. 핵심 결론 — 전면 교체 ≠ 합리적

세 외부 모델 공동 합의: **Cohere를 전면 교체하지 않는다**. 근거:

1. **결합도**: 현재 6개 클래스가 Cohere에 직접 결합 (`CohereService`, `CohereClient`, `CohereMetricEmitter`, `CohereCostCalculator`, `CohereEmbeddingClientAdapter`, `CoherePricingProperties`).
2. **DB 스키마 의존**: `vector(1024)` 차원이 Cohere `embed-v4.0` 출력에 맞춰져 있음 → 다른 provider 사용 시 코퍼스 재인덱싱 필요.
3. **3-way 가중치 튜닝**: `vector=0.5 / keyword=0.3 / trigram=0.2`는 Cohere 임베딩 출력 분포 기준으로 캘리브레이션됨.
4. **진행 중 리팩토링**: P5.1 Commit 4의 단가 외부화·메트릭 중앙화가 완료된 시점에 provider를 갈아엎으면 본 작업이 무의미.

동시에: **한국 법률 도메인의 최종 사용자 대면 레이어(Chat / Brief 생성)**에서 HyperCLOVA X가 Cohere보다 나을 가능성이 높음. 근거:

- 네이버 공식 발표: KMMLU에서 HyperCLOVA X가 한국 특화 지식 영역에서 GPT-4를 상회
- 한국 법률 서비스에서 가장 중요한 "비조언적 안내 톤", "법조 존댓말 문체", "한국 실무 용어 자연성"은 한국어 특화 학습 데이터 이점이 직접 반영되는 영역

## 2. 전략 — 역할 분리 (하이브리드)

| 레이어 | 책임 | 권장 provider | 사유 |
|---|---|---|---|
| **RAG 백본** | Embed / Rerank / Classify | **Cohere 유지** | 기존 인덱싱 + 3-way 튜닝 + rerank-v3.5 한국어 공식 지원 |
| **Chat / Brief 생성** | 사용자 대면 답변 + 의뢰서 작성 | **HyperCLOVA X 후보** | 한국어 법조 톤·존댓말·실무 용어 자연성 |
| **Judge (출력 컴플라이언스)** | GuardrailFilter regex가 못 잡는 의미론적 법적 단정 감지 | **HyperCLOVA X (adversarial diversity)** | Cohere가 생성한 답변을 다른 벤더로 평가 → blind spot 분리 |

### 2.1 핵심 분기점: `OutputComplianceShadowJudge` 본체로 HyperCLOVA X

가장 흥미로운 합의점은 **현재 미구현인 judge 본체로 HyperCLOVA X를 도입하는 것이 최적**이라는 판단.

**이유**:
- 동일 벤더 self-judgement는 blind spot 공유 위험
- GuardrailFilter의 ~40 regex 패턴이 못 잡는 의미론적 법적 단정 (예: "대항력을 인정하는 경향이 있습니다", "승소 가능성이 있어 보입니다") 감지는 한국 법조 문맥 학습이 결정적
- 현재 feature flag 구조(`shadow-enabled=false`, `sampling-rate=0.0`, `max-cost-ratio=0.10`, `max-p95-latency-increase-ms=200`)가 이미 갖춰져 있어 **저가 모델(HyperCLOVA X DASH-002)로 sampling 1%부터 안전 시작 가능**

## 3. 실행 순서 (운영 안전성 우선)

GPT-5.5 Thinking은 "Chat 비교 1순위" (제품 관점), Claude Opus 4.7 Thinking은 "Judge 1순위" (운영 관점). **SHIELD의 변호사법 리스크 + 평가 인프라 미완성 상황에서 Judge 우선이 합리적**:

```
[완료] P5.1 Commit 4 — 메트릭 emit 중앙화 + 단가 외부화 + emitter 추출
[완료] P5.1 Commit 2 — AiChatClient / AiEmbeddingClient / AiRerankClient / AiClassificationClient interface
   ↓
[P5.5 Commit 1] HyperCLOVA X SDK 통합 + AiJudgeClient 인터페이스 신설
   ↓
[P5.5 Commit 2] OutputComplianceShadowJudge에 HyperCLOVA X judge 연결 (sampling 1% shadow)
   ↓
[P5.5 Commit 3] Judge 데이터 1~2주 축적 + false positive/negative 표본 검토
   ↓
[P5.5 Commit 4] HyperCLOVA X Chat/Brief shadow 평가 (오프라인 50~100건 baseline 비교)
   ↓
[P5.5 Commit 5] 최종 결정 — Chat 전환 / Brief 전환 / Judge만 유지 / 모두 보류
```

## 4. Commit 분해

### Commit 1 — `AiJudgeClient` 인터페이스 + HyperCLOVA X SDK 통합 (~1.5일)

**의존**: P5.1 Commit 2 (provider 패키지 존재)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/provider/AiJudgeClient.java`
  - 메서드: `JudgeResult judge(String maskedResponse, JudgeRequest request)`
- 신규: `src/main/java/org/example/shield/ai/provider/JudgeResult.java` (record)
  - 필드: `complianceVerdict` (PASS/SOFT_VIOLATION/HARD_VIOLATION), `confidence`, `reason`, `categories[]` (e.g. "legal_conclusion", "case_citation")
- 신규: `src/main/java/org/example/shield/ai/provider/hyperclova/HyperClovaJudgeClient.java`
- 신규: `src/main/java/org/example/shield/ai/config/HyperClovaApiConfig.java`
  - WebClient bean, API key from `HYPERCLOVA_API_KEY` env
- 신규: `src/main/resources/ai/prompts/judge/legal-compliance-judge.md` (Judge 프롬프트 — 한국 변호사법 위반 패턴 가이드)

**스켈레톤**:
```java
public interface AiJudgeClient {
    JudgeResult judge(String maskedResponse, JudgeRequest request);
}

public record JudgeResult(
    Verdict verdict,             // PASS | SOFT_VIOLATION | HARD_VIOLATION
    double confidence,           // 0.0 ~ 1.0
    String reason,               // 짧은 자연어 설명
    List<String> categories,     // e.g. ["legal_conclusion", "win_prediction"]
    int inputTokens,
    int outputTokens,
    long latencyMs
) {
    public enum Verdict { PASS, SOFT_VIOLATION, HARD_VIOLATION }
}
```

**테스트**:
- Unit: mock WebClient 응답 → JudgeResult 매핑 정확성
- Integration (선택): 실제 HyperCLOVA X test profile 호출 1회

**완료 기준**:
- [ ] `AiJudgeClient` interface 정의
- [ ] HyperCLOVA adapter + WebClient 설정
- [ ] Judge 프롬프트 작성 (regex가 못 잡는 패턴 + 변호사법 조항 명시)

### Commit 2 — `OutputComplianceShadowJudge`에 judge 본체 연결 (shadow 1%) (~1일)

**의존**: Commit 1

**파일**:
- 수정: [OutputComplianceShadowJudge.java](../../src/main/java/org/example/shield/ai/application/OutputComplianceShadowJudge.java) — sampled 시 `aiJudgeClient.judge()` 호출
- 수정: [application.yml](../../src/main/resources/application.yml) — `app.ai.output-judge.judge-provider=hyperclova` 추가
- 수정: [AiRagOperationalMetrics](../../src/main/java/org/example/shield/ai/infrastructure/AiRagOperationalMetrics.java) — `recordJudgeOutcome(provider, verdict, confidence_bucket)` 추가

**스켈레톤** (judge 호출 부분):
```java
public OutputComplianceResult evaluate(String response, String conversationId) {
    boolean deterministic = guardrailFilter.containsForbiddenText(response);
    boolean sampled = shouldSampleByConversation(conversationId);

    JudgeResult judgeResult = null;
    if (sampled && aiJudgeClient != null) {
        try {
            String masked = piiMasker.mask(response);
            judgeResult = aiJudgeClient.judge(masked, JudgeRequest.legalCompliance(masked));
            metrics.recordJudgeOutcome("hyperclova",
                judgeResult.verdict().name(),
                bucketize(judgeResult.confidence()));
        } catch (Exception e) {
            log.warn("Judge call failed (provider=hyperclova): {}", e.getMessage());
            // fail-open: judgeResult=null, 운영 차단 안 함
        }
    }
    // 결과는 OutputComplianceResult에 judgeResult 포함
    ...
}
```

**중요**: 
- judge 결과는 **운영 차단에 절대 사용 안 함** (shadow only)
- judge 호출 실패는 fail-open (request 정상 진행)
- 비용/지연 가드는 기존 `max-cost-ratio`, `max-p95-latency-increase-ms` 활용

**완료 기준**:
- [ ] sampling 시점에만 judge 호출
- [ ] judge 결과를 메트릭 + OutputComplianceResult에 포함 (운영 미차단)
- [ ] 비용 가드 위반 시 자동 sampling 중단 (회로 차단)

### Commit 3 — Judge 데이터 축적 + 표본 검토 (~1~2주 운영)

**활동**:
- Production 환경에 `AI_OUTPUT_JUDGE_SHADOW_ENABLED=true`, `AI_OUTPUT_JUDGE_SAMPLING_RATE=0.01` (1%)
- Grafana 패널 추가:
  - `shield.ai.output_judge.shadow` 분포 (PASS / SOFT_VIOLATION / HARD_VIOLATION)
  - HyperCLOVA judge latency p50/p95
  - HyperCLOVA judge 비용 (`shield.ai.cohere.*`와 별도)
- 표본 검토:
  - SOFT/HARD_VIOLATION으로 판정된 응답 30~50건 수동 검토
  - GuardrailFilter regex가 못 잡은 케이스 비율
  - false positive (정상인데 violation 판정) 비율

**산출물**: 1~2주 후 보고서 [docs/ai-rag-v2.2/reports/p5_5-judge-shadow.md](../reports/p5_5-judge-shadow.md)

### Commit 4 — Chat/Brief HyperCLOVA X shadow 평가 (~3~4일)

**의존**: Commit 3 완료 (Judge 안정성 검증됨)

**파일**:
- 신규: `src/main/java/org/example/shield/ai/provider/hyperclova/HyperClovaChatClientAdapter.java` (implements `AiChatClient`)
- 수정: [CohereService.java](../../src/main/java/org/example/shield/ai/application/CohereService.java) — chat/brief 호출 시 deterministic sampling으로 HyperCLOVA shadow 호출 (결과 미사용, 메트릭만)
- 신규: `src/main/java/org/example/shield/ai/application/ChatProviderShadowComparator.java`
  - Cohere 응답과 HyperCLOVA 응답을 동시 수집 (둘 다 호출 후 Cohere만 user-facing)
  - 응답 비교 메트릭: 길이 / 인용 패턴 / regex violation rate

**오프라인 평가** (`scripts/eval_chat.py` 신설):
- v1.6 평가셋에서 chat/brief 응답 생성 — Cohere vs HyperCLOVA
- 비교 지표: 
  - 평균 응답 길이
  - GuardrailFilter regex hit rate
  - HyperCLOVA judge 평가 (PASS rate)
  - LLM-as-judge (Claude/GPT-4) 평가 — 톤·자연성·정확성 점수
  - 비용 / 지연

**활성화 조건** (sampled mode):
- HyperCLOVA judge PASS rate ≥ Cohere PASS rate (안전성 미회귀)
- LLM-as-judge 톤 점수 +0.05 이상
- p95 latency 회귀 ≤ +400ms
- 비용 비교 보고서

### Commit 5 — 최종 결정 보고서 (~0.5일)

**산출물**: [docs/ai-rag-v2.2/reports/p5_5-final-decision.md](../reports/p5_5-final-decision.md)

**Decision Tree**:
- Judge HyperCLOVA가 ① regex 못잡는 케이스 잘 잡고 ② false positive < 10% → **Judge production 활성화** (sampling 5% → 10% → enforce는 별도 phase)
- 위 조건 미달 → Judge mode=off, 추후 모델/프롬프트 튜닝 후 재시도
- Chat HyperCLOVA가 톤·정확성 모두 우위 + latency·비용 허용 → **Chat sampled rollout**
- Chat HyperCLOVA가 일부만 우위 → **Brief만 전환** 또는 **혼합 (특정 도메인만 HyperCLOVA)** 검토
- 명확한 우위 없음 → **Cohere 유지**, Judge는 별도 결정

## 5. 결정적 비결정 요소 — 컨텍스트 길이는 핵심 아님

Cohere Command A (256K) vs HyperCLOVA X HCX-005 (128K). 차이가 있지만 SHIELD의 법률 RAG에서는 결정적이지 않음:

- 실제 context budget = rerank 후 top 5~8 chunk + 상담 히스토리 + 시스템 프롬프트 ≈ 16K~32K
- 128K는 넉넉한 수준

## 6. 인터페이스/API 변경

| 인터페이스 | 변경 | BC |
|---|---|---|
| `AiJudgeClient` | 신규 | N/A |
| `JudgeResult` | 신규 record | N/A |
| `OutputComplianceShadowJudge` | judge 호출 분기 추가 | BC 유지 (기본 mode=off) |
| `AiChatClient` | HyperCLOVA adapter 추가만 (기존 Cohere adapter 유지) | BC 유지 |
| `OutputComplianceResult` | `judgeResult` 필드 추가 (nullable) | BC (기존 6-arg 생성자 유지) |

## 7. Feature Flag 신설

```yaml
app:
  ai:
    judge:
      provider: ${AI_JUDGE_PROVIDER:none}       # none | hyperclova | cohere
      mode: ${AI_JUDGE_MODE:off}                # off | shadow | sampled | enforce
      sampling-rate: ${AI_JUDGE_SAMPLING_RATE:0.0}
      timeout-ms: ${AI_JUDGE_TIMEOUT_MS:3000}
      max-cost-per-day-usd: ${AI_JUDGE_MAX_COST_PER_DAY_USD:5.0}
    chat:
      provider: ${AI_CHAT_PROVIDER:cohere}      # cohere | hyperclova | shadow_compare
      shadow-compare-sampling: ${AI_CHAT_SHADOW_COMPARE_SAMPLING:0.0}

hyperclova:
  api-key: ${HYPERCLOVA_API_KEY:}
  base-url: ${HYPERCLOVA_BASE_URL:https://clovastudio.stream.ntruss.com}
  model:
    chat: ${HYPERCLOVA_CHAT_MODEL:HCX-005}
    judge: ${HYPERCLOVA_JUDGE_MODEL:DASH-002}
  timeout:
    connect: 5000
    read: 30000
  pricing:
    HCX-005:
      input-per-million: 5.00
      output-per-million: 10.00
    DASH-002:
      input-per-million: 0.50
      output-per-million: 1.50
```

## 8. 테스트 계획

- Unit: HyperCLOVA adapter mock 응답 매핑, JudgeResult 직렬화, fail-open 동작
- Integration: 실제 HyperCLOVA X API 1회 호출 (test profile)
- Shadow regression: Cohere chat 응답에 영향 0 (sampled 모드 검증)
- Cost gate: 일일 비용 한도 초과 시 자동 sampling 중단
- 회귀 가드: judge 호출 실패해도 user-facing request 정상

## 9. 완료 기준

- [ ] `AiJudgeClient` interface + HyperCLOVA adapter
- [ ] OutputComplianceShadowJudge sampling 시 judge 호출, 결과는 메트릭만 (운영 미차단)
- [ ] 1~2주 shadow 데이터 + 표본 검토 보고서
- [ ] Chat/Brief shadow 평가 보고서 (오프라인 50~100건)
- [ ] 최종 결정 문서 (Cohere 유지 / Judge 활성 / Chat 전환 / Brief만 / 혼합)
- [ ] 모든 변경은 mode flag로 보호 (기본 OFF)

## 10. Rollback / Feature Flag

- `AI_JUDGE_PROVIDER=none` 또는 `AI_JUDGE_MODE=off` → Judge 호출 완전 비활성
- `AI_CHAT_PROVIDER=cohere` (기본) → Chat은 Cohere만 사용
- 비상 시: yml 변경 → 재배포
- 회로 차단기: 일일 비용 초과 시 자동 sampling 중단

## 11. 비목표 (Non-Goals)

- **RAG 백본 (embed/rerank/classify) provider 교체 절대 안 함** — Cohere 유지
- **DB 스키마 변경 안 함** — `vector(1024)` 그대로
- **HyperCLOVA X로 100% 전환 안 함** — 모든 변경은 sampled까지만, enforce는 별도 phase

## 12. 참고

- 본 phase 분석 근거: 3개 외부 모델 공동 평가 (사용자가 GPT-5.5 Thinking / Claude Opus 4.7 Thinking 등에 별도 조사 요청)
- 관련 미구현 컴포넌트: [OutputComplianceShadowJudge.java](../../src/main/java/org/example/shield/ai/application/OutputComplianceShadowJudge.java) — Judge 인프라만 있고 본체 미연결
- 운영 안전성 우선 원칙: Judge 먼저, Chat은 나중 (변호사법 리스크 + compliance 회귀 자동 감지 가능해진 후)
