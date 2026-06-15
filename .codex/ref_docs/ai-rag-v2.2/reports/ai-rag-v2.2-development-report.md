# SHIELD AI/RAG v2.2 Phase 1~4 개발 완료 및 운영 검증 대기 보고서

작성일: 2026-05-18  
대상: AI/RAG 구조개편 v2.2 Phase 1, P1.5, P2, P3, P4 개발 결과  
작성 목적: 팀장 보고 및 staging/운영 적용 판단을 위한 개발 범위, 검증 결과, 미측정 지표, 잔여 리스크 정리

---

## 1. Executive Summary

AI/RAG 구조개편 v2.2 계획에 따라 Phase 1부터 Phase 4까지의 백엔드 기능 개발을 완료했습니다.

이번 개발의 핵심은 LLM이 상담 흐름을 직접 결정하지 않도록 하고, 백엔드가 상태 저장, 분기, 검증, 질문 선택, rollback 기준을 통제하도록 구조를 바꾼 것입니다. 기존 RAG/Cohere 흐름은 유지하되, structured output, slot ledger, intent router, dynamic plan, RAG 품질 루프를 모두 feature flag 기반으로 단계적 활성화할 수 있게 준비했습니다.

단, 본 보고서에서 말하는 "완료"는 **코드 작성과 자동화 테스트 통과**를 의미합니다. 운영 효과가 이미 확인됐다는 뜻은 아닙니다. 반복 질문률, intent 정확도, slot auto-update precision, RAG baseline 지표는 아직 staging 또는 eval set 기반 측정 전입니다.

P1/P1.5 staging 배포 목표일은 **2026-05-22**로 두고, 실제 배포 티켓에서 담당자, dashboard URL, 알림 채널을 최종 확정합니다.

최종 자동화 테스트 결과는 다음과 같습니다.

| 항목 | 결과 |
|---|---:|
| 테스트 suite 수 | 71 |
| 테스트 case 수 | 329 |
| 실패 | 0 |
| 에러 | 0 |
| 스킵 | 2 |
| 실행 명령 | `.\gradlew.bat test` |
| 최종 결과 | `BUILD SUCCESSFUL` |

스킵 2건은 의도된 조건부 integration test입니다.

| Skipped test | 사유 | 운영 승인 영향 |
|---|---|---|
| `LegalChunkRepositoryIT` | Docker/Testcontainers 기반 PostgreSQL + pgvector 통합 테스트이며, 현재 실행 환경에서 Docker 조건이 충족되지 않아 skip | 운영 전 CI 또는 Docker 가능 환경에서 별도 실행 필요 |
| `BaselineMetricsRealIT` | 실제 외부 인프라, Cohere, DB를 사용하는 baseline 측정 테스트이며, `BASELINE_REAL=true`가 설정되지 않아 skip | P4 baseline 측정 단계에서 별도 실행 필요 |

커버리지는 JaCoCo 기준으로 산출 가능해졌습니다. 현재 full test 기준 전체 line coverage는 56.49%, branch coverage는 49.37%입니다. 다만 전체 수치에는 AI/RAG 외 레거시 영역이 함께 포함됩니다. `org.example.shield.ai.*` 패키지만 보면 line coverage는 68.93%, 이번 v2.2 하드닝 신규 서비스 8개는 75.88%입니다. 이번 단계에서는 gate를 blocking하지 않고 리포트 생성만 수행합니다.

---

## 2. 현재 판정

| 항목 | 판정 | 근거 |
|---|---|---|
| 아키텍처 방향 | 승인 가능 | LLM은 제안/분류/생성만 수행하고, 결정과 상태 변경은 백엔드가 담당 |
| 코드 완성도 | 자동화 테스트 기준 확인 | `.\gradlew.bat test` 통과 |
| 운영 효과 검증 | 미완료 | staging 지표와 eval set 지표가 아직 없음 |
| P1/P1.5 운영 적용 | staging 검증 후 재판단 | parse failure, guardrail false positive, 반복 질문률 측정 필요 |
| P2 이후 운영 적용 | shadow eval 후 단계별 승인 필요 | intent/slot/skip false positive 라벨링 필요 |
| P3 dynamic plan 운영 write | 보류 | `slot_state`와 `dynamic_plan_slot` drift 감지 및 backfill 계획 필요 |
| P4 retrieval gate/RRF/output judge | 보류 | baseline과 calibration 전에는 운영 적용 불가 |

---

## 3. 전체 개발 범위와 상태

| Phase | 코드 상태 | 운영 효과 검증 상태 | 핵심 결과 |
|---|---|---|---|
| P1 | 개발 완료 | staging 지표 측정 전 | Cohere/OpenAI structured output 기반, prompt 상단 상태 주입, guardrail 강화 |
| P1.5 | 개발 완료 | staging 지표 측정 전 | `slot_state` JSONB 기반 slot ledger, Slot Status Block, pending confirmation, static selector |
| P2 | 개발 완료 | shadow eval 전 | IntentRouterResponse 확장, intent 기반 Cohere skip 구조, slot auto-update shadow 구조 |
| P3 | 개발 완료 | 운영 write 비활성 | UUID 기반 dynamic plan schema, DynamicPlanProposer, BackendValidator, alias mapping |
| P4 | 개발 완료 | eval set 측정 전 | RRF, calibrated retrieval gate, intent-aware retrieval, output judge shadow, offline quality report 기반 |
| Rollout | 구현/문서 보강 완료 | 실행 전 | feature flag 기본값 테스트, rollback policy 코드화, rollout checklist 정리 |

---

## 4. 팀장 리뷰 이후 실제 구현 보강 사항

보고서만 수정하지 않고, 운영 판단에 필요한 일부 기준을 실제 코드와 테스트로 보강했습니다.

