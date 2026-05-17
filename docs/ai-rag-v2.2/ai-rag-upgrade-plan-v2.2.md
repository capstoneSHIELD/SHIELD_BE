# SHIELD AI·RAG 시스템 업그레이드 계획서 v2.2

**현행 백엔드 구조 반영 + GPT 의도 라우팅 + 슬롯 상태 관리 단계화**

작성일: 2026-05-17  
기준 브랜치/코드: 현재 `C:\SHIELD_BE` 워크스페이스  
수정 기준: v2.1 계획서의 방향성은 유지하되, 현행 코드 구조와 공식 API 제약을 반영해 구현 순서를 조정한다.

---

## 0. v2.1에서 유지할 원칙과 수정할 전제

### 유지할 원칙

1. **LLM은 분류·추출·제안·문장 생성만 수행한다.**
   - 최종 분기, 슬롯 상태 변경, 다음 질문 선택, 상담 종료 판단은 백엔드가 수행한다.

2. **런타임 자율 에이전트는 도입하지 않는다.**
   - 법률 상담 서비스에서는 재현 가능성, 감사 가능성, 비용 예측 가능성이 우선이다.

3. **반복 질문 제거의 핵심은 명시적 상태 주입이다.**
   - LLM이 기억하길 기대하지 않고, 백엔드가 매 턴 수집 상태를 system prompt 최상단에 주입한다.

4. **Intent routing은 shadow evaluation 후 단계적으로 활성화한다.**
   - 오분류로 Cohere 호출을 잘못 생략하는 위험이 있으므로 바로 운영 분기에 사용하지 않는다.

### 수정할 전제

| v2.1 전제 | v2.2 수정 |
|---|---|
| `PromptService` 수정만으로 값 포함 Slot Status Block 가능 | 현재 구조화 슬롯 저장소가 없으므로 불가능하다. P1.5에서 최소 slot ledger를 먼저 도입한다. |
| `CohereService`에 분기 한 줄 추가 | 현재 실제 분기 지점은 `MessageService`의 RAG/Cohere 호출 전이다. |
| `consultation.metadata`에 임시 저장 가능 | 현재 `Consultation` 엔티티에는 metadata JSON 컬럼이 없다. 필요하면 별도 JSONB 컬럼 또는 Redis를 추가한다. |
| P3 SQL의 `consultation_id BIGINT` | 현재 엔티티 ID는 UUID다. 신규 테이블도 UUID FK를 사용한다. |
| YAML `mappingKeywords`는 파일 수정만 | 현재 YAML schema와 맞지 않는다. 별도 alias 파일 또는 호환 loader 변경이 필요하다. |
| Hybrid RAG는 P4에서 추가 | 현재 이미 vector + BM25 + trigram hybrid가 있다. P4는 RRF/score gate 비교 실험으로 수정한다. |
| `gpt-5-nano`, `minimal` 고정 | 운영 계정에서 유효한 모델명인지 확인한다. 최신 공식 문서 기준 모델명과 reasoning effort 값을 재검증한다. |

---

## 1. 현행 코드 기준 구조 요약

### 현재 대화 처리 흐름

1. `MessageService.sendMessage()`가 사용자 메시지를 저장한다.
2. 도메인이 있으면 `RagPipelineService.execute()`를 호출한다.
3. 이후 `CohereService.chat()`를 매 턴 호출한다.
4. `ChecklistCoverageService`가 대화 이력을 기반으로 `[x]/[ ]` 체크리스트 요약을 만든다.
5. Cohere 응답을 저장하고, coverage gate로 상담 완료 여부를 판단한다.

### 현재 제약

- `IntentClassificationResult`는 `intentSummary`, `matchedNodes`, `keywords`, `retrievalQueries` 중심이다.
- `RagPipelineService`는 확장된 intent 결과를 외부로 전달하지 않고 최종 RAG context 문자열만 반환한다.
- 수집된 슬롯 값, pending confirmation, asked question history를 보존하는 구조화 상태 저장소가 없다.
- Cohere 요청은 `response_format: {"type":"json_object"}` 수준이며 JSON Schema 강제는 아직 없다.
- OpenAI classifier도 JSON mode만 사용하고 strict JSON Schema는 사용하지 않는다.
- 상담 ID는 UUID 기반이다.
- 체크리스트 YAML은 배열형 항목 구조라 `id/label/mappingKeywords` 객체 예시와 바로 호환되지 않는다.
- RAG는 이미 vector, BM25, trigram을 조합하는 hybrid retrieval을 사용한다.

