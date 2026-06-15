# 리팩토링 세션 시작 프롬프트 모음 (Refactoring Session Prompts)

> 기반 문서:
> - 단계별 실행 계획: `C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md` (Phase 0~8)
> - Task 백로그: `C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md` (RF-TASK-001 ~ 090, T090은 전수 리뷰 후 T030에서 분리)
> - 민감 영역: `C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md`
>
> 본 문서는 각 Phase의 리팩토링 세션을 시작할 때 그대로 복사해 붙여넣을 프롬프트 9개(Phase 0~8)를 제공한다.

---

## 0. 프롬프트 사용 방법 (반드시 읽을 것)

1. **Phase 순서 준수 (단, 6번 Wave 우선 규칙이 상위)**: 기본 의존성 방향은 Phase 0 → 1 → 2 → … → 8 (type/DTO → API client → async 안정화 → hook → component → route → util → 검증)이다. 다만 실제 실행 단위·순서는 `refactoring-execution-order.md`의 Wave 설계가 우선하며(아래 6번), Wave가 Phase 번호와 다른 구간(예: T045·T047 전진)은 Wave를 따른다. Phase 간 병렬은 `dependency-aware-sequence.md` 3장의 병렬 그룹(그룹 A: PFM / 그룹 B: CMS / 그룹 C: 독립 소형 — execution-order의 트랙 S/A/C/Q와 명칭 체계가 다름에 주의) 범위에서만 허용된다.
2. **이전 Phase 완료 확인**: 새 Phase 프롬프트를 붙여넣기 전에, 직전 Phase의 필수 Task(P3 검토성 Task 제외)가 백로그에서 모두 ✅인지 확인한다. 선행 Task가 ⏸(보류)이면 백로그 1.2절의 우회 경로(예: RF-TASK-010의 alias 유지)가 명시된 경우에만 진행한다.
3. **게시판 앱 수정 지양 원칙**: 프로젝트 규칙상 게시판 앱(`/cmsl*`, `/board`, Supabase 기반)은 별도 요청이 없으면 수정을 지양한다. 게시판 앱 영역을 포함하는 Task(Phase 0 ③④, Phase 2 RF-003 계열, Phase 3 RF-020/021/037, Phase 5 RF-044/013/012, Phase 6 RF-005/006/007/009 일부 등)는 **착수 전 사용자 확인**을 받고, 결함 수정/리팩토링 근거와 변경 범위를 커밋·PR에 명시하며 최소 수정으로 진행한다.
4. **상태 갱신**: 각 Task 완료 시 `refactoring-task-backlog.md`의 상태 마커(🔵/✅/⏸/❌)와 commit hash를 갱신하고, Phase 완료 시 본 문서 말미의 traceability 갱신 지침을 따른다.
5. **검증 명령어**: 모든 Phase 공통으로 `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:coverage` / `npm run test:boundaries`를 사용한다. `npx tsc --noEmit`은 후보 명령(전용 스크립트 없음, strict off라 검출력 약함 — RF-FINDING-038)이다. 수동 검증은 Playwright MCP로 local `http://localhost:3000`, production(시뮬레이션 앱) `https://pfm.cmsl-kookmin.com/simulation2`를 사용한다.
6. **Wave 설계 우선**: 실행 순서는 `refactoring-execution-order.md`의 Wave 설계를 우선한다. Wave 순서가 Phase 번호 순서와 다른 구간(예: helper 분리(T045)가 Phase 4 hook 추출(T043)보다 선행)에서는 Wave 순서를 따른다.

---

## Phase 0 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 0 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 0. 리팩토링 준비 및 안전장치 확인" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 0" 표 (RF-TASK-001~006)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (민감 영역 표 + 3장 체크리스트)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-032, 036, 051, 061)
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-priority-roadmap.md 3장 (P0 선행 트랙)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session4\async-state-and-race-review.md, session4\refactoring-brief.md (RF-032 폴링 race)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\polling-review.md, session5\session5-findings.md (RF-032/036)
  - C:\pfm-FE\.codex\ref_docs\codereview\session2\page-state-and-async-flow.md, session2\refactoring-brief.md (RF-061 URL state)
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\config-constant-env-review.md, session6\refactoring-brief.md (RF-051 env)

작업 범위:
- RF-TASK-001: baseline 검증 기록 (5종 스크립트 + npx tsc --noEmit 결과 문서화, 소스 무변경)
- RF-TASK-002: 민감 영역 목록 확정 + "확인 필요" 수집 (storage path/URL parsing, env 이름/배포 설정)
- RF-TASK-003: job polling in-flight guard 도입 (RF-FINDING-032, Simulation2Page.tsx:1605)
- RF-TASK-004: AdminPage3 URL NaN-safe parser (RF-FINDING-061, AdminPage3.tsx:498)
- RF-TASK-005: EditNoticePage attachment rollback (RF-FINDING-036, EditNoticePage.tsx:107)
- RF-TASK-006: required public env helper 도입 (RF-FINDING-051, lib/supabaseClient.ts:5-6, ContactPage.tsx:26-29)

