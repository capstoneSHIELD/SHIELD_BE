# 코드리뷰 소스 인벤토리 (codereview-source-inventory)

## 1. 문서 목적

이 문서는 pfm-FE 리팩토링 전략 문서 작성 시 참조한 코드리뷰 원본 소스(`C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6`)의 존재 여부, 구성 파일, 분석 범위를 기록하는 인벤토리다.

- 리팩토링 Phase 0~8 계획 문서가 인용하는 모든 이슈 ID(S1-* ~ S6-*), 파일 경로, 라인 번호의 출처는 아래 세션 문서들이다.
- 각 세션의 핵심 문서는 `session{N}-findings.md`(이슈 표)와 `refactoring-brief.md`(우선순위/민감영역)이며, 세부 리뷰 문서에 추가 ID가 존재할 수 있다.
- 원본 문서의 "확인 필요" 표기는 그대로 유지하며, 이 문서에서 새로 단정하지 않는다.
- 인벤토리 확인 일자: 2026-06-12

## 2. 세션별 인벤토리 표

session1 ~ session6 폴더가 모두 존재함을 Glob 및 디렉터리 조회로 확인했다. **누락된 세션은 없다.**

| 세션 | 경로 | 주요 분석 범위 | 핵심 문서 | 상태 |
|---|---|---|---|---|
| Session 1 | `C:\pfm-FE\.codex\ref_docs\codereview\session1` | 전체 구조/아키텍처: App Router 진입점(`app/**/page.tsx`, `app/layout.tsx`, `app/providers.tsx`), 페이지 컨테이너(`components/pages/*`), simulation/admin 컴포넌트, API/service 계층(`lib/apiClient.ts`, `lib/auth.ts`, `lib/api/*`), CMS/Supabase 직접 연동 페이지, legacy AI/외부 연동, 타입/테스트/설정 | `session1-findings.md`, `refactoring-brief.md` | 확인 완료 |
| Session 2 | `C:\pfm-FE\.codex\ref_docs\codereview\session2` | route/page/layout/container 계층: `app/**/page.tsx`, 전역 layout/provider/not-found, route guard 및 page-level 인증, 주요 page container(`Simulation2Page.tsx`, `AdminPage3.tsx`), CMS board/detail/edit container | `session2-findings.md`, `refactoring-brief.md` | 확인 완료 |
| Session 3 | `C:\pfm-FE\.codex\ref_docs\codereview\session3` | component 계층: `components/**` 전체 구조, feature component 호출 흐름, 공통 UI vs feature 전용 구분, form/modal/table/viewer 책임 분리, props/state/event 흐름, 렌더링 성능 후보 | `session3-findings.md`, `refactoring-brief.md` | 확인 완료 |
| Session 4 | `C:\pfm-FE\.codex\ref_docs\codereview\session4` | hook/state management: `Simulation2Page.tsx`, `AdminPage3.tsx`, `components/simulation/*Card.tsx`, `ResultExplorerPanel.tsx`, `hooks/*`, `app/providers.tsx`, `lib/auth.ts` 등의 server/client state 분리, `useEffect` dependency, race condition, React Query cache 전략 | `session4-findings.md`, `refactoring-brief.md` | 확인 완료 |
| Session 5 | `C:\pfm-FE\.codex\ref_docs\codereview\session5` | API/service/async flow: PFM API client(`lib/apiClient.ts`, `lib/api/http.ts`, `lib/api/errors.ts`), domain API helper(`lib/api/*`), 호출 주체 page/component, CMS/외부 연동(Supabase, `api/chat.js`, legacy), Labserver/Trame client, polling/retry/error handling/request safety | `session5-findings.md`, `refactoring-brief.md` | 확인 완료 |
| Session 6 | `C:\pfm-FE\.codex\ref_docs\codereview\session6` | type/util/config/constant 계층: type/interface/union, API request/response DTO, form/props/workflow state type, util/helper/formatter/parser/mapper/validator, constant/config/env, validation schema, dead code 및 import/export | `session6-findings.md`, `refactoring-brief.md` | 확인 완료 |

## 3. 세션별 전체 파일 목록

각 파일의 역할은 해당 세션 README.md의 "문서 구성" 표를 근거로 기재했다.