---

## 2. 목표 아키텍처 v2.2

```
[사용자 발화]
      |
      v
[MessageService]
      |
      v
[L1 Intent/Slot Router - OpenAI Structured Outputs]
  - dialogueIntent
  - extractedSlots
  - caseType
  - retrievalQueries
      |
      v
[Backend Router]
  - fixed response 가능 intent는 Cohere 생략
  - PROVIDE_INFO/CORRECT_INFO는 slot ledger 갱신
  - END_CONSULTATION은 coverage 확인 후 종료 또는 재질문
      |
      v
[Slot Ledger]
  - collected/pending/missing 상태 보존
  - collectedValue/pendingValue 보존
  - asked question history 보존
      |
      v
[RAG Pipeline - 필요한 intent만 실행]
  - 기존 hybrid retrieval 유지
  - P4에서 RRF/rerank/score gate 비교
      |
      v
[Cohere Response Generator]
  - JSON Schema response_format 적용
  - system prompt 최상단 Slot Status Block 주입
      |
      v
[Backend Validator / Output Gate]
  - 법적 판단 표현 차단
  - next slot deterministic override
      |
      v
[응답 저장 및 반환]
```

핵심 변경점은 `CohereService` 내부가 아니라 **`MessageService`의 RAG/Cohere 호출 전 단계에 intent routing을 추가**하는 것이다.

---

## 3. Phase 1 - 파싱 안정화와 프롬프트 정비

기간: 1~2주  
목표: DB schema나 대화 흐름을 크게 바꾸지 않고 JSON 안정성, guardrail, 반복 질문 완화 장치를 먼저 넣는다.

### P1-A. Cohere JSON Schema 적용

현재 `json_object`만 사용하므로 schema 준수는 보장하지 않는다. `ChatParsedResponse`가 기대하는 필드에 맞춰 Cohere `response_format.schema`를 추가한다.

적용 범위:

- `CohereChatRequest.forChat()`
- `CohereChatRequest.forBrief()`
- 필요 시 `forClassify()`

주의:

- Cohere Structured Outputs는 지원 모델과 schema 제약이 있다.
- Cohere native RAG mode에서는 schema가 지원되지 않는다. SHIELD처럼 RAG context를 직접 prompt에 넣는 방식이면 이 제한은 직접 적용되지 않는다.
- API 오류, refusal, legacy model fallback을 고려해 기존 parser fallback은 제거하지 않는다.

완료 기준:

- 정상 응답의 JSON parse 실패율 1% 미만
- schema 누락 필드에 대한 fallback 로깅 추가
- chat/brief 응답 DTO unit test 추가

### P1-B. OpenAI classifier strict schema 적용

현재 classifier 응답도 JSON mode만 사용한다. 먼저 기존 DTO 범위에서 strict JSON Schema를 적용한다.

현 단계의 schema:

```json
{
  "intent_summary": "string",
  "matched_node_ids": ["string"],
  "core_keywords": ["string"],
  "retrieval_query": "string"
}
```

주의:

- OpenAI strict Structured Outputs는 `json_schema`와 `strict: true`를 사용한다.
- 모델명은 환경변수로 유지하되, 기본값은 공식 문서와 운영 계정에서 실제 사용 가능한 low-cost 모델로 재확인한다.
- `gpt-5-nano`가 운영 alias가 아니라면 `gpt-5.4-nano` 등 현재 공식 모델명으로 교체한다.
- reasoning effort 값도 운영 모델 문서 기준으로 재확인한다.

완료 기준:

- classifier 응답 schema validation test 추가
- 기존 `retrievalQueries` 동작 보존
- 모델명/effort 설정 문서화

### P1-C. 체크리스트 요약 최상단 이동

P1에서는 아직 구조화 슬롯 값이 없으므로 v2.1의 값 포함 Slot Status Block은 만들 수 없다. 대신 현재 `ChecklistCoverageService.buildCollectedSummary()` 결과를 system prompt 최상단으로 이동한다.

추가 블록:

```text
=== CURRENT CHECKLIST COVERAGE ===
[x] ...
[ ] ...

=== DO NOT REPEAT EXACT QUESTIONS ===
- 최근 AI가 이미 물어본 질문 3~5개

RULE: Do not ask an identical question again. Prefer the highest-priority unchecked item.
```

완료 기준:

- checklist coverage가 system prompt 첫 부분에 위치
- 최근 AI 질문 3~5개가 repeat blacklist로 주입
- 기존 응답 JSON 형식 유지

### P1-D. Guardrail 통합

새 `LegalJudgmentDetector`를 만들기보다 기존 guardrail 컴포넌트를 확장한다.

수정 방향:

- `가능합니다`, `할 수 있습니다` 같은 일반 표현만으로 차단하지 않는다.
- 승소/패소, 위법/적법, 배상 가능성, 고소 가능성처럼 법적 판단을 암시하는 표현을 문맥 기반으로 좁게 잡는다.
- `nextQuestion`, brief 필드, fixed template 모두 동일 guardrail을 거치게 한다.

완료 기준:

- 정상 절차 안내 문장이 과차단되지 않음
- 법적 결론/승패/가능성 표현은 차단됨
- guardrail unit test 추가

### P1-E. P1에서 하지 않을 것

- 값 포함 Slot Status Block
- Cohere 조건부 skip
- dynamic plan DB schema
- YAML schema 대규모 변경

위 항목들은 상태 저장과 라우팅 계약 변경이 필요하므로 P1.5 이후로 넘긴다.

---

## 4. Phase 1.5 - 최소 Slot Ledger 도입

기간: 2~3주  
목표: 값 포함 Slot Status Block과 confirmation 처리를 위한 최소 상태 저장소를 만든다.

### P1.5-A. 저장 방식 선택

권장안: `consultations`에 `slot_state` JSONB 컬럼 추가

이유:

- 상담 상태 감사가 가능하다.
- P3의 정규화 테이블로 이전하기 쉽다.
- Redis 단독 저장보다 장애/재시작에 강하다.

예시 구조:

```json
{
  "version": 1,
  "caseType": {
    "l1": null,
    "l2": null,
    "l3": null,
    "confidence": 0.0
  },
  "slots": [
    {
      "slotId": "deposit_amount",
      "label": "보증금",
      "source": "static_checklist",
      "status": "missing",
      "collectedValue": null,
      "pendingValue": null,
      "confidence": 0.0,
      "askedQuestions": [],
      "answeredAt": null,
      "updatedAt": null
    }
  ]
}
```

대안:

- Redis/session JSON: 빠르지만 감사성과 복구성이 약하다.
- P3 정규화 테이블 선도입: 안정화 전 schema가 과해질 수 있다.

### P1.5-B. Slot Status Block 실제 적용

slot ledger가 생긴 뒤부터 값 포함 블록을 적용한다.

```text
=== COLLECTED INFORMATION (DO NOT ASK AGAIN) ===
- 보증금: 30000000
- 계약 종료일: 2024-03-31

=== PENDING CONFIRMATION ===
- 월세: 500000 으로 이해했음. 확인 필요.

=== MISSING INFORMATION (TARGET ONLY THESE) ===
- 임대인의 반환 거절 사유
- 보증금 반환 요청 여부

=== ALREADY ASKED QUESTIONS (DO NOT REPEAT) ===
- 보증금은 얼마였나요?

RULE: Never ask again about collected items. Confirm pending items before treating them as collected.
```

완료 기준:

- 사용자가 제공한 값이 다음 턴 system prompt 최상단에 표시됨
- 같은 슬롯의 동일 질문 반복률 감소
- pending slot은 확인 전 collected로 취급하지 않음

### P1.5-C. StaticQuestionSelector 도입

Cohere가 제안한 다음 질문은 참고값으로만 사용하고, 백엔드가 다음 슬롯을 결정한다.

우선순위:

1. static required missing
2. static optional missing
3. dynamic missing
4. pending confirmation

단, pending confirmation은 사용자 직전 발화가 모호할 때만 우선한다.

완료 기준:

- Cohere `nextSlotId`가 백엔드 선택과 다르면 백엔드 선택으로 override
- override 사유 로깅
- 다음 질문 선택 unit test 추가

---

## 5. Phase 2 - Intent Router 확장과 조건부 Cohere Skip