중요 규칙:
- Phase 0에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- RF-TASK-001(baseline)과 RF-TASK-002(민감 영역/확인 필요 해소)를 핫픽스 4건보다 먼저 완료해라.
- Simulation2Page WebSocket/polling은 민감 영역이다 — RF-TASK-003은 guard 추가만 하고 lifecycle 이동/hook 분리는 금지(RF-TASK-043으로 이연). WS fallback/terminal status/result availability 경로와 함께 테스트해라.
- AdminPage3 URL correction은 민감 영역이다 — RF-TASK-004는 safe parser를 pure function으로 분리만 하고 correction 흐름 재배치는 금지(RF-TASK-069로 이연). 기존 deep link/query 호환성을 보존해라.
- RF-TASK-005는 게시판 앱(Supabase) 영역이다 — 착수 전 사용자 확인을 받고, 실제 storage path/URL parsing 확인 후 최소 수정 + 근거를 commit에 명시해라. storage delete/upload 순서는 변경 금지(운영 데이터 손실 위험).
- RF-TASK-006은 env 이름/Vercel 배포 설정 확인 후 적용해라. 확인 전에는 fallback 동작을 보수적으로 설계해라.
- 핫픽스 4건(RF-TASK-003~006)은 각각 독립 commit으로 분리해 개별 revert가 가능하게 해라.
- 핫픽스 완료 후 baseline 명령어를 재실행해 차이를 기록해라.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 1 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 1 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 1. type / DTO / API contract 정리" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 1" 표 (RF-TASK-007~014)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (admin DTO, status union, apiRequest<T>, parameter mapper 민감 영역)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-039, 040, 041, 042)
- C:\pfm-FE\.codex\ref_docs\refactoring\dependency-aware-sequence.md (4.4 alias 우회 경로)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\type-duplication-review.md, session6\api-dto-contract-map.md, session6\type-safety-review.md, session6\refactoring-brief.md (RF-039/040/041/042)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\api-type-contract-review.md, session5\session5-findings.md (RF-040/042)
  - C:\pfm-FE\.codex\ref_docs\codereview\session1\dependency-flow.md, session1\refactoring-brief.md (RF-039 status union 중복)

작업 범위:
- RF-TASK-007: admin DTO 백엔드 명세 확인 (RF-FINDING-039 선행 확인)
- RF-TASK-008: shared status/DTO 모듈 신설 — alias 연결 (RF-FINDING-039)
- RF-TASK-009: 일반 API 파일 중복 정의 → shared 참조 교체 (simulations → jobs → results → visualizations 순)
- RF-TASK-010: admin.ts DTO 정리 — shared + admin 확장 (RF-FINDING-039, 민감 영역)
- RF-TASK-011: workflow stage를 shared status의 mapper 파생으로 전환 (RF-FINDING-039)
- RF-TASK-012: WorkflowState.parameters 타입 3분리 (RF-FINDING-041)
- RF-TASK-013: buildUpdateSimulationBody DTO builder 분리 (RF-FINDING-042, 민감 영역)
- RF-TASK-014: apiRequest call site 타입 명시 강화 + 핵심 parser/guard (RF-FINDING-040)

중요 규칙:
- Phase 1에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- admin DTO 통합은 민감 영역이다 — 백엔드 admin response가 일반 response와 같은지 확인(RF-TASK-007) 전에 통합 금지. 미확인이면 alias 유지로 RF-TASK-010을 ⏸ 처리해라.
- apiRequest<T> 기본 generic의 unknown 전환은 이번 Phase의 비범위다(전체 call site 영향, 장기 단계 적용) — call site 명시 강화만 수행해라.
- parameter mapper(RF-TASK-013)는 민감 영역이다 — job submit/update/restore 흐름과 함께 테스트해라.
- status union 단일화는 backend enum과의 일치가 전제다(확인 필요) — 확인 전에는 mapper/alias 명확화에 머물러라. 기존 타입은 alias/re-export로 연결해 행동 무변경 상태에서 컴파일만 통과시키고, 한 번에 정의를 삭제하지 마라.
- tsconfig strict 계열이 off라 타입 교체 누락이 컴파일에서 안 잡힐 수 있다 — 파일별로 작게 교체하고 매 단계 build/test + 화면 단위 수동 확인을 병행해라.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 2 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 2 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 2. API client / service layer 정리" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 2" 표 (RF-TASK-015~023)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (apiClient token refresh, lib/api/http.ts, Supabase delete/upload, token storage, auth 이원 체계, legacy 흐름)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-003, 022, 028, 030, 031)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\request-safety-review.md, session5\api-layer-architecture-review.md, session5\error-loading-retry-review.md, session5\refactoring-brief.md (RF-028/030)
  - C:\pfm-FE\.codex\ref_docs\codereview\session1\oop-architecture-review.md, session1\refactoring-brief.md (RF-003/030/031)
  - C:\pfm-FE\.codex\ref_docs\codereview\session4\state-management-map.md, session4\refactoring-brief.md (RF-022 token storage)
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\component-responsibility-review.md (RF-003 CMS 직접 호출)