### Session 1 (8개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | 세션 1 문서의 목적, 범위, 읽는 순서 |
| `frontend-structure.md` | frontend 디렉터리와 주요 모듈 분류 |
| `domain-map.md` | 주요 기능/도메인 단위와 책임, 의존 대상 |
| `dependency-flow.md` | Route/Page부터 API/backend까지의 실제 의존성 흐름 |
| `oop-architecture-review.md` | 책임 분리, 응집도, 결합도, 추상화 수준 평가 |
| `session1-findings.md` | 세션 1 주요 문제 후보와 리팩토링 방향 (S1-* 이슈 표) |
| `refactoring-brief.md` | 이후 리팩토링 세션에서 바로 사용할 작업 지침 |
| `next-session-prompt.md` | 다음 세션(Session 2)에 붙여넣어 사용할 프롬프트 |

### Session 2 (9개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | Session 2 문서 목적, 범위, 읽는 순서 |
| `route-page-map.md` | route/page/layout/container 연결 맵 |
| `entry-flow.md` | 사용자 진입 후 page/container 실행 흐름 |
| `page-container-review.md` | page/container 책임 분리 리뷰 |
| `routing-review.md` | routing, layout, guard, boundary 리뷰 |
| `page-state-and-async-flow.md` | page/container 상태 및 비동기 흐름 정리 |
| `session2-findings.md` | Session 2 주요 문제 후보 종합 (S2-* 이슈 표) |
| `refactoring-brief.md` | 리팩토링 세션용 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션(Session 3)용 프롬프트 |

### Session 3 (10개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | Session 3 문서 목적, 분석 범위, 읽는 순서 |
| `component-inventory.md` | 주요 component 목록과 분류 |
| `component-hierarchy.md` | page/container에서 하위 component로 이어지는 호출 구조 |
| `component-responsibility-review.md` | component 책임 분리 관점의 주요 리뷰 |
| `props-and-state-flow.md` | props, event handler, local state 흐름 |
| `rendering-performance-review.md` | key, re-render, 계산/목록 성능 후보 |
| `component-quality-review.md` | file size, 접근성, props type, loading/error 등 품질 이슈 |
| `session3-findings.md` | Session 3 종합 findings (S3-* 이슈 표) |
| `refactoring-brief.md` | 이후 리팩토링 작업 지침 |
| `next-session-prompt.md` | Session 4 시작용 프롬프트 |

### Session 4 (11개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | Session 4 문서 목적, 분석 범위, 읽는 순서 |
| `hook-inventory.md` | custom hook과 주요 React hook 사용 위치 목록 |
| `state-management-map.md` | local/server/form/URL/persistent/cache state의 소유 위치와 변경 위치 |
| `server-client-state-review.md` | server state와 client state 분리 여부 리뷰 |
| `effect-dependency-review.md` | `useEffect` dependency, cleanup, stale closure 후보 리뷰 |
| `store-and-context-review.md` | Context, persistence, 전역 상태 사용 구조 리뷰 |
| `query-cache-review.md` | React Query query key, polling, mutation, invalidation 전략 리뷰 |
| `async-state-and-race-review.md` | race condition, stale data, unmount 이후 update 가능성 리뷰 |
| `session4-findings.md` | Session 4 주요 문제 후보 종합 (S4-* 이슈 표) |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션(Session 5)에서 그대로 사용할 프롬프트 |

### Session 5 (13개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | Session 5 문서 목적, 분석 범위, 읽는 순서 |
| `api-service-inventory.md` | API client, service 함수, query/mutation, polling 함수 목록 |
| `endpoint-map.md` | frontend에서 호출하는 endpoint, method, payload, response type 맵 |
| `async-flow-map.md` | 사용자 액션/page 진입 후 API 실행과 상태 반영 흐름 |
| `query-mutation-review.md` | React Query query/mutation/cache 전략 리뷰 |
| `polling-review.md` | polling/refetchInterval/manual refresh 구조 리뷰 |
| `error-loading-retry-review.md` | loading/error/success/retry/rollback 처리 리뷰 |
| `request-safety-review.md` | race, cancellation, timeout, stale response 리뷰 |
| `api-type-contract-review.md` | request/response type, DTO, API contract 리뷰 |
| `api-layer-architecture-review.md` | API/service 계층 책임 분리 리뷰 |
| `session5-findings.md` | 주요 문제 후보 종합 (S5-* 이슈 표) |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션(Session 6)용 프롬프트 |

