# 검증 전략 (Verification Strategy)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001~061)
> 원본 코드리뷰: `C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6`
> 본 문서는 Phase 0~8 리팩토링 전 과정에서 사용하는 공통 검증 전략을 정의한다.
> Phase별 작업 범위·RF 배정은 별도 Phase 문서가 기준이며, 본 문서의 Phase-RF 매핑은 검증 관점의 참조 매핑이다. (추론)

---

## 1. 검증 원칙

1. **모든 Phase 완료 시 검증 수행.** 각 Phase가 끝날 때마다 2장의 자동 검증 전체와 4장의 해당 Phase 회귀 체크리스트를 수행한다. 검증 실패 상태로 다음 Phase에 진입하지 않는다.
2. **변경 전 baseline 기록.** 각 Phase 시작 전(최초는 Phase 0에서) 아래를 기록한다:
   - `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:boundaries` 결과 (성공 여부, 경고/실패 목록)
   - `npm run test:coverage` 커버리지 수치 (기존 테스트 인벤토리가 미상이므로 Phase 0에서 최초 측정 — 5장 참조)
   - 핵심 화면 스크린샷/스냅샷 (Playwright MCP `browser_take_screenshot`/`browser_snapshot`): `/simulation2`, `/cmsl20043`, `/`, `/board/news`
   - (후보) `npx tsc --noEmit` 결과 — 2장 비고 참조
3. **"행동 변경 없음"이 기본 합격 기준.** Phase 1~8의 구조 리팩토링은 변경 전후 동일 동작이 원칙이다. 단 **Phase 0의 P0 핫픽스 4건**(RF-FINDING-032, 061, 036, 051)은 의도된 행동 변경(버그 수정)이므로, "변경 전 결함 재현 → 변경 후 해소"를 별도 시나리오로 검증한다.
4. **민감 영역 변경 시 수동 검증 필수.** Session 1 refactoring-brief에 명시된 민감 영역을 건드린 Phase는 자동 검증 통과만으로 완료 처리하지 않는다:
   - `lib/apiClient.ts` token refresh/error normalization (`lib/apiClient.ts:265, 278, 304`)
   - `lib/api/http.ts` WebSocket/binary/keepalive helper (`lib/api/http.ts:56, 90, 103`)
   - `Simulation2Page` WebSocket refs/lifecycle (`components/pages/Simulation2Page.tsx:611, 1674, 1949`)
   - `AdminPage3` 권한/early return (`components/pages/AdminPage3.tsx:1157, 1166`)
   - Supabase delete/upload/update 흐름 (`components/pages/EditMemberPage.tsx:71, 74`, `components/pages/AdminPage2.tsx:66`)
5. **웹 변경사항은 Playwright MCP로 브라우저 검증 후 완료 처리.** 프로젝트 CLAUDE.md의 필수 워크플로우다. UI/ API 연동/라우팅 변경 시 `browser_navigate` → `browser_snapshot` → 기능 조작 → `browser_console_messages`(에러 없음 확인) 순서를 따른다.
6. **게시판(CMS) 앱 주의.** 프로젝트 지침상 게시판 앱 수정은 지양 대상이며, Supabase는 운영 데이터다. CMS 영역(RF-FINDING-003, 036 등) 검증 시 production에서 mutation(작성/수정/삭제/업로드) 테스트를 수행하지 않고, 로컬 환경 + 테스트 데이터로 수행한다. (추론: 운영 데이터 보호 원칙. Supabase RLS/권한 정책은 원본 리뷰에서도 확인 필요로 남아 있음)
7. **타입 검증의 한계 인지.** `tsconfig.json`이 `strict: false`, `noImplicitAny: false`, `strictNullChecks: false`, `allowJs: true`(tsconfig.json:8, 10, 29, 32 — RF-FINDING-038)이므로 컴파일 통과가 타입 안전을 보장하지 않는다. 타입 관련 Phase(특히 Phase 1)는 컴파일 성공에 더해 수동 플로우 검증으로 보완한다.
8. **검증 실패 시 대응 원칙.** 실패 원인이 리팩토링 변경분인지 기존 결함인지 먼저 구분한다. 변경분이 원인이면 수정 또는 해당 커밋 revert 후 재시도, 기존 결함이면 baseline에 기록하고 해당 RF-FINDING과 연결한다(새 ID 채번 금지, 기존 RF-FINDING 범위 밖이면 "확인 필요"로 기록).
9. **Wave 실행 시 게이트 연결.** 실행 순서를 `refactoring-execution-order.md`의 Wave 설계로 진행하는 경우(기본), 검증 시점은 그 문서 4장의 게이트(G0~G8)를 따르고, 각 게이트에서는 그 Wave에서 수행한 task의 **주관 Phase**에 해당하는 본 문서 4장 체크리스트 행을 적용한다. 한 Phase의 마지막 task가 완료되는 Wave 종료 시점에 해당 Phase 행 전체를 수행한다. (추론)

