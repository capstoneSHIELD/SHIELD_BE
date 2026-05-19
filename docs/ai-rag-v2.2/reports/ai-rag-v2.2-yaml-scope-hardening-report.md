# SHIELD AI/RAG v2.2 YAML Scope Hardening 완료 보고서

작성일: 2026-05-18  
보고 대상: AI/RAG v2.2 P1/P1.5 staging 배포 전 하드닝 결과  
작성 목적: 새 YAML 구조 적용 이후 AI/RAG 체크리스트 해석 경로의 정합성, 검증 결과, 운영 판단 포인트 보고

---

## 1. Executive Summary

새 체크리스트 YAML 구조가 `L1 > L2 > L3` 계층과 optional `nodes/<node-id>.yaml` override를 지원하도록 바뀌면서, AI/RAG 파이프라인의 prompt, coverage, slot ledger, alias validation이 서로 다른 체크리스트 해석 결과를 볼 위험이 있었습니다.

이번 하드닝에서는 `ChecklistScopeResolver`를 공통 해석기로 추가해 Cohere prompt, coverage/allCompleted, slot ledger, correctedSlots, dynamic alias validation이 모두 같은 scoped checklist item set을 기준으로 동작하도록 정합화했습니다.

핵심 결론은 다음과 같습니다.

| 항목 | 결과 |
|---|---|
| YAML scope 정합성 | 공통 resolver 기반으로 정리 완료 |
| Prompt와 coverage 기준 | 동일 item set 사용 |
| Slot ledger scope 변경 대응 | L1 → L2 → L3 narrowing 시 reconcile 처리 |
| Stable slot id | 신규 생성 정책 적용, legacy id fallback 유지 |
| Dynamic alias validation | 전체 L1 YAML static item 기반 generated alias 확장 |
| DB schema 변경 | 없음 |
| 위험 기능 활성화 | 없음. 기존 default off/shadow 유지 |
| 전체 테스트 | `.\gradlew.bat test` 통과 |

이번 변경은 새 기능 활성화가 아니라 P1/P1.5 staging 전에 체크리스트 구조 불일치 리스크를 낮추는 하드닝 작업입니다.

---

## 2. 배경과 문제

기존 구조에서는 `ChecklistPromptBuilder`가 Cohere prompt용 scoped checklist를 만들었지만, `ChecklistCoverageService`와 `SlotLedgerService`는 별도 경로로 YAML을 해석했습니다.

이 상태에서 새 YAML 구조가 적용되면 다음 문제가 발생할 수 있었습니다.

| 리스크 | 영향 |
|---|---|
| Prompt와 coverage item 불일치 | Cohere는 A 슬롯을 질문하지만 allCompleted는 B 슬롯 기준으로 계산 |
| L1 전체 체크리스트 과주입 | 현재 사건과 무관한 L2/L3 항목이 prompt에 노출 |
| 대화 중 분류 narrowing 미반영 | L1 상담에서 L3로 좁혀져도 slot_state가 이전 범위에 머무름 |
| Legacy `static_001` id 의존 | correctedSlots와 slot update가 새 YAML 구조에서 깨질 수 있음 |
| Alias 범위 편중 | P3 dynamic validator가 일부 도메인 alias에만 의존 |

특히 법률 상담에서는 수집 기준과 완료 판정 기준이 어긋나면 반복 질문, 누락 슬롯, 잘못된 상담 요약으로 이어질 수 있으므로 staging 전에 정리할 필요가 있었습니다.

---

## 3. 구현 결과

### 3.1 ChecklistScopeResolver 추가

`ChecklistScopeResolver`를 공통 YAML 해석기로 추가했습니다.

입력:

- `l1Name`
- `l2Name`
- `l3Name`

출력:

- `ChecklistScope`
- `ChecklistScopeItem[]`
- scope warning

각 item은 다음 정보를 포함합니다.

| 필드 | 설명 |
|---|---|
| `slotId` | stable static slot id |
| `label` | 사용자/프롬프트 표시명 |
| `level` | L1/L2/L3 |
| `required` | 필수 여부 |
| `priority` | 질문 우선순위 |
| `sourcePath` | 원본 YAML 또는 override 경로 |
| `nodeId` | ontology node id |
| `valueType` | money/date/text 등 타입 추론 |

Stable slot id 형식:

```text
static:{l1Slug}:{l2NodeIdOrRoot}:{l3NodeIdOrRoot}:{normalizedLabelHash}
```

