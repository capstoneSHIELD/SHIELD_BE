# AI/RAG Phase P1.5 Implementation: Slot Ledger

상위 문서: `docs/ai-rag-upgrade-plan-v2.2.md`  
Phase: P1.5  
목표 기간: 2~3주  
코드 변경 범위: `slot_state` JSONB 추가, 값 포함 Slot Status Block, deterministic next slot selector

---

## 1. 목표와 비목표

### 목표

- `consultations` 테이블에 `slot_state` JSONB 컬럼을 추가한다.
- `Consultation` 엔티티에 `slotState` JSONB 필드를 추가한다.
- 상담별 slot ledger serializer/deserializer를 구현한다.
- 수집된 값, pending 값, asked question history를 system prompt 최상단에 주입한다.
- static checklist 우선순위 기반 `StaticQuestionSelector`를 도입한다.
- `money/date/text` 최소 valueType 검증 정책을 도입한다.
- P3 이후 `slot_state`와 `dynamic_plan_slot`의 소유권 관계를 미리 명시한다.

### 비목표

- OpenAI intent router 확장과 Cohere skip은 구현하지 않는다. 해당 작업은 P2에서 수행한다.
- 정규화된 dynamic plan table은 만들지 않는다. 해당 작업은 P3에서 수행한다.
- Redis를 primary slot 저장소로 사용하지 않는다. Redis는 비선택 대안으로만 남긴다.
- YAML checklist schema는 변경하지 않는다.

---

## 2. 현재 코드 기준 진입점

- `Consultation`: 이미 여러 JSONB 필드를 가진 상담 aggregate다.
- `MessageService`: 사용자 메시지 저장, RAG 호출, Cohere 호출, AI 메시지 저장 흐름의 중심이다.
- `CohereService`: system prompt를 조립하며 P1에서 checklist summary 최상단 주입이 적용된다.
- `ChecklistCoverageService`: 현재 checklist coverage를 계산하는 안전망이다.
- `src/main/resources/db/migration`: Flyway migration 위치이며 현재 다음 번호는 `V14`다.

먼저 읽을 테스트:

- `ConsultationTest`
- `MessageServiceTest`
- `PromptServiceTest`
- `ChecklistCoverageServiceTest`
- `CohereServiceHistoryAppendTest`

---

## 3. 구현 순서

### Commit 1. DB migration 추가

1. `src/main/resources/db/migration/V14__add_slot_state_to_consultations.sql`을 추가한다.
2. `consultations` 테이블에 nullable `slot_state jsonb` 컬럼을 추가한다.
3. 기존 상담 row는 migration 시 `NULL`로 둔다.
4. 애플리케이션에서 읽을 때 `NULL`이면 빈 ledger로 초기화한다.

Migration:

```sql
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS slot_state jsonb;
```

### Commit 2. 엔티티와 ledger DTO 추가

1. `Consultation`에 `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` 형태로 `slotState` 필드를 추가한다.
2. `SlotLedger`, `SlotStateItem`, `SlotStatus`, `SlotSource` 같은 내부 DTO를 추가한다.
3. JSON shape는 P3 정규화 table로 이전 가능한 형태로 둔다.

기본 shape:

```json
{
  "version": 1,
  "caseType": {
    "l1": null,
    "l2": null,
    "l3": null,
    "confidence": 0.0
  },
  "slots": []
}
```

Slot item shape:

```json
{
  "slotId": "deposit_amount",
  "label": "보증금",
  "source": "static_checklist",
  "required": true,
  "priority": 1,
  "status": "missing",
  "collectedValue": null,
  "pendingValue": null,
  "valueType": "money",
  "confidence": 0.0,
  "askedQuestions": [],
  "answeredAt": null,
  "updatedAt": null
}
```

P3 이후 소유권 정책:

| 시점 | Source of truth | `slot_state` 역할 | 충돌 정책 |
|---|---|---|---|
| P1.5~P2 | `consultations.slot_state` | 실제 상태 저장소 | 해당 없음 |
| P3 dynamic plan disabled | `consultations.slot_state` | 실제 상태 저장소 | 해당 없음 |
| P3 dynamic plan enabled | `dynamic_plan_slot` | 읽기 최적화 summary cache | 정규화 테이블 우선 |