---

## 2. 자동 검증

`package.json`에서 확인된 실제 스크립트만 사용한다. (`npm run test`는 vitest watch 모드이므로 검증용으로는 `test:run`을 사용)

| 검증 항목 | 목적 | 실행 방법 | 성공 기준 | 실패 시 대응 |
|---|---|---|---|---|
| ESLint | 코드 규칙/명백한 오류 검출 | `npm run lint` (next lint) | 에러 0건. 경고는 baseline 대비 증가 없음 | 신규 에러는 즉시 수정. baseline에 있던 경고는 기록 후 해당 영역 Phase에서 처리 |
| Production 빌드 | 컴파일/번들/route 생성 회귀 검출 | `npm run build` (next build) | 빌드 성공. 신규 빌드 경고 없음 | 실패 원인 커밋 식별 후 수정. 타입 에러면 RF-FINDING-038 한계(아래 tsc 비고)와 함께 원인 분석 |
| 단위/컴포넌트 테스트 | 기존+신규 테스트 회귀 검출 | `npm run test:run` (vitest run) | 전체 통과. baseline에서 통과하던 테스트의 신규 실패 0건 | 실패 테스트가 변경분 영향인지 확인. 의도된 동작 변경(P0 핫픽스)이면 테스트를 새 기대값으로 갱신하고 사유 기록 |
| 테스트 커버리지 | 리팩토링 대상 영역의 안전망 수준 추적 | `npm run test:coverage` (vitest run --coverage) | 커버리지가 baseline 대비 감소하지 않음. 신규 분리 모듈(hook/mapper/util)은 테스트 동반 | 커버리지 하락 영역에 테스트 보강. 측정 자체가 실패하면 vitest 설정 확인 |
| PFM API boundary 정적 검사 | PFM page의 API 경계 위반 검출 — `Simulation2Page.tsx`가 `apiRequest`/`authFetch`를 직접 import·호출하거나, `/api/v1/(results\|visualizations\|jobs\|simulations)` endpoint를 하드코딩하거나, inline `new WebSocket('...')` 생성, unload cleanup의 raw fetch 사용 등을 금지 패턴으로 검사 (`scripts/check-pfm-api-boundaries.mjs`) | `npm run test:boundaries` | 위반 0건 (exit 0) | 위반 즉시 수정. RF-FINDING-001(컨테이너 분해) 작업 중 경계 위반이 재발하기 쉬우므로 Phase 2~6에서 매 커밋 단위 실행 권장 (추론). 경계 자체를 바꿔야 하면 스크립트 갱신을 같은 커밋에 포함하고 사유 기록 |
| Circular import 정적 검사 | `app`, `components`, `hooks`, `lib`, `api`, `scripts`의 내부 source import graph cycle 검출 (`scripts/check-circular-imports.mjs`) | `npm run test:circular` | circular dependency 0건 (exit 0) | RF-FINDING-060 회귀 방지. 순환 발견 시 즉시 대규모 리팩토링에 섞지 않고 후속 백로그로 기록한 뒤 영향 분석 후 수정한다. |
| Local route smoke | 핵심 route status/body, uncaught page error, expected sandbox resource classification, unexpected console/resource failure, Admin invalid pagination URL을 Playwright headless로 점검 (`scripts/check-local-route-smoke.mjs`) | `npm run test:route-smoke` (`.next` build 필요, 또는 `ROUTE_SMOKE_BASE_URL`로 기존 서버 지정; `ROUTE_SMOKE_FAIL_ON_CONSOLE_ERROR=1`이면 expected sandbox resource-load console message도 실패 처리, `ROUTE_SMOKE_FAIL_ON_RESOURCE_ERROR=1`이면 expected sandbox resource issue도 실패 처리) | 대상 route가 5xx 없이 body를 렌더링하고 `/simulation2`는 비인증 상태에서 `/pfm_chat/login`으로 이동하며 `/cmsl20043?page=abc&size=999` body에 `NaN`이 없고 uncaught page error와 unexpected console/resource issue가 없음 | RF-TASK-084의 재실행 가능한 smoke 안전망. sandbox network restriction, dummy Supabase URL, local Vercel analytics 404처럼 예상 가능한 리소스 실패는 expected로 분류하고, 그 외 console/resource issue는 기본 실패 처리한다. expected-resource classifier self-test가 대표 허용/거부 샘플을 먼저 검증해 allowlist 과확장을 막는다. backend-authenticated job/admin workflow, WS fallback, visual/screenshot 회귀를 대체하지 않고 해당 항목은 별도 수동/환경 게이트로 유지한다. |
| 타입체크 (후보) | 컴파일 타임 타입 오류 검출 | `npx tsc --noEmit` — **전용 npm 스크립트는 없음 (확인 필요: 스크립트 추가 여부는 Phase 0에서 결정)** | 에러 0건 또는 baseline 대비 증가 없음 | 신규 에러 수정. 단 **tsconfig strict 계열 off(RF-FINDING-038)라 검출력이 약함** — 통과해도 타입 안전을 의미하지 않음. strict 옵션을 켰을 때의 오류량은 원본 리뷰 기준 별도 측정 필요(확인 필요, Session 6 보존 항목) |
| Scoped strict 타입체크 | 선택된 신규/리팩토링 helper와 검증 script에 strict + unused check 적용 | `npm run test:strict-scope` (`tsconfig.strict-scope.json`) | 에러 0건 (exit 0) | RF-TASK-088의 단계 반영 안전망. 전역 `tsconfig.json` strict/unused 옵션은 아직 켜지 않는다. scoped 대상 확대 시 이 config와 통과 결과를 같은 작업에서 갱신한다. |
| Scoped strict coverage | 변경된 non-gated 코드/config 파일이 scoped strict TypeScript program에 포함되는지 검증 | `npm run test:strict-scope-coverage` | 누락 0건 (exit 0) | 승인 게이트 파일은 제외하되, 비승인 변경 파일이 `tsconfig.strict-scope.json`에서 빠지는 회귀를 차단한다. |
| Diff hygiene | staged/unstaged diff의 whitespace error와 conflict marker 회귀 검증 | `npm run test:diff-check` | `git diff --check` exit 0 | W12/final review에서 수동 체크리스트에만 의존하지 않고 diff 위생 회귀를 자동 guard로 차단한다. |