| 리뷰 이슈 | 구현 보강 | 테스트 |
|---|---|---|
| feature flag 기본값 보장 범위 불명확 | `application.yml`을 실제 Spring property loader로 읽어 기본값을 검증하도록 변경 | `AiRagFeatureFlagDefaultsTest` |
| rollback 기준이 문서에만 있음 | 기능별 rollback 판단 기준을 `AiRagRollbackPolicy`로 코드화 | `AiRagRollbackPolicyTest` |
| rollback 시간/표본 조건 불일치 | 기능별 duration, sample count, high-risk count 조건을 정책 객체로 통일 | `AiRagRollbackPolicyTest` |
| 스킵 2건 사유 불명확 | real infra/Docker 조건부 테스트임을 코드 annotation/tag와 보고서에 명시 | 전체 테스트 결과 스킵 2건 유지 |
| ASK_LEGAL_ADVICE leak 판단 주체 불명확 | Go 기준에 개발팀 1차 라벨링, 법무/정책 담당자 최종 판정으로 명시 | 문서 기준 반영 |
| coverage 미측정 | JaCoCo 리포트 생성 task 추가 | `.\gradlew.bat test`, `jacocoTestReport` |
| P1/P1.5 Go 지표 수집 기반 부재 | structured output, AI API error, guardrail, repeated question, slot pollution metric 추가 | `AiRagOperationalMetricsTest` |
| P2 shadow eval 산출물 부재 | JSONL/CSV exporter와 summary aggregator 추가 | `IntentShadowEvalExporterTest`, `IntentShadowEvalAggregatorTest` |
| P3 drift/backfill 미구현 | drift detector와 dry-run/execute backfill service 추가 | `DynamicPlanDriftDetectorTest`, `DynamicPlanBackfillServiceTest` |
| P4 baseline 산출 기반 부재 | v2.2 eval set/schema, baseline evaluator, Markdown/JSON report writer 추가 | `RagBaselineEvaluatorTest` |
| rollout 판단 리포트 부재 | `AiRagRollbackPolicy`를 사용하는 rollout summary generator 추가 | `AiRagRolloutSummaryGeneratorTest` |
| L1 체크리스트 전체 주입으로 관련 없는 L2/L3 항목이 프롬프트에 노출 | 확정된 L1/L2/L3 범위만 잘라 주입하는 scoped checklist builder 추가 | `ChecklistPromptBuilderTest`, `CohereServiceHistoryAppendTest` |
| 새 YAML 구조에서 prompt/coverage/slot ledger 해석 경로 불일치 위험 | `ChecklistScopeResolver`를 공통 해석기로 추가하고 prompt, coverage, slot ledger, alias index를 같은 item model로 통합 | `ChecklistScopeResolverTest`, `SlotLedgerServiceTest`, `ChecklistAliasIndexTest` |

추가된 주요 파일:

| 파일 | 역할 |
|---|---|
| `src/main/java/org/example/shield/ai/application/AiRagRollbackPolicy.java` | 기능별 rollback 판단 정책 |
| `src/main/java/org/example/shield/ai/dto/AiRagRolloutFeature.java` | rollback 대상 기능 enum |
| `src/main/java/org/example/shield/ai/dto/AiRagRollbackSignal.java` | rollback 판단 입력 신호 |
| `src/main/java/org/example/shield/ai/dto/AiRagRollbackDecision.java` | rollback/keep 판단 결과 |
| `src/test/java/org/example/shield/ai/application/AiRagRollbackPolicyTest.java` | rollback 기준 테스트 |
| `src/test/java/org/example/shield/ai/application/AiRagFeatureFlagDefaultsTest.java` | 실제 YAML property 기반 feature flag 기본값 테스트 |
| `src/main/java/org/example/shield/ai/application/ChecklistPromptBuilder.java` | L1 YAML에서 확정된 L1/L2/L3 범위만 프롬프트용 체크리스트로 조립 |
| `src/test/java/org/example/shield/ai/application/ChecklistPromptBuilderTest.java` | scoped checklist, fallback, node-id override 검증 |
| `src/main/java/org/example/shield/ai/application/ChecklistScopeResolver.java` | L1/L2/L3 YAML scope, node override, stable slot id를 단일 item model로 해석 |
| `src/main/java/org/example/shield/ai/dto/checklist/ChecklistScope*.java` | prompt/coverage/ledger/alias가 공유하는 scoped checklist DTO |

`AiRagRollbackPolicy`는 자동 rollback 실행기가 아니라, 운영/모니터링 레이어가 동일 기준을 재사용할 수 있게 만든 판단 정책 컴포넌트입니다. 실제 config를 자동 변경하는 배선은 별도 운영 자동화 범위입니다.

### 4.1 체크리스트 Scoped 주입 개선

추가 검토 결과, 백엔드에는 이미 L1 YAML 내부에 L2/L3 체크리스트가 포함되어 있었습니다. 따라서 중분류/소분류별 YAML 파일을 전면 생성하는 대신, 기존 L1 YAML을 canonical source로 유지하고 런타임 프롬프트 주입 범위만 축소하는 방식으로 수정했습니다.

#### 문제

기존 `CohereService`는 L1 도메인이 확정되면 `PromptService.loadChecklist(domain)`으로 해당 L1 YAML 전체를 system prompt에 붙였습니다. 예를 들어 `부동산 거래 > 부동산 임대차 > 보증금 및 차임` 상담에서도 `부동산 매매`, `부동산 담보`, `부동산 권리관계`의 L2/L3 체크리스트가 함께 노출될 수 있었습니다.

이 구조는 다음 리스크가 있었습니다.

| 리스크 | 영향 |
|---|---|
| 관련 없는 L2/L3 항목 노출 | LLM이 현재 사건과 무관한 질문을 생성할 가능성 증가 |
| 프롬프트 토큰 낭비 | 같은 L1 안의 모든 하위 체크리스트가 매 턴 포함됨 |
| premature narrowing 또는 topic drift | L2/L3가 확정된 뒤에도 형제 카테고리 힌트가 남아 질문 방향이 흔들릴 수 있음 |

#### 수정 내용

- `ChecklistPromptBuilder`를 추가했습니다.
  - 입력: `l1Name`, `l2Name`, `l3Name`
  - 출력: 프롬프트에 주입할 축소 체크리스트 문자열
  - L1만 확정된 경우: `l1_checklist.required`, `l1_checklist.domain_specific`만 포함
  - L2까지 확정된 경우: L1 공통 + 해당 L2 `focus`만 포함
  - L3까지 확정된 경우: L1 공통 + 해당 L2 `focus` + 해당 L3 항목만 포함
- `CohereService`의 체크리스트 주입 경로를 교체했습니다.
  - 기존: `promptService.loadChecklist(domain)`으로 L1 YAML 전체 주입
  - 변경: `checklistPromptBuilder.build(l1, l2, l3)`로 scoped checklist 주입
- `OntologyService`에 node id 조회 기능을 추가했습니다.
  - `idOf(nodeName)`
  - `childIdOf(parentName, childName)`