기존 `static_001` 계열 id는 신규 생성에 사용하지 않고, migration 없이 legacy fallback으로만 유지합니다.

### 3.2 Prompt와 Coverage 기준 통합

`ChecklistPromptBuilder`와 `ChecklistCoverageService`가 같은 resolver 결과를 사용하도록 변경했습니다.

변경 전:

```text
Prompt Builder      → 자체 scoped YAML 해석
Coverage Service    → 별도 coverage item 수집
Slot Ledger Service → coverage 결과 기반 초기화
```

변경 후:

```text
ChecklistScopeResolver
  ├─ ChecklistPromptBuilder
  ├─ ChecklistCoverageService
  ├─ SlotLedgerService
  └─ ChecklistAliasIndex
```

이제 prompt에 주입되는 checklist item과 coverage/allCompleted 계산에 쓰이는 item이 동일합니다.

### 3.3 Slot Ledger Reconciliation

상담 중 분류가 `L1 → L2 → L3`로 좁혀질 때 기존 `slot_state`를 새 scope에 맞춰 reconcile하도록 했습니다.

정책:

| 상황 | 처리 |
|---|---|
| stable slot id 동일 | 기존 status/value/askedAt/answeredAt 보존 |
| legacy id만 존재 | normalized label match 후 stable id로 승격, 기존 id는 `legacySlotId`로 보존 |
| 새 scope에만 있는 slot | `MISSING`으로 추가 |
| 이전 scope에만 있고 값이 있는 slot | 삭제하지 않고 `outOfScope=true`로 보존 |
| 이전 scope에만 있고 값이 없는 slot | 질문 후보에서 제외 |
| correctedSlots | stable id exact match → legacy id exact match 순서로만 처리 |

이 변경으로 분류가 좁혀져도 이미 수집한 답변을 잃지 않고, 동시에 현재 사건 범위 밖의 슬롯을 다시 질문하지 않게 됩니다.

### 3.4 Alias Index 전체 도메인 확장

`ChecklistAliasIndex`가 수동 alias YAML뿐 아니라 `ChecklistScopeResolver`가 생성한 전체 static checklist item을 generated alias로 등록하도록 확장했습니다.

효과:

- 부동산 도메인 외 checklist item도 P3 validator에서 참조 가능
- `staticMappingId = stable slot id` 우선 사용
- 기존 `domain.slotId` 계열 mapping은 legacy alias로 유지
- 중복 label이 있어도 stable id 기준으로 충돌 가능성 감소

---

## 4. 운영 안전성

이번 변경은 하드닝 성격이며 운영 위험 기능을 새로 켜지 않습니다.

| 기능 | 현재 기본값 | 이번 변경 영향 |
|---|---|---|
| Intent router 운영 분기 | shadow mode | 변경 없음 |
| ASK_LEGAL_ADVICE skip | off | 변경 없음 |
| Slot auto-update | off | 변경 없음 |
| Dynamic plan | off | 변경 없음 |
| Retrieval gate | off | 변경 없음 |
| Output judge | off/shadow | 변경 없음 |

DB migration도 없습니다. `slot_state` JSONB에는 optional field만 추가되므로 기존 데이터 역직렬화와 backward compatibility를 유지합니다.

---

## 5. 테스트 결과

전체 테스트를 통과했습니다.

| 항목 | 결과 |
|---|---:|
| 테스트 suite 수 | 71 |
| 테스트 case 수 | 329 |
| 실패 | 0 |
| 에러 | 0 |
| 스킵 | 2 |
| 실행 명령 | `.\gradlew.bat test` |
| 최종 결과 | `BUILD SUCCESSFUL` |

스킵 2건은 기존과 동일한 조건부 integration test입니다.

| Skipped test | 사유 |
|---|---|
| `LegalChunkRepositoryIT` | Docker/Testcontainers 기반 PostgreSQL + pgvector 테스트 |
| `BaselineMetricsRealIT` | `BASELINE_REAL=true`에서만 실행하는 실제 baseline 테스트 |

핵심 검증 항목:

