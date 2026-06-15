# 세션 → 리팩토링 추적표 (Session-to-Refactoring Traceability)

> 기반 문서:
> - 통합 이슈 목록: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001 ~ 061, 좋은 패턴 14건 포함)
> - 리팩토링 Task 백로그: `C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md` (RF-TASK-001 ~ 090, 말미 61건 전수 매핑 메모 준수. T090은 전수 리뷰 후 T030에서 분리)
> - 단계별 실행 계획: `C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md` (Phase 0~8 주관 배정 준수)
>
> 본 추적표의 원본 ID(S1-* ~ S6-*), 통합 ID(RF-FINDING-*), Task ID(RF-TASK-*), Phase 배정은 전부 위 문서의 표기를 그대로 옮긴 것이다. 새 ID는 만들지 않는다.
> 행 단위 상태 초기값 배정(계획됨/보류/확인 필요/제외)은 백로그 3장(보류/제외 표)과 consolidated-findings 5~6장(확인 필요/좋은 패턴)에 근거한 판단이며, 그 외 추론은 없다.

---

## Recent Traceability Status Updates

| Source ID | Finding | Task | Status |
|---|---|---|---|
| S4-HOOK-002 | RF-FINDING-025 | RF-TASK-032 | complete on 2026-06-12; `useIsMobile` now preserves the pending state and `Sidebar` guards rendering until viewport measurement. Targeted tests/eslint, `npx tsc --noEmit`, and build passed. See `q-execution-log.md`. |
| S4-HOOK-001 / S4-DEPENDENCY-002 / S4-GLOBAL-001 | RF-FINDING-024 | RF-TASK-033 | complete on 2026-06-12; `useToast` listener effect is mount-only and a hook regression test was added. Targeted tests/eslint, `npx tsc --noEmit`, and build passed. See `q-execution-log.md`. |
| S4-CONTEXT-001 | RF-FINDING-023 | RF-TASK-034 | complete by review on 2026-06-12; no code change. useMemo/persistence extraction deferred to a dedicated i18n pass with language-toggle smoke coverage. See `q-execution-log.md`. |
| S6-MAPPER-001 (util-responsibility-review) | RF-FINDING-045 | RF-TASK-047 | complete on 2026-06-12; `JobMonitorMessageDto` parser added, parser tests and `npx tsc --noEmit` passed. See `s7-execution-log.md`. |
| S2-ASYNC-001 | RF-FINDING-033 | RF-TASK-043 | complete on 2026-06-12; `useJobMonitorSession` extracted, targeted S7 tests and `npx tsc --noEmit` passed. Backend-backed Playwright G4 carried forward. |
| S2-ASYNC-002 | RF-FINDING-033 | RF-TASK-044 | complete on 2026-06-12; `useVisualizationSession` extracted, targeted S7 tests and `npx tsc --noEmit` passed. Browser leak/network inspection carried forward. |
| S3-RENDER-001 / S3-PERF-001 | RF-FINDING-014 | RF-TASK-049 | complete on 2026-06-12; `Simulation2Page` chat/event index keys replaced with stable key helpers. See `s8-execution-log.md`. |
| S3-PERF-002 / S3-RENDER-002 | RF-FINDING-014 | RF-TASK-048 | complete on 2026-06-12; `ImageCarousel` and `ResearchPageTemplate` index keys replaced with media URL/CMS id/heading based helpers. Manual carousel/research smoke carried forward. See `q-execution-log.md`. |
| S1-ARCH-001 / S2-CONTAINER-001 / S3-COMP-001 / S4-STATE-001 / S5-SERVICE-001 / S6-IMPORT-001 | RF-FINDING-001 | RF-TASK-050 | complete on 2026-06-12; `ResultWorkspace` presenter extracted. Residual container cleanup completed by RF-TASK-053; backend-backed full workflow manual regression remains carried forward. See `s8-execution-log.md` and `s9-execution-log.md`. |
| S1-ARCH-001 / S2-CONTAINER-001 / S3-COMP-001 / S4-STATE-001 / S5-SERVICE-001 / S6-IMPORT-001 | RF-FINDING-001 | RF-TASK-051 | complete on 2026-06-12; `ChatPanel` presenter extracted. Chat send/restore/backend orchestration remains in `Simulation2Page`; residual cleanup completed by RF-TASK-053. See `s8-execution-log.md` and `s9-execution-log.md`. |
| S1-ARCH-001 / S2-CONTAINER-001 / S3-COMP-001 / S4-STATE-001 / S5-SERVICE-001 / S6-IMPORT-001 | RF-FINDING-001 | RF-TASK-052 | complete on 2026-06-12; `ParameterPanel` presenter extracted. PATCH builder/API/workflow orchestration remains in `Simulation2Page`; residual cleanup completed by RF-TASK-053. See `s9-execution-log.md`. |
| S1-ARCH-001 / S2-CONTAINER-001 / S3-COMP-001 / S4-STATE-001 / S5-SERVICE-001 / S6-IMPORT-001 | RF-FINDING-001 | RF-TASK-053 | complete on 2026-06-12; `GeneratedInputFileCard` extracted and residual `Simulation2Page` responsibilities recorded. Manual G6 regression remains carried forward. See `s9-execution-log.md`. |
| S3-PROPS-001 | RF-FINDING-010 | RF-TASK-054 | complete on 2026-06-12; `WorkspaceTabsCard` flat pass-through props grouped into `simulations` and `jobResults`, reducing the top-level contract while preserving child card behavior. Browser tab smoke remains carried forward. See `s9-execution-log.md`. |
| S4-QUERY-003 / S4-QUERY-004 / S5-QUERY-002 / S5-QUERY-003 | RF-FINDING-029 | RF-TASK-040 | complete on 2026-06-12; `AdminPage3` consumes `buildAdminQueryKeys` for admin query keys and invalidation-sensitive keys. Manual admin smoke carried forward. See `a4-execution-log.md`. |
| S4-MUTATION-001 / S4-INVALIDATE-001 / S5-MUTATION-001 / S5-MUTATION-002 / S5-INVALIDATE-001 | RF-FINDING-029 | RF-TASK-041 | complete on 2026-06-12; job sync/cancel mutations extracted to `adminJobMutations.ts`, including cache writes and invalidation fan-out. Manual mutation smoke carried forward. See `a4-execution-log.md`. |
| S4-CACHE-001 / S4-CACHE-002 / S5-CACHE-001 / S5-REFETCH-002 / S5-DUPREQ-001 / S5-LOADING-002 | RF-FINDING-029 | RF-TASK-042 | complete on 2026-06-12; result field-file loading moved to an enabled query keyed by selected result/field/filters. Manual field-file network smoke carried forward. See `a4-execution-log.md`. |
| S6-FORMATTER-001 / S6-FORMAT-001 | RF-FINDING-048 | RF-TASK-068 | complete on 2026-06-12; admin formatters/parsers/blob-download helper extracted to `adminFormatters.ts` with unit tests. Manual display/download smoke carried forward. See `a4-execution-log.md`. |
| S3-STATE-002 / S3-RESP-002 / S4-SERVER-003 / S4-RACE-004 / S4-CLEANUP-003 | RF-FINDING-019 | RF-TASK-055 | complete on 2026-06-12; `SessionListView`, `SessionRenameForm`, and `SessionDeleteDialog` presenters extracted while `useChatSessions` keeps API state and stale-response guards. See `s8-execution-log.md`. |
| S2-GUARD-001 / S2-PAGE-001 / S2-PAGE-002 / S2-ROUTE-001 | RF-FINDING-005 | RF-TASK-062 | complete on 2026-06-12; PFM-only auth gate extracted and PFM routes rewired. Supabase/CMS/legacy gates not touched; browser redirect loop validation carried forward. |