- `ai/checklists/nodes/<node-id>.yaml` 경로를 optional override 경로로 예약했습니다.
  - 예: `ai/checklists/nodes/law-001-02.yaml`
  - 예: `ai/checklists/nodes/law-001-02-02.yaml`
  - 파일이 있으면 해당 L2/L3 항목을 override하고, 없으면 기존 L1 YAML 내부 항목을 사용합니다.
  - 현재는 35개 L2 + 136개 L3 파일을 전면 생성하지 않았습니다.

#### 동작 예시

| 확정 분류 | 프롬프트 주입 범위 |
|---|---|
| `부동산 거래` | L1 공통 항목만 |
| `부동산 거래 > 부동산 임대차` | L1 공통 + `부동산 임대차.focus` |
| `부동산 거래 > 부동산 임대차 > 보증금 및 차임` | L1 공통 + `부동산 임대차.focus` + `보증금 및 차임` 항목 |

이제 `보증금 및 차임` 상담 프롬프트에는 `부동산 매매`, `부동산 담보` 같은 형제 L2 체크리스트가 포함되지 않습니다.

#### 검증

| 테스트 | 검증 내용 |
|---|---|
| `ChecklistPromptBuilderTest` | L1-only, L1+L2, L1+L2+L3 scope 동작 검증 |
| `ChecklistPromptBuilderTest` | 존재하지 않는 L2/L3 입력 시 L1 공통 항목으로 fallback |
| `ChecklistPromptBuilderTest` | node-id 기반 L2/L3 YAML override 동작 검증 |
| `CohereServiceHistoryAppendTest` | `CohereService`가 전체 L1 YAML 대신 scoped checklist를 주입하는지 검증 |

최종 검증 명령은 `.\gradlew.bat test`이며, 전체 테스트 결과는 71 suites / 329 cases / failures 0 / errors 0 / skipped 2입니다.

### 4.2 YAML Scope Resolver 정합화

새 YAML 구조(`L1 > L2 > L3`, optional `nodes/<node-id>.yaml`)에 맞춰 체크리스트 해석 경로를 추가로 정합화했습니다.

#### 문제

`ChecklistPromptBuilder`는 scoped checklist를 사용하지만, coverage/allCompleted와 slot ledger 초기화는 별도 YAML 해석 경로를 갖고 있었습니다. 이 상태에서 node override가 추가되거나 상담 중 L1에서 L3로 분류가 좁혀지면, Cohere prompt가 보는 체크리스트와 백엔드 상태/완료 판정 기준이 달라질 수 있었습니다.

#### 수정 내용

- `ChecklistScopeResolver`를 공통 해석기로 추가했습니다.
  - L1/L2/L3 scope item 생성
  - node override fallback
  - stable slot id 생성
  - value type 추론
- `ChecklistPromptBuilder`와 `ChecklistCoverageService`가 같은 resolver item set을 사용하도록 변경했습니다.
- `SlotLedgerService.ensureInitialized()`가 기존 `slot_state`를 그대로 반환하지 않고 현재 scope와 reconcile하도록 변경했습니다.
  - 같은 stable slot id는 기존 상태 보존
  - legacy `static_001`류 id는 `legacySlotId`로 보존 후 stable id로 승격
  - 새 scope slot은 `MISSING`으로 추가
  - 이전 scope에만 남은 slot은 `outOfScope=true`로 보존하고 질문 후보에서 제외
- `ChecklistAliasIndex`가 수동 alias YAML뿐 아니라 전체 scoped YAML item을 generated alias로 등록하도록 확장했습니다.

#### 검증

| 테스트 | 검증 내용 |
|---|---|
| `ChecklistScopeResolverTest` | L1/L2/L3 scope, stable id, fallback warning, node override |
| `ChecklistScopeResolverTest` | prompt와 coverage가 같은 resolved item을 사용함 |
| `SlotLedgerServiceTest` | L1 → L3 narrowing 시 기존 상태 보존 + 새 slot 추가 |
| `SlotLedgerServiceTest` | legacy `static_001` id 승격 및 correctedSlots fallback |
| `ChecklistAliasIndexTest` | manual alias 유지 + generated scope alias 확장 |

---

## 5. Phase별 상세 결과

## Phase 1. Structured Output + Prompt 정비

### 개발 내용

- Cohere chat/brief/classify 응답에 JSON Schema 기반 structured output 계약을 추가했습니다.
- OpenAI classifier 응답도 strict schema 방식으로 확장할 수 있도록 정리했습니다.
- `schema_version` 기반 응답 버전 정책을 DTO/parser 레이어에 반영했습니다.
- 기존 checklist coverage와 최근 질문 blacklist를 system prompt 상단에 주입하도록 개선했습니다.
- 확정된 L1/L2/L3 범위만 체크리스트 프롬프트에 주입하도록 scoped checklist builder를 추가했습니다.
- `GuardrailFilter`를 확장해 법적 판단, 승패 예측, 손해배상 가능성 단정 등 위험 표현을 차단하도록 강화했습니다.

### 준비된 구조

- JSON parsing 실패를 줄일 수 있는 API-level schema 계약이 준비됐습니다.
- P2/P3의 DTO 확장에 대비한 schema version 기반 파싱 구조가 준비됐습니다.
- 같은 질문 반복을 줄이기 위한 prompt 상단 상태 주입 구조가 준비됐습니다.
- 법적 판단 표현을 deterministic guardrail로 차단하는 기반이 강화됐습니다.

### 아직 측정되지 않은 효과

| 지표 | 현재 상태 | 측정 계획 |
|---|---|---|
| JSON parse failure rate | 미측정 | staging 1주 로그에서 chat/brief/classify 응답 전체 대비 parser fallback 발생률 측정 |
| Guardrail false positive rate | 미측정 | 수동 라벨링 200건 이상, 정상 절차 안내 문장의 과차단 비율 측정 |
| 반복 질문률 | 미측정 | P1.5 slot ledger 적용 후 상담 turn 단위로 동일 slot 재질문률 측정 |

---

## Phase 1.5. Slot Ledger 도입

### 개발 내용

- `consultations.slot_state` JSONB 컬럼을 추가하는 Flyway migration을 작성했습니다.
  - `V14__add_slot_state_to_consultations.sql`
- `Consultation` 엔티티에 `SlotLedger` 저장 필드를 추가했습니다.
- slot 상태 DTO를 추가했습니다.
  - `SlotLedger`
  - `SlotStateItem`
  - `SlotStatus`
  - `SlotSource`
  - `SlotValueType`