- 위 표 외의 full e2e 자동 검증 명령어는 package.json에서 확인되지 않았다. **확인 필요** — `test:route-smoke`는 route status/body, uncaught page error, expected sandbox resource classification, unexpected console/resource failure smoke를 수행하지만 전체 사용자 workflow 검증을 의미하지 않는다.
- CMS/Supabase boundary 정적 검사는 현재 부재(RF-FINDING-004, `scripts/check-pfm-api-boundaries.mjs:6` — PFM 일부 page 한정). CMS service 경계 도입(Phase 2) 이후 검사 확대를 검토하며, 그 전까지 CMS 영역은 수동 검증에 의존한다.

---

## 3. 핵심 사용자 플로우 수동 검증 체크리스트

**도구**: Playwright MCP — `browser_navigate`(접속), `browser_snapshot`(렌더링 확인), `browser_console_messages`(콘솔 에러 0건 확인), `browser_network_requests`(요청 중복/중단 확인), `browser_click`/`browser_fill_form`(조작), `browser_take_screenshot`(증적). 프로젝트 CLAUDE.md의 필수 테스트 워크플로우를 따른다.

**검증 URL**:
- Local: `http://localhost:3000` (Phase 진행 중 기본)
- Production: `https://pfm.cmsl-kookmin.com/simulation2` (배포 후 검증, Phase 8)
- 게시판 앱 production(`https://cmsl.kookmin.ac.kr`)에서는 조회만 수행하고 mutation 검증은 로컬로 한정 (1장 원칙 6)

**라우트 매핑** (코드에서 확인됨): `/simulation2` → `Simulation2Page`, `/cmsl20043` → `AdminPage3`, PFM 로그인 → `/pfm_chat/login`. `PFMSimulationPage`(legacy)는 `app/` 라우트에서 직접 import가 확인되지 않음 — legacy 유지/접근 경로 확인 필요(RF-FINDING-034).

### 3.1 simulation2 워크플로우 (PFM 시뮬레이션 앱)

| 순서 | 단계 | 확인 항목 | 관련 RF-FINDING | Playwright 도구 |
|---|---|---|---|---|
| 1 | 로그인 | `/pfm_chat/login` 접속 → 로그인 성공 → `/simulation2` redirect. 로그인 상태에서 login page 재진입 시 redirect. 콘솔 에러 0건 | RF-FINDING-005 (auth guard), RF-FINDING-022 (token storage) | `browser_navigate`, `browser_fill_form`, `browser_snapshot`, `browser_console_messages` |
| 2 | 세션 생성 | 새 chat session 생성 → `SessionListCard` 목록에 즉시 반영. 검색/페이지 이동/rename/delete 후 목록·total·page가 마지막 의도와 일치 | RF-FINDING-019 (`SessionListCard.tsx:63~212`) | `browser_click`, `browser_snapshot` |
| 3 | 시뮬레이션 생성·파라미터 수정 | simulation 생성 → 목록 반영(`SimulationListCard`). parameter edit 후 저장(PATCH) 성공, 저장된 값 재진입 시 유지 | RF-FINDING-001, RF-FINDING-018, RF-FINDING-041 (`workflowTypes.ts:72`), RF-FINDING-042 (`Simulation2Page.tsx:2378`) | `browser_click`, `browser_fill_form`, `browser_network_requests` |
| 4 | job 제출 | job 생성 요청 성공, 화면에 job 상태(stage) 표시 시작 | RF-FINDING-001, RF-FINDING-033 | `browser_click`, `browser_snapshot`, `browser_network_requests` |
| 5 | 상태 추적 (polling/WS) | WS 연결 시 실시간 상태/이벤트 갱신, WS 불가 시 polling fallback으로 갱신 — 세부는 3.2 | RF-FINDING-032 [P0], RF-FINDING-033, RF-FINDING-035, RF-FINDING-045 (WS payload) | `browser_network_requests`, `browser_console_messages` |
| 6 | 결과 조회 | job 완료 후 `JobResultListCard`에 결과 목록 표시. `ResultExplorerPanel`에서 result/field/filter 전환 시 올바른 데이터 표시(이전 응답 잔상 없음), 다운로드 동작 | RF-FINDING-016 (`ResultExplorerPanel.tsx:286, 392, 411`), RF-FINDING-017 (`JobResultListCard.tsx:93, 98, 121`) | `browser_click`, `browser_snapshot` |
| 7 | 시각화 | visualization 시작 → viewer 렌더링, viz WS/sync 동작, 콘솔 에러 0건. 실패 시 PNG fallback(S5-FALLBACK-001 기준 패턴) | RF-FINDING-033, (기준 패턴 S4-ABORT-001 `Simulation2Page.tsx:2057, 2100`) | `browser_snapshot`, `browser_console_messages`, `browser_take_screenshot` |