## 1. 추적표 사용 방법 (상태 갱신 규칙)

### 1.1 행 구성 원칙

- **원본 이슈 ID 1건 = 1행.** 여러 원본 ID가 같은 RF-FINDING으로 병합된 경우에도 ID별로 행을 유지한다 (같은 통합 ID를 가리키는 행이 여러 개 존재할 수 있음).
- consolidated-findings의 **`A(=B)` alias 표기는 한 행에 그대로 기재**한다 (요약표↔세부 문서가 같은 이슈에 다른 번호를 부여한 경우). 이 경우 한 행이 2개 이상의 원본 ID 문자열을 대표한다.
- **세션 간 alias**(예: `S4-QUERY-003(=S5-QUERY-002=S5-REFETCH-001)`)는 세션별 커버리지를 위해 **각 세션 섹션에 모두 등장**한다. 갱신 시 양쪽 행을 반드시 동기화한다.
- 인벤토리 ID(`S5-ENDPOINT-002`~`040`, `S5-FLOW-001`~`017`)는 이슈가 아닌 호출/흐름 지도 항목이라 RF-FINDING에 등재되지 않았으므로(consolidated-findings 1.1) 본 추적표에도 행을 만들지 않는다. 단 `S5-ENDPOINT-001`은 좋은 패턴으로 등재되어 행이 존재한다.
- RF-TASK-090은 전수 리뷰 후 T030에서 분리된 CMS 트랙 task다.

### 1.2 상태 값 정의

| 상태 | 의미 |
|---|---|
| 계획됨 | 해당 RF-TASK가 백로그에 채번되어 실행 대기 중 (초기 기본값) |
| 진행 중 | 연결된 RF-TASK 중 하나라도 백로그에서 🔵 표기됨 |
| 부분 완료 | 연결된 RF-TASK가 2개 이상이고 일부만 ✅ (완료된 Task ID를 상태 셀에 추기) |
| 완료 | 연결된 RF-TASK가 전부 ✅ (해당 Phase와 commit hash를 상태 셀에 추기) |
| 보류 | 연결된 RF-TASK가 ⏸ (선행 차단/승인 게이트 — 예: RF-TASK-005/006 게시판·CMS 승인 전 보류) |
| 확인 필요 | 원본 코드리뷰의 "확인 필요" 해소(정책/legacy 유지/제품 결정)가 코드 변경보다 선행해야 하는 행 |
| 제외 | 처리하지 않는 행 — 좋은 패턴(기준 패턴으로 유지) 또는 관찰 항목. 갱신 대상 아님 |

### 1.3 갱신 규칙 (Phase 완료 시)

1. **Phase N 완료 직후**, 백로그에서 ✅가 된 RF-TASK를 참조하는 본 추적표의 **모든 행**을 찾아 상태를 갱신한다 (`Task ID` 컬럼 기준 역추적).
2. 한 행에 여러 Task가 연결된 경우(예: RF-FINDING-001 → RF-TASK-050~053) 전부 ✅여야 "완료"로 표기하고, 일부만 ✅이면 "부분 완료(✅ Task 나열)"로 표기한다.
3. **같은 RF-FINDING을 가리키는 행들은 항상 함께 갱신**한다 (통합 ID 컬럼으로 묶어 일괄 처리). 세션 간 alias 행도 동일하게 동기화한다.
4. "확인 필요" 행은 확인이 해소되는 시점에 ① 진행 확정 시 "계획됨"으로 전환 후 일반 규칙 적용, ② 제거/문서화 종결 시 "제외(사유)"로 전환한다. 백로그의 ❌ 전환 규칙(사유 필수)과 함께 기록한다.
5. "제외(기준 패턴으로 유지)" 행은 갱신하지 않는다. 해당 코드가 리팩토링 중 이동되더라도 패턴 보존 여부만 해당 Task의 완료 조건에서 확인한다.
6. 백로그 마커와의 대응: (없음)→계획됨/확인 필요, 🔵→진행 중, ✅→완료(또는 부분 완료), ⏸→보류, ❌→제외(사유). 추적표와 백로그의 상태가 어긋나면 **백로그가 우선**이며 추적표를 따라 맞춘다.
7. Phase 컬럼은 phased-refactoring-plan의 **주관 Phase**다. `(연관 N)` 표기는 후속/이월 작업이 Phase N에 있다는 뜻이며, 주관 Phase 완료만으로 행을 "완료" 처리하지 말고 연관 Task까지 확인한다.

---

## 2. 추적표

