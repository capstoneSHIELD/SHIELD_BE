# pfm-FE 리팩토링 전략 문서 세트 (README)

> 작성일: 2026-06-12
> 위치: `C:\pfm-FE\.codex\ref_docs\refactoring\`
> 성격: **계획 문서 세트** — 이 폴더의 문서는 코드를 수정하지 않으며, 실제 리팩토링 세션의 기준 자료로만 사용한다.

---

## 1. 이 문서 세트의 목적

Session 1~6 코드리뷰(`C:\pfm-FE\.codex\ref_docs\codereview\session1~6`)에서 도출된 모든 이슈를 **하나의 통합 이슈 목록(RF-FINDING-001~061)** 으로 병합하고, 이를 우선순위(P0~P3) / Phase(0~8) / Task(RF-TASK-001~090) / 위험(RF-RISK-*) 체계로 구조화하여 **실행 가능한 리팩토링 계획**으로 만드는 것이 목적이다.

핵심 원칙:

- **근거 보존**: 파일 경로·라인·문제 요약·심각도는 코드리뷰 원본 문서의 근거를 그대로 옮긴다.
- **추론 구분**: 계획 수립 과정에서 새로 내린 판단(Phase 배정, 우선순위, 위험도 등)은 "(추론)" 표기를 붙인다.
- **단정 금지**: 원본 문서에 없는 사항은 단정하지 않고 "확인 필요"로 표시하며, 원본의 "확인 필요" 표기는 그대로 유지한다.

---

## 2. 참조한 코드리뷰 세션

원본 코드리뷰: `C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6` (전체 인벤토리는 `codereview-source-inventory.md` 참조)

| 세션 | 경로 | 분석 범위 |
|---|---|---|
| Session 1 | `..\codereview\session1` | 전체 구조 / 아키텍처 (App Router 진입점, 페이지 컨테이너, API/service 계층, CMS/Supabase 연동, legacy 연동) |
| Session 2 | `..\codereview\session2` | route / page / container 계층 (layout/provider, route guard, 페이지 인증, 주요 page container) |
| Session 3 | `..\codereview\session3` | component 계층 (구조·책임 분리, props/state/event 흐름, 렌더링 성능 후보) |
| Session 4 | `..\codereview\session4` | hook / state management (server/client state 분리, useEffect dependency, race condition, React Query cache) |
| Session 5 | `..\codereview\session5` | API / service / async flow (API client, polling/retry/error handling, request safety, API 타입 계약) |
| Session 6 | `..\codereview\session6` | type / util / config / constant (DTO, 타입 중복·안전성, util/validator, env, dead code) |

> **Session 7 (test / performance / accessibility)은 수행되지 않았다.** 관련 항목은 본 계획에서 P3 또는 "로드맵 이후" 범위로만 다룬다.

---

## 3. 문서 구성 — 42개 문서(계획 문서 + 실행 순서/리뷰 보고 + 실행 기록/정책/측정/W12 closure 산출물)

| 문서 | 역할 |
|---|---|
| `README.md` | (본 문서) 문서 세트의 목적, 구성, 읽기 순서, ID 체계 안내 |
| `codereview-source-inventory.md` | 코드리뷰 원본 소스(session1~6)의 존재 여부·구성 파일·분석 범위 인벤토리. 모든 인용 ID의 출처 기록 |
| `consolidated-findings.md` | **기준 문서.** Session 1~6 이슈를 RF-FINDING-001~061로 병합한 통합 이슈 목록. 병합 기록, 좋은 패턴 14건, "확인 필요" 항목 보존 |
| `refactoring-priority-roadmap.md` | 61건 전건에 P0(즉시 수정)~P3(장기 개선) 우선순위 배정. 우선순위 기준 정의와 합계 검증 포함 |
| `phased-refactoring-plan.md` | Phase 0(준비+P0 핫픽스) ~ Phase 8(검증/정리)의 단계별 실행 계획. 각 RF-FINDING의 주관 Phase 확정 |
| `dependency-aware-sequence.md` | 계층 의존성 기반 작업 순서(type/DTO → API → async → hook → component → page) 정의. 병렬 그룹(A: PFM / B: CMS / C: 독립 소형) 포함 |
| `risk-and-impact-map.md` | 리팩토링 작업 자체가 유발할 수 있는 회귀 위험을 RF-RISK-* ID로 평가한 위험 맵. 완화 전략 포함 |
| `verification-strategy.md` | Phase 공통 검증 전략 — baseline 기록, 자동 검증 명령어, Phase별 회귀 체크리스트, Playwright 수동 검증 |
| `refactoring-task-backlog.md` | RF-FINDING을 실행 단위로 분할한 Task 백로그(RF-TASK-001~090, T090은 전수 리뷰 후 T030에서 분리 추가). 상태 마커(🔵/✅/⏸/❌)·선행 작업·완료 조건 관리 |
| `session-to-refactoring-traceability.md` | 원본 ID(S1-*~S6-*) → RF-FINDING → RF-TASK → Phase의 전수 추적표. 진행 상태 동기화 규칙 포함 |
| `refactoring-session-prompts.md` | 각 Phase 리팩토링 세션 시작 시 그대로 붙여넣는 프롬프트 9개(Phase 0~8) |
| `do-not-touch-and-caution-areas.md` | 사전 확인 없이 건드리면 안 되는 민감 영역 / 안전하게 먼저 개선 가능한 영역 통합표 |
| `final-summary.md` | 문서 세트 전체 요약 — 핵심 수치, Phase 개요, 시작 방법을 한눈에 보는 최종 정리 |
| `refactoring-execution-order.md` | Wave 기반 구체 실행 순서 (트랙/게이트/크리티컬 패스). **실행 시 Phase 번호 순서보다 이 문서의 Wave 순서를 우선 적용** |
| `plan-review-report.md` | 계획 문서 13종 전수 리뷰 보고 (2026-06-12, 발견 사항과 조치 내역) |
| `w0-execution-log.md` | W0 실행 기록 — baseline 결과, W0 task 상태, 보류 게이트 기록 |
| `w1-decision-log.md` | W1 의사결정 기록 — 조건형 P0 승인 게이트, admin DTO 계약, RLS/legacy chat, legacy page 유지 범위 |
| `w2-execution-log.md` | W2 실행 기록 — shared 타입 alias, 일반 API 타입 참조 교체, workflow mapper 파생 전환, parameter 타입 3분리, circular import 재측정 |
| `w3-execution-log.md` | W3 실행 기록 — admin DTO alias/확장 경계 정리, parameter PATCH body builder 분리, API 계약 보존 검증 |
| `s4-execution-log.md` | S4 실행 기록 — apiClient timeout/signal opt-in, refresh timeout, token refresh/401 retry 회귀 검증 |
| `api-retry-policy.md` | S4 RF-TASK-017 retryable error 정책 — query/hook에서 opt-in으로 사용할 분류 기준과 public helper |
| `auth-token-storage-policy.md` | S4 RF-TASK-018 PFM token storage adapter 정책 — PFM token과 Supabase session storage 경계 |
| `js-route-typecheck-policy.md` | W11/RF-TASK-083 부분 실행 기록 — legacy JavaScript API route의 `@ts-check`, JSDoc, `tsconfig` 포함 정책 |
| `s5-execution-log.md` | S5 실행 기록 — Simulation2 pure helper/constant 분리, warning guard narrowing, polling failure notice, P0 regression 고정 |
| `s6-execution-log.md` | S6 실행 기록 — QueryClient 도메인별 query 정책 문서화, `useSimulationList`, `useSimulationJobResults`, `useResultExplorerData`, `useChatSessions` 분리 |
| `s7-execution-log.md` | S7 실행 기록 — `JobMonitorMessageDto` parser, `useJobMonitorSession`, `useVisualizationSession` 분리와 WS lifecycle 검증 기록 |
| `s8-execution-log.md` | S8 실행 기록 — chat/job event stable key, ResultWorkspace 분리, ChatPanel 분리, SessionListCard view 분리, PFM auth gate 추출 및 잔여 검증 항목 기록 |
| `s9-execution-log.md` | S9 실행 기록 — ParameterPanel/GeneratedInputFileCard 분리, WorkspaceTabsCard props 정리, Simulation2 잔여 책임/검증 이월 기록 |
| `a4-execution-log.md` | A4 실행 기록 — AdminPage3 query key/mutation/field-file query/formatter util 안정화 및 A5 이월 기록 |
| `a5-execution-log.md` | A5 실행 기록 — AdminPage3 URL-state hook/correction helper, System/Account Requests/Users/Simulation list/detail/jobs/results/visualization presenter 분리 및 잔여 admin tab 검증 이월 기록 |
| `phase6-execution-log.md` | Phase 6 route/page/container 기록 — RF-TASK-067 admin guard fallback presenter, error boundary 전략, CMS/board 승인 게이트 이월 기록 |
| `w11-execution-log.md` | W11 실행 기록 — config/comment 정리, API helper ownership 정리, QueryParams/constant 수렴, 잔여 util/config 이월 조건 기록 |
| `w12-strict-measurement.md` | W12 RF-TASK-087/088 기록 — strict/unused 옵션별 `tsc` 오류량과 scoped strict gate 적용 |
| `w12-convergence-log.md` | W12 convergence log for RF-TASK-085/087/088 measurement results, madge retry status, and carry-forward gates |
| `w12-gate-correction-log.md` | W12 approval-gate correction log — reverted/remapped Supabase-backed public CMS cleanup and verification evidence |
| `w12-lint-carry-forward.md` | W12 lint carry-forward ledger — remaining lint debt bucketed by approval/product gate |
| `w12-disposition-snapshot.md` | W12 RF-FINDING disposition snapshot — RF-FINDING-001~061 current status and remaining gates |
| `w12-needs-confirmation-snapshot.md` | W12 needs-confirmation snapshot — 30 preserved confirmation items and current closure status |
| `w12-open-gates-decision-packet.md` | W12 open-gates packet — external approvals, runtime evidence, and product decisions needed for final closure |
| `w12-completion-audit.md` | W12 active-goal completion audit — requirement-by-requirement evidence and non-closure rationale |
| `w12-commit-boundary-plan.md` | W12 commit-boundary plan — proposed split matrix for plan v2 one-task/one-commit rollback proof |
| `q-execution-log.md` | Q 트랙 실행 기록 — 독립 quick-win task 수행 결과, review-only disposition, 잔여 Q 항목 |

---

## 4. 권장 읽기 순서 (리팩토링 세션 기준)

리팩토링 세션을 시작할 때는 아래 순서로 읽는다.

1. `README.md` (본 문서) — 전체 구조와 ID 체계 파악
2. `final-summary.md` — 핵심 수치와 Phase 개요 파악
3. `refactoring-priority-roadmap.md` — 우선순위(P0~P3) 배정 확인
4. `phased-refactoring-plan.md` — 수행할 Phase의 작업 범위 확인
5. `refactoring-execution-order.md` — Wave 기반 구체 실행 순서 확인 (실행 시 Phase 번호 순서보다 Wave 순서 우선)
6. `refactoring-session-prompts.md` — **해당 Phase의 시작 프롬프트** 사용
7. `do-not-touch-and-caution-areas.md` — 민감 영역 숙지 (작업 전 필수)
8. `verification-strategy.md` — baseline 기록 및 Phase 완료 검증 기준 확인

상세 근거 추적이 필요할 때:

- 개별 이슈의 원문 근거 → `consolidated-findings.md`
- 원본 ID ↔ 통합 ID ↔ Task ↔ Phase 매핑 → `session-to-refactoring-traceability.md`
- Task 단위 선행 관계·완료 조건 → `refactoring-task-backlog.md`
- 작업 순서의 이유(왜 type부터인가) → `dependency-aware-sequence.md`
- 회귀 위험과 완화 전략 → `risk-and-impact-map.md`

---

## 5. 계획 문서와 실행 기록의 구분

- **계획 문서 15종은 기준 자료다.** 계획 문서 작성 과정에서는 frontend 소스 코드를 수정하지 않았고, 계획 근거를 임의로 바꾸지 않는다.
- 실제 코드 수정은 **별도 리팩토링 세션에서 Wave/Phase 단위로** 수행한다. 실행 결과는 `w0-execution-log.md`, `w1-decision-log.md`, `w2-execution-log.md`, `w3-execution-log.md`, `s4-execution-log.md`, `s7-execution-log.md`처럼 별도 실행 기록에 남긴다.
- Phase 진행 중 발견된 새로운 사실은 새 ID를 임의 채번하지 않고, 기존 RF-FINDING에 연결하거나 "확인 필요"로 기록한다 (`verification-strategy.md` 검증 실패 대응 원칙 참조).

---

## 6. 리팩토링 착수 전 확인 사항

리팩토링 세션(Phase 0)을 시작하기 전에 아래를 반드시 확인한다.

1. **baseline 기록**: `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:boundaries` / `npm run test:coverage` 결과를 변경 전에 기록한다 (`verification-strategy.md` 2장). `npx tsc --noEmit`은 후보 명령(전용 스크립트 없음, strict off라 검출력 약함 — RF-FINDING-038).
2. **"확인 필요" 항목 해소**: 착수할 Phase의 Task가 의존하는 "확인 필요" 항목(예: admin DTO 명세, Supabase RLS/권한 정책, legacy 코드(/api/chat, legacy admin/simulation page) 유지 여부 결정)이 해소되었는지 확인한다. 미해소 시 해당 Task는 ⏸(보류) 처리한다.
3. **민감 영역 숙지**: `do-not-touch-and-caution-areas.md`의 주의 영역 표(예: `lib/apiClient.ts` token refresh, `Simulation2Page` WebSocket lifecycle, `AdminPage3` invalidation/권한)를 숙지하고, 해당 영역 변경 시 수동 검증을 병행한다.
4. **앱 경계 준수**: 같은 프론트(cmsl) 안에 **게시판 앱(`/cmsl*`, `/board` — Supabase 기반 백엔드)** 과 **시뮬레이션 앱(`/simulation2` — 자체 개발 백엔드)** 이 공존한다. 두 앱은 인증을 포함해 충돌해선 안 되며, **별도 요청이 없는 한 게시판 앱 수정은 지양한다.** 게시판 앱 영역을 포함하는 Task는 착수 전 사용자 확인을 받고 최소 수정으로 진행한다.

---

## 7. ID 체계 안내

| ID | 채번 위치 | 의미 |
|---|---|---|
| `S{N}-*` (예: `S4-RACE-001`) | 코드리뷰 원본 (`codereview\session{N}`) | 세션별 원본 이슈 ID. 요약표↔세부 문서 간 번호 재사용·alias는 `A(=B)` 형식으로 보존 |
| `RF-FINDING-001` ~ `061` | `consolidated-findings.md` | 같은 근본 원인의 원본 이슈들을 병합한 **통합 이슈 ID** (총 61건). 모든 계획 문서의 기준 |
| `RF-TASK-001` ~ `090` | `refactoring-task-backlog.md` | RF-FINDING을 실행 단위로 분할한 **작업 ID** (총 90건, T090은 전수 리뷰 후 T030에서 분리). 선행 관계·완료 조건·상태 마커로 관리 |
| `RF-RISK-*` | `risk-and-impact-map.md` | 리팩토링 작업 자체의 **회귀 위험 ID**. 원본 심각도(코드 문제)와 별개로 "고치는 과정의 위험"을 평가 |

관계: **S{N}-\* (원본, 다대일) → RF-FINDING-\* (통합, 일대다) → RF-TASK-\* (실행)**, RF-RISK-\*는 RF-FINDING/Phase에 교차 연결된다. 전수 매핑은 `session-to-refactoring-traceability.md`에서 관리한다.

참고: 인벤토리성 ID(`S5-ENDPOINT-002~040`, `S5-FLOW-001~017`)는 이슈가 아니라 호출/흐름 지도 항목이므로 RF-FINDING으로 등재되지 않았고, 좋은 패턴 14건은 이슈가 아니므로 Task로 배정하지 않고 기준 패턴으로 활용한다.

---

## 8. 비고

- 이전 리팩토링 phase 문서(phase-01~08)는 **1차 리팩토링에 사용 후 정리**되었으며, git 히스토리(커밋 `5861ee5`)에 보존되어 있다. 본 문서 세트는 그 이후 Session 1~6 코드리뷰 결과를 기반으로 새로 작성된 2차 계획이다.