- `SlotLedgerService`를 통해 collected, missing, pending confirmation 상태를 관리하도록 했습니다.
- `SlotStatusBlockBuilder`를 통해 매 턴 system prompt 최상단에 값 포함 상태 블록을 주입할 수 있게 했습니다.
- `SlotValueValidator`로 money/date/text 타입 검증 정책을 추가했습니다.
- `StaticQuestionSelector`와 `PendingConfirmationHeuristic`을 추가해 백엔드 중심 질문 선택 기반을 만들었습니다.

### 준비된 구조

- LLM 기억에 의존하지 않고, 백엔드가 수집된 사실과 누락 정보를 매 턴 명시적으로 제공할 수 있습니다.
- pending confirmation과 collected를 분리해 모호한 값이 바로 확정되지 않도록 했습니다.
- money/date/text value type 검증 실패 시 collected로 바로 반영하지 않는 정책이 추가됐습니다.
- P2 intent slot extraction과 P3 dynamic plan이 사용할 상태 저장 기반을 마련했습니다.

### 아직 측정되지 않은 효과

| 지표 | 현재 상태 | 측정 계획 |
|---|---|---|
| 반복 질문률 감소 | 미측정 | staging 상담 100건 이상에서 동일 slot 재질문률 비교 |
| pending confirmation 오탐률 | 미측정 | 확인 질문 이후 사용자 긍정/부정/모호 응답 수동 라벨링 |
| slot value type validation 정확도 | 자동 테스트만 완료 | money/date/text 샘플 확장 후 false reject/false accept 측정 |

---

## Phase 2. Intent Router + 조건부 Cohere Skip

### 개발 내용

- 기존 retrieval query 중심 분류 결과를 `IntentRouterResponse`로 확장했습니다.
- 8-class intent 구조를 추가했습니다.
  - `PROVIDE_INFO`
  - `CORRECT_INFO`
  - `CONFIRM`
  - `CHANGE_TOPIC`
  - `ASK_LEGAL_ADVICE`
  - `IRRELEVANT`
  - `GREETING`
  - `END_CONSULTATION`
- `ExtractedSlot`, `CaseTypeResult`, `RagPipelineResult` DTO를 추가했습니다.
- `RagPipelineService`를 문자열 반환 중심에서 상세 결과 반환 구조로 확장했습니다.
- 실제 분기는 `MessageService`의 RAG/Cohere 호출 전 단계에 배치했습니다.
- `BackendIntentRouter`를 추가해 fixed response 또는 Cohere 진행 여부를 백엔드가 결정하도록 했습니다.
- fixed response template을 YAML 리소스로 분리했습니다.
- mixed utterance 정책을 반영했습니다.
  - 예: "보증금은 3천만 원이고, 제가 이길 수 있나요?"
  - high-confidence slot은 수집하되, 사용자 응답은 법적 판단 차단 템플릿으로 처리합니다.
- intent router는 기본 shadow mode로 두고, skip/auto-update는 기본 비활성화했습니다.

### 준비된 구조

- 인사, 무관 발화, 법적 판단 요청 등에 대해 Cohere 호출을 조건부로 생략할 수 있는 구조가 준비됐습니다.
- slot auto-update는 confidence gate와 value type validation을 통과한 경우에만 동작하도록 설계됐습니다.
- CONFIRM skip은 pending slot 존재, intent confidence, corrected slot 부재, deterministic 긍정/부정 heuristic 조건을 모두 만족해야만 수행합니다.

### 아직 측정되지 않은 효과

| 지표 | 현재 상태 | 측정 계획 |
|---|---|---|
| Intent accuracy | 미측정 | 실제 상담 발화 300~500건 수동 라벨링 후 shadow 결과 비교 |
| Slot precision/recall | 미측정 | extracted slot 수동 라벨링과 비교 |
| Cohere skip false positive rate | 미측정 | shadow mode 2주 로그 기준으로 skip됐을 경우 오작동 여부 라벨링 |
| Cohere 호출 감소율 | 미측정 | skip flag 활성화 후 intent 분포 기준 산출 |

---

## Phase 3. DynamicPlanProposer + 정규화 Schema

### 개발 내용

- UUID 기반 dynamic plan 테이블 migration을 추가했습니다.
  - `V15__create_dynamic_plan_tables.sql`
- JPA entity/repository를 추가했습니다.
  - `ConsultationDynamicPlan`
  - `DynamicPlanSlot`
  - `ConsultationDynamicPlanRepository`
  - `DynamicPlanSlotRepository`
- `DynamicPlanProposer`를 추가했습니다.
  - LLM은 plan을 제안만 하고, 실행/승인은 하지 않습니다.
- `BackendValidator`를 추가했습니다.
  - ontology 범위 검증
  - static alias mapping 검증
  - dynamic slot 매핑 가능성 검증
  - legal judgment 표현 검증
- `ChecklistAliasIndex`와 alias YAML을 추가했습니다.
- `DynamicPlanService`에 incremental update 및 plan regeneration 조건을 구현했습니다.
- P3 이후 source of truth 정책을 반영했습니다.
  - `dynamic_plan_slot`이 정규 source of truth
  - `slot_state`는 요약 캐시 역할

### 데이터 일관성 정책 및 현재 구현 상태

| 항목 | 현재 상태 | 운영 전 보완 필요 |
|---|---|---|
| source of truth | P3 활성화 시 `dynamic_plan_slot` 우선 정책 | 운영 문서와 service write path에서 강제 필요 |
| `slot_state` 역할 | `DynamicPlanService.buildSlotStateCache()`로 plan table에서 요약 캐시 재생성 가능 | 매 턴 또는 plan 변경 직후 sync 호출 위치 확정 필요 |
| 불일치 감지 | `DynamicPlanDriftDetector` 구현 완료 | 운영 배치 또는 metric 연결 필요 |
| CORRECT_INFO 갱신 타이밍 | `saveValidatedPlanAndSync`로 plan 저장 직후 같은 transaction에서 `slot_state` 재생성 가능 | CORRECT_INFO P3 write path 연결은 P3 활성화 시 적용 |
| 기존 상담 migration | `DynamicPlanBackfillService` dry-run/execute 기반 구현 | execute는 `AI_DYNAMIC_PLAN_BACKFILL_EXECUTE_ENABLED=true` 명시 시에만 사용 |

현재 결론은 P3 schema, drift detector, backfill 기반은 준비됐지만, `dynamic_plan_slot`을 운영 source of truth로 쓰기 전에는 staging dry-run 결과와 운영 배치 연결을 확인해야 한다는 것입니다. 따라서 `app.ai.dynamic-plan.enabled=false` 기본값을 유지합니다.