### 3.2 polling 흐름

| 항목 | 확인 방법 | 합격 기준 | 관련 RF-FINDING |
|---|---|---|---|
| job 실행 중 상태 갱신 | job 실행 중 `browser_network_requests`로 `getJob`→`listJobEvents`→`listSimulationResults` 호출 주기 관찰 | 상태/이벤트가 주기적으로 갱신되고, **interval 주기 내 요청 겹침(이전 tick 미완료 상태에서 새 tick 시작) 없음** — P0 수정(RF-FINDING-032) 후 단일 비행 보장 | RF-FINDING-032 [P0] (`Simulation2Page.tsx:1605`, S4-RACE-001/S5-POLLING-001) |
| terminal status 도달 시 중단 | job 완료/실패 후 network 요청 관찰 | terminal status 이후 polling 요청이 더 이상 발생하지 않음 | RF-FINDING-032, RF-FINDING-033 |
| WS 끊김 시 fallback | WS 차단/끊김 상황에서 polling 전환 확인 — **재현 방법 확인 필요** (개발자 도구 네트워크 차단 또는 백엔드 협조 필요. Playwright MCP만으로 WS 단절 주입 가능한지 미확인) | WS 단절 후에도 polling으로 상태 갱신 지속, 콘솔에 미처리 에러 없음 | RF-FINDING-033 (`Simulation2Page.tsx:1674`) |
| polling 실패 처리 | `getJob` 실패 반복 시 동작 관찰 | 현재는 실패를 삼키고 null 반환(RF-FINDING-035, `Simulation2Page.tsx:1469`) — Phase 3에서 inline notice 도입 후에는 사용자 피드백 표시 확인 | RF-FINDING-035 |
| legacy polling | `PFMSimulationPage.tsx:468`의 legacy polling — **legacy 유지 여부·접근 경로 확인 필요** (원본 리뷰의 확인 필요 항목) | 유지 결정 시에만 guard 추가 후 동일 기준 검증 | RF-FINDING-034 |

### 3.3 loading / error / success 상태

| 대상 | 시나리오 | 합격 기준 | 관련 RF-FINDING |
|---|---|---|---|
| HomePage (`/`) | 정상 로드 | loading 해제 후 CMS 콘텐츠 렌더링, 콘솔 에러 0건 | RF-FINDING-021 (`HomePage.tsx:18, 36, 66`) |
| HomePage | CMS fetch 실패 (네트워크 차단/Supabase 오류 유도 — 재현 방법 확인 필요) | 변경 전: loading 고착 가능(기존 결함). Phase 3/4 수정 후: error state 표시, loading 고착 없음 | RF-FINDING-021 [High] |
| NoticeBoardPage (`/board/news`) | 목록 로드, 검색, 페이지 이동을 빠르게 연속 수행 | 표시된 목록/고정글/count가 마지막 검색·페이지 상태와 일치(stale 응답 미반영) | RF-FINDING-020 (`NoticeBoardPage.tsx:75`), RF-FINDING-009 (session 재조회) |
| NoticeBoardPage | pin/delete 실패 시 (로컬에서 유도) | 실패 피드백(toast/error) 표시 — 변경 전에는 피드백 제한적(기존 결함, RF-FINDING-037) | RF-FINDING-037 (`NoticeBoardPage.tsx:98`) |
| ResultExplorerPanel | result A 선택 직후 result B로 빠르게 전환, field/filter 연속 변경 | 화면에 B의 detail/catalog/files만 표시(A의 늦은 응답이 덮어쓰지 않음). loading 표시가 실제 요청 상태와 일치 | RF-FINDING-016 |

### 3.4 admin 플로우 (`/cmsl20043` → AdminPage3)

