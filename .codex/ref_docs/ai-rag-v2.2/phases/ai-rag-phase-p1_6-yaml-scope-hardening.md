# AI/RAG v2.2 Phase P1.6 — YAML Scope Compatibility Hardening

작성일: 2026-05-18  
위치: P1.5 Slot Ledger 이후, P2 운영 분기 활성화 이전  
목적: 새 YAML 구조(`L1 > L2 > L3`, optional `nodes/<node-id>.yaml`)를 AI/RAG 체크리스트 경로 전체에 동일하게 적용한다.

---

## 1. 목표와 비목표

### 목표

- `ChecklistPromptBuilder`, `ChecklistCoverageService`, `SlotLedgerService`, `ChecklistAliasIndex`가 같은 scoped checklist 해석 결과를 사용한다.
- Cohere prompt, coverage/allCompleted, slot ledger, correctedSlots, dynamic alias validation의 체크리스트 기준을 일치시킨다.
- L1 YAML은 계속 canonical source로 유지하고, node override는 optional 확장으로만 둔다.
- 기존 `static_001`류 slot id는 신규 생성하지 않고 legacy fallback으로만 유지한다.

### 비목표

- Dynamic plan 운영 활성화는 하지 않는다.
- node override YAML을 대량 생성하지 않는다.
- DB migration은 추가하지 않는다.
- P2 intent skip, slot auto-update, retrieval gate, output judge 기본값은 변경하지 않는다.

---

## 2. 현재 코드 기준 진입점

| 진입점 | 기존 책임 | P1.6 변경 후 책임 |
|---|---|---|
| `ChecklistScopeResolver` | 신규 | L1/L2/L3 scope, node override, stable slot id, value type을 단일 모델로 해석 |
| `ChecklistPromptBuilder` | prompt용 YAML 직접 해석 | resolver 결과를 prompt 문자열로 렌더링 |
| `ChecklistCoverageService` | coverage용 YAML 직접 해석 | resolver item으로 coverage/allCompleted/summary 계산 |
| `SlotLedgerService` | 최초 slot_state 생성 | scope narrowing 시 ledger reconcile, stable id/legacy id 처리 |
| `StaticQuestionSelector` | missing/pending slot 선택 | out-of-scope slot 제외 |
| `SlotStatusBlockBuilder` | slot 상태 prompt 주입 | stable slot id 포함, out-of-scope slot 제외 |
| `ChecklistAliasIndex` | 수동 alias YAML만 로드 | resolver의 모든 static item을 generated alias로 추가 |

---

## 3. 구현 순서

### Commit 1 — ChecklistScopeResolver 도입

- `ChecklistScope`, `ChecklistScopeItem`, `ChecklistScopeLevel` DTO를 추가한다.
- `ChecklistScopeResolver.resolve(l1, l2, l3)`를 구현한다.
- item 필드는 `slotId`, `label`, `level`, `required`, `priority`, `sourcePath`, `nodeId`, `valueType`을 가진다.
- stable slot id는 다음 형식으로 생성한다.

```text
static:{l1Slug}:{l2NodeIdOrRoot}:{l3NodeIdOrRoot}:{normalizedLabelHash}
```

- `ai/checklists/nodes/<node-id>.yaml` override를 resolver에서만 처리한다.
- override parse 실패 시 base L1 YAML로 fallback하고 warning을 남긴다.

### Commit 2 — Prompt와 Coverage를 Resolver로 통합

- `ChecklistPromptBuilder`는 YAML을 직접 읽지 않고 resolver 결과만 렌더링한다.
- `ChecklistCoverageService`의 직접 `collectItems()` 해석을 제거한다.
- `compute`, `buildCoverageItems`, `buildCollectedSummary`, `buildMissingSlotsGuidance`가 모두 resolver item을 사용한다.
- prompt에 들어가는 item과 coverage에 쓰이는 item이 같은지 테스트로 고정한다.

### Commit 3 — Slot Ledger Reconciliation

- `SlotLedgerService.ensureInitialized()`가 기존 ledger를 그대로 반환하지 않고 현재 scope와 reconcile한다.
- 같은 stable slot id는 기존 상태를 보존한다.
- legacy `static_001`류 slot은 label normalized match가 있으면 stable id로 승격한다.
- 승격 시 기존 id는 `legacySlotId`에 저장한다.
- 현재 scope에 새로 생긴 slot은 `MISSING`으로 추가한다.
- 이전 scope에만 있는 slot은 삭제하지 않고 `outOfScope=true`로 보존한다.
- `StaticQuestionSelector`와 `SlotStatusBlockBuilder`는 `outOfScope` slot을 질문/상태 주입 대상에서 제외한다.
- `correctedSlots` lookup 순서는 stable id exact match, legacy id exact match까지만 허용한다.