### 준비된 구조

- 상담별 동적 체크리스트를 정규화 테이블로 저장할 수 있는 기반을 만들었습니다.
- LLM이 만든 slot plan을 그대로 믿지 않고, 백엔드 validator가 배포 전 검증하도록 했습니다.
- static checklist와 dynamic slot 사이의 운영 승격 흐름을 준비했습니다.

---

## Phase 4. RAG Quality Loop

### 개발 내용

- 현재 weighted hybrid retrieval을 baseline으로 유지했습니다.
- RRF fusion service를 추가했습니다.
  - `RrfFusionService`
  - `RrfFusionInput`
  - `RrfFusionResult`
- retrieval score gate를 추가했습니다.
  - `RetrievalScoreGate`
  - method별 threshold 지원
  - threshold 미설정 시 통과
  - 고정 `score < 0.35` 즉시 적용은 하지 않음
- score calibration 유틸을 추가했습니다.
  - `RetrievalScoreCalibrator`
  - `RetrievalScoreObservation`
  - `RetrievalScoreCalibrationResult`
- intent-aware retrieval policy를 추가했습니다.
  - high confidence에서만 intent별 전략 적용
  - low confidence에서는 기존 weighted hybrid 유지
- output compliance shadow judge를 추가했습니다.
  - deterministic guardrail은 유지
  - LLM judge는 shadow/eval 용도로만 사용
  - PII masking 후 외부 judge 전달 가능
  - 운영 blocking은 하지 않음
- offline quality report JSONL schema와 writer를 추가했습니다.
  - `OfflineQualityReportRecord`
  - `OfflineQualityReportJob`
- P4 baseline 문서를 추가했습니다.
  - `../phases/ai-rag-phase-p4-baseline.md`

### P4 baseline 측정 현황

v2.2 UTF-8 seed eval set, schema, baseline evaluator, Markdown/JSON report writer는 추가했습니다. 다만 운영 로그 기반 150건 eval set은 아직 구성하지 않았으므로 weighted hybrid retrieval의 운영 정량 baseline은 미측정입니다. RRF/rerank/gate 운영 적용 승인은 아래 baseline 측정 이후에만 판단할 수 있습니다.

| 지표 | 현재 baseline 값 | 상태 | 측정 계획 |
|---|---:|---|---|
| Recall@5 | N/A | runner 구현, real baseline 미측정 | 최근 3개월 상담 로그 기반 eval set 150건 구성 후 산출 |
| MRR | N/A | runner 구현, real baseline 미측정 | 동일 eval set에서 weighted/RRF/rerank 비교 |
| nDCG@5 | N/A | runner 구현, real baseline 미측정 | 동일 eval set에서 산출 |
| Retrieval latency p50 | N/A | runner 구현, real baseline 미측정 | staging query replay로 측정 |
| Retrieval latency p95 | N/A | runner 구현, real baseline 미측정 | staging query replay로 측정 |
| Rerank API cost | N/A | 미측정 | rerank shadow benchmark 후 산출 |
| Retrieval false drop rate | N/A | 미측정 | calibrated threshold 후보별 수동 relevance label 기준 산출 |

### 준비된 구조

- RAG 개선 실험을 운영 baseline과 분리해 비교할 수 있게 됐습니다.
- score gate는 calibration 전에는 문서를 drop하지 않도록 설계했습니다.
- output judge는 비용, 지연, 개인정보 기준이 충족되기 전까지 shadow만 수행하도록 했습니다.

---

## 6. 테스트 및 검증 결과

### 자동화 테스트 요약

| 항목 | 결과 |
|---|---:|
| 테스트 suite 수 | 71 |
| 테스트 case 수 | 329 |
| 실패 | 0 |
| 에러 | 0 |
| 스킵 | 2 |
| 전체 테스트 명령 | `.\gradlew.bat test` |
| 최종 결과 | `BUILD SUCCESSFUL` |

스킵 2건의 세부 사유는 다음과 같습니다.

| Skipped test | 사유 | 후속 조치 |
|---|---|---|
| `LegalChunkRepositoryIT` | `@Testcontainers(disabledWithoutDocker = true)` 조건으로, Docker 사용 가능 환경에서만 PostgreSQL/pgvector integration test 실행 | CI 또는 로컬 Docker 환경에서 별도 실행 |
| `BaselineMetricsRealIT` | `@EnabledIfEnvironmentVariable(named = "BASELINE_REAL", matches = "true")` 조건으로, 실제 외부 인프라 baseline 측정 시에만 실행 | P4 eval set 구성 후 `BASELINE_REAL=true`로 별도 실행 |

### 커버리지

| 범위 | Line coverage | Branch coverage | 해석 |
|---|---:|---:|---|
| 전체 프로젝트 | 56.49% | 49.37% | AI/RAG 외 기존 코드까지 포함한 전체 기준 |
| `org.example.shield.ai.*` | 68.93% | 53.11% | 이번 개편 영향권인 AI/RAG 패키지 기준 |
| v2.2 하드닝 신규 서비스 8개 | 75.88% | 56.25% | metric, shadow eval, drift/backfill, baseline, rollout summary 신규 구현 기준 |

산출물은 `build/reports/jacoco/test/html/index.html`, `build/reports/jacoco/test/jacocoTestReport.xml`입니다. 현재 coverage gate는 blocking 비활성 상태입니다. 다음 단계에서는 전체 프로젝트가 아니라 AI/RAG 신규 패키지 기준 line coverage 70% 이상을 우선 목표로 검토합니다.

### 핵심 edge case 테스트 결과