작업 범위:
- RF-TASK-015: RLS/legacy 유지 여부 "확인 필요" 해소 (RF-FINDING-003/030 선행 확인)
- RF-TASK-016: apiRequest timeout/AbortSignal opt-in 옵션 (RF-FINDING-028, 민감 영역)
- RF-TASK-017: retryable error 정책 명시 (RF-FINDING-028)
- RF-TASK-018: authTokenStorage adapter 단일화 (RF-FINDING-022, 민감 영역)
- RF-TASK-019: CMS service/storage adapter 골격 신설 (RF-FINDING-003 1단계, 게시판 앱)
- RF-TASK-020: notice 도메인 Supabase 직접 호출 이관 (RF-FINDING-003 2단계, 게시판 앱)
- RF-TASK-021: gallery/home/research 도메인 이관 (RF-FINDING-003 3단계, 게시판 앱)
- RF-TASK-022: legacy /api/chat error envelope/schema 표준화 (RF-FINDING-030)
- RF-TASK-023: sendContactEmail adapter 분리 (RF-FINDING-031)

중요 규칙:
- Phase 2에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- lib/apiClient.ts token refresh/error normalization은 민감 영역이다(모든 PFM API 호출의 공통 기반) — timeout/signal은 기본 동작 무변경의 opt-in으로 설계하고, lib/api/errors.ts:255의 normalized error model(좋은 패턴 S5-ERRORNORM-001)을 보존해라. 로그인 → token refresh → 401 retry 시나리오를 변경 전후 동일하게 검증해라.
- lib/api/http.ts(WebSocket/binary/keepalive helper)는 민감 영역이다 — 이번 Phase에서 직접 수정하지 마라.
- Supabase delete/upload/update 흐름은 민감 영역이다 — RF-003 이관 시 storage delete/upload 순서를 바꾸지 마라(운영 데이터 손실 위험). RLS/권한 정책 확인(RF-TASK-015) 전에 service 이관을 시작하지 마라.
- RF-TASK-019~021은 게시판 앱 영역이다 — 착수 전 사용자 확인을 받고 최소 수정 + 근거 명시. "한 번에 전체 이관하지 말고 도메인별 진행"(S1 brief 원문).
- PFM auth(token)와 Supabase auth(session)의 경계를 넘나드는 변경 금지 — token storage 단일화는 PFM token에 한정하고 두 storage 경계를 문서화해라.
- legacy /api/chat은 유지/제거 결정(RF-TASK-015) 전에 기능 제거 금지 — 제거 확정 시 RF-TASK-022는 ❌ 전환 + 별도 격리/제거 계획 기록.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 3 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 3 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 3. async flow / polling / error handling 안정화" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 3" 표 (RF-TASK-024~030)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (Simulation2Page WS/polling, legacy 흐름, 게시판 앱)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-020, 021, 034, 035, 037 + 032/036 후속)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session4\async-state-and-race-review.md, session4\effect-dependency-review.md, session4\refactoring-brief.md (RF-020/021)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\polling-review.md, session5\error-loading-retry-review.md, session5\refactoring-brief.md (RF-034/035/037)
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\component-quality-review.md, session3\refactoring-brief.md (RF-021)

작업 범위:
- RF-TASK-024: legacy PFMSimulationPage 유지 여부 확인 (RF-FINDING-034 선행 확인)
- RF-TASK-025: useHomeContent 도입 — loading 고착 제거 (RF-FINDING-021, 게시판 앱)
- RF-TASK-026: notice/gallery 공통 list query hook + stale guard (RF-FINDING-020, 게시판 앱)
- RF-TASK-027: notice pin/delete mutation 실패 피드백 (RF-FINDING-037, 게시판 앱)
- RF-TASK-028: getJob 실패 연속 카운트 + inline notice (RF-FINDING-035)
- RF-TASK-029: legacy polling guard 이식 또는 제거/격리 (RF-FINDING-034)
- RF-TASK-030: P0 안정화 회귀 테스트 (PFM 분) (RF-FINDING-032/061 후속)
- RF-TASK-090: EditNoticePage attachment 보상 회귀 테스트 (T005 완료 후, 게시판 승인 게이트 하 CMS 트랙)