기간: 3~4주, shadow 2주 포함  
목표: 기존 retrieval query 생성 호출을 확장해 intent/slot/caseType을 함께 반환하게 하고, 검증된 intent부터 Cohere skip을 활성화한다.

### P2-A. DTO 확장

현재 `IntentClassificationResult`를 확장하거나 새 `IntentRouterResponse`를 만든다.

권장 필드:

```json
{
  "dialogueIntent": "PROVIDE_INFO",
  "intentConfidence": 0.91,
  "extractedSlots": [
    {
      "slotId": "deposit_amount",
      "value": "30000000",
      "rawText": "보증금이 3천만원이에요",
      "confidence": 0.89,
      "valueType": "money",
      "needsConfirmation": false
    }
  ],
  "caseType": {
    "l1": "부동산 거래",
    "l2": "부동산 임대차",
    "l3": "보증금 및 차임",
    "confidence": 0.87
  },
  "retrievalQueries": ["전세 보증금 반환 거절 임대차"],
  "correctedSlotIds": [],
  "topicChanged": false
}
```

`RagPipelineService`는 문자열만 반환하지 말고 아래처럼 결과 객체를 반환하도록 조정한다.

```java
public record RagPipelineResult(
    IntentRouterResponse intent,
    String ragContext,
    List<RetrievalResult> retrievalResults
) {}
```

### P2-B. Intent class

8-class 구조는 유지한다.

| Intent | 백엔드 처리 | Cohere |
|---|---|---|
| GREETING | 고정 인사 + 다음 질문 연결 | skip 가능 |
| IRRELEVANT | 상담 주제로 redirect | skip 가능 |
| ASK_LEGAL_ADVICE | 법적 판단 불가 안내 | skip |
| CONFIRM | pending slot 확정/거절 | 조건부 skip |
| PROVIDE_INFO | slot ledger 업데이트 후 필요 시 RAG | 호출 가능 |
| CORRECT_INFO | slot rollback/update 후 재질문 | 호출 가능 |
| CHANGE_TOPIC | caseType 재평가, plan 재생성 후보 | 호출 |
| END_CONSULTATION | coverage 확인 후 brief 또는 부족 정보 질문 | 조건부 |

### P2-C. 분기 위치

분기는 `CohereService` 내부가 아니라 `MessageService`의 RAG/Cohere 호출 전 단계에 둔다.

의사 흐름:

```java
IntentRouterResponse intent = intentRouter.classify(userMessage, session);

if (shadowMode) {
    logIntentOnly(intent);
    return existingPath();
}

IntentRoute route = backendIntentRouter.route(intent, session);
if (route.shouldSkipCohere()) {
    return fixedOrTemplateResponse(route, session);
}

applySlotUpdates(intent, session.slotState());
RagPipelineResult rag = ragPipeline.execute(intent, session);
return cohereService.chat(session, rag.ragContext());
```

### P2-D. Confidence gate

| 조건 | 처리 |
|---|---|
| confidence >= 0.85 and `needsConfirmation=false` | collected로 즉시 반영 |
| 0.65 <= confidence < 0.85 or `needsConfirmation=true` | pending_confirmation |
| confidence < 0.65 | 무시하고 기존 질문 흐름 유지 |

주의:

- CORRECT_INFO는 기존 collected 값을 바로 덮어쓰지 말고 audit log를 남긴다.
- CONFIRM은 pending slot이 있을 때만 slot 상태를 바꾼다.
- ASK_LEGAL_ADVICE는 응답 우선순위가 가장 높다. 단, 같은 발화에 high-confidence slot이 함께 추출되면 slot은 confidence gate에 따라 수집하고, 사용자 응답은 법적 판단 차단 템플릿으로 반환한다.
- 0.85/0.65는 시작 기본값이며, shadow evaluation 종료 후 intent별·도메인별 precision-recall curve로 조정한다.
- CONFIRM skip은 pending slot이 있고 `intentConfidence >= 0.85`이며 새 slot/correction이 없는 명확한 긍정·부정 응답일 때만 수행한다.

### P2-E. Shadow evaluation

활성화 전 필수 단계:

1. 실제 상담 로그 300~500개 발화를 샘플링한다.
2. intent, slot, caseType, legal advice risk를 수동 라벨링한다.
3. 2주간 기존 운영 경로는 유지하고 intent 결과만 로깅한다.
4. intent accuracy, slot precision/recall, Cohere skip false positive rate를 측정한다.