| 항목 | 시나리오 | 합격 기준 | 관련 RF-FINDING |
|---|---|---|---|
| tab 전환 | overview/account/users/simulation/jobs/results/viz 등 tab 순회 (정확한 tab 구성은 화면 기준 — 확인 필요) | 각 tab 데이터 정상 표시, tab 전환 시 콘솔 에러 0건, 불필요한 전체 refetch 없음(`browser_network_requests`) | RF-FINDING-002, RF-FINDING-029 (`AdminPage3.tsx:510~1187`) |
| URL deep link 직접 진입 | query 포함 URL로 직접 접속: 유효 값(예: page/size 정상 숫자), **무효 값(예: `page=abc` 등 `Number()` 결과 NaN)** | 유효 deep link는 기존과 동일하게 복원(호환성 보존 — RF-FINDING-061 주의사항). 무효 값은 P0 수정 후 안전한 기본값으로 보정되고 query key 안정 | RF-FINDING-061 [P0] (`AdminPage3.tsx:498, 489, 918`, S4-URL-001) |
| job 취소 | 실행 중 job 취소 실행 | 취소 성공, 관련 목록/상태 갱신(invalidation 누락 없음), 중복 요청 없음 | RF-FINDING-029 (cancelJobMutation `:697`) |
| job sync / field files | sync 실행, result field files 조회 | cache와 화면 표시 일치(`fieldFilesData` local 복사 불일치 없음) | RF-FINDING-029 (`:510, 744, 729, 676`) |
| 계정 처리 | account request 승인/거부 (세부 절차는 화면 기준 — 확인 필요) | 처리 후 목록 갱신, 에러 피드백 정상 | RF-FINDING-002 |
| 권한 guard | 비로그인/비admin으로 `/cmsl20043` 접근 | guard 동작(early return UX) 기존과 동일 — **민감 영역** (`AdminPage3.tsx:1157, 1166`) | RF-FINDING-002, RF-FINDING-007 |
| admin polling | admin 화면 유지 시 `refetchInterval`(10초, `components/pages/adminPolling.ts:4`) 동작 | 주기 갱신 유지, 정책 변경 시 의도된 주기 확인 | RF-FINDING-029, RF-FINDING-055 |

### 3.5 CMS 게시판 (notice / gallery) — 로컬 환경에서 수행

| 항목 | 시나리오 | 합격 기준 | 관련 RF-FINDING |
|---|---|---|---|
| notice 목록/상세 | `/board/news` 목록 → 상세 진입 | 목록/상세 정상 렌더링, 콘솔 에러 0건 | RF-FINDING-003 |
| invalid id 진입 | `/board/news/[id]`에 잘못된 id로 직접 진입 | not-found/error 처리 — 변경 전 `EditNoticePage`는 invalid id에서 무한 loading 가능(기존 결함). Phase 6 수정 후 error/not-found UI 표시 | RF-FINDING-006 (`app/board/news/[id]/page.tsx:9`, `EditNoticePage.tsx:46`) |
| notice 수정 + 첨부 | 첨부 교체(기존 제거 + 신규 업로드) 후 저장 / 저장(DB update) 실패 유도 | 정상 경로: 첨부와 DB attachment 일치. 실패 경로: P0 수정(RF-FINDING-036) 후 보상 처리로 파일-DB 불일치 없음. **실제 storage path/URL parsing은 원본 리뷰 기준 확인 필요** | RF-FINDING-036 [P0] (`EditNoticePage.tsx:107`, S5-ROLLBACK-001) |
| gallery 목록/상세/수정 | `/board/gallery` 목록 → 상세 → 수정 저장 | 정상 동작, stale 응답 미반영(RF-FINDING-020 gallery `:42`) | RF-FINDING-003, RF-FINDING-020 |
| edit route 접근 제어 | 비로그인 상태로 `/board/news/[id]/edit` 직접 진입 | 접근 차단 또는 의도된 동작 — **edit route 권한이 UI/RLS 어느 쪽에서 보장되는지 확인 필요** (Session 2 보존 항목). 확인 전 과도한 차단 추가 금지(RF-FINDING-005 개선 방향) | RF-FINDING-005 |
| Contact 폼 | `/contact` 제출 (테스트 데이터) | EmailJS 전송 또는 env 미설정 시 명확한 disabled/error 동작(P0 RF-FINDING-051 수정 후) | RF-FINDING-031, RF-FINDING-051 [P0] (`ContactPage.tsx:26-29`) |

---

## 4. 회귀 테스트 체크리스트 (Phase별)

각 Phase 완료 시: **공통 = 2장 자동 검증 전체 + baseline 대비 비교**. 아래는 Phase별 추가 필수 항목이다. Phase-RF 매핑은 확정 Phase 구조에 따른 검증 관점 배정이다. (추론)