중요 규칙:
- Phase 3에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- 이 Phase는 의도적으로 구조 이동(hook 추출)을 하지 않는다 — 국소 안정화만 수행해라. hook 추출까지 당기면 Phase 4와 중복 작업이 발생한다.
- Simulation2Page polling/WS는 민감 영역이다 — RF-TASK-028은 Phase 0 ① guard(RF-TASK-003)와 같은 폴링 루프이므로 guard 동작 회귀 테스트와 함께 한 commit에서 검증해라. WS fallback 전환 로직과 충돌하지 않는지 확인해라.
- RF-TASK-025/026/027은 게시판 앱 영역이다 — 착수 전 사용자 확인, 최소 수정 + 근거 명시. RF-TASK-026/027은 같은 파일(NoticeBoardPage)이므로 한 흐름으로 묶되 commit은 분리해라.
- legacy(RF-TASK-029)는 유지 여부 확인(RF-TASK-024) 전에 기능 제거 금지.
- error presenter는 기준 패턴 S3-BOUNDARY-001(components/common/ApiErrorNotice.tsx:19)을 따라라.
- RF-TASK-030으로 P0 guard/보상 동작을 테스트로 고정해 Phase 4 hook 이동(RF-TASK-043)의 안전망을 마련해라.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 4 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 4 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 4. hook / state management 구조 개선" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 4" 표 (RF-TASK-031~044)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (Simulation2Page WS lifecycle, AdminPage3 invalidation, 테스트 부족 핵심 플로우)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-016~019, 023~027, 029, 033)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session4\hook-inventory.md, session4\server-client-state-review.md, session4\query-cache-review.md, session4\effect-dependency-review.md, session4\async-state-and-race-review.md, session4\store-and-context-review.md, session4\refactoring-brief.md (RF-016~019/023~027/029)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\query-mutation-review.md, session5\refactoring-brief.md (RF-029)
  - C:\pfm-FE\.codex\ref_docs\codereview\session2\page-state-and-async-flow.md, session2\refactoring-brief.md (RF-033 WS lifecycle)
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\props-and-state-flow.md, session3\refactoring-brief.md (RF-016~019)

작업 범위:
- RF-TASK-031: ResearchHighlightsSlider empty guard (RF-FINDING-026)
- RF-TASK-032: use-mobile tri-state/mounted guard (RF-FINDING-025)
- RF-TASK-033: use-toast mount-only subscription 정리 (RF-FINDING-024)
- RF-TASK-034: LanguageProvider useMemo/persistence 분리 검토 (RF-FINDING-023, P3 — 검토 기록만으로 완료 가능)
- RF-TASK-035: QueryClient 도메인별 query 정책 문서화 (RF-FINDING-027)
- RF-TASK-036: useSimulationList 추출 + stale guard (RF-FINDING-018)
- RF-TASK-037: useSimulationJobResults 추출 + stale guard (RF-FINDING-017)
- RF-TASK-038: ResultExplorerPanel server state hook 분리 (RF-FINDING-016)
- RF-TASK-039: useChatSessions 추출 + mutation state 분리 (RF-FINDING-019)
- RF-TASK-040: buildAdminQueryKeys helper 도입 (RF-FINDING-029 1단계)
- RF-TASK-041: admin mutation hook 추출 — cache side effect 캡슐화 (RF-FINDING-029 2단계, 민감 영역)
- RF-TASK-042: fieldFilesData enabled query 전환 + refresh fan-out 정리 (RF-FINDING-029 3단계)
- RF-TASK-043: useJobMonitorSession 격리 (RF-FINDING-033 1단계, 민감 영역)
- RF-TASK-044: useVisualizationSession 격리 (RF-FINDING-033 2단계, 민감 영역)

중요 규칙:
- Phase 4에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- 작업 순서: 소형·저위험(RF-TASK-031~035) → list/catalog hook(036→037→038→039) → admin(040→041→042) → WS lifecycle(043→044). RF-TASK-043/044는 가장 마지막에, 가장 작게 수행해라.
- T043 착수 전 T045(helper 분리)와 T047의 parser 정의부가 선행되어야 한다 (Wave S7 진입 조건).
- Simulation2Page WebSocket lifecycle은 민감 영역이다 — "guard 테스트 없이 대규모 이동 금지", "한 번에 전체 분리하지 말고 lifecycle 단위로 분할"(원문). Phase 0 ① guard와 Phase 3 RF-035 처리를 hook 내부로 행동 무변경 보존 이동하고, reconnect timer/beforeunload/stale token guard를 함께 테스트해라.
- AdminPage3 React Query invalidation은 민감 영역이다 — "query key helper를 먼저 안정화 / helper 정리 후 이동"(S4 brief 원문). 기존 refetch interval(adminPolling) 정책과 deep link 호환성을 유지해라.
- RF-TASK-037: Lab sync 비용 때문에 sync:false 정책을 유지해라(S4 brief 원문). refreshKey contract와 기존 테스트 영향을 확인해라.
- RF-TASK-038: field selection callback과 visualization field preference를 유지해라(S4 brief 원문).
- RF-TASK-036: server pagination 전환은 확인 필요 — 이번 Phase에서는 클라이언트 동작 유지가 기본이다.
- RF-TASK-039: parent callbacks(onDeleted/onRenamed) contract를 유지해라. view component 분리는 Phase 5(RF-TASK-055)로 이월.
- QueryClient 전역 기본값은 무단 변경 금지 — 정책 문서화/명시까지만(전역 변경은 양 트랙 합의 후 단일 commit).
- 같은 파일을 다루는 Task는 동시에 1개만 진행해라(Simulation2Page/AdminPage3는 직렬 진행). 카드별/단계별 독립 commit을 유지해라.
- 기준 패턴: visualizationSyncInFlightRef(S4-ABORT-001, Simulation2Page.tsx:2057/2100), TrameExportCenter AbortController(S5-CANCEL-002, :147-149).

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 5 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 5 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 5. component 책임 분리 및 props/state flow 개선" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 5" 표 (RF-TASK-045~061)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (Simulation2Page lifecycle, CMS HTML sanitize, 게시판 앱)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-001, 010~015, 043~045, 047)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\component-responsibility-review.md, session3\props-and-state-flow.md, session3\rendering-performance-review.md, session3\component-quality-review.md, session3\refactoring-brief.md (RF-001/010~015)
  - C:\pfm-FE\.codex\ref_docs\codereview\session1\frontend-structure.md, session1\refactoring-brief.md (RF-001 거대 컨테이너)
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\util-responsibility-review.md, session6\type-safety-review.md, session6\refactoring-brief.md (RF-043/045/047)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\api-type-contract-review.md (RF-044)

