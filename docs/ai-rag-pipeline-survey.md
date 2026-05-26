# SHIELD 백엔드 — AI / RAG 파이프라인 조사 보고서

> 본 문서는 구현 계획이 아닌 **현재 코드베이스 기준 조사 보고**입니다. 사용자가 다음 단계(구현/리팩토링/문서화 등)를 지시하면 별도 계획을 수립합니다.

## Context

사용자 요청: "현재 이 프로젝트의 AI, RAG 파이프라인에 대해 조사해줘"

배경:
- `docs/paper-rag-quality-loop-code-based.md`에 RAG 품질 루프 논문 초안이 작성되어 있음 (사용자가 IDE에서 선택한 부분).
- `git status` 기준 다음 파일이 작업 중 (P5.1 Commit 4 관련 변경):
  - **modified**: `CohereService.java`, `OutputComplianceShadowJudge.java`, `CohereCostCalculator.java`, `CohereEmbeddingClientAdapter.java`, `application.yml`, `CohereCostCalculatorTest.java`
  - **untracked (신규)**: `CoherePricingProperties.java`, `RagFeatureModeConverter.java`, `CohereMetricEmitter.java`, `PiiMasker.java`, `PiiMaskerTest.java`

조사 목적: 현재 AI/RAG 파이프라인의 전체 아키텍처, 진행 중인 리팩토링의 의도, 출력 컴플라이언스 파이프라인의 현재 상태를 정리하여 사용자가 다음 의사결정을 내릴 수 있게 한다.

---

## 1. RAG 파이프라인 전체 구조

### 1.1 진입점과 흐름