| Phase | 주요 변경 대상 (RF-FINDING) (추론) | 필수 수동 플로우 | 추가 확인 |
|---|---|---|---|
| **Phase 0** 준비 + P0 핫픽스 | RF-FINDING-032 (polling in-flight guard, `Simulation2Page.tsx:1605`), RF-FINDING-036 (attachment rollback, `EditNoticePage.tsx:107`), RF-FINDING-051 (env helper, `supabaseClient.ts:5-6`/`ContactPage.tsx:26-29`), RF-FINDING-061 (URL NaN parser, `AdminPage3.tsx:498`) | 3.2 전체(polling 겹침 해소·terminal 중단·WS fallback), 3.4 deep link(유효/무효 query), 3.5 notice 첨부 수정(정상+실패 경로), 3.5 Contact 폼 | baseline 최초 기록(1장 원칙 2), 테스트 인벤토리 확인(5장). RF-FINDING-051은 env 미설정 빌드/실행 시 명확한 실패 메시지 확인(검증 방법 세부는 확인 필요). P0는 행동 변경이므로 변경 전 결함 재현 증적 먼저 확보 |
| **Phase 1** type/DTO/API contract | RF-FINDING-039, 040, 041, 042 | 3.1의 3~5단계(파라미터 수정·job 제출·상태 추적 — DTO/mapper 변경 영향), 3.4 tab 전환(admin DTO drift), 3.3 HomePage(CMS DTO) | `npm run build` 필수 + `npx tsc --noEmit` 비교(검출력 한계 명시 — 2장). DTO 단일화 후 admin/일반 API 계약이 실제로 같은지 **백엔드 명세 확인 필요**(Session 6 보존 항목). 타입만 변경한 커밋도 런타임 검증 생략 금지(strict off라 컴파일이 못 잡음) |
| **Phase 2** API client/service layer | RF-FINDING-003, 022, 028, 030, 031 | 3.1의 1단계(로그인·token refresh — **민감 영역**: 401 refresh 후 재시도 동작), 3.5 전체(CMS service 분리 영향), 3.5 Contact 폼. legacy `/api/chat` 흐름은 **유지 여부 확인 필요** 후 검증 | `npm run test:boundaries` 매 커밋 권장. timeout/AbortSignal 도입(RF-FINDING-028) 시 정상 요청이 조기 abort되지 않는지 long-running 요청(다운로드 등) 확인. CMS boundary 검사 확대(RF-FINDING-004)는 service 경계 확정 후 |
| **Phase 3** async flow/polling/error | RF-FINDING-020, 021, 034, 035, 037 (+RF-FINDING-032 재검증) | 3.2 전체, 3.3 전체 | 좋은 패턴(S4-ABORT-001, S5-CANCEL-002, S5-TIMEOUT-002)과 동일 구조인지 코드 리뷰. 연속 실패 notice(RF-FINDING-035) 신규 UX 확인 |
| **Phase 4** hook/state | RF-FINDING-016, 017, 018, 019, 023, 024, 025, 026, 027, 029, 033 | 3.1의 2·6단계(세션/결과 — stale guard), 3.1의 5·7단계(WS lifecycle hook 분리 — **민감 영역**: `Simulation2Page.tsx:611, 1674, 1949` cleanup/reconnect), 3.3 전체(특히 ResultExplorerPanel 빠른 전환), 3.4 전체(admin query/mutation hook 분리 — query key/invalidation 호환) | WS 분리 전 기존 cleanup 동작 재검증 선행(Session 4 보존 항목). React Query key 변경 시 invalidation 누락 검사(RF-FINDING-029): mutation 후 관련 목록 갱신을 network 단위로 확인. `refetchInterval`/staleTime 정책 변경은 **product freshness 요구 확인 필요**(Session 4/5 보존 항목). toast/use-mobile 변경(RF-FINDING-024/025) 시 기존 toast 테스트 확인 |
| **Phase 5** component 책임 분리 | RF-FINDING-001, 010, 011, 012, 013, 014, 015, 043, 044, 045, 047 | 3.1 전체(ChatPanel/ParameterPanel/ResultWorkspace 분리 회귀), chat 메시지 추가/이벤트 로그 갱신 시 목록 안정성(RF-FINDING-014 key 변경), MemberDetailModal 키보드/포커스(RF-FINDING-011), ResearchPageTemplate 렌더링(RF-FINDING-013 — sanitize 정책 **확인 필요** 전 동작 변경 금지) | 분리 전후 props contract 비교(RF-FINDING-010 — pass-through 축소가 동작 동일성 유지하는지). index key → stable key 전환 시 reorder/삽입 시나리오 수동 확인 |
| **Phase 6** route/page/container | RF-FINDING-002, 005, 006, 007, 008, 009, 048 (+RF-FINDING-061 재검증) | 3.1의 1단계(PFM auth gate 분리 후 로그인/redirect/직접 URL 진입), 3.4 권한 guard·deep link, 3.5 invalid id·edit route 접근, legacy admin(`/cmsl2004`, `/cmsl20042`) gate 동작, 404/global error fallback(`not-found.tsx`만 존재 — error.tsx 추가 여부는 **확인 필요** 결정 후) | guard 이동 시 redirect 목적지/조건의 변경 전후 동일성. `middleware.ts` 부재가 의도인지 **확인 필요**(Session 2 보존 항목). Suspense fallback 단일화 후 초기 로드 UX 비교 |
| **Phase 7** util/config/constant/validation | RF-FINDING-046, 049, 050, 052, 053, 054, 055, 056, 057, 058, 059 | blob 다운로드/파일명(RF-FINDING-047/048/050 — Simulation2Page·AdminPage3·ResultExplorerPanel 다운로드 동작과 파일명 동일성), admin formatter 표시값(RF-FINDING-048), 외부 이미지 렌더링(RF-FINDING-053 remotePatterns 제한 후 기존 CMS 이미지 깨짐 없음 — 허용 도메인 목록 **확인 필요**) | pure helper 이동은 "행동 변경 없이 이동 → 테스트 추가" 순서(RF-FINDING-047 개선 방향). 이동된 helper에 단위 테스트 동반 → `test:run`/`test:coverage`로 확인. env 정리(RF-FINDING-052)는 배포 env 설정 확인 후. circular import(RF-FINDING-060)는 `npm run test:circular`로 재검증한다. |
| **Phase 8** 테스트/빌드/회귀/최종 | RF-FINDING-004, 038, 060 (+잔여 RF 마감 확인) | 3.1~3.5 **전체** 수행 — 로컬 + production(`https://pfm.cmsl-kookmin.com/simulation2`) 배포 후 Playwright 재검증(CLAUDE.md 워크플로우: commit/push → Vercel 배포 대기 → browser_navigate/snapshot/console) | 2장 자동 검증 전체 + 커버리지 최종 비교(Phase 0 baseline 대비). 61건 RF-FINDING 처리 상태 대조(처리/이월/확인 필요 분류). 미해결 "확인 필요" 항목 목록화하여 후속 과제로 이관 |