활성화 순서:

1. ASK_LEGAL_ADVICE
2. GREETING
3. IRRELEVANT
4. CONFIRM
5. PROVIDE_INFO slot auto-update

완료 기준:

- Cohere skip false positive rate가 0.5% 이하
- ASK_LEGAL_ADVICE 차단 누락률이 high-risk 라벨셋 기준 0건
- slot auto-update precision이 95% 이상이며 recall보다 우선 충족

---

## 6. Phase 3 - DynamicPlanProposer와 정규화 schema

기간: 4~6주  
목표: 상담별 동적 슬롯 계획을 저장하고, Cohere 제안과 백엔드 검증을 분리한다.

### P3-A. 명명

`DynamicPlanAgent`가 아니라 `DynamicPlanProposer`로 명명한다.

이유:

- LLM은 plan을 제안할 뿐 실행하지 않는다.
- 최종 승인, 저장, 질문 선택은 백엔드가 한다.

### P3-B. UUID 기반 DB schema

현재 상담 ID가 UUID이므로 FK도 UUID를 사용한다.

```sql
CREATE TABLE consultation_dynamic_plan (
    id              UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultations(id),
    version         INT NOT NULL DEFAULT 1,
    case_type_l1    VARCHAR(50),
    case_type_l2    VARCHAR(50),
    case_type_l3    VARCHAR(50),
    plan_confidence DECIMAL(4,3),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE dynamic_plan_slot (
    id                UUID PRIMARY KEY,
    plan_id           UUID NOT NULL REFERENCES consultation_dynamic_plan(id),
    slot_id           VARCHAR(100) NOT NULL,
    label             VARCHAR(200) NOT NULL,
    source            VARCHAR(30) NOT NULL,
    static_mapping_id VARCHAR(200),
    required          BOOLEAN NOT NULL DEFAULT FALSE,
    priority          INT NOT NULL,
    status            VARCHAR(30) NOT NULL,
    collected_value   TEXT,
    pending_value     TEXT,
    validation_hint   VARCHAR(50),
    question_text     TEXT,
    asked_at          TIMESTAMP,
    answered_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX idx_plan_consultation ON consultation_dynamic_plan(consultation_id);
CREATE INDEX idx_slot_plan_status ON dynamic_plan_slot(plan_id, status);
CREATE INDEX idx_slot_asked ON dynamic_plan_slot(plan_id, asked_at);
```

DB에서 UUID를 자동 생성하려면 `gen_random_uuid()` 확장 여부를 별도로 확인한다. 그렇지 않으면 애플리케이션에서 UUID를 생성한다.

### P3-C. Checklist alias/mapping 전략

v2.1의 `mappingKeywords`를 기존 YAML에 바로 추가하지 않는다.

권장 순서:

1. 기존 checklist YAML은 유지한다.
2. `src/main/resources/ai/checklists/aliases/*.yaml`을 새로 만든다.
3. alias loader가 static checklist 항목과 keyword를 연결한다.
4. P3 후반에 필요하면 checklist schema를 object 기반으로 정식 마이그레이션한다.

예시:

```yaml
real-estate:
  lease_end_date:
    labels:
      - 계약 종료일
    keywords:
      - 계약 종료
      - 전세 만료
      - 임대차 종료
      - 계약 기간 만료
      - lease_expiry
```

### P3-D. BackendValidator

검증 항목:

1. caseType이 ontology 범위 안에 있는지 확인
2. static slot이 실제 checklist/alias에 존재하는지 확인
3. dynamic slot이 최소 하나의 static category에 매핑 가능한지 확인
4. 질문 문장에 법적 판단 표현이 없는지 확인
5. required/priority가 백엔드 정책을 위반하지 않는지 확인

완료 기준:

- unmapped dynamic slot은 운영 질문으로 사용하지 않음
- Cohere의 `nextSlotId`는 항상 deterministic selector 결과로 검증
- validator rejection 사유가 로그와 평가 데이터에 남음

### P3-E. Incremental update

매 턴 plan을 재생성하지 않는다.

재생성 조건:

- 첫 턴
- L2 이상 topic change 감지
- 기존 plan slot 3개 이상이 CORRECT_INFO로 무효화
- planConfidence가 기준 이하로 하락

그 외에는 slot status만 갱신한다.

---

## 7. Phase 4 - RAG 고도화와 품질 평가 루프

기간: 4~6주  
목표: 이미 있는 hybrid retrieval을 기준선으로 삼고, RRF/rerank/score gate를 평가 기반으로 도입한다.

### P4-A. RRF는 교체가 아니라 비교 실험

현재 구조:

- pgvector dense retrieval
- BM25 keyword retrieval
- trigram retrieval
- weighted score fusion
- Cohere rerank benchmark 문서 존재

P4 수정 목표:

1. 현재 weighted fusion을 baseline으로 고정한다.
2. RRF fusion을 feature flag로 추가한다.
3. weighted vs RRF vs rerank 조합을 eval set으로 비교한다.
4. Recall@5, MRR, nDCG@5, latency, cost를 함께 본다.

### P4-B. Retrieval Score Gate

v2.1의 `score < 0.35` 고정 기준은 바로 적용하지 않는다.

이유:

- 현재 score는 vector/BM25/trigram 가중 합이라 query마다 분포가 다르다.
- RRF score와 rerank score는 서로 scale이 다르다.

권장 방식:

- method별 score distribution을 수집한다.
- domain별 false drop rate를 측정한다.
- threshold는 전체 고정값이 아니라 retrieval method별 config로 둔다.
- 가능하면 rerank score 또는 calibrated percentile 기반으로 gate를 둔다.

### P4-C. Intent-aware retrieval

P2가 안정화된 뒤 intent별 검색 전략을 적용한다.

예시:

| Intent | 검색 전략 |
|---|---|
| PROVIDE_INFO + high confidence slots | topK 축소, latency 우선 |
| PROVIDE_INFO + low confidence slots | topK 유지, context 보강 |
| CHANGE_TOPIC | topK 확대, L1/L2 재탐색 |
| ASK_LEGAL_ADVICE/GREETING/IRRELEVANT | RAG skip |
| END_CONSULTATION | brief 생성에 필요한 최소 context만 사용 |

### P4-D. Output Compliance Gate

1단계는 deterministic guardrail을 전체 응답에 적용한다.  
2단계 LLM judge는 처음에는 offline/shadow로만 실행하고, 운영 10% 샘플링은 비용/개인정보/레이턴시 기준을 확인한 뒤 켠다.

완료 기준:

- legal judgment leak rate 측정 가능
- blocked response에 fallback template 제공
- guardrail false positive/false negative 평가 리포트 생성

### P4-E. 오프라인 평가 에이전트

런타임이 아니라 배치 평가에만 agent pattern을 허용한다.

| 평가 작업 | 주기 | 산출물 |
|---|---|---|
| 상담 품질 평가 | 완료 상담 100건마다 | 반복 질문, 누락 슬롯, 법적 표현 유출 리포트 |
| 체크리스트 개선 후보 | 월 1회 | dynamic slot의 static 승격 후보 |
| RAG 실패 분석 | 주 1회 | 검색 실패 유형, chunking/alias 개선안 |

---

## 8. 수정된 로드맵

| Phase | 기간 | 핵심 작업 | schema 변경 | 예상 효과 |
|---|---:|---|---|---|
| P1 | 1~2주 | JSON Schema 적용, prompt 최상단 정리, guardrail 통합 | 없음 | 파싱 안정화, 반복 질문 일부 완화 |
| P1.5 | 2~3주 | slot ledger JSONB, 값 포함 Slot Status Block, static selector | 경량 JSONB 1개 | 반복 질문 근본 완화, pending confirmation 기반 마련 |
| P2 | 3~4주 | intent/slot router 확장, shadow eval, 단계적 Cohere skip | 없음 또는 slot_state 확장 | Cohere 호출 감소, 법적 판단 요청 선차단 |
| P3 | 4~6주 | DynamicPlanProposer, BackendValidator, 정규화 plan table | UUID 기반 신규 테이블 | 동적 슬롯과 정적 guardrail 결합 |
| P4 | 4~6주 | RRF/rerank 비교, calibrated score gate, output compliance | 없음 | RAG 정밀도와 운영 품질 개선 |

---

## 9. 명시적으로 도입하지 않을 것