| 구분 | Edge case | 테스트 위치 | 결과 |
|---|---|---|---|
| P1 prompt | Slot Status Block이 checklist coverage보다 먼저 주입됨 | `CohereServiceHistoryAppendTest` | 통과 |
| P1 prompt | 최근 질문 blacklist가 system prompt 상단에 주입됨 | `CohereServiceHistoryAppendTest` | 통과 |
| P1.6 YAML scope | L1/L2/L3 resolver item, stable slot id, node override | `ChecklistScopeResolverTest` | 통과 |
| P1.6 YAML scope | prompt와 coverage가 동일 resolver item 사용 | `ChecklistScopeResolverTest` | 통과 |
| P1 guardrail | 법적 판단/승패/손해배상 가능성 표현 차단 | `GuardrailFilterTest` | 통과 |
| P1 guardrail | 정상 절차 안내 문장 과차단 방지 | `GuardrailFilterTest` | 통과 |
| P1.5 slot | money/date/text value type validation | `SlotValueValidatorTest` | 통과 |
| P1.5 slot | pending confirmation 확정/거절 heuristic | `PendingConfirmationHeuristicTest` | 통과 |
| P1.5 selector | static required 우선 질문 선택 | `StaticQuestionSelectorTest` | 통과 |
| P1.6 slot reconcile | L1 → L3 narrowing 시 상태 보존 및 새 slot 추가 | `SlotLedgerServiceTest` | 통과 |
| P1.6 slot reconcile | legacy `static_001` id 승격 및 correctedSlots fallback | `SlotLedgerServiceTest` | 통과 |
| P2 mixed utterance | 법적 판단 요청 + high-confidence slot 동시 처리 | `BackendIntentRouterTest` | 통과 |
| P2 confirm | 모호하거나 안전하지 않은 CONFIRM은 Cohere skip하지 않음 | `BackendIntentRouterTest` | 통과 |
| P2 regression | intent fixed response가 RAG/Cohere를 skip하고 template 사용 | `MessageServiceTest` | 통과 |
| P2 regression | 기존 상담 메시지 저장, blank response, turn limit 흐름 유지 | `MessageServiceTest` | 통과 |
| P3 validator | dynamic slot alias mapping과 legal judgment 검증 | `BackendValidatorTest` | 통과 |
| P3 alias | 수동 alias 유지 및 전체 YAML generated alias 확장 | `ChecklistAliasIndexTest` | 통과 |
| P3 plan | plan regeneration 조건 | `DynamicPlanIncrementalUpdateTest` | 통과 |
| P4 RAG | RRF duplicate chunk merge와 rank score 계산 | `RrfFusionServiceTest` | 통과 |
| P4 gate | threshold 미설정 시 통과, 설정 시 drop metric 기록 | `RetrievalScoreGateTest` | 통과 |
| P4 policy | intent confidence 구간별 retrieval fallback | `IntentAwareRetrieverTest` | 통과 |
| P4 output | output judge shadow sampling과 PII masking | `OutputComplianceShadowJudgeTest` | 통과 |
| Rollout | 위험 기능 기본값 비활성, weighted/shadow 유지. YAML property 실제 해석 기반 검증 | `AiRagFeatureFlagDefaultsTest` | 통과 |
| Rollout | rollback 지표의 시간/표본/고위험 오류 조건 코드화 | `AiRagRollbackPolicyTest` | 통과 |

### 회귀 테스트

| 영향 범위 | 검증 내용 | 결과 |
|---|---|---|
| `MessageService` | RAG/Cohere 앞단 router 추가 후 기존 상담 흐름 유지 | 통과 |
| `RagPipelineService` | 상세 결과 반환 구조 변경 후 context-only entrypoint 유지 | 통과 |
| `CohereService` | prompt/history 구성과 truncation 동작 유지 | 통과 |
| `ChatTransactionalBoundary` | USER/AI message 저장 transaction 분리 계약 유지 | 통과 |

---

## 7. 운영 안전장치

운영 위험이 있는 기능은 모두 feature flag 또는 shadow mode 뒤에 있습니다.

| 기능 | 현재 기본값 | 운영 의미 |
|---|---|---|
| Intent router | `shadow-mode=true` | 결과는 관찰하되 운영 분기에는 기본 미적용 |
| ASK_LEGAL_ADVICE skip | `false` | 검증 전 Cohere 생략 없음 |
| GREETING/IRRELEVANT skip | `false` | 검증 전 Cohere 생략 없음 |
| CONFIRM skip | `false` | pending 처리 오탐 방지 |
| Slot auto-update | `false` | slot 오염 방지 |
| Dynamic plan | `false` | 정규화 schema 준비만 완료 |
| RAG fusion mode | `weighted` | 기존 retrieval 유지 |
| Retrieval gate | `false` | calibration 전 drop 없음 |
| Intent-aware retrieval | `false` | P2 안정화 전 retrieval 변경 없음 |
| Output judge | `shadow-enabled=false` | 비용/지연/PII 기준 확인 전 미실행 |

관련 문서:

- `../rollout/ai-rag-v2.2-rollout-checklist.md`

---

## 8. 운영 적용 Go 기준

| Phase/기능 | Go 기준 | 측정 방법 | 라벨링/판단 주체 | 현재 상태 |
|---|---|---|---|---|
| P1 structured output | JSON parse failure < 1%, AI API 4xx/5xx < 5% | staging 1주 로그 | 백엔드 담당자 | metric 수집 기반 구현, staging 미측정 |
| P1 guardrail | false positive rate < 2% | 수동 라벨링 200건 이상 | 개발팀 1차, 법무/정책 담당자 검토 | block metric 구현, false positive 라벨링 미측정 |
| P1.5 slot ledger | 반복 질문률 기존 대비 감소, slot 오염률 < 1%, p95 latency +300ms 이하 | staging 상담 100건 이상 | 백엔드 담당자, 상담 UX 담당자 | 후보 metric 구현, staging 미측정 |
| P2 ASK_LEGAL_ADVICE skip | high-risk leak 0건, skip false positive <= 0.5% | 2주 shadow 로그 + 수동 라벨링 | 개발팀 1차 라벨링, 법무/정책 담당자 최종 판정 | exporter/aggregator 구현, 라벨링 미측정 |
| P2 GREETING skip | intent accuracy >= 98%, skip false positive <= 0.5% | shadow 로그 | 개발팀 | exporter/aggregator 구현, 라벨링 미측정 |
| P2 IRRELEVANT skip | intent accuracy >= 95%, skip false positive <= 0.5% | shadow 로그 | 개발팀, 서비스 기획 담당자 | exporter/aggregator 구현, 라벨링 미측정 |
| P2 CONFIRM skip | confirm precision >= 95%, ambiguous confirm skip 0건 | pending confirmation 샘플 라벨링 | 개발팀, 상담 UX 담당자 | exporter/aggregator 구현, 라벨링 미측정 |
| P2 slot auto-update | slot auto-update precision >= 95% | extracted slot 라벨링 | 개발팀, 도메인 리뷰어 | exporter/aggregator 구현, 라벨링 미측정 |
| P3 dynamic plan | validator false positive <= 5%, plan regeneration rate <= 30% | 일부 도메인 staging | 개발팀, 도메인 리뷰어 | 미측정 |
| P3 plan cache sync | `dynamic_plan_slot` ↔ `slot_state` drift 0건 | drift detector 또는 sync audit | 백엔드 담당자 | drift detector/backfill 구현, 운영 미실행 |
| P4 RRF/rerank | Recall@5 baseline 대비 -2%p 이상 하락 없음 | eval set 150건 이상 | AI/RAG 담당자 | baseline runner 구현, real baseline 미측정 |
| P4 retrieval gate | false drop rate <= 2% | calibrated threshold eval | AI/RAG 담당자, 도메인 리뷰어 | baseline runner 구현, calibration 미측정 |
| P4 output judge | p95 latency +200ms 이하, 비용 +10% 이하, PII masking 통과 | shadow judge 2주 | AI/RAG 담당자, 개인정보/보안 담당자 | 미측정 |