**P0 재검증 규칙**: RF-FINDING-032는 Phase 3에서, RF-FINDING-061은 Phase 6 (RF-TASK-069)에서, RF-FINDING-036은 Phase 2(CMS service)·Phase 3(rollback 정책)에서, RF-FINDING-051은 Phase 7(config 정리)에서 해당 영역 구조 변경 시 반드시 재검증한다. (추론: P0 핫픽스 지점이 후속 Phase의 구조 변경 대상과 겹치기 때문)

---

## 5. 기존 테스트 현황 — 확인 필요 (Phase 0 선행 조건)

- **기존 테스트 인벤토리는 미상이다.** 테스트 현황 조사(Session 7)는 미수행 상태이며, 코드리뷰 세션 1~6은 테스트 커버리지를 측정하지 않았다 → **확인 필요**. Session 1 brief도 "테스트 커버리지와 주요 회귀 시나리오"를 추가 조사 필요 영역으로 명시했다.
- `package.json`에서 확인된 사실: vitest 스크립트(`test`, `test:run`, `test:coverage`), `scripts/check-pfm-api-boundaries.mjs`(`test:boundaries`), `scripts/check-circular-imports.mjs`(`test:circular`), `scripts/check-local-route-smoke.mjs`(`test:route-smoke`), `tsconfig.strict-scope.json`(`test:strict-scope`), `scripts/check-strict-scope-coverage.mjs`(`test:strict-scope-coverage`), `git diff --check`(`test:diff-check`), `scripts/check-w12-guards.mjs`(`test:w12-guards`), and `scripts/check-w12-full-regression.mjs`(`test:w12-full`)가 존재한다. `tsconfig.json` exclude에 `vitest.setup.ts`, `**/*.test.ts(x)` 패턴이 있어 테스트 파일 체계의 존재는 간접 확인된다. 그러나 **실제 테스트 파일 수, 대상 범위, 통과율, 커버리지 수치는 확인 필요**.
- **Phase 0 선행 조건 (P0 핫픽스 이전 수행)**:
  1. vitest 테스트 인벤토리 확인: 테스트 파일 목록(glob `**/*.test.{ts,tsx}`), 대상 모듈, 테스트 수 기록
  2. `npm run test:run` 실행 → 통과/실패/스킵 수를 baseline으로 기록 (기존 실패 테스트가 있으면 리팩토링 결함과 구분하기 위해 명시)
  3. `npm run test:coverage` 실행 → 커버리지 수치 baseline 기록 (커버리지 도구 설정 자체가 동작하는지 포함 — 확인 필요)
  4. `npm run test:boundaries` 실행 → 현재 위반 0건인지 확인
  5. P0 대상 4개 지점(`Simulation2Page.tsx:1605`, `AdminPage3.tsx:498`, `EditNoticePage.tsx:107`, env 사용처)에 기존 테스트가 있는지 확인 → 없으면 P0 수정과 함께 회귀 테스트 신규 작성 (기준 패턴: S4-ABORT-001 in-flight guard, S5-TIMEOUT-002 timeout polling)