### Session 6 (13개 파일)

| 파일명 | 역할 |
|---|---|
| `README.md` | Session 6 문서 목적, 분석 범위, 읽는 순서 |
| `type-inventory.md` | 주요 type/interface/DTO/schema 후보 목록 |
| `api-dto-contract-map.md` | API 함수와 request/response type 연결 관계 |
| `type-safety-review.md` | `any`, type assertion, non-null assertion, nullable 처리 리뷰 |
| `type-duplication-review.md` | 중복 type/interface/status union 후보 |
| `util-helper-inventory.md` | util/helper/formatter/parser/mapper 목록 |
| `util-responsibility-review.md` | util/helper 책임 분리 리뷰 |
| `config-constant-env-review.md` | config/env/constant/magic value 리뷰 |
| `validation-formatting-review.md` | validation, formatter, parser, mapper 구조 리뷰 |
| `dead-code-and-import-review.md` | dead code와 import/export 확인 결과 |
| `session6-findings.md` | 주요 문제 후보 종합 목록 (S6-* 이슈 표) |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션(Session 7) 시작용 프롬프트 |

**총계: 6개 세션, 64개 .md 파일** (session1: 8, session2: 9, session3: 10, session4: 11, session5: 13, session6: 13)

## 4. codereview 루트 구성

`C:\pfm-FE\.codex\ref_docs\codereview` 루트에는 `session1` ~ `session6` 폴더 6개만 존재하며, **세션 폴더 외의 파일은 없다.** 또한 모든 세션 폴더 내 파일은 전부 `.md` 확장자이며 `.md` 이외의 파일은 발견되지 않았다 (PowerShell 재귀 조회로 확인).

## 5. 비고: 예상 세션 범위 대비 실제 범위 일치 여부

| 세션 | 예상 범위 | 실제 범위 (README 근거) | 일치 여부 |
|---|---|---|---|
| Session 1 | 전체구조/아키텍처 | 전체 구조, 아키텍처 맵, 책임/의존성 분석, 주요 문제 후보 | 일치 |
| Session 2 | route/page/container | routing, page, layout, container 계층 리뷰 | 일치 |
| Session 3 | component | component 계층(책임 분리, props/state 흐름, 렌더링 성능, 품질) 리뷰 | 일치 |
| Session 4 | hook/state | hook, state management, server/client state, `useEffect`, cache, 비동기 상태 흐름 리뷰 | 일치 |
| Session 5 | API/service/async | API 호출 구조, service/API client 경계, React Query, polling, retry, error handling, request safety 리뷰 | 일치 |
| Session 6 | type/util/config | Type / Util / Config / Constant 계층 리뷰 (validation, dead code 포함) | 일치 |
| Session 7 | test/performance/accessibility/quality gate | `session6/next-session-prompt.md`에 Session 7 진행 지시 프롬프트만 존재. session7 폴더 및 findings 문서 없음 | **미수행** |

- Session 7은 `session6/next-session-prompt.md`(unit/integration/e2e test, 커버리지, rendering performance, accessibility, lint/build/typecheck/CI, architecture boundary 검증 대상으로 명시)로만 존재하며 실제 리뷰는 수행되지 않았다. 따라서 test/performance/accessibility 영역의 이슈 ID(S7-*)는 존재하지 않으며, 해당 영역의 리스크는 리팩토링 계획에서 "확인 필요"로 다뤄야 한다. (추론) Phase 8(테스트/빌드/회귀 검증) 수행 시 Session 7 미수행 공백을 보완하는 검증 절차가 필요하다.
- 각 세션 README는 공통적으로 "frontend 소스 코드를 수정하지 않았다", "`확인 필요`는 해당 세션 범위에서 검증하지 못한 항목"임을 명시하고 있다. 이 인벤토리 및 후속 리팩토링 문서에서도 동일 기준을 유지한다.