| 구분 | 검증 내용 | 테스트 |
|---|---|---|
| YAML scope | L1/L2/L3 scope item, stable id, fallback warning | `ChecklistScopeResolverTest` |
| Prompt/Coverage 정합성 | prompt와 coverage가 같은 resolver item 사용 | `ChecklistScopeResolverTest` |
| Node override | `nodes/<node-id>.yaml` override 처리 | `ChecklistScopeResolverTest`, `ChecklistPromptBuilderTest` |
| Ledger narrowing | L1 → L3 narrowing 시 기존 상태 보존, 새 slot 추가 | `SlotLedgerServiceTest` |
| Legacy fallback | `static_001` 계열 id 승격 및 correctedSlots fallback | `SlotLedgerServiceTest` |
| Alias 확장 | manual alias 유지, 전체 YAML generated alias 확장 | `ChecklistAliasIndexTest` |
| 회귀 | MessageService, CohereService, RagPipelineService 주요 흐름 유지 | 기존 regression test |

---

## 6. 남은 리스크와 대응

| 리스크 | 현재 대응 | 후속 확인 |
|---|---|---|
| 실제 staging 대화에서 scope narrowing 빈도 미측정 | resolver/reconcile 테스트 완료 | P1/P1.5 staging 로그에서 narrowing case 샘플 확인 |
| generated alias의 과매칭 가능성 | manual alias 우선, stable id 우선 | P3 shadow/dynamic validator 단계에서 false positive 측정 |
| node override 운영 사용 시 override YAML 품질 편차 | parse 실패 시 base L1 YAML fallback 및 warning | override 추가 시 schema validation 필수 |
| legacy `static_001`가 장기적으로 남을 수 있음 | fallback 전용으로 유지 | P3 migration/backfill 계획에서 정리 여부 판단 |

---

## 7. 운영 적용 판단

P1/P1.5 staging 배포 관점에서 이번 하드닝은 승인 가능한 상태입니다. 단, 운영 Go는 staging 지표 측정 후 재판단해야 합니다.

판단 근거:

- 새 YAML 구조의 핵심 리스크였던 prompt/coverage/slot ledger 해석 불일치를 공통 resolver로 정리했습니다.
- DB schema 변경이 없어 migration 리스크가 없습니다.
- 기존 위험 기능은 계속 off/shadow 상태입니다.
- 전체 자동화 테스트가 통과했습니다.

다만 운영 효과는 아직 측정 전이므로, staging에서는 아래 항목을 반드시 확인해야 합니다.

| 확인 항목 | 방법 |
|---|---|
| L1-only 상담 | L1 공통 checklist만 prompt/coverage에 반영되는지 확인 |
| L2 확정 상담 | L1 공통 + 해당 L2 focus만 반영되는지 확인 |
| L3 확정 상담 | L1 공통 + L2 focus + 해당 L3 item만 반영되는지 확인 |
| 대화 중 narrowing | L1 → L3 전환 시 기존 collected/pending 값 보존 확인 |
| correctedSlots | stable id 또는 legacy id로 pending correction 전환 확인 |
| 반복 질문 후보 | out-of-scope slot이 질문 후보에서 제외되는지 확인 |

---

## 8. 다음 액션

| 우선순위 | 액션 | 담당 역할 | 목표 |
|---:|---|---|---|
| 1 | P1/P1.5 staging 배포 티켓에 YAML scope regression 항목 추가 | 백엔드 담당자 | 배포 전 |
| 2 | Docker 가능 환경에서 `LegalChunkRepositoryIT` 별도 실행 | 백엔드 담당자 | staging 전 |
| 3 | staging에서 L1/L2/L3 scope별 실제 prompt와 coverage 로그 샘플 확인 | 백엔드 담당자 | 배포 후 24시간 |
| 4 | P2 shadow eval 전에 generated alias false positive 샘플링 기준 추가 | AI/RAG 담당자 | P2 shadow 전 |
| 5 | legacy slot id 제거 여부를 P3 backfill 계획에 포함 | 백엔드 담당자 | P3 활성화 전 |

---

## 9. 결론

AI/RAG v2.2의 새 YAML 구조 대응 하드닝은 완료됐습니다.

이번 변경으로 Cohere가 보는 체크리스트, 백엔드 coverage/allCompleted 기준, slot ledger 상태, dynamic alias validation이 동일한 scoped checklist 해석 결과를 공유하게 됐습니다. 따라서 P1/P1.5 staging 전에 우려됐던 YAML 구조 불일치 리스크는 코드와 테스트 기준으로 해소됐습니다.

운영 적용은 기존 원칙대로 진행합니다. P1/P1.5는 staging에서 지표를 측정한 뒤 Go/No-go를 판단하고, P2 이후 기능은 shadow eval 결과를 별도 승인받아 순차 활성화합니다.