작업 범위:
- RF-TASK-045: Simulation2Page pure helper/constant 모듈 분리 (RF-FINDING-047, RF-001 선행 정지작업)
- RF-TASK-046: extractWarnings guard 기반 narrowing (RF-FINDING-043)
- RF-TASK-047: JobMonitorMessageDto union + parser 정의 (RF-FINDING-045)
- RF-TASK-048: index key 제거 — ImageCarousel/ResearchPageTemplate (RF-FINDING-014 일부)
- RF-TASK-049: index key 제거 — Simulation2Page chat/event log (RF-FINDING-014 잔여)
- RF-TASK-050: ResultWorkspace presenter 분리 (RF-FINDING-001 1단계)
- RF-TASK-051: ChatPanel presenter 분리 (RF-FINDING-001 2단계)
- RF-TASK-052: ParameterPanel presenter 분리 (RF-FINDING-001 3단계, 민감 영역)
- RF-TASK-053: Simulation2Page 잔여 컨테이너 정리 (RF-FINDING-001 4단계)
- RF-TASK-054: WorkspaceTabsCard props pass-through 정리 (RF-FINDING-010)
- RF-TASK-055: SessionListCard view 분리 (RF-FINDING-019 잔여)
- RF-TASK-056: MemberDetailModal 접근성 보강 (RF-FINDING-011)
- RF-TASK-057: NewsPage list/pagination presenter 정리 (RF-FINDING-012, 게시판 앱)
- RF-TASK-058: slider motion variants 재생성 정리 (RF-FINDING-015, P3)
- RF-TASK-059: CMS HTML sanitize 정책 확인 + 경계 이동 (RF-FINDING-013)
- RF-TASK-060: CMS 데이터 shape 확인 + view model/form model 정의 (RF-FINDING-044 1단계)
- RF-TASK-061: CMS typed model 단계 적용 (RF-FINDING-044 2단계, 게시판 앱)

중요 규칙:
- Phase 5에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- Simulation2Page는 코드베이스 최대 회귀 위험 지점이다 — "한 번에 전체 분리 금지. 테스트가 있는 경계부터 작게 이동"(S3 brief 원문). presenter 분리 순서는 ResultWorkspace → ChatPanel → ParameterPanel이며, 각 분리는 독립 commit + 전체 워크플로우(chat→parameter→job→monitor→result/viz) 수동 검증을 동반해라.
- RF-TASK-045는 행동 변경 없이 pure function부터 이동 후 테스트를 추가해라(S6 brief 원문). polling/reconnect constant 이동 시 참조 누락에 주의해라.
- presenter 분리가 Phase 4 hook 경계를 침범하지 않도록 props/contract만 이동해라. 내부에 누적된 stale closure/race 방어 코드의 보존을 확인해라.
- ParameterPanel(RF-TASK-052)은 parameter mapper 민감 영역이다 — job submit/update/restore 흐름과 함께 테스트해라.
- RF-TASK-059: sanitize 정책 확인(HTML이 관리자 trusted input인지, 저장 시점 sanitize 여부) 전에 sanitizer를 추가하지 마라 — 기존 콘텐츠 렌더링이 깨질 수 있다. trusted 확정 시 정책 문서화로 종결 가능.
- RF-TASK-060/061: CMS content가 pageKey별 자유 schema 의도일 수 있다(확인 필요) — 데이터 구조 확인 후 타입화하고, 적용은 최소 HomePage + edit form 1종부터.
- RF-TASK-057/059/061은 게시판 앱 영역이다 — 착수 전 사용자 확인, 최소 수정 + 근거 명시.
- Simulation2Page 전량 분해를 완료 조건으로 삼지 마라 — 잔여 항목은 후속 백로그로 기록해라.
- 기준 패턴: VisualizationControlBar(S3-COMP-003 — intent callback만 전달하는 presenter 경계).

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 6 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 6 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 6. route / page / container 계층 정리" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 6" 표 (RF-TASK-062~072)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (AdminPage3 권한/early return, auth 이원 체계, routing 변경 영향 큰 page)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-002, 005~009, 048 + 061 후속)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session2\route-page-map.md, session2\entry-flow.md, session2\page-container-review.md, session2\routing-review.md, session2\refactoring-brief.md (RF-005/006/007/008/009)
  - C:\pfm-FE\.codex\ref_docs\codereview\session1\frontend-structure.md, session1\refactoring-brief.md (RF-002 거대 컨테이너)
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\component-responsibility-review.md (RF-002)
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\validation-formatting-review.md (RF-048 formatter)