현 시점 운영 적용 판단은 **P1/P1.5 staging 검증 전에는 보류**가 맞습니다. P2 이후 기능은 shadow eval 결과를 별도 보고하고 intent/기능별로 승인받아야 합니다.

`ASK_LEGAL_ADVICE`의 high-risk leak 여부는 개발팀 단독으로 최종 판정하지 않습니다. 개발팀은 1차로 후보를 분류하고, 법무 또는 서비스 정책 책임자가 최종 leak 여부를 확인해야 합니다.

---

## 9. 배포 및 Rollback 실행 절차

본 보고서의 일정 기준점은 **2026-05-22 P1/P1.5 staging 배포 후보일**입니다. 실제 날짜가 바뀌면 섹션 11 액션 아이템의 목표 일정도 배포 티켓에서 함께 재산정합니다.

### 배포 절차

1. 운영 OpenAI 계정에서 `OPENAI_CLASSIFY_MODEL`, `OPENAI_CLASSIFY_REASONING_EFFORT` 유효성을 공식 API 기준으로 확인합니다.
2. staging DB에 Flyway migration을 적용합니다.
   - `V14__add_slot_state_to_consultations.sql`
   - `V15__create_dynamic_plan_tables.sql`
3. staging 배포 후 위험 기능 기본값을 유지합니다.
   - intent router는 shadow mode
   - skip/auto-update/dynamic-plan/retrieval-gate/output-judge는 off
   - RAG fusion은 weighted
4. staging에서 P1/P1.5 지표를 1주 이상 수집합니다.
5. P2는 최소 2주 shadow logging을 수행합니다.
6. Go 기준을 만족하는 기능만 feature flag 단위로 순차 활성화합니다.
7. 기능별 활성화 후 24시간 동안 오류율, latency, guardrail, slot drift, skip false positive를 집중 모니터링합니다.

### 배포 모니터링 담당과 채널

| 항목 | 담당 | 확인 위치/채널 | 확인 주기 |
|---|---|---|---|
| 배포 당일 전체 모니터링 | 백엔드 on-call 또는 배포 담당자 | 애플리케이션 로그, Actuator/Prometheus, 배포 티켓 | 활성화 후 1시간, 6시간, 24시간 |
| AI/RAG 품질 지표 | AI/RAG 담당자 | shadow log, offline quality report JSONL, RAG eval artifact | 활성화 후 24시간 |
| 법적 판단 leak 후보 | 법무/서비스 정책 담당자 | ASK_LEGAL_ADVICE shadow 샘플, guardrail/output judge 후보 리포트 | 일 단위 검토 |
| 알림 채널 | 배포 담당자 지정 | Slack `#shield-ai-rag-rollout` 또는 배포 티켓에 명시된 채널 | 이상 징후 즉시 공유 |

현재 저장소에는 Grafana dashboard나 Slack 알림 자동화가 포함되어 있지 않습니다. 따라서 staging 배포 전 배포 티켓에 실제 담당자, dashboard URL, 알림 채널을 명시해야 합니다.

### 긴급 rollback config key

| 기능 | Rollback config |
|---|---|
| Slot ledger | `AI_SLOT_LEDGER_ENABLED=false` |
| Cohere structured output | `AI_COHERE_STRUCTURED_OUTPUT_ENABLED=false` |
| OpenAI structured output | `AI_OPENAI_STRUCTURED_OUTPUT_ENABLED=false` |
| Intent router 전체 운영 분기 | `AI_INTENT_ROUTER_SHADOW_MODE=true` |
| ASK_LEGAL_ADVICE skip | `AI_INTENT_ROUTER_ENABLE_ASK_LEGAL_ADVICE_SKIP=false` |
| GREETING skip | `AI_INTENT_ROUTER_ENABLE_GREETING_SKIP=false` |
| IRRELEVANT skip | `AI_INTENT_ROUTER_ENABLE_IRRELEVANT_SKIP=false` |
| CONFIRM skip | `AI_INTENT_ROUTER_ENABLE_CONFIRM=false` |
| Slot auto-update | `AI_INTENT_ROUTER_ENABLE_SLOT_AUTO_UPDATE=false` |
| Dynamic plan | `AI_DYNAMIC_PLAN_ENABLED=false` |
| RAG fusion | `AI_RAG_FUSION_MODE=weighted` |
| Retrieval gate | `AI_RAG_RETRIEVAL_GATE_ENABLED=false` |
| Intent-aware retrieval | `AI_RAG_INTENT_AWARE_ENABLED=false` |
| Output judge | `AI_OUTPUT_JUDGE_SHADOW_ENABLED=false` |

### Rollback 판단 기준

아래 기준은 문서에만 있는 기준이 아니라 `AiRagRollbackPolicy`로 코드화되어 있습니다. 운영 자동화 레이어는 이 정책을 호출해 rollback/keep 결정을 일관되게 재사용할 수 있습니다.

| 기능 | Rollback 기준 | 지속/표본 조건 |
|---|---|---|
| Structured output | JSON parse failure > 1% 또는 AI API 4xx/5xx > 5% | 10분 이상 지속 또는 30건 이상 표본에서 재현 |
| Guardrail | false positive rate > 2% | 수동 라벨링 100건 이상 또는 10건 이상 연속 과차단 후보 발생 |
| Slot ledger | slot 오염률 > 1% 또는 p95 latency +300ms 초과 | staging/운영 샘플 100건 이상 또는 p95 지연 30분 이상 지속 |
| Intent skip | skip false positive rate > 0.5% | shadow/운영 샘플 200건 이상 또는 high-risk 오분류 1건 이상 |
| Slot auto-update | precision < 95% | 라벨링 샘플 100건 이상 또는 동일 slot 오염 3건 이상 |
| Dynamic plan | validator false positive > 5% 또는 plan 재생성률 > 30% | 활성화 도메인 샘플 100건 이상 또는 30분 이상 지속 |
| Retrieval gate | false drop rate > 2% 또는 Recall@5 baseline 대비 -2%p | eval set 150건 이상 또는 관련 문서 drop 3건 이상 확인 |
| Output judge | p95 latency +200ms 초과 또는 비용 +10% 초과 | 30분 이상 지속 또는 일 단위 비용 추정치 기준 초과 |