### Session 1 — 전체 구조 / 아키텍처 (12행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S1 | S1-ARCH-001 | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`ResultWorkspace`/`ChatPanel`/`ParameterPanel`/`GeneratedInputFileCard` 분리 + 잔여 책임 기록, `s8-execution-log.md`, `s9-execution-log.md`) |
| S1 | S1-ARCH-002 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S1 | S1-ARCH-003 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S1 | S1-ARCH-004 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S1 | S1-DEPENDENCY-001 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S1 | S1-EXTERNAL-001 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |
| S1 | S1-EXTERNAL-002 | RF-FINDING-031 | RF-TASK-023 | 2 | 계획됨 |
| S1 | S1-STRUCT-001(연관) | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`ResultWorkspace`/`ChatPanel`/`ParameterPanel`/`GeneratedInputFileCard` 분리 + 잔여 책임 기록, `s8-execution-log.md`, `s9-execution-log.md`) |
| S1 | S1-TEST-001 | RF-FINDING-004 | RF-TASK-086 | 8 | 계획됨 |
| S1 | S1-TYPE-001 | RF-FINDING-038 | RF-TASK-001, 087, 088 | 8 (연관 0) | 부분 적용(RF-TASK-087: baseline 0, 최초 effective strict-family 16 / strict+unused 51 diagnostics, 최신 2026-06-13 effective strict-family 18 / strict+unused 38 diagnostics. RF-TASK-088: `tsconfig.strict-scope.json` + `npm run test:strict-scope` 통과; root 전역 옵션은 미적용) |
| S1 | S1-TYPE-002 | RF-FINDING-040 | RF-TASK-014 | 1 | 완료(RF-TASK-014 완료 — `w3-execution-log.md`) |
| S1 | S1-TYPE-003 | RF-FINDING-041 | RF-TASK-012 | 1 | 완료(RF-TASK-012 완료 — `w2-execution-log.md`) |

### Session 2 — route / page / container (28행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S2 | S2-ASYNC-001 | RF-FINDING-033 | RF-TASK-043 | 4 | 계획됨 |
| S2 | S2-ASYNC-002 | RF-FINDING-033 | RF-TASK-044 | 4 | 계획됨 |
| S2 | S2-BOUNDARY-001 | RF-FINDING-007 | RF-TASK-067 | 6 | 완료(`adminGuardPresenters.tsx`로 route/guard fallback presenter 분리, global `error.tsx`는 정책 확인 전 보류 — `phase6-execution-log.md`) |
| S2 | S2-BOUNDARY-002 | RF-FINDING-007 | RF-TASK-067 | 6 | 완료(`adminGuardPresenters.tsx`로 route/guard fallback presenter 분리, global `error.tsx`는 정책 확인 전 보류 — `phase6-execution-log.md`) |
| S2 | S2-BOUNDARY-003 | — (좋은 패턴: viewer dynamic import SSR 회피) | — | — | 제외 (기준 패턴으로 유지) |
| S2 | S2-CONTAINER-001 | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`Simulation2Page` presenter 분리 4단계 완료, manual G6 이월) |
| S2 | S2-CONTAINER-002 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S2 | S2-CONTAINER-003 | RF-FINDING-009 | RF-TASK-066 | 6 | 계획됨 |
| S2 | S2-CONTAINER-004 | RF-FINDING-009 | RF-TASK-066 | 6 | 계획됨 |
| S2 | S2-CONTAINER-005 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S2 | S2-CONTAINER-008 | RF-FINDING-007 | RF-TASK-067 | 6 | 완료(AdminPage3 권한 조건/early return 동작 보존, fallback UI만 presenter로 분리 — `phase6-execution-log.md`) |
| S2 | S2-DEPENDENCY-001 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S2 | S2-DEPENDENCY-002(=S2-CONTAINER-006) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S2 | S2-DEPENDENCY-003(=S2-CONTAINER-007) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S2 | S2-GUARD-001(=S2-PAGE-001(상세)) | RF-FINDING-005 | RF-TASK-062 | 6 | 계획됨 |
| S2 | S2-GUARD-002 | RF-FINDING-005 | RF-TASK-063 | 6 | 계획됨 |
| S2 | S2-GUARD-003(=S2-PAGE-004(상세)) | RF-FINDING-005 | RF-TASK-063 | 6 | 계획됨 |
| S2 | S2-GUARD-004(=S2-PAGE-006(상세)) | RF-FINDING-005 | RF-TASK-063 | 6 | 계획됨 |
| S2 | S2-LAYOUT-001 | RF-FINDING-008 | RF-TASK-072 | 6 | 완료 by review-only disposition (single root layout confirmed; route-group extraction deferred until product UX decision, no code change) |
| S2 | S2-LAYOUT-002 | RF-FINDING-027 | RF-TASK-035 | 4 | 완료(`docs/architecture/query-policy.md`, `s6-execution-log.md`) |
| S2 | S2-PAGE-001(요약)(=S2-PAGE-003(상세)) | RF-FINDING-005 | RF-TASK-062 | 6 | 계획됨 |
| S2 | S2-PAGE-002(요약)(=S2-ROUTE-002) | RF-FINDING-006 | RF-TASK-065 | 6 | 계획됨 |
| S2 | S2-PAGE-002(상세) | RF-FINDING-005 | RF-TASK-062 | 6 | 계획됨 |
| S2 | S2-PAGE-005(상세) | RF-FINDING-005 | RF-TASK-063 | 6 | 계획됨 |
| S2 | S2-PAGE-007(상세) | RF-FINDING-005 | RF-TASK-064 | 6 | 계획됨 |
| S2 | S2-ROUTE-001 | RF-FINDING-005 | RF-TASK-062 | 6 | 계획됨 |
| S2 | S2-ROUTE-003 | RF-FINDING-006 | RF-TASK-065 | 6 | 계획됨 |
| S2 | S2-STATE-001 | RF-FINDING-061 | RF-TASK-004, 069 | 0 (연관 6) | 완료(코드+parser/hook 단위 테스트 완료, 수동 admin deep-link smoke 이월 — `w0-execution-log.md`, `a5-execution-log.md`) |