작업 범위:
- RF-TASK-062: PFM auth gate 추출 (RF-FINDING-005 1단계)
- RF-TASK-063: LegacyAdminGate 중복 제거 (RF-FINDING-005 2단계, 게시판 앱)
- RF-TASK-064: board edit route 권한 기준 명시 (RF-FINDING-005 3단계, 게시판 앱)
- RF-TASK-065: parseBoardId + invalid id not-found 정책 (RF-FINDING-006, 게시판 앱)
- RF-TASK-066: board session ownership 단일화 (RF-FINDING-009, 게시판 앱)
- RF-TASK-067: admin guard presenter 분리 + error boundary 전략 (RF-FINDING-007, 민감 영역)
- RF-TASK-068: AdminPage3 formatter/file util 분리 (RF-FINDING-048)
- RF-TASK-069: useAdminUrlState correction hook 도입 (RF-FINDING-061 후속)
- RF-TASK-070: AdminPage3 tab 분해 1차 — Overview/AccountRequests (RF-FINDING-002 1단계)
- RF-TASK-071: AdminPage3 tab 분해 2차 — Users/Simulation/Job/Result/Viz (RF-FINDING-002 2단계)
- RF-TASK-072: route group layout 필요성 검토 (RF-FINDING-008, P3 — 검토/기록만)

중요 규칙:
- Phase 6에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- AdminPage3 권한/early return은 민감 영역이다(:1157/:1166) — guard presenter 분리 시 차단 동작 자체는 불변 유지해라. 인증 redirect 루프/권한 있는 사용자 차단이 발견되면 즉시 revert.
- PFM gate(usePfmAuthGate)와 Supabase gate(useSupabaseSessionGate)를 하나의 추상화로 합치지 마라 — 두 앱의 인증 체계는 완전히 분리되어야 한다(프로젝트 규칙).
- board edit route 권한(RF-TASK-064)은 RLS 정책 확인 전 과도한 UI 차단 금지(S2 brief 원문). RF-TASK-015의 RLS 확인 결과를 기반으로 해라. middleware 부재가 의도인지 확인해라.
- legacy admin gate(RF-TASK-063)는 기존 LegacyLoginPage 분기와 subscription cleanup을 보존해라(S2 brief 원문).
- admin URL state(RF-TASK-069)와 tab 분해(RF-TASK-070/071)는 기존 deep link/query 호환성을 보존해라. tab 분해는 Phase 4의 query key helper/mutation hook 완료를 전제로 하며, 한 tab씩 독립 commit으로 분리하고 tab마다 admin 수동 플로우를 검증해라.
- RF-TASK-068(formatter 분리)은 tab 분해 직전에 행동 무변경으로 수행해라(중복 복사 방지).
- global error.tsx 도입은 로깅/복구 UX 정책 확인 전에 강행하지 마라(확인 필요).
- redirect 방식 변경 시 login↔simulation2 양방향 UX를 수동 검증해라.
- RF-TASK-063~066은 게시판 앱 영역이다 — 착수 전 사용자 확인, 최소 수정 + 근거 명시.
- AdminPage3 전량 분해를 완료 조건으로 강제하지 마라 — 잔여 항목은 후속 백로그로 기록해라.
- RF-TASK-072는 제품 UX 결정 대기 항목이다 — 검토/기록만 수행하고 코드는 변경하지 마라.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 7 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 7 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 7. util / config / constant / validation 정리" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 7" 표 (RF-TASK-073~083)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (lib/api/http.ts, env/config, legacy 흐름, Supabase storage)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-046, 049, 050, 052~059)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\util-helper-inventory.md, session6\util-responsibility-review.md, session6\config-constant-env-review.md, session6\validation-formatting-review.md, session6\dead-code-and-import-review.md, session6\refactoring-brief.md (RF-046/049/052~059)
  - C:\pfm-FE\.codex\ref_docs\codereview\session3\component-quality-review.md, session3\refactoring-brief.md (RF-050 중복 helper)