### Commit 4 — Alias Index 전체 도메인 확장

- `ChecklistAliasIndex`가 수동 alias YAML 외에 resolver의 모든 static checklist item을 generated alias로 등록한다.
- 수동 alias는 기존 legacy mapping id를 유지한다.
- generated alias의 `staticMappingId`는 stable slot id를 사용한다.
- `BackendValidator`는 기존 호출 방식을 유지하되, alias index 확장으로 모든 L1 도메인 static item을 참조할 수 있다.
- alias coverage report를 제공한다.

### Commit 5 — 문서와 Rollout 체크

- 본 phase 문서를 추가한다.
- 개발 보고서에 P1.6 완료 요약을 추가한다.
- rollout checklist에 YAML scope regression 시나리오를 추가한다.

---

## 4. 인터페이스/API 변경

### 신규 DTO

- `ChecklistScope`
- `ChecklistScopeItem`
- `ChecklistScopeLevel`

### 확장 DTO

- `ChecklistCoverageService.CoverageItem`
  - `slotId`
  - `label`
  - `required`
  - `valueType`
  - `sourcePath`
  - `nodeId`
  - `collected`

- `SlotStateItem`
  - `sourcePath`
  - `nodeId`
  - `outOfScope`
  - `legacySlotId`

- `ChecklistAliasIndex.AliasEntry`
  - `source`
  - `level`
  - `sourcePath`

### Backward Compatibility

- 기존 JSONB `slot_state`에 새 필드가 없어도 역직렬화 가능하다.
- 기존 `static_001` id는 삭제하지 않고 `legacySlotId` fallback으로 보존한다.
- 외부 API 응답 구조는 변경하지 않는다.

---

## 5. 테스트 계획

| 테스트 | 검증 내용 |
|---|---|
| `ChecklistScopeResolverTest` | L1/L2/L3 scope item 생성, stable id, fallback warning |
| `ChecklistScopeResolverTest` | node override가 동일 item model로 해석됨 |
| `ChecklistScopeResolverTest` | prompt와 coverage가 같은 resolved item을 사용함 |
| `ChecklistScopeResolverTest` | 8개 L1 YAML 전체에서 generated static item 생성 |
| `ChecklistPromptBuilderTest` | scoped prompt 렌더링, node override 동작 유지 |
| `ChecklistCoverageServiceTest` | coverage, missing guidance, collected summary 회귀 |
| `SlotLedgerServiceTest` | L1 → L3 narrowing 시 기존 상태 보존 + 새 slot 추가 |
| `SlotLedgerServiceTest` | legacy `static_001` id 승격 및 correctedSlots fallback |
| `ChecklistAliasIndexTest` | manual alias 유지 + generated scope alias 확장 |

전체 검증 명령:

```powershell
.\gradlew.bat test
```

---

## 6. 완료 기준

- prompt와 coverage가 같은 resolver item set을 사용한다.
- scope narrowing 시 `slot_state`가 새 L2/L3 slot을 merge한다.
- collected/pending/asked 상태가 scope 변경 후에도 보존된다.
- out-of-scope missing slot은 다음 질문 후보에서 제외된다.
- alias index가 real-estate 수동 alias에만 의존하지 않고 모든 L1 YAML static item을 포함한다.
- Dynamic plan, intent skip, retrieval gate, output judge는 계속 default off/shadow 상태다.

---

## 7. Rollback/Feature Flag

P1.6은 별도 운영 기능 flag를 추가하지 않는다. 문제가 발생하면 기존 안전장치를 사용한다.

| 문제 | Rollback 방법 |
|---|---|
| slot ledger reconcile로 질문 선택 이상 | `AI_SLOT_LEDGER_ENABLED=false` |
| scoped prompt 주입 이상 | 기존 `PromptService.loadChecklist(domain)` fallback 코드 경로 복구 |
| generated alias 과매칭 | dynamic plan flag 유지 off, `AI_DYNAMIC_PLAN_ENABLED=false` |
| correctedSlots id 매칭 이상 | `AI_COHERE_CORRECTED_SLOTS_ENABLED=false` |

운영 적용 전에는 staging에서 아래 시나리오를 반드시 확인한다.

- L1-only 상담
- L2 확정 상담
- L3 확정 상담
- 대화 중 L1 → L3 narrowing 상담
- correctedSlots correction 상담