**진입점**: [MessageService.java:181](../src/main/java/org/example/shield/consultation/application/MessageService.java#L181) — `RagPipelineService.executeDetailed(messages, domain, consultationId, providedIntent)`를 호출.

**오케스트레이터**: [RagPipelineService.java](../src/main/java/org/example/shield/ai/application/RagPipelineService.java) — 6단계로 구성:

| 단계 | 컴포넌트 | 책임 |
|---|---|---|
| 1 | `IntentClassificationService.route()` | Cohere `command-r7b-12-2024`로 의도/온톨로지 노드/키워드/retrieval_query 산출 |
| 2 | `CategoryLawMappingService.resolveLawIds()` | 온톨로지 노드 → 법령 ID 목록 변환 |
| 3 | `IntentAwareRetrievalPolicy.decide()` | 의도별로 topK / skipRag 결정 (예: CHANGE_TOPIC → topK=10) |
| 4 | `LegalRetrievalService.retrieve()` 또는 `retrieveMixed()` | 3-way hybrid 검색 (법령 + 판례) |
| 5 | `RetrievalScoreGate.filter()` | 점수 임계값 게이트 (현재 존재함 — 문서와 불일치) |
| 6 | `RagContextBuilder.build()` | LLM 컨텍스트 문자열 합성 |

반환 DTO: [RagPipelineResult.java](../src/main/java/org/example/shield/ai/dto/RagPipelineResult.java) — `(intent, ragContext, retrievalResults)`.

> ⚠️ **문서와 코드 불일치 1**: 논문 2.4.4절은 "RetrievalScoreGate가 구현되어 있지 않다"고 기술하지만, 실제 코드에는 존재한다. 논문 업데이트 또는 게이트 비활성화 여부 확인 필요.

### 1.2 의도 분류 (Intent Classification)

- 위치: [IntentClassificationService.java](../src/main/java/org/example/shield/ai/application/IntentClassificationService.java)
- 프롬프트: `classpath:ai/prompts/rag/intent-classifier.md`
- 온톨로지: [src/main/resources/ontology/legal-ontology-slim.json](../src/main/resources/ontology/legal-ontology-slim.json) — 계층형 JSON (L1 > L2 > L3)
- 결과 DTO: `IntentClassificationResult` — `matchedNodeIds`, `keywords.core()`, `retrievalQueries`, `intentSummary`
- 확장 DTO: `IntentRouterResponse` — `dialogueIntent` (PROVIDE_INFO / ASK_LEGAL_ADVICE / CHANGE_TOPIC 등 8종), `intentConfidence`, `extractedSlots`

### 1.3 검색 레이어

- 인터페이스: [LegalRetrievalService.java](../src/main/java/org/example/shield/ai/application/LegalRetrievalService.java)
- 구현체: [PgLegalRetrievalService.java](../src/main/java/org/example/shield/ai/infrastructure/PgLegalRetrievalService.java) (운영, `rag.retrieval.stub=false`일 때 활성)
- 3-way 점수: `vector(0.5) * cosine_sim + keyword(0.3) * ts_rank + trigram(0.2) * pg_trgm_similarity`
- 가중치/HNSW 튜닝: `application.yml`의 `rag.retrieval.weights.*`, `rag.retrieval.hnsw.ef-search=40`
- 필터: `category_ids && ARRAY[...]` (배열 겹침) + `law_id IN (...)` (정확 매치)
- 테이블: `legal_chunks` (조문 청크), `legal_cases` (판례 1행) — 둘 다 `vector(1024)` + `content_tsv`
- **Degraded retrieval**: Cohere 실패 → 영벡터 사용 → 자동으로 BM25 + trigram 2-way로 축소 (`RagMetrics.recordVectorDegrade(reason)` 기록)

### 1.4 카테고리/법령 매핑

- 위치: [CategoryLawMappingService.java](../src/main/java/org/example/shield/ai/application/CategoryLawMappingService.java)
- 매핑 YAML: [src/main/resources/ontology/category-law-mappings.yml](../src/main/resources/ontology/category-law-mappings.yml)
- 핵심 메서드:
  - `resolveLawIds(nodeIds)` — 노드 → 법령 ID. L3 미매핑 시 부모로 fallback. EXTERNAL 제외
  - `resolveCategoryIds(nodeIds)` — 노드 → "group:jeonse", "book:civil-code" 등 토큰 (DB 포맷)
  - `resolveCategoriesByLsi()` — 특별법 인제스트용 역인덱스 (`lawIdToNodeIds`)

> ⚠️ **문서와 코드 불일치 2**: 논문 2.4.3절은 "`RagPipelineService`가 `resolveCategoryIds()`를 호출하지 않고 `matchedNodeIds`를 그대로 전달한다"고 기술. 실제 호출 여부는 `RagPipelineService.java`에서 확인 필요.

### 1.5 관측 지표

- 위치: [RagMetrics.java](../src/main/java/org/example/shield/ai/infrastructure/RagMetrics.java)
- Micrometer 메트릭 (`/actuator/prometheus` 노출):
  - `shield.rag.cohere.embed` (outcome=success/failure)
  - `shield.rag.retrieve` (outcome=success/empty/failure)
  - `shield.rag.vector.degrade` (reason=empty_query/cohere_error/empty_response)
  - `shield.rag.classify` (outcome=success/failure)
  - `shield.rag.pipeline.total` (outcome=success/empty/failure)
  - `shield.rag.retrieval_gate` (method, outcome) — **신규 게이트 메트릭**

---

## 2. Cohere 통합 및 진행 중인 리팩토링 (P5.1 Commit 4)

### 2.1 패키지 구조

| 패키지 | 책임 |
|---|---|
| `ai/application/CohereService` | chat / brief / classify 3종 API 오케스트레이션 |
| `ai/infrastructure/CohereClient` | 저수준 Cohere SDK 호출 |
| `ai/infrastructure/CohereMetricEmitter` *(신규)* | 토큰/비용/지연 메트릭 통합 emit |
| `ai/infrastructure/CohereCostCalculator` | 비용 계산 (단가 → 비용 환산) |
| `ai/provider/cohere/CohereEmbeddingClientAdapter` | `AiEmbeddingClient` 인터페이스 구현 |
| `ai/config/CoherePricingProperties` *(신규)* | `@ConfigurationProperties("cohere.pricing")` |
| `ai/config/RagFeatureMode` + `RagFeatureModeConverter` *(신규)* | 기능 단계 enum (OFF/SHADOW/SAMPLED/ENFORCE) |

사용 모델: `command-a-03-2025` (chat/brief), `command-r7b-12-2024` (classify), `embed-v4.0` (RAG), `rerank-v3.5` (미통합, 향후).

### 2.2 변경 의도 — 메트릭 emit 중앙화 + 단가 외부화

**Before**: `CohereService`(3곳)와 `CohereEmbeddingClientAdapter`(임베딩) 각각이 token/cost/latency emit 로직을 중복 보유.

**After**:
- `CohereMetricEmitter`가 `emit(AiCallResult)` / `emitEmbed(EmbeddingResult)` 2개 메서드로 통합
- 내부 `emitInternal()`은 예외 swallow (best-effort)
- `CohereService`와 `CohereEmbeddingClientAdapter`는 `CohereMetricEmitter` 한 곳에만 의존

**단가 외부화**:
- 기존: `CohereCostCalculator` 내부 static Map
- 신규: `CoherePricingProperties` → `application.yml`의 `cohere.pricing.<model>.input-per-million / output-per-million` 바인딩
- 모든 단가는 환경변수(`COHERE_PRICE_*`)로 override 가능 → 재배포 없이 가격 조정 가능

**기본 단가** (USD per 1M tokens):
- `command-a-03-2025`: input $2.50 / output $10.00
- `command-r7b-12-2024`: input $0.0375 / output $0.15
- `embed-v4.0`: input $0.10 / output $0
- `rerank-v3.5`: input $2.00 / output $0

### 2.3 RagFeatureMode

- enum: `OFF (기본) → SHADOW → SAMPLED → ENFORCE`
- 신규 RAG 기능(Retrieval Gate, Rerank, Intent-aware 등)을 안전하게 단계적 롤아웃
- `fromOrThrow()`: invalid 값은 fail-fast
- `RagFeatureModeConverter`: Spring `@ConfigurationPropertiesBinding`으로 String → enum 자동 변환

### 2.4 테스트

- [CohereCostCalculatorTest.java](../src/test/java/org/example/shield/ai/infrastructure/CohereCostCalculatorTest.java): 단가 정확성, null/음수 토큰 안전 처리, properties 미주입 시 fallback(EMPTY map → 0.0)
- [CohereMetricEmitterTest.java](../src/test/java/org/example/shield/ai/infrastructure/CohereMetricEmitterTest.java) *(신규)*: chat은 token(input/output) + cost + latency 3개 메트릭, embed는 input token만 기록, 메트릭 발행 예외가 호출 결과에 영향 없음

---

## 3. 출력 컴플라이언스 / PII 처리

### 3.1 입력 Sanitize (결정론적 거부)

- 위치: [SanitizeService.java](../src/main/java/org/example/shield/ai/infrastructure/SanitizeService.java)
- 동작 (3단계):
  1. NFC 유니코드 정규화
  2. PII 패턴(주민번호/계좌/카드) 감지 시 `PiiDetectedException` → 입력 거부
  3. 역할 구분자("AI:", "USER:", "SYSTEM:")에 zero-width space 삽입 → 프롬프트 인젝션 방지
- 호출: [MessageService.java](../src/main/java/org/example/shield/consultation/application/MessageService.java) `sendMessage()` 진입점. PII 감지 시 안내 메시지만 저장 후 조기 반환

### 3.2 출력 GuardrailFilter (결정론적 대체)

- 위치: [GuardrailFilter.java](../src/main/java/org/example/shield/ai/infrastructure/GuardrailFilter.java)
- 정규식 기반 차단 패턴: 법률 해석/조언, 판례 인용, 승패 예측, 법적 결론 단정, 변호사 추천
- 채팅: `filterChatResponse()` — `nextQuestion` 필터 → 적발 시 "법률적 판단이나 해석은 변호사를 통해…" 대체
- 의뢰서: `filterBriefResponse()` — `strategy`, `keyIssues[].description` 필터 → 적발 시 제거/대체

### 3.3 OutputComplianceShadowJudge (변경 중)

- 위치: [OutputComplianceShadowJudge.java](../src/main/java/org/example/shield/ai/application/OutputComplianceShadowJudge.java)
- **현재 상태**: shadow-only 평가 컴포넌트 (운영 차단 미연결)
- 책임:
  - 채팅 응답 샘플링 — `conversationId` 해시 기반 deterministic sampling (같은 상담 일관성 유지)
  - `PiiMasker`로 응답 마스킹 후 외부 judge에 전달
  - 메트릭: `outcome` 태그 ("regex_violation" / "sampled" / "skipped"), `hashedConversationId`
- 호출: [CohereService.java](../src/main/java/org/example/shield/ai/application/CohereService.java) `chat()` — GuardrailFilter 통과 후 호출
- **judge 구현체 부재**: 현재 Cohere나 다른 LLM judge 로직은 미연결. shadow infrastructure만 준비됨

> ⚠️ **문서와 코드 불일치 3**: 논문 2.4.5절은 "`OutputComplianceShadowJudge`는 구현되어 있지 않다"고 기술. 실제로는 shadow-only로 존재. 논문을 "shadow infrastructure는 존재하지만 judge 본체는 미구현" 식으로 보정 필요.

### 3.4 PiiMasker (신규)

- 위치: [PiiMasker.java](../src/main/java/org/example/shield/ai/infrastructure/PiiMasker.java)
- 책임: 응답 텍스트에서 5종 PII를 `[RRN]`, `[CARD]`, `[ACCOUNT]`, `[PHONE]`, `[EMAIL]` 토큰으로 마스킹
- **이름/주소 제외**: false positive 위험으로 의도적 제외 (후속 NER/LLM redactor 검토)
- `SanitizeService`와의 관계: **보완 관계** (입력 거부 vs 출력 마스킹)
- 테스트: [PiiMaskerTest.java](../src/test/java/org/example/shield/ai/infrastructure/PiiMaskerTest.java) — 5종 카테고리, 복합 마스킹, null/blank, false positive 회피(예: "법령 제618조" 같은 정상 텍스트 보존)

### 3.5 Feature Flag

[application.yml](../src/main/resources/application.yml) 244–248줄:

```yaml
output-judge:
  shadow-enabled: ${AI_OUTPUT_JUDGE_SHADOW_ENABLED:false}
  sampling-rate: ${AI_OUTPUT_JUDGE_SAMPLING_RATE:0.0}
  max-p95-latency-increase-ms: ${AI_OUTPUT_JUDGE_MAX_P95_LATENCY_INCREASE_MS:200}
  max-cost-ratio: ${AI_OUTPUT_JUDGE_MAX_COST_RATIO:0.10}
```

기본값 모두 0/false → 현재 운영에서는 비활성.

---

## 4. 오프라인 평가 (보조 정보)

- 스크립트: `scripts/eval_rag.py` (Python, Java와 별도 경로)
- 입력: `eval/eval-set.v1.jsonl`, `eval/eval-set.v1.5.jsonl`
- 지표: Recall@{1,3,5,10}, MRR, nDCG@5
- 옵션: `--include-cases` (판례 포함), `--rerank --pool N` (Cohere rerank-v3.5 후단 재정렬 실험)
- 출력: Markdown + JSON 리포트 (meta/summary/cases 구조)
- 운영 코드에는 rerank 미통합 — **오프라인 실험 전용**

---

## 5. 발견된 핵심 이슈 / 의사결정 포인트

1. **문서-코드 정합성 (3건)**
   - 논문 2.4.4: "RetrievalScoreGate 미구현" → 실제는 구현됨
   - 논문 2.4.3: `resolveCategoryIds()` 호출 여부 — `RagPipelineService` 실제 호출 경로 확인 필요
   - 논문 2.4.5: "OutputComplianceShadowJudge 미구현" → shadow infra는 존재, judge 본체만 미구현

2. **진행 중인 P5.1 Commit 4 리팩토링**
   - 메트릭 emit 중앙화 + 단가 외부화는 거의 완성 (테스트도 작성됨)
   - 커밋 가능한 상태로 보이나, 변경 의도 검증 필요

3. **OutputComplianceShadowJudge의 다음 단계**
   - judge 호출 본체(어떤 LLM/Cohere 모델, 프롬프트, 응답 스키마) 미정
   - sampling-rate 0.0 → 1.0% 등으로 점진 활성화 시점
   - 비용/지연 가드(`max-cost-ratio`, `max-p95-latency-increase-ms`) 실제 enforce 위치

4. **검색 질의 팬아웃 미적용**
   - 분류 파서는 `retrieval_queries` 배열 수용
   - `RagPipelineService`는 첫 번째만 사용
   - 다중 질의 검색 또는 후단 재정렬 도입 여지

5. **카테고리 필터 일관성**
   - 노드 ID와 DB `category_ids` 토큰 체계의 매핑이 코드 경로에서 보장되지 않을 수 있음
   - `resolveCategoryIds()`를 운영 경로에 연결하거나, soft boost로 전환하는 방안 검토 가능

---

## 6. 검증 방법 (Verification)

조사 결과 자체에는 변경 사항이 없으므로 build/test 실행은 필요 없음. 단, 다음 단계로 진행할 경우 권장 검증:

- 단위 테스트: `./gradlew test --tests "*CohereCostCalculatorTest" --tests "*CohereMetricEmitterTest" --tests "*PiiMaskerTest"`
- 통합 동작: `./gradlew bootRun` 후 `/actuator/prometheus`로 `shield.rag.*` 메트릭 확인
- 오프라인 평가: `python scripts/eval_rag.py --input eval/eval-set.v1.5.jsonl --include-cases`