작업 범위:
- RF-TASK-073: next.config 주석 인코딩 정리 (RF-FINDING-054)
- RF-TASK-074: admin API helper 재-export 제거 (RF-FINDING-059)
- RF-TASK-075: withQuery QueryParams 타입 좁히기 (RF-FINDING-057, 민감 영역 — 타입만)
- RF-TASK-076: ColorBends `as any` 정리 (RF-FINDING-046, P3)
- RF-TASK-077: sanitizeForStorage/blob download 공통 util 통합 (RF-FINDING-050)
- RF-TASK-078: colormap/page size/polling constant 정리 (RF-FINDING-055)
- RF-TASK-079: PFM API env canonical 정리 (RF-FINDING-052)
- RF-TASK-080: legacy validateParams/parseLLMResponse 모듈 분리 (RF-FINDING-049)
- RF-TASK-081: images.remotePatterns allowlist 제한 (RF-FINDING-053)
- RF-TASK-082: formatRelativeTime locale 처리 (RF-FINDING-056, P3)
- RF-TASK-083: unused check/lint rule 보완 + JS route 정책 (RF-FINDING-058, P3)

중요 규칙:
- Phase 7에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- 작업 순서: 무위험 정리부터 — RF-TASK-073(주석) → 074(재-export) → 075(타입만) → 076 → 이후 077/078/079/080/081/082/083.
- lib/api/http.ts는 민감 영역이다(WS/binary/keepalive helper) — RF-TASK-075는 타입 시그니처만 좁히고 런타임 동작은 변경하지 마라. test:boundaries 통과를 확인해라.
- RF-TASK-081(remotePatterns)은 실제 CMS/CDN 도메인 목록 확인 전 적용 금지 — allowlist 누락 도메인이 있으면 운영 이미지가 깨진다. 적용 후 갤러리/뉴스 이미지 렌더링을 local + production에서 수동 확인하고, 미표시 발견 시 즉시 revert.
- RF-TASK-077(파일명 sanitizer)은 파일명 정책이 backend/storage 정책과 충돌하지 않는지 확인해라(원문). Supabase storage 흐름은 민감 영역이다 — 기존 업로드 파일과의 호환을 보존해라.
- RF-TASK-079(env)는 Vercel 배포 설정 확인 전 기존 fallback 제거 금지. NEXT_PUBLIC_LAB_SERVER_API_KEY/NEXT_PUBLIC_PFM_AUTH_TOKEN의 runtime 사용 여부도 이때 확인해라.
- RF-TASK-078의 adminPolling interval은 운영 정책이다 — 값 변경 없이 위치만 이동해라.
- RF-TASK-080은 Phase 3 RF-TASK-024의 legacy 유지 결정 결과를 재사용해라 — 제거 결정 시 ❌ 전환 + 사유 기록.
- RF-TASK-082/083은 정책 확인(다국어 정책, RF-030 결정) 후 적용 또는 보류 기록으로 종결 가능하다.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## Phase 8 리팩토링 시작 프롬프트