| 금지 패턴 | 이유 |
|---|---|
| 런타임 Orchestrator Agent | 같은 입력에 다른 경로를 선택할 수 있어 감사가 어렵다. |
| 런타임 Retrieval Agent | 재검색 루프, 비용 증가, latency 폭증 위험이 있다. |
| Multi-agent ReAct/AutoGPT 패턴 | 턴당 LLM 호출이 크게 증가하고 결정 경로가 불투명하다. |
| P1에서 dynamic plan table 선도입 | slot ledger 검증 전 schema가 과도하게 굳어진다. |
| confidence gate 없는 slot 자동 반영 | 오분류가 상담 상태를 오염시킬 수 있다. |
| 고정 retrieval score threshold 즉시 적용 | 현재 score scale이 method별로 달라 과필터링 위험이 있다. |

---

## 10. 배포 전 체크리스트

### P1

- [ ] Cohere schema response_format 추가
- [ ] OpenAI classifier strict JSON Schema 적용
- [ ] 응답 DTO와 JSON Schema에 `schema_version` 추가
- [ ] 운영 OpenAI 계정에서 모델명과 `reasoning_effort` 유효성 확인 후 `.env` 또는 config 반영
- [ ] prompt 최상단 checklist summary 이동
- [ ] 최근 질문 blacklist 주입
- [ ] prompt block별 토큰 예산 적용
- [ ] guardrail test 추가
- [ ] guardrail false positive rate 수동 라벨링 기준 충족

### P1.5

- [ ] `slot_state` 저장 방식 결정
- [ ] P3 이후 `slot_state`와 `dynamic_plan_slot`의 source of truth 정책 확정
- [ ] slot ledger serializer/deserializer 구현
- [ ] `money/date/text` valueType 검증 정책 구현
- [ ] Slot Status Block 생성
- [ ] StaticQuestionSelector 구현
- [ ] 반복 질문 회귀 테스트 추가

### P2

- [ ] IntentRouterResponse DTO 확장
- [ ] RagPipelineResult 반환 계약 변경
- [ ] MessageService 앞단 router 추가
- [ ] 혼합 발화 처리 정책 구현
- [ ] CONFIRM skip 조건 구현
- [ ] fixed response template 관리 방식 확정
- [ ] shadow logging
- [ ] confidence threshold 도메인별 분포 수집
- [ ] 단계별 feature flag 추가

### P3

- [ ] UUID 기반 dynamic plan schema 작성
- [ ] alias mapping loader 구현
- [ ] ontology 데이터 소스와 업데이트 방식 문서화
- [ ] dynamic slot의 static 승격 체크리스트 작성
- [ ] DynamicPlanProposer 구현
- [ ] BackendValidator 구현
- [ ] 정량 기준이 포함된 incremental update 정책 적용

### P4

- [ ] eval set 최초 구성과 갱신 주기 정의
- [ ] weighted/RRF/rerank 비교 실험
- [ ] score distribution 수집
- [ ] calibrated retrieval gate 설계
- [ ] intent confidence별 retrieval fallback 정책 적용
- [ ] output compliance shadow judge
- [ ] offline quality report 입출력 schema 정의
- [ ] offline quality report 생성

---

## 11. 참고 문서

- OpenAI Structured Outputs: https://platform.openai.com/docs/guides/structured-outputs
- OpenAI latest model guide: https://developers.openai.com/api/docs/guides/latest-model.md
- Cohere Structured Outputs: https://docs.cohere.com/v2/docs/structured-outputs
- Amazon Science REIC: https://www.amazon.science/publications/reic-rag-enhanced-intent-classification-at-scale
- Stanford HAI LegalBench-RAG article: https://hai.stanford.edu/news/ai-trial-legal-models-hallucinate-1-out-6-or-more-benchmarking-queries%26quot

---

## 12. v2.1 문구 중 삭제 또는 교체 권장

아래 문구는 그대로 유지하지 않는다.

1. "`PromptService` 수정만으로 Slot Status Block 적용"
   - 교체: "P1.5에서 slot ledger를 도입한 뒤 값 포함 Slot Status Block 적용"

2. "`CohereService` 분기 한 줄 추가"
   - 교체: "`MessageService`의 RAG/Cohere 호출 전 backend router 추가"