---

## 10. 주요 리스크와 대응

| 리스크 | 현재 대응 |
|---|---|
| OpenAI/Cohere 모델명, 파라미터, 응답 스키마 변경 | staging 배포 전 운영 계정에서 모델명과 샘플 요청을 확인하고, 실패 시 기존 모델/기존 parser 경로를 유지 |
| Flyway migration 충돌 또는 DB 권한 문제 | staging에서 `V14`, `V15` migration dry-run을 먼저 수행하고, 배포 전 DB snapshot/rollback 절차를 배포 티켓에 첨부 |
| metric은 코드에 있으나 dashboard/알림이 없어 관측 누락 | staging 티켓에 Actuator/Prometheus 확인 위치, dashboard URL, Slack 채널, on-call 담당자를 필수 항목으로 지정 |
| shadow export 또는 baseline 실행으로 로그/아티팩트가 과도하게 증가 | shadow export 기본값 off 유지, staging에서는 기간과 retention을 티켓에 명시하고 일 단위 산출물 크기를 확인 |
| PII가 shadow/eval artifact에 포함될 위험 | 기본 record는 `user_text_hash`만 저장하고 원문 저장은 local-only debug flag에서만 허용 |
| backfill execute 오사용으로 plan table이 오염될 위험 | `AI_DYNAMIC_PLAN_BACKFILL_EXECUTE_ENABLED=false` 기본값 유지, dry-run 결과 승인 후에만 execute 허용 |
| 법무/정책 리뷰 병목으로 ASK_LEGAL_ADVICE 승인 지연 | P2 shadow 시작 전 최종 판정자를 배포 티켓에 명시하고, 판정 완료 전 skip 활성화 금지 |
| eval set이 오래되어 실제 운영 질의를 반영하지 못함 | 최초 150건 구성 후 분기별 갱신, 신규 도메인 추가 또는 retrieval 실패 유형 변화 시 수시 갱신 |

---

## 11. 다음 액션 아이템

섹션 8의 Go 기준을 실행 가능한 작업으로 변환한 목록입니다. 목표 일정은 **2026-05-22 P1/P1.5 staging 배포 후보일**을 기준으로 잡았습니다. 실제 담당자 이름과 최종 날짜는 배포 티켓에서 확정해야 합니다.

| 우선순위 | 액션 | 담당 역할 | 목표 일정 | 산출물 |
|---:|---|---|---|---|
| 1 | P1/P1.5 staging 배포 티켓 작성 | 백엔드 담당자 | 2026-05-20 | feature flag 기본값, rollback key, 모니터링 담당 포함 티켓 |
| 2 | 배포 모니터링 dashboard/channel 확정 | 배포 담당자 | 2026-05-20 | dashboard URL, Slack 채널, on-call 담당자 |
| 3 | 스킵된 `LegalChunkRepositoryIT`를 Docker 가능 환경에서 별도 실행 | 백엔드 담당자 | 2026-05-21 | 실행 로그와 통과 여부 |
| 4 | Guardrail false positive 라벨링 샘플 200건 준비 | 개발팀 1차, 법무/정책 담당자 검토 | 2026-05-29 | false positive rate 보고서 |
| 5 | P2 shadow eval 라벨링 가이드 작성 | AI/RAG 담당자, 법무/정책 담당자 | 2026-06-03 | intent/slot/ASK_LEGAL_ADVICE 라벨링 기준서 |
| 6 | ASK_LEGAL_ADVICE high-risk leak 최종 판정자 지정 | 서비스 책임자 | 2026-06-03 | 법무 또는 정책 책임자 명시 |
| 7 | `BASELINE_REAL=true` 기반 `BaselineMetricsRealIT` 실행 계획 수립 | AI/RAG 담당자 | 2026-06-05 | baseline 실행 절차와 비용 확인 |
| 8 | `slot_state` ↔ `dynamic_plan_slot` drift detector 운영 배치 설계 | 백엔드 담당자 | 2026-06-12 | drift metric 또는 sync audit 설계안 |
| 9 | 기존 `slot_state`를 `dynamic_plan_slot`으로 옮기는 migration/backfill 계획 작성 | 백엔드 담당자 | 2026-06-12 | lazy migration 또는 one-time backfill 절차 |
| 10 | RAG eval set 150건 구성 | AI/RAG 담당자, 도메인 리뷰어 | 2026-06-19 | query, expected chunk/case id, domain, failure_type 포함 eval set |

---

## 12. 결론

AI/RAG v2.2 Phase 1~4 개발은 완료됐으며, 전체 자동화 테스트도 통과했습니다.

이번 개발로 SHIELD의 AI 상담 파이프라인은 LLM 중심의 비결정적 흐름에서, 백엔드가 상태와 분기를 통제하는 구조로 전환할 준비를 마쳤습니다. 또한 팀장 리뷰에서 지적된 일부 운영 판단 기준은 보고서 문구에 그치지 않고 `AiRagRollbackPolicy`, feature flag 기본값 검증 테스트, 조건부 integration test 사유 정리로 실제 구현까지 보강했습니다.

다만 현재 확인된 것은 코드 레벨 준비 상태와 자동화 테스트 통과이며, 운영 효과는 아직 측정되지 않았습니다.

| 항목 | 판단 |
|---|---|
| 아키텍처 설계 방향 | 승인 가능 |
| 코드 완성도 | 자동화 테스트 기준 확인됨 |
| 운영 효과 검증 | 미완료 |
| P1/P1.5 운영 적용 | staging 지표 측정 후 재보고 필요 |
| P2 이후 운영 적용 | shadow eval 결과 후 단계별 승인 필요 |
| P3 dynamic plan 운영 write | drift 감지와 migration 절차 추가 전까지 보류 |
| P4 retrieval gate/RRF/output judge | baseline/eval 지표 확보 전까지 보류 |

따라서 다음 단계는 추가 기능 개발이 아니라, staging shadow evaluation과 운영 feature flag 활성화 순서 관리입니다.