```text
C:\pfm-FE\.codex\ref_docs\codereview
C:\pfm-FE\.codex\ref_docs\refactoring

위 문서들을 먼저 읽고 Phase 8 리팩토링을 수행해라.

참고 문서:
- C:\pfm-FE\.codex\ref_docs\refactoring\phased-refactoring-plan.md 의 "Phase 8. 테스트, 빌드, 회귀 검증, 최종 정리" 섹션
- C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-task-backlog.md 의 "Phase 8" 표 (RF-TASK-084~089)
- C:\pfm-FE\.codex\ref_docs\refactoring\do-not-touch-and-caution-areas.md (테스트가 부족한 핵심 플로우)
- C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md (RF-FINDING-004, 038, 060 + 5장 "확인 필요" 30건)
- C:\pfm-FE\.codex\ref_docs\refactoring\verification-strategy.md (검증 전략)
- 원본 세션 문서:
  - C:\pfm-FE\.codex\ref_docs\codereview\session1\session1-findings.md, session1\refactoring-brief.md (RF-004 boundary/test)
  - C:\pfm-FE\.codex\ref_docs\codereview\session5\api-layer-architecture-review.md (RF-004 boundary)
  - C:\pfm-FE\.codex\ref_docs\codereview\session6\type-safety-review.md, session6\dead-code-and-import-review.md, session6\refactoring-brief.md (RF-038/060)

작업 범위:
- RF-TASK-084: 전체 회귀 검증 + baseline 비교 리포트 (Phase 0~7 누적 결과)
- RF-TASK-085: circular import 도구 검증 (RF-FINDING-060, madge/dependency-cruiser)
- RF-TASK-086: CMS boundary 정적 검사 추가 (RF-FINDING-004)
- RF-TASK-087: strict 옵션별 오류량 측정 (RF-FINDING-038 1단계, 코드 무변경)
- RF-TASK-088: strict 계열 옵션 단계 반영 (RF-FINDING-038 2단계)
- RF-TASK-089: 잔여 "확인 필요" 정리 + 사이클 종결 (확인 필요 30건 + RF-FINDING 61건 최종 상태 표)

중요 규칙:
- Phase 8에 해당하는 작업만 수행해라.
- 관련 없는 코드는 수정하지 마라.
- 변경 전후로 type check, lint, build 또는 가능한 검증을 수행해라.
- 기능 변경 없이 구조 개선을 우선해라.
- 문서의 이슈 ID와 작업 결과를 연결해서 보고해라.
- Phase 0~7 필수 Task(P3 검토성 Task 제외)가 모두 ✅인 상태에서만 착수해라.
- "단번에 전체 strict 전환 금지"(원문) — RF-TASK-087에서 옵션별 플래그를 npx tsc --noEmit에 임시 적용해 오류량을 먼저 측정(코드/설정 변경 0건)하고, RF-TASK-088에서 오류량이 수용 가능한 옵션부터 단계 반영해라(예: noUnusedLocals/noUnusedParameters → noImplicitAny → strictNullChecks). 오류량 과다 옵션은 후속 백로그로 이월하고, strict 옵션 반영 후 build 실패가 즉시 해소되지 않으면 tsconfig commit을 revert해라.
- RF-TASK-086의 CMS boundary 검사는 Phase 2에서 확정된 service 경계와 정확히 일치시켜라 — "service 경계를 먼저 정한 뒤 검사 추가"(S1 brief 원문). 검사 규칙이 과도하면 정상 코드가 차단된다 — 기존 정상 코드를 차단하면 규칙 commit revert 후 재정의해라.
- RF-TASK-085의 circular import는 도구 보고가 실제 런타임 문제인지 별도 분석이 필요하다 — 발견 항목은 즉시 수정하지 말고 후속 백로그에 기록해라.
- RF-TASK-084는 baseline(RF-TASK-001) 대비 명령어 결과/커버리지를 비교하고, 주요 사용자 플로우 전체(시뮬레이션 앱 전체 워크플로우, admin, 게시판 앱, error/not-found fallback)를 Playwright로 재검증해라.
- 회귀에서 P0급 결함 발견 시 원인 commit을 revert하고 관련 Phase를 재개해라 — 추적을 위해 Phase별 commit 경계를 보존해라.

완료 후 보고 형식:
- 변경 파일
- 해결한 이슈 ID (RF-FINDING-*, RF-TASK-*)
- 수행한 검증
- 남은 문제
```

---

## 부록. Phase 완료 후 traceability 상태 갱신 지침

각 Phase의 세션이 끝나면, 다음 절차로 추적 문서 상태를 갱신한다.

1. **백로그 상태 갱신** (`refactoring-task-backlog.md`):
   - 완료 Task는 ✅ 마커 + commit hash를 완료 조건 셀에 추기한다.
   - 보류 Task는 ⏸ + 보류 사유/차단 항목, 제외 Task는 ❌ + 사유(3장 보류/제외 표로 이동)를 기록한다.
2. **traceability 문서 갱신** (`C:\pfm-FE\.codex\ref_docs\refactoring\session-to-refactoring-traceability.md`):
   - 해당 Phase에서 처리한 **RF-FINDING별 상태**(완료/부분 완료/이월)를 갱신하고, 연결된 RF-TASK ID와 commit hash를 추기한다.
   - 부분 완료 항목(예: RF-FINDING-003의 도메인별 점진 이관, RF-FINDING-061의 Phase 0 parser → Phase 6 hook 후속)은 **주관 Phase / 연관 Phase 중 어디까지 진행되었는지**를 명시한다 (phased-refactoring-plan 9.2의 주관/연관 표와 대조).
   - 원본 세션 ID(S1-*~S6-*)와의 연결이 끊기지 않도록, 상태 갱신 시 RF-FINDING ID 기준으로만 갱신하고 원본 ID 매핑 컬럼은 수정하지 않는다.
3. **검증 결과 기록**: Phase 종료 시점의 검증 명령어 5종(+ tsc 후보) 결과와 Playwright 수동 검증 결과를 baseline(RF-TASK-001) 대비 차이와 함께 traceability 문서 또는 Phase별 리포트에 남긴다.
4. **신규 발견 사항**: 세션 중 발견된 신규 이슈는 RF-FINDING을 새로 채번하지 않고 "후속 백로그" 섹션에 기록한다 (백로그 말미 규칙).
5. **다음 Phase 게이트 확인**: 위 1~4가 완료되고 해당 Phase의 필수 Task가 모두 ✅임을 확인한 뒤에만 다음 Phase 프롬프트를 사용한다.

> 생성: 2026-06-12. 본 문서는 phased-refactoring-plan.md(Phase 0~8), refactoring-task-backlog.md(RF-TASK-001~090), do-not-touch-and-caution-areas.md와 정합하도록 작성되었다.