3. "`consultation.metadata` JSON 임시 저장"
   - 교체: "`slot_state` JSONB 또는 Redis session 중 선택"

4. "`consultation_id BIGINT`"
   - 교체: "`consultation_id UUID`"

5. "`mappingKeywords`는 YAML 파일 수정만"
   - 교체: "별도 alias YAML 또는 checklist schema migration"

6. "`score < 0.35` 고정 gate"
   - 교체: "retrieval method별 calibration 후 threshold 적용"

7. "Cohere Structured Output이 모든 상황에서 schema 100% 보장"
   - 교체: "지원 모델과 API 제약을 만족할 때 schema 준수율을 높이며, fallback은 유지"

8. "2026년 법률 6.4% vs 일반 0.8%"
   - 교체: "1차 출처 확인 전에는 정량 수치 대신 LegalBench-RAG 등 검증된 출처로 표현"

---

## 13. Phase 의존성 매트릭스

| 기능 | 필요 Phase | 선행 조건 | Feature flag off 시 영향 |
|---|---|---|---|
| Cohere/OpenAI strict schema | P1 | 현재 DTO와 parser 유지 | 기존 JSON mode/fallback parser 사용 |
| Checklist coverage 최상단 주입 | P1 | `ChecklistCoverageService` 유지 | 기존 prompt 조립 순서로 복귀 |
| 값 포함 Slot Status Block | P1.5 | `slot_state` JSONB | P1 checklist coverage만 사용 |
| StaticQuestionSelector | P1.5 | `slot_state` slot list | Cohere 질문 선택을 log-only로 유지 |
| Intent router shadow logging | P2 | P1 strict schema, P1.5 slot ledger | 기존 RAG/Cohere 경로만 사용 |
| Cohere conditional skip | P2 | shadow eval 통과, fixed template 검토 | 매 턴 Cohere 호출 유지 |
| Slot auto-update | P2 | valueType 검증, confidence threshold 검토 | 사용자 발화 기반 자동 수집 비활성화 |
| DynamicPlanProposer | P3 | P2 caseType/topicChanged, P1.5 slot model | P1.5 slot ledger 기반 흐름 유지 |
| `dynamic_plan_slot` source of truth | P3 | plan table migration, validator | `slot_state`를 임시 source로 유지 |
| Intent-aware retrieval | P4 | P2 intent confidence 안정화 | 기존 hybrid retrieval 유지 |
| Retrieval score gate | P4 | eval calibration 완료 | 모든 검색 결과를 기존 방식으로 주입 |
| Output compliance LLM judge | P4 | deterministic guardrail 안정화 | shadow judge 미실행, regex guardrail만 사용 |

---

## 14. 운영 롤백 판단 기준

각 Phase 문서는 세부 rollback flag를 가진다. 공통 롤백 판단 기준은 아래를 기본값으로 둔다.

| Phase | 롤백 트리거 | 1차 조치 | 결정 권한 |
|---|---|---|---|
| P1 | JSON parse failure > 1% 또는 AI API 4xx/5xx > 5%가 10분 지속 | structured output flag off | 백엔드 리드 |
| P1 | guardrail false positive rate > 2% | guardrail 패턴 commit revert 또는 relaxed profile 적용 | 백엔드 리드 + 법무 검토자 |
| P1.5 | slot 오염률 > 1% 또는 p95 latency +300ms 초과 | `app.ai.slot-ledger.enabled=false` | 백엔드 리드 |
| P2 | Cohere skip false positive rate > 0.5% | 해당 intent flag off, shadow mode 복귀 | 백엔드 리드 |
| P2 | slot auto-update precision < 95% | `enable-slot-auto-update=false` | 백엔드 리드 |
| P3 | validator rejection false positive > 5% 또는 plan 재생성률 > 30% | dynamic plan off, P1.5 ledger로 복귀 | 백엔드 리드 |
| P4 | retrieval gate false drop rate > 2% 또는 Recall@5 baseline 대비 -2%p | retrieval gate off, `fusion-mode=weighted` | 백엔드 리드 |
| P4 | output judge p95 latency +200ms 초과 또는 비용 증가율 > 10% | output judge shadow off | 백엔드 리드 |

부분 롤백을 기본으로 하며, 전체 Phase 비활성화는 부분 flag 해제로 회복되지 않을 때만 수행한다.