P3 이후 write는 `dynamic_plan_slot`에 먼저 반영하고, 같은 transaction 안에서 `slot_state` summary cache를 재생성한다. 양쪽 값이 다르면 `dynamic_plan_slot` 값을 사용하고 mismatch metric을 남긴다. `slot_state` 컬럼 제거는 P3 안정화 후 최소 2개 release 동안 cache mismatch가 없을 때 별도 migration으로 검토한다.

### Commit 3. Ledger 초기화와 동기화

1. 상담에 `slot_state`가 없으면 현재 domain/checklist coverage 기반으로 빈 ledger를 초기화한다.
2. P1.5에서는 사용자 발화에서 값을 자동 추출하지 않는다.
3. 기존 `ChecklistCoverageService`가 collected로 판단한 항목은 label 중심으로 `status=collected`, `collectedValue=null`로 반영할 수 있다.
4. 실제 값이 없는 collected item은 Slot Status Block에서 `값 미확인`으로 표시한다.

### Commit 4. Slot Status Block 생성

1. `SlotStatusBlockBuilder` 또는 `CohereService` 내부 helper를 추가한다.
2. block은 Cohere system prompt 최상단에 들어간다.
3. collected, pending confirmation, missing, already asked questions를 분리한다.
4. `askedQuestions`는 AI가 해당 slot 질문을 생성한 뒤 append한다.

Block 형식:

```text
=== COLLECTED INFORMATION (DO NOT ASK AGAIN) ===
- 보증금: 값 미확인
- 계약 종료일: 2024-03-31

=== PENDING CONFIRMATION ===
- 월세: 500000 으로 이해했음. 확인 필요.

=== MISSING INFORMATION (TARGET ONLY THESE) ===
- 임대인의 반환 거절 사유

=== ALREADY ASKED QUESTIONS (DO NOT REPEAT) ===
- 보증금은 얼마였나요?

RULE: Never ask again about collected items. Confirm pending items before treating them as collected.
```

Prompt budget:

| Block | P1.5 예산 | 초과 시 축소 정책 |
|---|---:|---|
| COLLECTED INFORMATION | 120 tokens | 최근 answeredAt 순 8개, 나머지는 count summary |
| PENDING CONFIRMATION | 80 tokens | pending 전체 보존, 길면 value truncate |
| MISSING INFORMATION | 120 tokens | required + priority 상위 8개 |
| ALREADY ASKED QUESTIONS | 80 tokens | 최근 3개 우선, 최대 5개 |

caseType이 확정되고 required collected 비율이 80% 이상이면 collected section은 "수집 완료 N개" summary로 축소하고, missing/pending section을 우선 보존한다.

### Commit 5. ValueType 검증 추가

P1.5에서는 자동 추출을 하지 않지만, P2에서 들어올 값을 받을 수 있도록 ledger 레벨 검증기를 먼저 둔다.

| valueType | 허용 형식 | 불일치 처리 |
|---|---|---|
| `money` | 숫자 문자열, 쉼표 포함 숫자, `원` 단위 제거 가능 값 | `pending_confirmation`으로 전환하고 원문을 `pendingValue`에 보관 |
| `date` | `yyyy-MM-dd`, `yyyy-MM`, `yyyy`, 한국어 상대 날짜 원문 | parse 가능하면 정규화, 불가하면 `pending_confirmation` |
| `text` | trim 후 2자 이상 | 2자 미만이면 ignored |

valueType이 없으면 `text`로 취급한다. 검증 실패 값은 `collectedValue`에 쓰지 않는다.

### Commit 6. StaticQuestionSelector 추가

1. ledger의 missing slot 중 다음 질문 대상을 deterministic하게 선택한다.
2. 우선순위는 static required, static optional, dynamic, pending confirmation 순서다.
3. P1.5에서는 intent router가 없으므로 pending 우선 조건을 deterministic 휴리스틱으로만 판단한다.
4. Cohere가 생성한 질문이 selector 대상과 다르면 P1.5에서는 override하지 않고 warning log만 남긴다. 실제 override는 P2에서 활성화한다.

P1.5 pending 우선 조건:

- 직전 AI 질문이 `맞나요`, `확인`, `말씀하신`, `이해했는데` 중 하나를 포함한다.
- 사용자 응답이 긍정어 또는 부정어로 시작한다.
- 사용자 응답 길이가 30자 이하이거나, 새 금액/날짜/도메인 키워드를 포함하지 않는다.