- 인벤토리 확인 결과에 따라 본 문서 2장(자동 검증 성공 기준)과 4장(Phase별 테스트 요구 수준)의 세부 기준을 Phase 0에서 보정한다. (추론)
## W12 Approval-Gated Diff Check Addendum

- Command: `npm run test:approval-gates`
- Script: `scripts/check-approval-gated-diffs.mjs`
- Purpose: verify that CMS/board/Contact/legacy approval-gated paths, including their App Router route wrappers, have no staged/unstaged content diffs and no untracked approval-gated files before explicit approval. The script also includes path-pattern self-test samples so gated route wrappers and non-gated PFM/Admin routes are not accidentally reclassified.
- This is not the RF-FINDING-004 CMS service-boundary inspection. The CMS service-boundary check remains gated until Track C service boundaries are approved and implemented.
- Include this command with W12 and final regression checks alongside `test:boundaries`, `test:circular`, `test:route-smoke`, `test:strict-scope`, and `test:strict-scope-coverage`.

## W12 Change Group Check Addendum

- Command: `npm run test:change-groups`
- Manifest command: `npm run test:change-groups:list`
- Checklist command: `npm run test:change-groups:checklist`
- Git add command manifest: `npm run test:change-groups:commands`
- JSON manifest command: `npm run test:change-groups:json`
- Script: `scripts/check-refactoring-change-groups.mjs`
- Purpose: verify that current staged content diffs, unstaged content diffs, and untracked files are assigned to the W12 split matrix groups before commit review.
- The manifest command uses the same classifier and prints the exact current paths per group for human review and manual commit slicing.
- The checklist command uses the same classifier and prints the verification commands to run before reviewing or committing each active split group.
- The commands mode uses the same classifier and prints reviewable PowerShell-safe `git add -- ...` pathspec chunks for each active split group. It stages nothing and does not create commits.
- The JSON manifest command emits the same split manifest, group checklists, git-add command chunks, and final checklist as machine-readable JSON for tooling or review capture.
- The command builder includes self-tests for PowerShell quoting/chunking and verifies that each active group's git-add command chunks cover the grouped path list exactly.
- The classifier includes representative self-test samples for each split group, intentionally leaves approval-gated CMS/board paths unassigned so accidental gated work cannot be hidden in a non-gated group, and rejects unapproved overlapping ownership between split groups.
- This is not proof that the working tree already satisfies one-task/one-commit. It is a pre-commit review aid that prevents unclassified files from slipping into the large assembled change set.
- Include this command with W12 and final regression checks whenever `w12-commit-boundary-plan.md` is updated.

## W12 Lint Carry-Forward Guard Addendum

- Command: `npm run test:lint-carry-forward`
- Script: `scripts/check-lint-carry-forward.mjs`
- Purpose: verify that the current global lint failures remain confined to the approved carry-forward paths/rules, do not exceed the recorded per-file rule counts, and do not exceed the recorded 112-problem baseline.
- This is not a replacement for `npm run lint`. Global lint remains incomplete until those issues are fixed after approval/product decisions or explicitly waived.
- Include this command with W12 and final regression checks whenever `w12-lint-carry-forward.md` is updated.

## W12 Diff Hygiene Guard Addendum

- Command: `npm run test:diff-check`
- Underlying command: `git diff --check`
- Purpose: fail W12/final review on whitespace errors or conflict markers in the staged/unstaged diff.
- This replaces the previous manual-only `git diff --check` checklist item inside the automated W12 guard bundle.
- Include this command with W12 and final regression checks whenever any script, source, or documentation diff changes.

## W12 Aggregate Guard Addendum

- Command: `npm run test:w12-guards`
- Script: `scripts/check-w12-guards.mjs`
- Purpose: sequentially run the local W12 safety guard set: approval-gated diff check, lint carry-forward guard, change-group check, change-group checklist output, change-group JSON manifest output, change-group git-add command manifest, diff hygiene check, circular import check, scoped strict check, scoped strict coverage check, and local route smoke.
- This is not a replacement for full regression (`test:run`, `test:coverage`, build) or backend-authenticated Playwright workflows. It is a fast local safety-gate bundle for W12 carry-forward review.
- Include this command before final review whenever any W12 guard script or W12 closure document changes.

## W12 Full Regression Addendum

- Command: `npm run test:w12-full`
- Script: `scripts/check-w12-full-regression.mjs`
- Purpose: sequentially run root typecheck, full vitest, coverage, PFM boundary check, production build with local dummy public Supabase env fallbacks, and `test:w12-guards`.
- This is not a replacement for `npm run lint` or backend-authenticated Playwright workflows. Global lint remains represented by `test:lint-carry-forward` inside `test:w12-guards` until gated/product decisions resolve the remaining lint buckets.
- Include this command before final local review when runtime/backend/browser gates are still unavailable but a reproducible full local regression bundle is needed.