### Session 3 — component (30행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S3 | S3-ACCESS-001(=S3-QUAL-004) | RF-FINDING-011 | RF-TASK-056 | 5 | 완료(`MemberDetailModal` Radix-backed dialog shell 전환 + role/aria/Escape test 추가. Browser tab-cycle/focus-trap smoke 이월 — `q-execution-log.md`) |
| S3 | S3-BOUNDARY-001(=S3-QUAL-012) | — (좋은 패턴: ApiErrorNotice error presenter) | — | — | 제외 (기준 패턴으로 유지) |
| S3 | S3-CMS-001(=S3-RESP-007) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S3 | S3-CMS-002(=S3-RESP-006) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S3 | S3-CMS-003(=S3-QUAL-003) | RF-FINDING-044 | RF-TASK-060, 061 | 5 | 계획됨 |
| S3 | S3-COMP-001(=S3-QUAL-001) | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`Simulation2Page` presenter 분리 4단계 완료, manual G6 이월) |
| S3 | S3-COMP-002(=S3-QUAL-002) | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S3 | S3-COMP-003 | — (좋은 패턴: VisualizationControlBar intent callback 경계) | — | — | 제외 (기준 패턴으로 유지) |
| S3 | S3-DUP-001(=S3-QUAL-007) | RF-FINDING-050 | RF-TASK-077 | 7 | 계획됨 |
| S3 | S3-PERF-002(상세) | RF-FINDING-014 | RF-TASK-048 | 5 | 완료(`ImageCarousel` stable media key helper 적용 + unit test, manual carousel smoke 이월 — `q-execution-log.md`) |
| S3 | S3-PERF-003 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S3 | S3-PERF-004(연관) | RF-FINDING-018 | RF-TASK-036 | 4 | 완료(`useSimulationList` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S3 | S3-PROPS-001 | RF-FINDING-010 | RF-TASK-054 | 5 | 완료(`WorkspaceTabsCard` top-level props grouped to `activeTab`/`onTabChange`/`simulations`/`jobResults`, browser tab smoke 이월) |
| S3 | S3-QUAL-008 | RF-FINDING-013 | RF-TASK-059 | 5 | 확인 필요 (sanitize 정책/trusted content 확인 선행) |
| S3 | S3-QUAL-009 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S3 | S3-QUAL-010 | RF-FINDING-012 | RF-TASK-057 | 5 | 계획됨 |
| S3 | S3-QUAL-011 | RF-FINDING-050 | RF-TASK-077 | 7 | 계획됨 |
| S3 | S3-QUALITY-001(=S3-QUAL-005) | RF-FINDING-006 | RF-TASK-065 | 6 | 계획됨 |
| S3 | S3-QUALITY-002(=S3-QUAL-006) | RF-FINDING-021 | RF-TASK-025 | 3 | 계획됨 |
| S3 | S3-RENDER-001(요약)(=S3-PERF-001(상세)) | RF-FINDING-014 | RF-TASK-049, 051 | 5 | 계획됨 |
| S3 | S3-RENDER-002(요약)(=S3-RENDER-001(상세)) | RF-FINDING-014 | RF-TASK-048 | 5 | 완료(`ImageCarousel` stable media key helper 적용 + unit test, manual carousel smoke 이월 — `q-execution-log.md`) |
| S3 | S3-RENDER-002(상세) | RF-FINDING-014 | RF-TASK-048 | 5 | 완료(`ResearchPageTemplate` section key helper 적용 + unit test, manual research page smoke 이월 — `q-execution-log.md`) |
| S3 | S3-RENDER-003(상세) | RF-FINDING-015 | RF-TASK-058 | 5 | 완료(`ResearchHighlightsSlider` motion variants moved to module scope; targeted tests/eslint and `npx tsc --noEmit` passed, animation smoke carried forward) |
| S3 | S3-RENDER-004(상세) | RF-FINDING-012 | RF-TASK-057 | 5 | 계획됨 |
| S3 | S3-RESP-004 | RF-FINDING-018 | RF-TASK-036 | 4 | 완료(`useSimulationList` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S3 | S3-RESP-005 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S3 | S3-RESP-008 | RF-FINDING-031 | RF-TASK-023 | 2 | 계획됨 |
| S3 | S3-STATE-001(=S3-RESP-001) | RF-FINDING-016 | RF-TASK-038 | 4 | 완료(`useResultExplorerData` 분리 + stale field-file 테스트, `s6-execution-log.md`) |
| S3 | S3-STATE-002(=S3-RESP-002) | RF-FINDING-019 | RF-TASK-039, 055 | 4 (연관 5) | 완료(RF-TASK-039 `useChatSessions` 추출 + stale guard 테스트 완료, RF-TASK-055 view presenter 분리 완료 — `s6-execution-log.md`, `s8-execution-log.md`) |
| S3 | S3-STATE-003(=S3-RESP-003) | RF-FINDING-017 | RF-TASK-037 | 4 | 완료(`useSimulationJobResults` 추출 + stale guard 테스트, `s6-execution-log.md`) |

### Session 4 — hook / state (46행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S4 | S4-ABORT-001(=S5-INTERVAL-001) | — (좋은 패턴: visualizationSyncInFlightRef sequence guard) | — | — | 제외 (기준 패턴으로 유지) |
| S4 | S4-ASYNCSTATE-001 | RF-FINDING-021 | RF-TASK-025 | 3 | 계획됨 |
| S4 | S4-ASYNCSTATE-002 | RF-FINDING-020 | RF-TASK-026 | 3 | 계획됨 |
| S4 | S4-ASYNCSTATE-003 | RF-FINDING-020 | RF-TASK-026 | 3 | 계획됨 |
| S4 | S4-CACHE-001(요약)(=S4-CACHE-002(상세)=S5-CACHE-001(상세)) | RF-FINDING-029 | RF-TASK-042 | 4 | 완료(field-file enabled query 전환, manual field-file smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-CLEANUP-001 | RF-FINDING-017 | RF-TASK-037 | 4 | 완료(`useSimulationJobResults` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-CLEANUP-002 | RF-FINDING-018 | RF-TASK-036 | 4 | 완료(`useSimulationList` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-CLEANUP-003 | RF-FINDING-019 | RF-TASK-039 | 4 | 완료(RF-TASK-039 `useChatSessions` request cleanup/stale guard 적용 — `s6-execution-log.md`) |
| S4 | S4-CLEANUP-004 | — (좋은 패턴: useIdleTimer cleanup/dependency 명시) | — | — | 제외 (기준 패턴으로 유지) |
| S4 | S4-CLIENT-001 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S4 | S4-CONTEXT-001 | RF-FINDING-023 | RF-TASK-034 | 4 | 완료 by review-only disposition (`LanguageProvider` useMemo/persistence extraction deferred to dedicated i18n pass; no code change) |
| S4 | S4-CONTEXT-002 | — (좋은 패턴: sidebar context value useMemo) | — | — | 제외 (기준 패턴으로 유지) |
| S4 | S4-DEPENDENCY-001 | RF-FINDING-026 | RF-TASK-031 | 4 | 완료(`ResearchHighlightsSlider` empty autoplay guard + test, visual smoke 이월 — `q-execution-log.md`) |
| S4 | S4-EFFECT-001(요약)(=S4-EFFECT-004(상세)) | RF-FINDING-021 | RF-TASK-025 | 3 | 계획됨 |
| S4 | S4-EFFECT-005(연관) | RF-FINDING-061 | RF-TASK-004, 069 | 0 (연관 6) | 완료(코드+parser/hook 단위 테스트 완료, 수동 admin deep-link smoke 이월 — `w0-execution-log.md`, `a5-execution-log.md`) |
| S4 | S4-GLOBAL-001(연관) | RF-FINDING-024 | RF-TASK-033 | 4 | 완료(`useToast` mount-only subscription + hook regression test, manual toast smoke carried forward) |
| S4 | S4-HOOK-001(=S4-DEPENDENCY-002(상세)) | RF-FINDING-024 | RF-TASK-033 | 4 | 완료(`useToast` mount-only subscription + hook regression test, manual toast smoke carried forward) |
| S4 | S4-HOOK-002 | RF-FINDING-025 | RF-TASK-032 | 4 | 완료(`useIsMobile` tri-state + `Sidebar` mounted guard + hook regression test, manual mobile breakpoint smoke carried forward) |
| S4 | S4-INVALIDATE-001(=S5-INVALIDATE-001) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-MUTATION-001(요약)(=S4-CACHE-001(상세)=S5-MUTATION-001) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-MUTATION-001(상세)(=S5-MUTATION-002) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-PERSIST-001 | RF-FINDING-022 | RF-TASK-018 | 2 | 완료(RF-TASK-018 authTokenStorage adapter + storage boundary 문서 완료, `auth-token-storage-policy.md`) |
| S4 | S4-PERSIST-002 | RF-FINDING-022 | RF-TASK-018 | 2 | 완료(RF-TASK-018 authTokenStorage adapter + storage boundary 문서 완료, `auth-token-storage-policy.md`) |
| S4 | S4-PERSIST-003(연관) | RF-FINDING-022 | RF-TASK-018 | 2 | 완료(RF-TASK-018 authTokenStorage adapter + storage boundary 문서 완료, `auth-token-storage-policy.md`) |
| S4 | S4-QUERY-001 | RF-FINDING-027 | RF-TASK-035 | 4 | 완료(`docs/architecture/query-policy.md`, `s6-execution-log.md`) |
| S4 | S4-QUERY-002(=S5-QUERY-001) | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S4 | S4-QUERY-003(=S5-QUERY-002=S5-REFETCH-001) | RF-FINDING-029 | RF-TASK-040 | 4 | 완료(`buildAdminQueryKeys` 소비, manual admin smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-QUERY-004(=S5-QUERY-003) | RF-FINDING-029 | RF-TASK-040 | 4 | 완료(`buildAdminQueryKeys` 소비, manual admin smoke 이월 — `a4-execution-log.md`) |
| S4 | S4-QUERY-005 | — (관찰: SWR 미사용 확인 — RF-FINDING-027 참고) | — | — | 제외 (관찰 — 이슈 아님) |
| S4 | S4-RACE-001(=S4-EFFECT-001(상세)) | RF-FINDING-032 | RF-TASK-003, 030 | 0 (연관 3) | 부분 완료(RF-TASK-003 코드+단위 테스트 완료, RF-TASK-030 자동 회귀 완료. backend-backed Playwright polling overlap 관찰은 이월 — `w0-execution-log.md`, `s5-execution-log.md`) |
| S4 | S4-RACE-002 | RF-FINDING-017 | RF-TASK-037 | 4 | 완료(`useSimulationJobResults` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-RACE-003 | RF-FINDING-018 | RF-TASK-036 | 4 | 완료(`useSimulationList` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-RACE-004 | RF-FINDING-019 | RF-TASK-039 | 4 | 완료(RF-TASK-039 refreshKey stale response suppression 테스트 완료 — `s6-execution-log.md`) |
| S4 | S4-SERVER-001 | RF-FINDING-017 | RF-TASK-037 | 4 | 완료(`useSimulationJobResults` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-SERVER-002 | RF-FINDING-018 | RF-TASK-036 | 4 | 완료(`useSimulationList` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S4 | S4-SERVER-003 | RF-FINDING-019 | RF-TASK-039 | 4 | 완료(RF-TASK-039 session list/search/delete/rename API state hook 분리 — `s6-execution-log.md`) |
| S4 | S4-SERVER-004 | RF-FINDING-016 | RF-TASK-038 | 4 | 완료(`useResultExplorerData` 분리 + stale field-file 테스트, `s6-execution-log.md`) |
| S4 | S4-SERVER-005 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S4 | S4-SERVER-006 | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S4 | S4-STALE-001(=S4-EFFECT-002) | RF-FINDING-016 | RF-TASK-038 | 4 | 완료(`useResultExplorerData` 분리 + stale field-file 테스트, `s6-execution-log.md`) |
| S4 | S4-STALE-002(=S4-EFFECT-003) | RF-FINDING-016 | RF-TASK-038 | 4 | 완료(`useResultExplorerData` 분리 + stale field-file 테스트, `s6-execution-log.md`) |
| S4 | S4-STATE-001 | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`Simulation2Page` presenter 분리 4단계 완료, state orchestration은 container에 보존) |
| S4 | S4-STATE-002(요약) | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S4 | S4-STATE-002(상세) | RF-FINDING-021 | RF-TASK-025 | 3 | 계획됨 |
| S4 | S4-STORE-001 | — (관찰: 전역 store 미사용 — query/hook/service 경계 정리 우선) | — | — | 제외 (관찰 — store 미도입 유지) |
| S4 | S4-URL-001 | RF-FINDING-061 | RF-TASK-004, 069 | 0 (연관 6) | 완료(코드+parser/hook 단위 테스트 완료, 수동 admin deep-link smoke 이월 — `w0-execution-log.md`, `a5-execution-log.md`) |

### Session 5 — API / service / async (45행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S5 | S5-APIARCH-003 | RF-FINDING-004 | RF-TASK-086 | 8 | 계획됨 |
| S5 | S5-BOUNDARY-001 | RF-FINDING-004 | RF-TASK-086 | 8 | 계획됨 |
| S5 | S5-CACHE-001(요약) | RF-FINDING-029 | RF-TASK-042 | 4 | 완료(field-file enabled query 전환, manual field-file smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-CACHE-001(상세)(=S4-CACHE-001(요약)=S4-CACHE-002(상세)) | RF-FINDING-029 | RF-TASK-042 | 4 | 완료(field-file enabled query 전환, manual field-file smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-CANCEL-001 | RF-FINDING-028 | RF-TASK-016 | 2 | 완료(RF-TASK-016 자동 검증 완료, Playwright auth 플로우 재검증 이월 — `s4-execution-log.md`) |
| S5 | S5-CANCEL-002 | — (좋은 패턴: TrameExportCenter AbortController cleanup) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-CONTRACT-001 | RF-FINDING-042 | RF-TASK-013 | 1 | 완료(RF-TASK-013 자동 검증 완료, Playwright job 플로우 재검증 이월 — `w3-execution-log.md`) |
| S5 | S5-CONTRACT-002 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |
| S5 | S5-CONTRACT-003 | — (좋은 패턴: results field files query building helper 경계) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-DTO-001 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S5 | S5-ENDPOINT-001 | — (좋은 패턴: PFM base URL/path 조합 공통화) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-ERROR-001 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |
| S5 | S5-ERROR-002 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |
| S5 | S5-ERROR-003 | RF-FINDING-035 | RF-TASK-028 | 3 | 코드+자동 회귀 완료(`getJob` 연속 실패 notice/복구 테스트 추가). backend-backed Playwright 네트워크 차단/복구 확인은 이월 — `s5-execution-log.md` |
| S5 | S5-ERROR-004 | RF-FINDING-037 | RF-TASK-027 | 3 | 계획됨 |
| S5 | S5-ERRORNORM-001 | — (좋은 패턴: lib/api/errors.ts normalized error model/redaction) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-FALLBACK-001 | — (좋은 패턴: TrameViewer PNG fallback) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-INTERCEPTOR-001 | RF-FINDING-028 | RF-TASK-016 | 2 | 완료(RF-TASK-016 자동 검증 완료, Playwright auth 플로우 재검증 이월 — `s4-execution-log.md`) |
| S5 | S5-INTERVAL-001(=S4-ABORT-001) | — (좋은 패턴: visualizationSyncInFlightRef — S4 행과 동기 관리) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-INVALIDATE-001(=S4-INVALIDATE-001) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-LOADING-001 | RF-FINDING-021 | RF-TASK-025 | 3 | 계획됨 |
| S5 | S5-LOADING-002 | RF-FINDING-029 | RF-TASK-042 | 4 | 완료(field-file enabled query 전환, manual field-file smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-MUTATION-001(=S4-MUTATION-001(요약)=S4-CACHE-001(상세)) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-MUTATION-002(=S4-MUTATION-001(상세)) | RF-FINDING-029 | RF-TASK-041 | 4 | 완료(job sync/cancel mutation hook 추출, manual mutation smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-PERSIST-001(=S5-AUTH-001) | RF-FINDING-022 | RF-TASK-018 | 2 | 완료(RF-TASK-018 authTokenStorage adapter + storage boundary 문서 완료, `auth-token-storage-policy.md`) |
| S5 | S5-POLLING-001(=S5-RACE-001(상세)) | RF-FINDING-032 | RF-TASK-003, 030 | 0 (연관 3) | 부분 완료(RF-TASK-003 코드+단위 테스트 완료, RF-TASK-030 자동 회귀 완료. backend-backed Playwright polling overlap 관찰은 이월 — `w0-execution-log.md`, `s5-execution-log.md`) |
| S5 | S5-POLLING-002 | RF-FINDING-034 | RF-TASK-024, 029 | 3 | W1 결정 완료(RF-TASK-024: App Router 진입점 없음, legacy inactive 보존/격리 — `w1-decision-log.md`; T029 후속 보류) |
| S5 | S5-QUERY-001(=S4-QUERY-002) | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S5 | S5-QUERY-002(=S5-REFETCH-001, =S4-QUERY-003) | RF-FINDING-029 | RF-TASK-040 | 4 | 완료(`buildAdminQueryKeys` 소비, manual admin smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-QUERY-003(=S4-QUERY-004) | RF-FINDING-029 | RF-TASK-040 | 4 | 완료(`buildAdminQueryKeys` 소비, manual admin smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-QUERY-004 | RF-FINDING-027 | RF-TASK-035 | 4 | 완료(`docs/architecture/query-policy.md`, `s6-execution-log.md`) |
| S5 | S5-REFETCH-002(=S5-DUPREQ-001) | RF-FINDING-029 | RF-TASK-042 | 4 | 완료(field-file enabled query 전환, manual field-file smoke 이월 — `a4-execution-log.md`) |
| S5 | S5-RETRY-001 | RF-FINDING-028 | RF-TASK-017 | 2 | 완료(RF-TASK-017 retry policy API + 정책 문서 완료, `api-retry-policy.md`) |
| S5 | S5-ROLLBACK-001 | RF-FINDING-036 | RF-TASK-005, 090 (보상 회귀 테스트 — T030에서 분리) | 0 (연관 3) | 보류(RF-TASK-005: 게시판 승인/storage path/backup/test data 확인 전 보류 — `w1-decision-log.md`) |
| S5 | S5-SERVICE-001(요약)(=S5-APIARCH-001) | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`Simulation2Page` presenter 분리 4단계 완료, API orchestration은 container/service 경계에 보존) |
| S5 | S5-SERVICE-002(요약)(=S5-APIARCH-002) | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S5 | S5-SERVICE-002(상세) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S5 | S5-SERVICE-003(요약)(=S5-SERVICE-001(상세)) | RF-FINDING-003 | RF-TASK-015, 019~021 | 2 | 보류(RLS/사용자 승인 미확인으로 C track 보류 — `w1-decision-log.md`) |
| S5 | S5-STALE-001 | RF-FINDING-017 | RF-TASK-037 | 4 | 완료(`useSimulationJobResults` 추출 + stale guard 테스트, `s6-execution-log.md`) |
| S5 | S5-STALE-002 | RF-FINDING-016 | RF-TASK-038 | 4 | 완료(`useResultExplorerData` 분리 + stale field-file 테스트, `s6-execution-log.md`) |
| S5 | S5-TIMEOUT-001 | RF-FINDING-028 | RF-TASK-016 | 2 | 완료(RF-TASK-016 자동 검증 완료, Playwright auth 플로우 재검증 이월 — `s4-execution-log.md`) |
| S5 | S5-TIMEOUT-002(=S5-POLLING-003) | — (좋은 패턴: labserverTrameClient timeout+AbortSignal polling loop) | — | — | 제외 (기준 패턴으로 유지) |
| S5 | S5-TYPE-001 | RF-FINDING-040 | RF-TASK-014 | 1 | 완료(RF-TASK-014 완료 — `w3-execution-log.md`) |
| S5 | S5-TYPE-002 | RF-FINDING-044 | RF-TASK-060, 061 | 5 | 계획됨 |
| S5 | S5-VALIDATION-001 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |

> (참고) `S5-ENDPOINT-002`~`S5-ENDPOINT-040`(endpoint-map.md)과 `S5-FLOW-001`~`S5-FLOW-017`(async-flow-map.md)은 호출 지도/흐름 지도 인벤토리 항목으로 RF-FINDING에 등재되지 않았다(consolidated-findings 1.1). 관련 RF-FINDING(016/017/019/021/029/032/036 등)에 "(참고)"로만 연결되어 있으며 본 추적표의 행 대상이 아니다.

### Session 6 — type / util / config (44행)

| 원본 세션 | 원본 이슈 ID | 통합 이슈 ID | 리팩토링 Task ID | Phase | 상태 |
|---|---|---|---|---|---|
| S6 | S6-ANY-001 | RF-FINDING-041 | RF-TASK-012 | 1 | 완료(RF-TASK-012 완료 — `w2-execution-log.md`) |
| S6 | S6-ANY-002 | RF-FINDING-042 | RF-TASK-013 | 1 | 완료(RF-TASK-013 자동 검증 완료, Playwright job 플로우 재검증 이월 — `w3-execution-log.md`) |
| S6 | S6-ANY-003 | RF-FINDING-040 | RF-TASK-014 | 1 | 완료(RF-TASK-014 완료 — `w3-execution-log.md`) |
| S6 | S6-ANY-004 | RF-FINDING-046 | RF-TASK-076 | 7 | 완료(`ColorBends` Three.js color-space `as any` 제거, visual smoke 이월 — `w11-execution-log.md`) |
| S6 | S6-ASSERT-001 | RF-FINDING-040 | RF-TASK-014 | 1 | 완료(RF-TASK-014 완료 — `w3-execution-log.md`) |
| S6 | S6-ASSERT-002 | RF-FINDING-040 | RF-TASK-014 | 1 | 완료(RF-TASK-014 완료 — `w3-execution-log.md`) |
| S6 | S6-ASSERT-003(=S6-PARSER-002) | RF-FINDING-043 | RF-TASK-046 | 5 | 완료(RF-TASK-046 guard 기반 `extractWarnings` narrowing 완료, `s5-execution-log.md`) |
| S6 | S6-BARREL-001 | RF-FINDING-060 | RF-TASK-085 | 8 | 완료(RF-TASK-085 W0 madge 측정 후 W2 madge 0건, W12 local `test:circular` 0건) |
| S6 | S6-CMS-001(=S6-TYPE-002+S6-TYPE-003) | RF-FINDING-044 | RF-TASK-060, 061 | 5 | 계획됨 |
| S6 | S6-CONFIG-001 | RF-FINDING-052 | RF-TASK-079 | 7 | 완료(`NEXT_PUBLIC_PFM_API_URL` canonical comment + preserved `NEXT_PUBLIC_PFM_LLM_URL` fallback regression test; Vercel deployment confirmation carried forward) |
| S6 | S6-CONFIG-002 | RF-FINDING-053 | RF-TASK-081 | 7 | 확인 필요 (이미지 도메인 정책 — 도메인 목록 확인 전 적용 금지) |
| S6 | S6-CONFIG-003 | RF-FINDING-054 | RF-TASK-073 | 7 | 완료(`next.config.ts` corrupted comments cleaned — `w11-execution-log.md`) |
| S6 | S6-CONST-001 | RF-FINDING-047 | RF-TASK-045 | 5 | 완료(RF-TASK-045 Simulation2 pure helper/constant 분리 완료, `s5-execution-log.md`) |
| S6 | S6-CYCLE-001 | RF-FINDING-060 | RF-TASK-085 | 8 | 완료(RF-TASK-085 W0 madge 측정 후 W2 madge 0건, W12 local `test:circular` 0건) |
| S6 | S6-DEAD-001 | RF-FINDING-058 | RF-TASK-083 | 7 | 부분 진행(`api/**/*.js` included in `tsconfig.json`, `api/chat.js` uses `// @ts-check`; global unused/lint debt remains open — `js-route-typecheck-policy.md`) |
| S6 | S6-DEAD-002 | RF-FINDING-058 | RF-TASK-083 | 7 | 부분 진행(JS route type-check policy applied to `/api/chat`; TS route migration or broader lint policy remains follow-up — `js-route-typecheck-policy.md`) |
| S6 | S6-DTO-001 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-DUPTYPE-001 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-DUPTYPE-002 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-DUPTYPE-003 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-DUPTYPE-004 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-DUPTYPE-005 | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-ENUM-001(요약)(=S6-CONST-002(상세)) | RF-FINDING-055 | RF-TASK-078 | 7 | 완료(colormap/page-size/admin polling constants 분리, manual UI smoke 이월 — `w11-execution-log.md`) |
| S6 | S6-ENUM-001(상세 type-duplication-review) | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-ENV-001(=S6-NULLABLE-001) | RF-FINDING-051 | RF-TASK-006 | 0 | 보류(RF-TASK-006: Supabase/Contact 사용처 교체 승인 전 보류 — `w1-decision-log.md`) |
| S6 | S6-ENV-002(=S6-NULLABLE-002) | RF-FINDING-051 | RF-TASK-006 | 0 | 보류(RF-TASK-006: Supabase/Contact 사용처 교체 승인 전 보류 — `w1-decision-log.md`) |
| S6 | S6-EXPORT-001 | RF-FINDING-059 | RF-TASK-074 | 7 | 완료(`getFilenameFromContentDisposition` 직접 owner import로 정리 — `w11-execution-log.md`) |
| S6 | S6-FORMAT-002 | RF-FINDING-056 | RF-TASK-082 | 7 | 완료(`formatRelativeTime` optional locale 추가, default `ko` 유지, language-switch UI smoke 이월 — `w11-execution-log.md`) |
| S6 | S6-FORMATTER-001(=S6-FORMAT-001) | RF-FINDING-048 | RF-TASK-068 | 6 | 완료(admin formatter/file util 분리 + unit test, manual display/download smoke 이월 — `a4-execution-log.md`) |
| S6 | S6-FORMATTER-002 | — (좋은 패턴: errors.ts message catalog/normalization 경계) | — | — | 제외 (기준 패턴으로 유지) |
| S6 | S6-IMPORT-001 | RF-FINDING-001 | RF-TASK-050~053 | 5 | 완료(`Simulation2Page` presenter 분리 4단계 완료, import surface reduced through presenter extraction) |
| S6 | S6-IMPORT-002 | RF-FINDING-002 | RF-TASK-070, 071 | 6 | 구현 완료(RF-TASK-070 완료, RF-TASK-071 tab presenter 분리 완료, manual admin smoke 이월 — `a5-execution-log.md`) |
| S6 | S6-MAGIC-001 | RF-FINDING-055 | RF-TASK-078 | 7 | 완료(colormap/page-size/admin polling constants 분리, manual UI smoke 이월 — `w11-execution-log.md`) |
| S6 | S6-MAGIC-002 | RF-FINDING-055 | RF-TASK-078 | 7 | 완료(colormap/page-size/admin polling constants 분리, manual UI smoke 이월 — `w11-execution-log.md`) |
| S6 | S6-MAPPER-001(상세 util-responsibility-review) | RF-FINDING-045 | RF-TASK-047 | 5 | 계획됨 |
| S6 | S6-MAPPER-001(상세 validation-formatting-review) | RF-FINDING-039 | RF-TASK-007~011 | 1 | 완료(RF-TASK-007~011 완료 — `w2-execution-log.md`, `w3-execution-log.md`) |
| S6 | S6-PARSER-001 | RF-FINDING-047 | RF-TASK-045 | 5 | 완료(RF-TASK-045 Simulation2 pure helper/constant 분리 완료, `s5-execution-log.md`) |
| S6 | S6-PROPS-001 | RF-FINDING-044 | RF-TASK-060, 061 | 5 | 계획됨 |
| S6 | S6-TYPE-001 | RF-FINDING-038 | RF-TASK-001, 087, 088 | 8 (연관 0) | 부분 적용(RF-TASK-087: baseline 0, 최초 effective strict-family 16 / strict+unused 51 diagnostics, 최신 2026-06-13 effective strict-family 18 / strict+unused 38 diagnostics. RF-TASK-088: `tsconfig.strict-scope.json` + `npm run test:strict-scope` 통과; root 전역 옵션은 미적용) |
| S6 | S6-UTIL-001 | RF-FINDING-047 | RF-TASK-045 | 5 | 완료(RF-TASK-045 Simulation2 pure helper/constant 분리 완료, `s5-execution-log.md`) |
| S6 | S6-UTIL-002 | RF-FINDING-057 | RF-TASK-075 | 7 | 완료(`withQuery`/backend WS query params typed as `QueryParams<TParams>` — `w11-execution-log.md`) |
| S6 | S6-VALIDATION-001 | RF-FINDING-030 | RF-TASK-015, 022 | 2 | 완료(RF-TASK-015: legacy chat 보존 결정, RF-TASK-022: `/api/chat` envelope/schema + adapter parser 표준화 완료. live Gemini/manual UI smoke는 이월 — `w1-decision-log.md`, `q-execution-log.md`) |
| S6 | S6-VALIDATION-002 | RF-FINDING-042 | RF-TASK-013 | 1 | 완료(RF-TASK-013 자동 검증 완료, Playwright job 플로우 재검증 이월 — `w3-execution-log.md`) |
| S6 | S6-VALIDATOR-001(=S6-PARSER-003) | RF-FINDING-049 | RF-TASK-080 | 7 | W1 결정 완료(RF-TASK-024 결과 재사용: legacy inactive 보존/격리 — `w1-decision-log.md`; T080 후속 보류) |

---

## 3. 세션별 커버리지 요약

> "원본 이슈 수"는 추적표 행 수 기준이다 (같은 세션 내 `A(=B)` alias는 consolidated-findings 표기에 따라 1행 = 1건으로 계산. 세션 간 alias는 각 세션에 1행씩 계상).

| 세션 | 원본 이슈 수 | 계획됨 | 부분 | 결정/완료 | 보류 | 확인 필요 | 제외 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Session 1 (전체 구조/아키텍처) | 12 | 8 | 2 | 0 | 2 | 0 | 0 |
| Session 2 (route/page/container) | 28 | 21 | 1 | 0 | 4 | 1 | 1 |
| Session 3 (component) | 30 | 24 | 0 | 0 | 3 | 1 | 2 |
| Session 4 (hook/state) | 46 | 35 | 3 | 0 | 2 | 1 | 5 |
| Session 5 (API/service/async) | 45 | 28 | 6 | 1 | 3 | 0 | 7 |
| Session 6 (type/util/config) | 44 | 27 | 9 | 3 | 2 | 2 | 1 |
| **합계** | **205** | **143** | **21** | **4** | **16** | **5** | **16** |

### 보충 통계

- 통합 이슈 커버리지: RF-FINDING-001 ~ 061 **전 61건**이 본 추적표에서 최소 1행 이상으로 추적된다 (백로그 말미 전수 매핑 메모와 동일 기준).
- 제외 16행 = 좋은 패턴 14건(S4-ABORT-001=S5-INTERVAL-001이 S4/S5 양쪽 행으로 2행 계상되어 15행) + 관찰 1건(S4-QUERY-005). S4-STORE-001은 좋은 패턴 표(관찰)의 일부로 14건에 포함된다.
- 확인 필요 5행의 근원 RF-FINDING: 008(제품 UX), 013(sanitize 정책), 023(P3 검토성), 053(이미지 도메인), 056(locale 정책). RF-FINDING-030/034/049는 W1에서 legacy 보존/격리 방침으로 전환했고, RF-FINDING-060은 W0/W2 madge 측정과 W12 local `test:circular` 재측정으로 완료 전환했다.
- P0 선행 트랙 4건(RF-FINDING-032/036/051/061)은 모두 Phase 0 행으로 추적된다. RF-FINDING-032는 RF-TASK-030 자동 회귀까지 완료했으나 backend-backed Playwright polling 관찰이 남아 부분 완료로 유지한다. RF-FINDING-061은 RF-TASK-004/RF-TASK-069 코드+단위 테스트 기준 완료이며 수동 admin deep-link smoke만 이월한다. RF-FINDING-036/051은 W1 승인 게이트로 보류이고, RF-TASK-090은 RF-FINDING-036 승인 후 수행한다 (1.3 규칙 7).

---

> 생성: 2026-06-12. 본 추적표는 consolidated-findings(61건 + 좋은 패턴 14건), refactoring-task-backlog(90 Task — T090은 전수 리뷰 후 T030에서 분리), phased-refactoring-plan(Phase 0~8)과 정합하도록 작성되었다. 갱신은 1.3의 규칙을 따르고, 백로그·추적표 간 상태 불일치 발견 시 백로그를 우선한다.