예시:

| 직전 AI 질문 | 사용자 응답 | 처리 |
|---|---|---|
| `보증금이 3000만원이라고 이해했는데 맞나요?` | `네 맞아요` | pending slot을 collected로 확정 |
| `보증금이 3000만원이라고 이해했는데 맞나요?` | `아니요` | pending 값을 버리고 해당 slot을 missing으로 되돌린 뒤 정정 요청 |
| `보증금이 3000만원이라고 이해했는데 맞나요?` | `아니요, 5000만원이고 계약은 작년에 끝났어요` | pending 우선 처리하지 않고 일반 정보 제공으로 간주, P2 전에는 Cohere 호출 유지 |

---

## 4. 인터페이스/API 변경

- DB migration:
  - `V14__add_slot_state_to_consultations.sql`
  - `consultations.slot_state jsonb`
- Entity:
  - `Consultation.slotState`
- Internal DTO:
  - `SlotLedger`
  - `SlotStateItem`
  - `SlotStatus`
  - `SlotSource`
  - `SlotValueType`
  - `SlotValueValidator`
- External API:
  - 응답 body 변경 없음
  - 상담 저장 데이터에만 `slot_state`가 추가된다.

---

## 5. 테스트 계획

### Unit tests

- `ConsultationTest`
  - `slotState` JSONB 필드가 저장 가능한지 확인한다.
- 신규 `SlotLedgerTest`
  - null state가 빈 ledger로 초기화되는지 확인한다.
  - serializer/deserializer가 shape를 유지하는지 확인한다.
- 신규 `SlotStatusBlockBuilderTest`
  - collected/pending/missing/asked sections가 올바른 순서로 만들어지는지 확인한다.
- 신규 `StaticQuestionSelectorTest`
  - static required가 static optional보다 우선하는지 확인한다.
  - dynamic slot은 static slot 이후 선택되는지 확인한다.
- 신규 `SlotValueValidatorTest`
  - money/date/text 값 검증과 pending 전환 정책을 확인한다.
  - 타입 불일치 값이 `collectedValue`에 저장되지 않는지 확인한다.
- 신규 `PendingConfirmationHeuristicTest`
  - 긍정/부정/새 정보 혼합 응답의 분기 결과를 확인한다.

### Integration tests

- `MessageServiceTest`
  - 기존 상담 흐름에서 `slot_state`가 null이어도 에러 없이 동작한다.
  - AI 질문 저장 후 asked question history가 append된다.
- `CohereServiceHistoryAppendTest`
  - Slot Status Block이 system prompt 최상단에 포함된다.

---

## 6. 완료 기준

- [ ] `V14__add_slot_state_to_consultations.sql`이 추가된다.
- [ ] `Consultation.slotState`가 JSONB로 매핑된다.
- [ ] null `slot_state` 상담도 기존처럼 정상 처리된다.
- [ ] P3 이후 `dynamic_plan_slot`이 source of truth이고 `slot_state`는 cache라는 정책이 문서와 테스트에 반영된다.
- [ ] valueType 불일치 값은 collected로 저장되지 않는다.
- [ ] Slot Status Block이 checklist summary보다 앞에 위치한다.
- [ ] asked question history가 ledger에 누적된다.
- [ ] `StaticQuestionSelector`가 deterministic한 결과를 반환한다.
- [ ] pending confirmation 휴리스틱 예시 3개가 테스트로 고정된다.
- [ ] P1.5에서 Cohere skip이나 intent router 분기는 추가되지 않는다.

---

## 7. Rollback / Feature Flag

- `slot_state` 컬럼은 nullable이므로 애플리케이션 rollback 시 기존 코드가 컬럼을 무시해도 된다.
- Slot Status Block 주입은 `app.ai.slot-ledger.enabled`로 끌 수 있게 한다.
- StaticQuestionSelector는 P1.5에서 log-only로 시작한다.
- Redis는 선택하지 않는다. 단, JSONB write latency가 운영상 문제가 되면 P2 이후 write-through cache 후보로 재검토한다.
- 즉시 rollback 기준은 slot 오염률 > 1%, `slot_state` 직렬화 오류 > 0.5%, 또는 p95 latency가 P1 대비 300ms 이상 증가하는 경우다.
