# 통합 이슈 목록 (Consolidated Findings)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6` 코드리뷰 결과 통합.
> 이 문서는 리팩토링 문서 세트(Phase 0~8)의 기준 문서이며, 모든 Phase 문서는 본 문서의 RF-FINDING ID를 참조한다.

---

## 1. 통합 기준 설명

### 1.1 병합 기준

- **같은 근본 원인 = 하나의 RF-FINDING.** 여러 세션이 같은 파일/같은 지점/같은 근본 원인을 서로 다른 관점(architecture, container, component, state, API)에서 지적한 경우 하나의 통합 ID로 병합하고, 원본 ID는 전부 보존한다.
- **세션 내 동일 finding의 중복 ID**(요약표와 세부 리뷰 문서가 같은 이슈에 다른 번호를 부여한 경우)도 병합하고 양쪽 ID를 `A(=B)` 형식으로 기록한다.
- **심각도**: 원본 표기(High/Medium/Low/Suggestion)를 보존하되, 병합 시 가장 높은 심각도를 채택한다. 채택 근거는 병합 기록(4장)에 명시한다.
- **인벤토리 ID 제외**: `S5-ENDPOINT-002`~`S5-ENDPOINT-040`(endpoint-map.md)과 `S5-FLOW-001`~`S5-FLOW-017`(async-flow-map.md)은 이슈가 아니라 호출 지도/흐름 지도 항목이므로 RF-FINDING으로 등재하지 않고, 관련 RF-FINDING에 "(참고)"로만 연결한다. 단 `S5-ENDPOINT-001`은 api-layer-architecture-review.md에서 긍정 관찰(base URL 공통화)로 별도 사용되어 좋은 패턴 표(6장)에 등재한다.

### 1.2 우선 판단 기준

- 세션 간 명시적 판단 충돌은 사전 조사에서 발견되지 않았다(5장 참조). 만약 충돌이 발견될 경우 **라인 단위의 구체적 지적 > 개괄 지적**을 우선한다.
- 심각도 표기 차이는 판단 충돌이 아닌 관점 차이로 보고, 최고 심각도를 채택한다.

### 1.3 근거 / 추론 구분

- 본 문서의 파일 경로, 라인, 문제 요약, 영향, 개선 방향은 **코드리뷰 원본 문서에 적힌 근거**를 그대로 옮긴 것이다.
- 계획 수립 과정에서 새로 내린 판단(병합 여부, 심각도 채택, 영역 분류 등)은 "(추론)" 표기를 붙인다.
- 코드리뷰 문서에 없는 내용은 단정하지 않고 "확인 필요"로 표시한다. 원본의 "확인 필요" 항목은 7장에 세션별로 그대로 보존한다.

### 1.4 세션 내 ID 표기 충돌 (요약표 ↔ 세부 문서 번호 재사용)

세션 요약표(session{N}-findings.md)와 세부 리뷰 문서가 **같은 번호를 서로 다른 이슈에** 부여한 사례가 있다. 본 문서에서는 `(요약)`/`(상세)`로 구분해 표기한다. (추론: 아래 매핑은 파일 경로·라인 대조로 확정)

| 충돌 ID | 요약표(session{N}-findings.md)의 의미 | 세부 문서의 의미 |
|---|---|---|
| S2-PAGE-001 | Suspense fallback/loading 중복 (`app/simulation2/page.tsx:43`) = 상세 S2-PAGE-003 | page-container-review: route page의 token/getMe/redirect 직접 처리 (`app/simulation2/page.tsx:16`) |
| S2-PAGE-002 | board dynamic id validation (`app/board/news/[id]/page.tsx:9`) = 상세 S2-ROUTE-002 | page-container-review: `window.location.href` 직접 redirect (`app/simulation2/page.tsx:20`) |
| S3-RENDER-001 | Simulation2Page chat index key (:3355) = 상세 S3-PERF-001 | rendering-performance-review: ImageCarousel index key (:32) |
| S3-RENDER-002 | ImageCarousel index key (:33) = 상세 S3-RENDER-001 | rendering-performance-review: ResearchPageTemplate section index key (:142) |
| S4-EFFECT-001 | HomePage fetch catch/finally 부재 (:36) = 상세 S4-EFFECT-004 | effect-dependency-review: job polling fallback in-flight guard 부재 (`Simulation2Page.tsx:1599`, High) |
| S4-STATE-002 | AdminPage3 admin server state 집중 (:551, High) | server-client-state-review: HomePage CMS data `any` local state (:18, Medium) |
| S4-CACHE-001 | AdminPage3 `fieldFilesData` local 복사 (:510) = 상세 S4-CACHE-002(:744) | query-cache-review: `syncJobMutation` cache side effect (:676) |
| S4-MUTATION-001 | AdminPage3 mutation cache side effect (:676) = 상세 S4-CACHE-001 | query-cache-review: `cancelJobMutation` invalidation fan-out (:697) = S5-MUTATION-002 |
| S5-SERVICE-001 | Simulation2Page API orchestration (:2255) = 상세 S5-APIARCH-001 | api-layer-architecture-review: NoticeBoardPage Supabase 직접 호출 (:58) |
| S5-SERVICE-002 | AdminPage3 query/mutation 집중 (:551) = 상세 S5-APIARCH-002 | api-layer-architecture-review: EditNoticePage storage/DB 직접 호출 (:85) |
| S5-CACHE-001 | AdminPage3 mutation cache side effect (:666) | query-mutation-review: `loadFieldFilesMutation` local 복사 (:729) |
| S6-ENUM-001 | colormap 옵션 중복 (VisualizationControlBar:29 등) = 상세 S6-CONST-002 | type-duplication-review: `VisualizationStatus` 타입 중복 |
| S6-MAPPER-001 | (요약표 미등재) | util-responsibility-review: workflowMappers WS payload DTO 부재 / validation-formatting-review: `normalizeSimulationCompositionDto` admin 유사 타입 drift — 두 세부 문서가 같은 번호를 다른 이슈에 사용 |

---

## 2. 통합 이슈 표 (메인)

### 2.1 architecture

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-001 | S1-ARCH-001, S1-STRUCT-001(연관), S2-CONTAINER-001, S3-COMP-001(=S3-QUAL-001), S4-STATE-001, S5-SERVICE-001(요약)(=S5-APIARCH-001), S6-IMPORT-001 | S1~S6 | High | architecture | `components/pages/Simulation2Page.tsx` | 589, 592, 2255, 4-102 | 3347 line(비공백 기준, 총 3632줄) 거대 컨테이너가 chat/workflow/parameter edit/job monitor/WebSocket/result/visualization 상태·API orchestration·렌더링을 모두 보유. import 목록 자체가 책임 집중 신호 | 변경 영향도와 회귀 위험이 코드베이스에서 가장 큼. 단위 테스트 불가 수준. stale closure/race 방어 코드가 내부 누적 | `useSimulationWorkflow`, `useJobMonitorSession`, `useVisualizationSession`, `useSimulationDraft` hook과 `ChatPanel`/`ParameterPanel`/`ResultWorkspace` presenter로 단계적 분리. 한 번에 전체 분리 금지 |
| RF-FINDING-002 | S1-ARCH-004, S2-CONTAINER-002, S3-COMP-002(=S3-QUAL-002), S3-PERF-003, S3-QUAL-009, S4-STATE-002(요약), S4-QUERY-002(=S5-QUERY-001), S4-CLIENT-001, S5-SERVICE-002(요약)(=S5-APIARCH-002), S6-IMPORT-002 | S1~S6 | High | architecture | `components/pages/AdminPage3.tsx` | 483, 551, 501, 1327, 2906, 21-117 | 2942 line(비공백 기준, 총 3076줄) admin 컨테이너가 URL state, 권한, React Query group(me/health/ready/account/users/simulation/jobs/results/viz), mutation, tab/table/dialog UI를 모두 보유. dialog가 inline 정의 | admin 기능 추가 시 결합도·회귀 위험 증가. tab 하나 변경이 대형 JSX tree 전체 평가와 충돌 | `AdminOverviewPanel`/`AccountRequestsPanel`/`UsersPanel`/`SimulationAdminPanel` 등 tab별 container, dialog/table component, tab별 query/mutation hook 분리. query key helper 안정화 선행 |
| RF-FINDING-003 | S1-ARCH-002, S1-ARCH-003, S2-DEPENDENCY-001, S2-DEPENDENCY-002(=S2-CONTAINER-006), S2-DEPENDENCY-003(=S2-CONTAINER-007), S2-CONTAINER-005, S3-CMS-001(=S3-RESP-007), S3-CMS-002(=S3-RESP-006), S3-RESP-005, S4-SERVER-005, S4-SERVER-006, S5-SERVICE-003(요약)(=S5-SERVICE-001(상세)), S5-SERVICE-002(상세) | S1~S5 | High | API/service | `components/pages/HomePage.tsx`, `NoticeBoardPage.tsx`, `NoticeDetailPage.tsx`, `EditNoticePage.tsx`, `EditGalleryPage.tsx`, `GalleryBoardPage.tsx`, `components/ResearchPageTemplate.tsx` | 11, 58, 58, 85/122, 112, 22, 72 | CMS/게시판 page·form component가 Supabase query/mutation/storage upload·remove를 직접 수행 (S5-FLOW-015 참고) | UI와 persistence 결합. RLS/권한/스토리지 정책 변경과 mutation 회귀에 취약. 테스트 어려움 | CMS domain service/repository 또는 query hook(`useNoticeBoard`, `useGalleryBoard`, `useNoticeEditor`) + storage adapter 도입. RLS/권한 정책 확인 필요. 도메인별 점진 이관 |
| RF-FINDING-004 | S1-TEST-001, S5-BOUNDARY-001, S5-APIARCH-003 | S1, S5 | Suggestion | testability | `scripts/check-pfm-api-boundaries.mjs` | 6 | PFM simulation boundary guard만 존재하고 일부 PFM page에 한정. CMS/Supabase boundary guard 부재 | CMS 리팩토링 중 UI-persistence 결합 재발 가능 | CMS service boundary 도입 후 정적 검사/테스트 확대. service 경계를 먼저 정한 뒤 검사 추가 |

### 2.2 routing / page

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-005 | S2-GUARD-001(=S2-PAGE-001(상세)), S2-GUARD-002, S2-GUARD-003(=S2-PAGE-004(상세)), S2-GUARD-004(=S2-PAGE-006(상세)), S2-PAGE-002(상세), S2-PAGE-005(상세), S2-PAGE-007(상세), S2-PAGE-001(요약)(=S2-PAGE-003(상세)), S2-ROUTE-001 | S2 | Medium | routing/page | `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx`, `app/cmsl2004/page.tsx`, `app/cmsl20042/page.tsx`, `app/board/news/[id]/edit/page.tsx`, `app/board/gallery/[id]/edit/page.tsx` | 16/18/20/43, 8/10, 13/14, 13, 7, 7 | route별 인증/redirect guard가 page effect에 직접 구현·분산: PFM auth(token/getMe/`window.location.href`), legacy admin Supabase session gate 중복(cmsl2004/20042), board edit route guard 미확인, Suspense fallback과 inner loading 중복 | 인증 정책 재사용·테스트 불가, 새 route 추가 시 guard 위치 흔들림, edit 접근 제어가 container/RLS에 숨을 수 있음 | `usePfmAuthGate`/`ProtectedPfmRoute`/`RedirectIfAuthenticated`, `LegacyAdminGate`/`useSupabaseSessionGate` 도입. edit route 권한 기준 명시화(RLS 확인 전 과도한 차단 금지). fallback presenter 단일화 |
| RF-FINDING-006 | S2-PAGE-002(요약)(=S2-ROUTE-002), S2-ROUTE-003, S3-QUALITY-001(=S3-QUAL-005) | S2, S3 | Medium | routing/page | `app/board/news/[id]/page.tsx`, `app/board/gallery/[id]/page.tsx`, `components/pages/EditNoticePage.tsx` | 9, 9, 46 | dynamic route id validation/not-found 처리 위치가 불명확. `EditNoticePage`는 invalid id(`Number(id)`가 `NaN` 포함)에서 `loading`이 false로 내려가지 않음 | 잘못된 URL 처리 UX가 route마다 다름. editor 무한 loading 가능 | board 공통 `parseBoardId` parser와 notFound/redirect 정책 표준화. invalid state를 error/not-found UI로 전환 |
| RF-FINDING-007 | S2-BOUNDARY-001, S2-BOUNDARY-002, S2-CONTAINER-008 | S2 | Medium | error/loading/retry | `app`, `app/cmsl20043/page.tsx`, `components/pages/AdminPage3.tsx` | 1, 14, 1139 | global `error.tsx`/route별 `loading.tsx` 미확인(`not-found.tsx`만 존재). route fallback과 container early return의 책임 기준 분산 | 예상치 못한 오류 fallback이 route별로 비일관. fallback/error UX가 container마다 분산 | global error boundary와 보호 route fallback 전략 검토. admin guard 상태 presenter 분리. 로깅/복구 UX 정책 필요(확인 필요) |
| RF-FINDING-008 | S2-LAYOUT-001 | S2 | Suggestion | layout/container | `app/layout.tsx` | 87 | 모든 route가 동일 Header/Footer layout 공유 | admin/workbench/viewer UX에서 layout 분리 필요 여부 확인 필요 | route group layout 필요성 검토(제품 UX 결정 필요, 확인 필요) |
| RF-FINDING-009 | S2-CONTAINER-003, S2-CONTAINER-004 | S2 | Medium | layout/container | `components/pages/NoticeBoardPage.tsx`, `components/pages/GalleryDetailPage.tsx` | 23, 35 | route에서 session을 받았는데 container가 다시 `supabase.auth.getSession()` 호출. detail container도 session prop 부재 시 재조회 | session source 중복으로 상태 불일치·불필요한 client auth 호출 가능 | session ownership을 route/server 또는 client gate 중 하나로 통일. board 공통 session hook 검토 |

### 2.3 component

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-010 | S3-PROPS-001 | S3 | Medium | props/state flow | `components/simulation/WorkspaceTabsCard.tsx` | 14 | workspace wrapper가 active tab, ids, refresh keys, 15개 이상의 props/callback을 pass-through | props contract가 계속 커질 수 있음 | workspace domain hook/presenter 경계 재정의. context 도입 전 상위 컨테이너 책임 분리 선행 |
| RF-FINDING-011 | S3-ACCESS-001(=S3-QUAL-004) | S3 | Medium | component | `components/MemberDetailModal.tsx` | 18 | custom modal에 `role="dialog"`/`aria-modal`/focus trap/escape 처리 미확인 | keyboard/screen reader 접근성 저하 가능 | Radix Dialog 기반 전환 또는 접근성 속성/포커스 관리 보강 |
| RF-FINDING-012 | S3-QUAL-010, S3-RENDER-004(상세) | S3 | Low | component | `components/pages/NewsPage.tsx` | 29, 65 | presentation list component가 search setter, pagination, edit/delete/pin action props를 직접 수신. pagination handler가 매 render 재생성 | list UI 재사용성 낮고 action policy가 props contract에 노출 | row action model 또는 action slot 도입. 공통 pagination presenter 추출(Notice/Gallery 유사 패턴 통합 후보) |
| RF-FINDING-013 | S3-QUAL-008 | S3 | Medium | component | `components/pages/ResearchPageTemplate.tsx` | 65 | CMS text를 `dangerouslySetInnerHTML`로 렌더링하며 sanitize 정책이 component에서 확인되지 않음 | CMS content 입력 경로가 안전하지 않으면 XSS 위험 | sanitize 위치/trusted content 정책 확인 후 sanitizer/service 경계로 이동 (확인 필요: 저장 시점 sanitize 여부) |
| RF-FINDING-014 | S3-RENDER-001(요약)(=S3-PERF-001(상세)), S3-PERF-002(상세), S3-RENDER-002(요약)(=S3-RENDER-001(상세)), S3-RENDER-002(상세) | S3 | Medium | performance | `components/pages/Simulation2Page.tsx`, `components/ImageCarousel.tsx`, `components/ResearchPageTemplate.tsx` | 3355/3360/3368/3419, 32-33, 142 | chat message list·job event log·carousel·CMS section에서 index key 사용 | list reconciliation 불안정, reorder/삽입 시 row/slide state 오재사용 가능 | stable id 기반 key 도입(message id, event timestamp/type 조합, item.url, CMS section id) |
| RF-FINDING-015 | S3-RENDER-003(상세) | S3 | Suggestion | performance | `components/ResearchHighlightsSlider.tsx` | 80 | `slideVariants`/`contentVariants` object가 매 render 재생성 | 현재 규모에서 영향 낮으나 motion subtree 확대 시 memoization 어려움 | static variants를 component 밖으로 이동 또는 `useMemo` 검토 |

### 2.4 hook / state (local·server state, query cache)

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-016 | S3-STATE-001(=S3-RESP-001), S4-SERVER-004, S4-STALE-001(=S4-EFFECT-002), S4-STALE-002(=S4-EFFECT-003), S5-STALE-002 | S3, S4, S5 | Medium | server state | `components/simulation/ResultExplorerPanel.tsx` | 286, 392, 411 | result detail/field catalog/field files/filter/download state가 한 component에 집중. detail에는 sequence guard가 있으나 catalog/files 요청에는 stale response guard 없음 (S5-FLOW-008/009 참고) | result/field 전환 시 이전 응답이 새 UI에 반영될 수 있음. API/cache/race 정책 변경이 UI에 결합 | `useResultDetail`/`useResultFieldCatalog`/`useResultFieldFiles`/`useResultDownload` 분리. `resultId+field+filters` 기준 request token 또는 AbortController 적용 |
| RF-FINDING-017 | S3-STATE-003(=S3-RESP-003), S4-SERVER-001, S4-RACE-002, S4-CLEANUP-001, S5-STALE-001 | S3, S4, S5 | Medium | server state | `components/simulation/JobResultListCard.tsx` | 93, 98, 121 | job/result server state를 local state + refreshKey로 관리. `simulationId`/`refreshKey` 변경 시 이전 `Promise.all` 응답 stale guard·effect cleanup 없음 (S5-FLOW-010 참고) | 다른 simulation의 job/result 목록이 잠깐 표시 가능. unmount 후 늦은 응답이 state 갱신 가능 | React Query key 기반 cache 또는 request sequence guard가 있는 `useSimulationJobResults` hook. `sync:false` 정책 유지(Lab sync 비용) |
| RF-FINDING-018 | S3-RESP-004, S4-SERVER-002, S4-RACE-003, S4-CLEANUP-002, S3-PERF-004(연관) | S3, S4 | Medium | server state | `components/simulation/SimulationListCard.tsx` | 50, 56, 61, 65, 77 | simulation list server state가 component local state. page/refresh 변경 stale guard·cleanup 없음. `FETCH_SIZE`(최대 100) 일괄 조회 후 client pagination | 이전 page/list 응답이 현재 UI를 덮을 수 있음. 데이터 증가 시 네트워크/렌더 비용 증가 | `useSimulationList` hook 또는 React Query 전환 + request token. server pagination 전환 여부 확인 |
| RF-FINDING-019 | S3-STATE-002(=S3-RESP-002), S4-SERVER-003, S4-RACE-004, S4-CLEANUP-003 | S3, S4 | Medium | server state | `components/simulation/SessionListCard.tsx` | 63, 73, 90, 122, 164, 212 | session list/search/delete/rename/dialog 상태와 API 호출이 한 component에 집중. 검색·페이지 이동·rename/delete 후 reload가 동시 발생해도 stale guard 없음 (S5-FLOW-011 참고) | 목록/total/page가 마지막 사용자 의도와 달라질 수 있음. action 후 reload race | `useChatSessions` hook + `SessionListView`/`SessionRenameForm`/`SessionDeleteDialog` 분리. request id와 action별 mutation state 분리. parent callbacks contract 유지 |
| RF-FINDING-020 | S4-ASYNCSTATE-002, S4-ASYNCSTATE-003 | S4 | Medium | server state | `components/pages/NoticeBoardPage.tsx`, `components/pages/GalleryBoardPage.tsx` | 75, 42 | CMS list `Promise.all`/fetch에 page/search 변경 중 stale response guard 없음(gallery는 try/finally는 있음) | 게시글/고정글 count가 search state와 어긋나거나 이전 응답 반영 가능 | notice/gallery 공통 list query hook으로 통일하고 request sequence 적용 (RF-FINDING-003 service 분리와 함께 진행) (추론) |
| RF-FINDING-021 | S3-QUALITY-002(=S3-QUAL-006), S4-EFFECT-001(요약)(=S4-EFFECT-004(상세)), S4-ASYNCSTATE-001, S4-STATE-002(상세), S5-LOADING-001 | S3, S4, S5 | High | error/loading/retry | `components/pages/HomePage.tsx` | 18, 36, 66 | homepage CMS fetch에 try/catch/finally/error state 없음. `setLoading(false)`가 정상 경로에만 존재. CMS data가 `any` local state (S5-FLOW-014 참고) | Supabase 호출 throw 시 home 화면 loading 고착 가능. error UI 부재 | `useHomeContent` hook에서 try/catch/finally와 typed error state, typed view model 도입 |
| RF-FINDING-022 | S4-PERSIST-001, S4-PERSIST-002, S4-PERSIST-003(연관), S5-PERSIST-001(=S5-AUTH-001) | S4, S5 | Medium | global state | `lib/auth.ts`, `lib/apiClient.ts`, `lib/supabaseClient.ts` | 66-67, 38, 21 | PFM token storage helper가 `lib/auth.ts`와 `lib/apiClient.ts`에 중복. Supabase도 sessionStorage 사용으로 경계 혼동 여지 | refresh/token persistence 정책 drift 가능. 테스트와 auth 정책 변경 영향 확대 | `authTokenStorage` adapter로 단일화. PFM token과 Supabase session storage 경계 문서화. 로그인/refresh/401 retry와 연결되므로 API client 변경과 함께 계획 |
| RF-FINDING-023 | S4-CONTEXT-001 | S4 | Low | global state | `components/LanguageProvider.tsx` | 230 | language context와 localStorage persistence가 같은 provider에 존재 | provider 변경 시 전체 consumer render 영향 확인 필요 | provider value `useMemo` 여부 확인과 persistence hook 분리 검토 |
| RF-FINDING-024 | S4-HOOK-001(=S4-DEPENDENCY-002(상세)), S4-GLOBAL-001(연관) | S4 | Low | hook | `hooks/use-toast.ts` | 131, 176 | toast listener effect dependency가 `[state]`라 state 변경마다 재구독. `listeners`/`memoryState`가 module-level mutable store | 불필요한 effect 재실행. React tree 밖 상태라 테스트/SSR 추적 어려움 | dependency `[]` 고정(mount-only subscription) 검토. shadcn 패턴과 기존 toast 테스트 확인 |
| RF-FINDING-025 | S4-HOOK-002 | S4 | Low | hook | `hooks/use-mobile.ts` | 20 | 초기 `undefined`가 `false`로 반환됨 | SSR/초기 render에서 desktop으로 잠깐 판단 가능 | tri-state 반환 또는 mounted guard 검토 |
| RF-FINDING-026 | S4-DEPENDENCY-001 | S4 | Medium | hook | `components/ResearchHighlightsSlider.tsx` | 32 | empty `highlights`에서도 autoplay interval effect가 등록될 수 있고 `handleNext`가 modulo `highlights.length` 사용 | empty array에서 interval tick 후 index가 `NaN` 가능 | effect 초기에 `highlights.length === 0` guard 추가 |
| RF-FINDING-027 | S4-QUERY-001, S2-LAYOUT-002, S5-QUERY-004 | S2, S4, S5 | Medium | server state | `app/providers.tsx` | 15-16 | QueryClient 전역 기본값이 `staleTime: 30_000`, `refetchOnWindowFocus: false`만 확인됨. admin polling/user workflow/CMS가 같은 정책 공유 | 도메인별 freshness/retry/gcTime 정책이 query 단위에 흩어지고 의도 확인 어려움 | admin/simulation/CMS별 query policy 문서화 및 민감 server state는 query별 정책 명시. retry/gcTime이 제품 요구와 맞는지 확인 필요 (S4-QUERY-005: SWR 미사용 확인) |

### 2.5 API / service

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-028 | S5-TIMEOUT-001, S5-CANCEL-001, S5-INTERCEPTOR-001, S5-RETRY-001 | S5 | Medium | API/service | `lib/apiClient.ts` | 151, 236, 278 | 공통 `doFetch`/refresh fetch에 AbortSignal/timeout 정책 없음. 401 refresh retry는 있으나 일반 network/5xx retry 정책 없음 | refresh hang 시 보호 요청 지연, loading 장기 지속 가능. unmount/long request cancellation을 caller마다 처리. transient error UX가 도메인별로 다름 | `apiRequest` 옵션에 signal/timeout 도입, refresh timeout 적용. retryable error 정책을 query/hook에 명시. 민감 영역(token refresh/error normalization)이므로 신중히 진행 |
| RF-FINDING-029 | S4-CACHE-001(요약)(=S4-CACHE-002(상세)=S5-CACHE-001(상세)), S4-MUTATION-001(요약)(=S4-CACHE-001(상세)=S5-MUTATION-001), S4-MUTATION-001(상세)(=S5-MUTATION-002), S4-INVALIDATE-001(=S5-INVALIDATE-001), S4-QUERY-003(=S5-QUERY-002=S5-REFETCH-001), S4-QUERY-004(=S5-QUERY-003), S5-CACHE-001(요약), S5-REFETCH-002(=S5-DUPREQ-001), S5-LOADING-002 | S4, S5 | High | server state | `components/pages/AdminPage3.tsx` | 510/744/729, 666/676, 697, 1080, 597, 617, 1187, 648 | admin mutation/cache/polling 정책이 container에 산재: `syncJobMutation`의 setQueryData/invalidate 직접 수행, `cancelJobMutation` invalidation fan-out, `fetchQuery` 결과를 `fieldFilesData` local state 복사, literal key와 builder key 혼재, `refetchInterval` 정책 내장, Refresh 버튼의 수동 refetch fan-out (S5-FLOW-013 참고) | cache 정책 변경 영향이 UI container에 집중. key rename 시 invalidation 누락 위험. cache/local state 불일치 가능. 중복 요청/누락 가능 | `useSyncAdminJobMutation`/`useCancelAdminJobMutation` 등 mutation hook으로 cache side effect 캡슐화. selected field/files를 enabled query로 전환. `buildAdminQueryKeys` root helper 추가. tab별 refresh hook |
| RF-FINDING-030 | S1-EXTERNAL-001, S5-ERROR-001, S5-ERROR-002, S5-VALIDATION-001, S5-CONTRACT-002, S6-VALIDATION-001 | S1, S5, S6 | High | API/service | `api/chat.js`, `lib/api/legacyAiChat.ts` | 70/80/88/103, 17/21 | legacy Gemini API route가 PFM error normalization과 별도 흐름: `{ error, details }` 비표준 envelope, `req.body.message` schema validation 부재(zod 의존성은 있으나 미적용), 실패를 `new Error('AI Server Error')`로 단순화, response를 type assertion으로 변환 | error UX/보안/로깅 정책 불일치. 잘못된 입력이 external API로 직접 전달 가능. upstream diagnostics 소실 | legacy 유지 여부 확인 후 adapter/error mapping 표준화. request schema(zod/manual guard)와 최소 response parser 추가. TS route handler 전환 검토 |
| RF-FINDING-031 | S1-EXTERNAL-002, S3-RESP-008 | S1, S3 | Low | API/service | `components/pages/ContactPage.tsx` | 13, 25 | EmailJS browser SDK를 form submit에서 직접 호출 | 외부 연동 실패 처리/계약 관리가 UI component에 분산 | `sendContactEmail` adapter/wrapper 분리와 error mapping 통일. 외부 연동 정책 문서화. env 문제는 RF-FINDING-051에서 처리 |

### 2.6 async flow / polling / error handling

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-032 | S4-RACE-001(=S4-EFFECT-001(상세)), S5-POLLING-001(=S5-RACE-001(상세)) | S4, S5 | High | polling | `components/pages/Simulation2Page.tsx` | 1605 (1599, 1611-1615) | **[P0]** job polling fallback `setInterval(async)`에 in-flight guard 없음. tick마다 `getJob`→`listJobEvents`→`listSimulationResults` 순차 실행되어 interval 주기보다 요청이 길면 겹침 (S5-FLOW-005 참고) | job status/events/result availability 중복 호출과 상태 순서 꼬임 가능 | single-flight polling loop 또는 `pollingInFlightRef` 도입. WS fallback/terminal status/result availability와 함께 테스트 |
| RF-FINDING-033 | S2-ASYNC-001, S2-ASYNC-002 | S2 | Medium | async flow | `components/pages/Simulation2Page.tsx` | 1674, 1949 | job monitor WebSocket(polling fallback/reconnect 포함)과 visualization WebSocket/sync interval 관리가 container 내부에 존재 | race condition/cleanup 회귀 위험. viz 상태 동기화 회귀 위험 | `useJobMonitorSession`/`useVisualizationSession`으로 lifecycle 격리 (RF-FINDING-001 분해의 핵심 축) (추론) |
| RF-FINDING-034 | S5-POLLING-002 | S5 | Medium | polling | `components/pages/PFMSimulationPage.tsx` | 468 | legacy simulation job polling(`setInterval(async)`)에도 in-flight guard 없음 | legacy 흐름에서 중복 요청 가능 | 유지 대상이면 guard 추가, 아니면 제거/격리 (legacy 유지 여부 확인 필요) |
| RF-FINDING-035 | S5-ERROR-003 | S5 | Medium | error/loading/retry | `components/pages/Simulation2Page.tsx` | 1469 | job polling `getJob` 실패를 삼키고 null 반환 | 반복 실패/인증 실패를 사용자에게 알리기 어려움 | 연속 실패 카운트와 inline notice 정책 도입 |
| RF-FINDING-036 | S5-ROLLBACK-001 | S5 | High | error/loading/retry | `components/pages/EditNoticePage.tsx` | 107 | **[P0]** attachment storage remove/upload 후 DB update 실패 시 보상(rollback) 처리 없음 (S5-FLOW-016 참고) | 파일과 DB attachment 불일치 가능 | attachment adapter/use-case와 실패 보상 정책 도입. 실제 storage path/URL parsing 확인 필요 |
| RF-FINDING-037 | S5-ERROR-004 | S5 | Medium | error/loading/retry | `components/pages/NoticeBoardPage.tsx` | 98 | pin/delete mutation의 실패 처리와 사용자 피드백 제한적 | 실패해도 사용자가 원인 파악 어려움 | mutation hook + toast/error state (RF-FINDING-003 service 분리와 함께) |

### 2.7 type safety / DTO·API contract

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-038 | S1-TYPE-001, S6-TYPE-001 | S1, S6 | High | type safety | `tsconfig.json` | 8, 10, 29, 32 | `allowJs: true`, `strict: false`, `noImplicitAny: false`, `strictNullChecks: false`로 핵심 타입 안전장치가 꺼져 있음 | any/null/API 계약 위반이 컴파일 타임에 걸러지지 않음 | 신규/리팩토링 영역부터 strict-friendly 타입 작성 후 옵션 단계적 강화. 단번에 전체 strict 전환 금지. strict 시 오류량 측정 필요(확인 필요) |
| RF-FINDING-039 | S1-DEPENDENCY-001, S5-DTO-001, S6-DTO-001, S6-DUPTYPE-001, S6-DUPTYPE-002, S6-DUPTYPE-003, S6-DUPTYPE-004, S6-DUPTYPE-005, S6-ENUM-001(상세 type-duplication-review), S6-MAPPER-001(상세 validation-formatting-review) | S1, S5, S6 | High | DTO/API contract | `lib/api/admin.ts`, `lib/api/simulations.ts`, `lib/api/jobs.ts`, `lib/api/results.ts`, `lib/api/visualizations.ts`, `components/pages/simulation2/workflowTypes.ts` | admin.ts 36/37/40/149-308/391, simulations.ts 16/24-42/136-142, jobs.ts 4/16-35, results.ts 14-51, visualizations.ts 11, workflowTypes.ts 4/5/22-36 | `SimulationStatus`/`JobStatus`/`VisualizationStatus` status union과 `Composition`/`JobSummary·Detail·Event`/`ResultSummary·Detail·FieldsResponse` 계열 DTO가 일반 API·admin API·workflow에 중복 정의. admin API 파일에 simulation/job/result/viz DTO·wrapper 공존 | backend contract 변경 시 일부 계층만 갱신되는 drift 위험. admin/job/result 화면 상태 불일치 가능 | API status/DTO를 shared module로 단일화하고 workflow stage는 mapper로 파생. shared DTO + admin 확장 type 구조. admin API가 의도적으로 다른 계약인지 백엔드 명세 확인 필요 |
| RF-FINDING-040 | S1-TYPE-002, S5-TYPE-001, S6-ANY-003, S6-ASSERT-001, S6-ASSERT-002 | S1, S5, S6 | Medium | type safety | `lib/apiClient.ts`, `lib/api/labserverTrameClient.ts` | 380, 395, 500-502 | `apiRequest<T = any>` 기본 generic이 `any`이고 `JSON.parse(text) as T`/`response.json() as Promise<T>`로 런타임 검증 없이 단정 | 타입 명시 누락이 조용히 확산. 응답 shape 불일치가 UI 렌더 시점까지 늦게 발견 | call site 타입 명시 강화 우선, 장기적으로 기본값 `unknown` 검토(전체 call site 영향 큼 — 단계 적용). 핵심 endpoint부터 parser/guard/schema 도입 |
| RF-FINDING-041 | S1-TYPE-003, S6-ANY-001 | S1, S6 | High | type safety | `components/pages/simulation2/workflowTypes.ts` | 72 | `WorkflowState.parameters`가 `Record<string, any>` | simulation parameter 계약 변경이 컴파일 단계에서 드러나지 않음 | `SimulationParametersDto`/`WorkflowParameters`/`EditableSimulationParameters` 분리. workflow 리팩토링과 함께 진행 |
| RF-FINDING-042 | S6-ANY-002, S5-CONTRACT-001, S6-VALIDATION-002 | S5, S6 | High | DTO/API contract | `components/pages/Simulation2Page.tsx` | 2378 | API PATCH body를 page component에서 `Record<string, any>`로 직접 조립 | form/view state와 API request DTO 결합. form validation과 API 계약 분리 안 됨 | `buildUpdateSimulationBody(formState): UpdateSimulationBody` DTO builder/mapper와 schema/guard 분리. job submit/update/restore 흐름과 함께 테스트 |
| RF-FINDING-043 | S6-ASSERT-003(=S6-PARSER-002) | S6 | Medium | type safety | `components/pages/Simulation2Page.tsx` | 414-422, 417 | `extractWarnings`가 warning payload 추출 시 `(obj.details as any)?.warnings`로 구조 우회 | error details shape 변경 시 warning 누락 또는 잘못된 표시 가능 | `isRecord(details)`/`isWarningPayload` guard 기반 narrowing으로 대체 |
| RF-FINDING-044 | S3-CMS-003(=S3-QUAL-003), S5-TYPE-002, S6-CMS-001(=S6-TYPE-002+S6-TYPE-003), S6-PROPS-001 | S3, S5, S6 | Medium | DTO/API contract | `components/pages/HomePage.tsx`, `components/pages/EditPageContentForm.tsx`, `components/ResearchPageTemplate.tsx`, `components/pages/EditHomePageForm.tsx`, `components/pages/introduction/Section2_CoreCapabilites.tsx`, `Section3_ResearchAreas.tsx` | 18/21/22, 21/24/62-64, 53, 181, 8, 10 | CMS content state/접근이 `any`·`Record<string, any>`·field string 기반 (HomePage state는 RF-FINDING-021과 연관) | CMS schema drift와 form field 오타를 컴파일 타임에 감지 불가. public page와 edit form의 content 구조 불일치 가능 | CMS DTO/view model/form model(pageKey별 또는 discriminated union)과 localized getter 타입화. CMS 데이터 shape 확인 필요 |
| RF-FINDING-045 | S6-MAPPER-001(상세 util-responsibility-review) | S6 | Medium | DTO/API contract | `components/pages/simulation2/workflowMappers.ts` | 38, 53, 63 | `getJobStatusHint`/`getJobMonitorStatusHint`가 raw websocket payload를 `Record<string, unknown>`로 다루며 schema/명시적 DTO 없음 | websocket message contract 변경 시 일부 필드 누락이 조용히 fallback될 수 있음 | `JobMonitorMessageDto` union과 parser result 정의 |
| RF-FINDING-046 | S6-ANY-004 | S6 | Low | type safety | `components/reactbits/ColorBends.tsx` | 180 | Three 버전 호환을 위한 `as any` 사용 | 라이브러리 API 변경을 타입이 보호하지 못함 | wrapper type 또는 지원 버전 확인 후 좁은 assertion으로 제한 |

### 2.8 util / config / constant / validation / import

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-047 | S6-UTIL-001, S6-PARSER-001, S6-CONST-001 | S6 | High | util/helper | `components/pages/Simulation2Page.tsx` | 221-534 (221, 313-325, 414, 438, 499, 534), 545-547 | `normalizeComposition`/`formatAssistantContent`(parser+formatter 겸직)/`extractWarnings`/`computeExpectedProcessCount`/`toWorkflowErrorDetails`/`saveBlobDownload` 등 pure helper와 polling/reconnect constant가 page 파일에 집중 | page 변경과 domain/API 변환 변경이 결합. 단위 테스트 어려움. hook 분리 시 설정이 page와 함께 이동 | `simulation2` 하위 `workflowMapper`/`parameterMapper`/`errorMapper`/`downloadUtil` 모듈과 workflow config constant로 분리. 행동 변경 없이 pure function부터 이동 후 테스트 추가 |
| RF-FINDING-048 | S6-FORMATTER-001(=S6-FORMAT-001) | S6 | Medium | util/helper | `components/pages/AdminPage3.tsx` | 262-339 | `formatDate`/`formatUnknown`/`formatBytes`/form numeric parser/`saveBlobDownload` 등 표시 formatter·file util이 admin page에 집중 | admin tab 분리 시 중복 복사 또는 대형 파일 유지 발생 | admin view util 또는 common formatter로 이동 후 테스트 |
| RF-FINDING-049 | S6-VALIDATOR-001(=S6-PARSER-003) | S6 | Medium | validation/formatter | `components/pages/PFMSimulationPage.tsx` | 158, 196-219 | legacy `validateParams`/`parseLLMResponse`(LLM 응답 JSON block 직접 추출)가 page 내부에 존재 | form UI와 LLM/domain parser 변경 결합. LLM 응답 format 변경 시 page 내부 로직 파손 | legacy simulation parser/validator 모듈로 분리하고 parse result 명시 |
| RF-FINDING-050 | S3-DUP-001(=S3-QUAL-007), S3-QUAL-011 | S3 | Medium | util/helper | `components/pages/AdminPage.tsx`, `EditNoticePage.tsx`, `EditGalleryPage.tsx`, `components/simulation/ResultExplorerPanel.tsx` | 43, 14, 13, 197 | `sanitizeForStorage` file name sanitizer와 blob download helper가 여러 파일에 중복(`Simulation2Page`/`AdminPage3`에도 유사 helper 존재) | 파일명/다운로드 정책 변경 시 중복 수정 누락 가능 | `lib/storage/filename` 또는 공통 storage/download util로 이동. 파일명 정책이 backend/storage 정책과 충돌하지 않는지 확인 |
| RF-FINDING-051 | S6-ENV-001(=S6-NULLABLE-001), S6-ENV-002(=S6-NULLABLE-002) | S6 | High | config/env/constant | `lib/supabaseClient.ts`, `components/pages/ContactPage.tsx` | 5-6, 26-29 | **[P0]** Supabase·EmailJS public env를 non-null assertion(`!`)으로 직접 사용 | 배포 누락 시 불명확한 초기화 실패 또는 사용자 submit 시점 runtime failure | `getRequiredPublicEnv` helper와 integration별 config module(`lib/config/emailjs.ts` 등) + disabled/error fallback 도입. env 이름/배포 설정 확인 후 적용 |
| RF-FINDING-052 | S6-CONFIG-001 | S6 | Medium | config/env/constant | `lib/apiClient.ts` | 217-231 | `NEXT_PUBLIC_PFM_API_URL`/`NEXT_PUBLIC_PFM_LLM_URL` fallback을 쓰지만 required error는 `NEXT_PUBLIC_PFM_API_URL`만 안내 | 실제 사용 env와 안내 env가 어긋날 수 있음 | canonical env를 하나로 정하고 legacy fallback은 주석/문서로 명시 |
| RF-FINDING-053 | S6-CONFIG-002 | S6 | Medium | config/env/constant | `next.config.ts` | 4-8 | `images.remotePatterns`가 모든 HTTPS host 허용 | 이미지 출처 정책 관리 약화, 운영 환경별 허용 도메인 추적 어려움 | 실제 CMS/CDN 도메인 allowlist로 제한. 전체 host 허용이 CMS 요구사항인지 확인 필요 |
| RF-FINDING-054 | S6-CONFIG-003 | S6 | Low | config/env/constant | `next.config.ts` | 14-19 | `experimental` 주석과 `CDN_IMG_PREFIX` env 설명에 인코딩 깨짐 | 설정 의도 파악 어려움 | 주석을 UTF-8 한국어 또는 영어로 정리 |
| RF-FINDING-055 | S6-ENUM-001(요약)(=S6-CONST-002(상세)), S6-MAGIC-001, S6-MAGIC-002 | S6 | Low | config/env/constant | `components/simulation/VisualizationControlBar.tsx`, `trame/TrameControlPanel.tsx`, `trame/CompositeDialog.tsx`, `SessionListCard.tsx`, `SimulationListCard.tsx`, `components/pages/adminPolling.ts` | 29, 33, 37, 44, 28-30, 4 | colormap 옵션이 여러 파일에 하드코딩, page size/fetch size·admin polling interval(10초)이 component/helper 내부 constant | 옵션/목록 정책 변경 시 파일별 drift 가능. 운영 정책 변경 시 재빌드 필요(adminPolling은 pure helper라 영향 낮음) | 공통 옵션이면 shared constant, 도메인별이면 이름으로 구분. feature config 또는 props 기본값으로 분리. 실제로 도메인별 옵션이 다른지 확인 |
| RF-FINDING-056 | S6-FORMAT-002 | S6 | Low | validation/formatter | `lib/utils.ts` | 10 | 공통 `formatRelativeTime`이 한국어 고정 | 다국어 UI에서 locale 정책과 어긋날 수 있음 | locale 인자 또는 i18n boundary와 연결. 확인 필요 |
| RF-FINDING-057 | S6-UTIL-002 | S6 | Low | util/helper | `lib/api/http.ts` | 12, 90, 103 | `withQuery` 등 API boundary helper의 `params: object` 인자가 넓음 | query param 직렬화 대상이 컴파일에서 제한되지 않음 | `QueryParams` 타입을 함수 인자에 일관 적용 |
| RF-FINDING-058 | S6-DEAD-001, S6-DEAD-002 | S6 | Medium | config/env/constant | `tsconfig.json` | 30-31, 8 | `noUnusedParameters`/`noUnusedLocals` 꺼짐, `allowJs`로 `api/chat.js` 등 JS 파일의 검출 약함 | 미사용 type/util 누적 가능. TS 기반 import/export 정리 비일관 | 신규 리팩토링 영역부터 unused check 또는 lint rule 보완. API route TS 전환 또는 JS 별도 lint/typecheck 정책 |
| RF-FINDING-059 | S6-EXPORT-001 | S6 | Low | import/export | `lib/api/admin.ts` | 31 | admin API 파일이 `getFilenameFromContentDisposition`을 `./http`에서 재-export | admin API boundary 역할이 helper export까지 확대 | 호출부가 `lib/api/http`에서 직접 import하도록 정리 검토 |
| RF-FINDING-060 | S6-BARREL-001, S6-CYCLE-001 | S6 | Suggestion | import/export | 전체 | 확인 필요 | barrel export 과다·circular import 여부를 정적 `rg`만으로 확정하지 않음 | 순환 import 존재 시 런타임 초기화 순서 문제 가능 | madge/dependency-cruiser 등 도구로 별도 검증 (확인 필요) |

---

## 3. 병합 기록

### 3.1 사전 식별 병합 클러스터 (15종)

| 클러스터 | RF ID | 병합된 원본 ID (출처 문서) | 병합 사유 | 심각도 처리 |
|---|---|---|---|---|
| 1. Simulation2Page 거대 컨테이너 | RF-FINDING-001 | S1-ARCH-001(s1-findings), S1-STRUCT-001(s1-findings, 연관), S2-CONTAINER-001(s2-findings/page-container-review), S3-COMP-001=S3-QUAL-001(s3-findings/component-responsibility/quality), S4-STATE-001(s4-findings/server-client-state), S5-SERVICE-001(요약)=S5-APIARCH-001(s5-findings/api-layer-architecture), S6-IMPORT-001(dead-code-and-import) | 동일 파일(3347 lines)의 책임 집중을 6개 세션이 각자 관점에서 지적. 각 세션 findings의 "이전 세션 연결 요약"이 같은 근본 원인임을 명시 | 전 세션 High → High |
| 2. AdminPage3 거대 컨테이너 | RF-FINDING-002 | S1-ARCH-004, S2-CONTAINER-002, S3-COMP-002=S3-QUAL-002, S3-PERF-003(rendering-performance), S3-QUAL-009(component-quality, inline dialog), S4-STATE-002(요약), S4-QUERY-002=S5-QUERY-001(query-cache/query-mutation), S4-CLIENT-001(server-client-state), S5-SERVICE-002(요약)=S5-APIARCH-002, S6-IMPORT-002 | 동일 파일(2942 lines)의 query/mutation/URL state/dialog/table 집중. S3-PERF-003·S3-QUAL-009·S4-CLIENT-001은 같은 근본 원인(거대 컨테이너)의 렌더링/dialog/form state 측면 (추론: 클러스터 정의에 추가 병합) | S1은 Medium, S2~S5 High → High |
| 3. job polling in-flight guard 부재 (P0) | RF-FINDING-032 | S4-RACE-001=S4-EFFECT-001(상세 effect-dependency :1599), S5-POLLING-001=S5-RACE-001(상세 request-safety) | `Simulation2Page.tsx:1605` 동일 지점을 S4(race), S5(polling/request safety)가 중복 지적 | High → High |
| 4. list/catalog stale response guard 부재 | RF-FINDING-016~019 (파일별 분리) | ResultExplorerPanel: S3-STATE-001=S3-RESP-001, S4-SERVER-004, S4-STALE-001/002(=S4-EFFECT-002/003), S5-STALE-002 / JobResultListCard: S3-STATE-003=S3-RESP-003, S4-SERVER-001, S4-RACE-002, S4-CLEANUP-001, S5-STALE-001 / SimulationListCard: S3-RESP-004, S4-SERVER-002, S4-RACE-003, S4-CLEANUP-002, S3-PERF-004(연관) / SessionListCard: S3-STATE-002=S3-RESP-002, S4-SERVER-003, S4-RACE-004, S4-CLEANUP-003 | 같은 근본 원인(local server state + stale guard 부재)이나 파일·수정 단위가 달라 4개 RF로 분리 (추론). S4-EFFECT-002/003은 S4-STALE-001/002와 같은 지점(:392, :411)의 effect 관점 표기 | 전부 Medium → Medium |
| 5. CMS/Supabase 직접 호출 | RF-FINDING-003 | S1-ARCH-002/003, S2-DEPENDENCY-001/002/003(=S2-CONTAINER-006/007), S2-CONTAINER-005, S3-CMS-001/002(=S3-RESP-007/006), S3-RESP-005, S4-SERVER-005/006, S5-SERVICE-003(요약)=S5-SERVICE-001(상세), S5-SERVICE-002(상세) | UI-persistence 결합이라는 같은 근본 원인을 page/container/component/state/service 관점에서 반복 지적. S3-RESP-005(ResearchPageTemplate)·S4-SERVER-005/006도 동일 패턴 (추론: 클러스터 정의에 추가 병합) | S1/S5 High, S2/S3/S4 Medium → High |
| 6. token storage 중복 | RF-FINDING-022 | S4-PERSIST-001/002(s4-findings/store-and-context), S4-PERSIST-003(연관), S5-PERSIST-001=S5-AUTH-001(s5-findings/api-layer-architecture) | `lib/auth.ts`·`lib/apiClient.ts` token helper 중복이라는 동일 원인. S4-PERSIST-003(Supabase storage 경계)은 같은 경계 문서화 이슈로 연관 병합 (추론) | Medium → Medium |
| 7. status/DTO 타입 중복 | RF-FINDING-039 | S1-DEPENDENCY-001, S5-DTO-001, S6-DTO-001, S6-DUPTYPE-001~005, S6-ENUM-001(상세 type-duplication), S6-MAPPER-001(상세 validation-formatting) | admin/일반 API/workflow 간 status union·DTO 중복이라는 동일 원인. composition mapper drift(S6-MAPPER-001 validation-formatting)도 같은 DTO 중복의 mapper 측면 (추론) | S6-DUPTYPE-001/002 High → High |
| 8a. strict 옵션 off | RF-FINDING-038 | S1-TYPE-001, S6-TYPE-001 | `tsconfig.json` 동일 지점 | S1 Medium, S6 High → High |
| 8b. apiRequest any/assertion | RF-FINDING-040 | S1-TYPE-002, S5-TYPE-001, S6-ANY-003, S6-ASSERT-001, S6-ASSERT-002 | `apiRequest<T = any>`·`as T` 단정이라는 동일 원인. S6-ASSERT-002(labserverTrameClient)는 동일 패턴의 다른 파일 발생으로 함께 병합 (추론) | Medium → Medium |
| 8c. workflow parameters any | RF-FINDING-041 | S1-TYPE-003, S6-ANY-001 | `workflowTypes.ts:72` 동일 지점 | S1 Medium, S6 High → High |
| 8d. PATCH body any | RF-FINDING-042 | S6-ANY-002, S5-CONTRACT-001, S6-VALIDATION-002 | `Simulation2Page.tsx:2378` 동일 지점 | S6-ANY-002/S6-VALIDATION-002 High → High |
| 9. env non-null assertion (P0) | RF-FINDING-051 | S6-ENV-001=S6-NULLABLE-001, S6-ENV-002=S6-NULLABLE-002 | config-constant-env-review와 type-safety-review가 같은 지점에 다른 ID 부여 | High → High |
| 10. HomePage fetch error 처리 부재 | RF-FINDING-021 | S3-QUALITY-002=S3-QUAL-006, S4-EFFECT-001(요약)=S4-EFFECT-004(상세), S4-ASYNCSTATE-001, S4-STATE-002(상세 server-client-state), S5-LOADING-001 | `HomePage.tsx:36` 동일 지점. S4-STATE-002(상세)는 같은 fetch의 `any` state 측면(타입 측면은 RF-FINDING-044와 연관) | S3/S4 Medium, S5-LOADING-001 High → High |
| 11. helper 중복(sanitize/download) | RF-FINDING-050 | S3-DUP-001=S3-QUAL-007, S3-QUAL-011 | `sanitizeForStorage`·blob download helper 중복이라는 동일 원인 | Medium → Medium |
| 12. legacy /api/chat 비표준 envelope | RF-FINDING-030 | S1-EXTERNAL-001, S5-ERROR-001, S5-ERROR-002, S5-VALIDATION-001, S5-CONTRACT-002, S6-VALIDATION-001 | legacy Gemini chat 흐름(route+client)의 error/validation/contract 문제를 통합. S5-ERROR-002/S5-CONTRACT-002(legacyAiChat.ts)는 같은 흐름의 client 측 | S6-VALIDATION-001이 validation-formatting-review에서 High(요약표는 Medium) → High |
| 13. AdminPage3 URL NaN parser (P0) | RF-FINDING-061 (아래 2.9 추가 표) | S4-URL-001, S2-STATE-001, S4-EFFECT-005(연관) | `AdminPage3.tsx:489-498` URL query parsing/correction 동일 지점. S4-EFFECT-005는 correction effect 분산이라는 연관 이슈 | S4-URL-001 High → High |
| 14. AdminPage3 mutation/cache side effect | RF-FINDING-029 | S4-CACHE-001(요약)=S4-CACHE-002(상세)=S5-CACHE-001(상세), S4-MUTATION-001(요약)=S4-CACHE-001(상세)=S5-MUTATION-001, S4-MUTATION-001(상세)=S5-MUTATION-002, S4-INVALIDATE-001=S5-INVALIDATE-001, S4-QUERY-003=S5-QUERY-002=S5-REFETCH-001, S4-QUERY-004=S5-QUERY-003, S5-CACHE-001(요약), S5-REFETCH-002=S5-DUPREQ-001, S5-LOADING-002 | admin query cache/mutation/polling 정책이 container에 노출된 동일 원인. ID 표기 충돌은 1.4 참조 | S5-MUTATION-001 High → High |
| 15. auth guard 분산 | RF-FINDING-005 | S2-GUARD-001~004, S2-PAGE-001(요약)=S2-PAGE-003(상세), S2-PAGE-001/002/004/005/006/007(상세), S2-ROUTE-001 | route-level 인증/redirect/guard 정책 분산이라는 동일 원인(PFM gate, legacy admin gate, edit route guard, fallback 중복 하위 그룹 포함) (추론: S2-ROUTE-001·상세 PAGE 항목 추가 병합) | 전부 Medium/Low → Medium |

### 3.2 클러스터 13 누락 보정 (RF-FINDING-061)

클러스터 13(AdminPage3 URL NaN parser)은 영역 정렬상 hook/state에 속하나 메인 표 작성 시 번호가 뒤로 밀렸다. 아래 행을 메인 표의 일부로 간주한다. (추론: 채번 순서만의 문제이며 내용 누락 아님)

| 통합 ID | 원본 ID | 출처 세션 | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 개선 방향 |
|---|---|---|---|---|---|---|---|---|---|
| RF-FINDING-061 | S4-URL-001, S2-STATE-001, S4-EFFECT-005(연관) | S2, S4 | High | local state | `components/pages/AdminPage3.tsx` | 498, 489, 918 | **[P0]** `Number(searchParams...)` 결과가 `NaN`일 수 있고, URL parsing/correction/selected entity reset/404 cleanup effect가 container에 분산 | invalid query에서 page/size와 query key 불안정. URL state 변경이 query/mutation 흐름에 직접 영향 | safe integer parser와 `useAdminUrlState` correction hook 도입. 기존 deep link/query 호환성 보존 |

### 3.3 추가 병합 (세션 내 요약=상세 alias)

위 클러스터 외 세션 내 alias 병합: S3-ACCESS-001=S3-QUAL-004(RF-011), S3-QUALITY-001=S3-QUAL-005(RF-006), S3-BOUNDARY-001=S3-QUAL-012(좋은 패턴), S3-RENDER-001(요약)=S3-PERF-001(RF-014), S3-RENDER-002(요약)=S3-RENDER-001(상세)(RF-014), S4-HOOK-001=S4-DEPENDENCY-002(상세)(RF-024), S5-POLLING-003≈S5-TIMEOUT-002(좋은 패턴, 동일 파일 `labserverTrameClient.ts:466`), S6-FORMATTER-001=S6-FORMAT-001(RF-048), S6-VALIDATOR-001=S6-PARSER-003(RF-049), S6-ENUM-001(요약)=S6-CONST-002(RF-055), S6-CMS-001=S6-TYPE-002+S6-TYPE-003(RF-044).

---

## 4. 세션 간 충돌 표

| 충돌 ID | 관련 세션 | 대상 파일 | 충돌 내용 | 우선 판단 | 이유 |
|---|---|---|---|---|---|
| (없음) | S1~S6 전체 | - | **명시적 판단 충돌 없음, 관점 보완 관계.** 근거: session2~session5 findings의 "이전 세션 연결 요약"이 Simulation2Page/AdminPage3/CMS 직접 의존 이슈를 "같은 근본 원인"의 관점별 구체화로 명시(예: s4-findings "S4-STATE-001은 S1-ARCH-001, S2-CONTAINER-001, S3-COMP-001의 hook/state 계층 근거", s5-findings "S5-SERVICE-001은 S1/S2/S3/S4와 같은 문제의 API/service 관점"). 서로 다른 개선 방향을 주장한 사례 없음 | - | 충돌 발생 시 우선 판단 기준: **라인 단위의 구체적 지적 > 개괄 지적** |
| SEV-DIFF-01 | S1 vs S2/S3/S4/S5 | `components/pages/AdminPage3.tsx` | 심각도 표기 차이: S1-ARCH-004=Medium, S2-CONTAINER-002/S3-COMP-002/S4-STATE-002(요약)/S5-SERVICE-002(요약)=High | High 채택 (RF-FINDING-002) | 후속 세션이 라인 단위로 구체화했고 최고 심각도 채택 원칙 적용 |
| SEV-DIFF-02 | S3/S4 vs S5 | `components/pages/HomePage.tsx` | S3-QUAL-006/S4-EFFECT-001(요약)=Medium, S5-LOADING-001=High | High 채택 (RF-FINDING-021) | 최고 심각도 채택 원칙 |
| SEV-DIFF-03 | S6 내부 (요약 vs 상세) | `api/chat.js`, `package.json` | S6-VALIDATION-001: 요약표 Medium, validation-formatting-review High | High 채택 (RF-FINDING-030) | 상세 문서가 라인 단위 구체 지적 + 최고 심각도 채택 |
| SEV-DIFF-04 | S6 내부 (요약 vs 상세) | `components/pages/Simulation2Page.tsx` | S6-UTIL-001: 요약표 Medium, util-responsibility-review High | High 채택 (RF-FINDING-047) | 상세 문서가 라인 단위 구체 지적 + 최고 심각도 채택 |
| SEV-DIFF-05 | S1 vs S6 | `tsconfig.json` | S1-TYPE-001=Medium, S6-TYPE-001=High | High 채택 (RF-FINDING-038) | S6이 옵션별(8, 10, 29, 32) 구체 지적 |
| SEV-DIFF-06 | S4 vs S5 | `components/pages/AdminPage3.tsx` | mutation cache side effect: S4 계열 Medium, S5-MUTATION-001=High | High 채택 (RF-FINDING-029) | 최고 심각도 채택 원칙 |

---

## 5. 원본 "확인 필요" 항목 (세션별, 그대로 보존)

### Session 1 (session1-findings.md)
- Supabase RLS/권한 정책과 현재 UI 직접 호출 구조가 의도된 설계인지 확인 필요.
- legacy AI assistant와 `api/chat.js`가 현재 제품 범위에 포함되는지 확인 필요.
- `store` 디렉터리 부재가 의도된 상태 관리 전략인지 확인 필요.
- route guard 정책은 Session 2에서 page/layout 계층 중심으로 추가 검토 필요.

### Session 2 (session2-findings.md, routing-review.md)
- edit route 접근 제어가 Supabase RLS와 UI에서 어떻게 보장되는지 확인 필요.
- global `error.tsx` 부재가 의도인지 확인 필요.
- route group layout 부재(미사용)가 의도인지 확인 필요.
- (routing-review) edit route 접근 제어가 Supabase RLS로 충분히 보호되는지 확인 필요.
- (routing-review) Next.js global error boundary 부재가 의도된 것인지 확인 필요.
- (routing-review) `middleware.ts`는 Session 2에서 확인되지 않았다. 보호 route를 middleware로 처리하지 않는 것이 의도인지 확인 필요.

### Session 3 (session3-findings.md, component-quality-review.md)
- CMS HTML content sanitize 정책.
- legacy admin/workbench component 유지 여부.
- Trame advanced panel 하위 component의 API/service 경계.
- (quality) HTML content가 모두 관리자 trusted input인지, sanitizer가 저장 시점에 적용되는지 확인 필요.
- (quality) legacy admin/editor component가 현재 운영 범위인지 확인 필요.

### Session 4 (session4-findings.md, effect-dependency-review.md, query-cache-review.md)
- product 요구사항상 어떤 데이터가 항상 fresh 해야 하는지, 어떤 데이터는 cache-only가 허용되는지 확인 필요.
- Supabase CMS 영역의 server state를 React Query로 통합할지, domain-specific hook으로만 분리할지 결정 필요.
- (effect-dep) `Simulation2Page`의 WebSocket cleanup 자체는 여러 ref와 cleanup helper로 구성되어 있으나, 전체 lifecycle이 한 component에 있어 분리 전 재검증 필요.
- (effect-dep) CMS page들의 Supabase query는 route/page 권한 정책과 함께 Session 5에서 API/service 관점 추가 확인 필요.
- (query-cache) React Query retry/gcTime 정책은 기본값 사용 여부만 확인했으며, product freshness 요구사항과 맞는지는 확인 필요.

### Session 5 (query-mutation-review.md, error-loading-retry-review.md, api-type-contract-review.md)
- React Query retry/gcTime 기본값이 제품 요구사항과 맞는지 확인 필요. SWR 사용은 확인되지 않았다.
- backend error envelope가 모든 PFM endpoint에서 동일한지, legacy `/api/chat` 유지 여부 확인 필요.
- backend OpenAPI/계약에서 nullable/optional field와 frontend DTO가 모두 일치하는지는 Session 6에서 추가 확인 필요.
- (endpoint-map) `logout` response type 확인 필요. job monitor WS message 구조 확인 필요.

### Session 6 (session6-findings.md, 세부 리뷰 5종)
- admin DTO와 일반 DTO가 의도적으로 다른 계약인지 백엔드 API 명세 확인 필요.
- 실제 dead code/circular import 여부는 별도 도구 실행 필요.
- (type-safety) `strict` 옵션을 켰을 때 실제 오류량과 우선순위는 별도 타입체크 실행이 필요하다.
- (type-safety) `apiRequest<T>` 기본값 변경은 전체 call site 영향이 크므로 단계적 적용 계획이 필요하다.
- (type-duplication) CMS content는 pageKey별 자유 schema를 의도했을 수 있으므로, 완전한 공통화 전에 CMS 데이터 구조 확인이 필요하다.
- (config) `NEXT_PUBLIC_LAB_SERVER_API_KEY`, `NEXT_PUBLIC_PFM_AUTH_TOKEN`은 테스트 파일에서 확인되지만 실제 runtime client에서 사용하는지는 추가 확인 필요하다.
- (config) 이미지 remote pattern을 모든 host로 둔 것이 CMS 요구사항인지 확인 필요하다.
- (validation) 실제 form validation 정책이 별도 백엔드 422에만 의존하는 설계인지 확인 필요하다.
- (validation) `zod`가 사용되지 않는다는 결론은 정적 검색 기준이다. 생성 코드나 외부 패키지 사용은 확인하지 않았다.
- (dead-code) 실제 unused export/type 목록은 `tsc --noUnusedLocals`, ESLint, dependency analyzer 실행이 필요하다.

---

## 6. 좋은 패턴 (리팩토링 시 기준 패턴으로 활용)

| 원본 ID | 파일 경로 | 라인 | 내용 | 활용 방안 |
|---|---|---|---|---|
| S4-ABORT-001 (=S5-INTERVAL-001) | `components/pages/Simulation2Page.tsx` | 2057, 2100 | visualization sync에 `visualizationSyncInFlightRef`와 sequence guard 존재 (S5-FLOW-007 참고) | RF-FINDING-032(P0 job polling guard)와 RF-FINDING-016~019의 기준 패턴 |
| S5-CANCEL-002 | `components/simulation/trame/TrameExportCenter.tsx` | 147-149 | export polling에 AbortController cleanup 존재 (S5-FLOW-017 참고) | PFM polling 리팩토링에 패턴 이전 |
| S5-TIMEOUT-002 (=S5-POLLING-003) | `lib/api/labserverTrameClient.ts` | 466-484 | timeout과 AbortSignal을 갖춘 polling loop | 일반 request timeout(RF-FINDING-028) 설계 기준 |
| S4-CLEANUP-004 | `hooks/useIdleTimer.ts` | 15 | listener/timeout cleanup과 dependency가 명시됨 | effect cleanup 기준 패턴으로 유지 |
| S3-BOUNDARY-001 (=S3-QUAL-012) | `components/common/ApiErrorNotice.tsx` | 19 | normalized error view model 기반 error presenter, 경계 명확 | page-local error UI를 같은 패턴으로 통일 |
| S3-COMP-003 | `components/simulation/VisualizationControlBar.tsx` | 76, 91 | 사용자 intent만 callback으로 전달, API 호출 없음. timestep local draft만 보유 | presenter 책임 경계의 기준. parent API 호출과 섞지 않도록 보호 |
| S5-ERRORNORM-001 | `lib/api/errors.ts` | 255 | normalized error model과 redaction 존재 | legacy/CMS error에도 확장 검토 |
| S5-ENDPOINT-001 (api-layer-architecture-review) | `lib/apiClient.ts` | 192 | PFM base URL/path 조합 공통화 | 유지 |
| S5-CONTRACT-003 | `lib/api/results.ts` | 120 | field files query building을 helper가 담당하는 긍정적 경계 | 유지. request safety는 caller에서 보강 |
| S5-FALLBACK-001 | `components/simulation/trame/TrameViewer.tsx` | 30 | `resolveViewerMode` 실패 시 PNG fallback 처리 | PFM visualization failure UX 기준 |
| S2-BOUNDARY-003 | `app/viewer/page.tsx` | 6 | dynamic import로 SSR 회피 명시 | 긍정 사례. 단 fallback/error UX는 확인 필요 |
| S4-CONTEXT-002 | `components/ui/sidebar.tsx` | 117 | context value를 `useMemo`로 생성 | UI primitive로 유지 |
| S4-STORE-001 | `package.json` | 1 | zustand/redux/swr 미사용 — 전역 store 남용 없음 (관찰) | store 도입보다 query/hook/service 경계 정리 우선. 필요성이 명확할 때만 도입 검토 |
| S6-FORMATTER-002 | `lib/api/errors.ts` | 62, 75, 255, 283 | error normalization과 표시 constant의 응집도가 현재 높음 | user-facing message catalog와 raw normalization 경계 유지 |

---

## 7. 통계 요약

### 7.1 세션별 raw 건수 → 통합 결과

| 세션 | raw ID 문자열 수(인벤토리 제외) | 비고 |
|---|---:|---|
| Session 1 (전체구조/아키텍처) | 12 | |
| Session 2 (route/page/container) | 33 | 요약↔상세 alias 다수 포함 |
| Session 3 (component) | 43 | S3-QUAL-*가 대부분 타 ID와 alias |
| Session 4 (hook/state) | 49 | |
| Session 5 (API/service/async) | 50 | 인벤토리 S5-ENDPOINT-002~040(39건), S5-FLOW-001~017(17건) 별도 제외 |
| Session 6 (type/util/config) | 50 | S6-ENUM-001/S6-MAPPER-001은 1개 문자열이 2개 이슈를 지칭 |
| **합계** | **237** | alias·긍정 패턴 포함 문자열 기준. alias 중복 제거 시 고유 이슈는 약 120~140건 수준 (추론) |

### 7.2 통합 후

| 구분 | 건수 |
|---|---:|
| 통합 RF-FINDING | **61건** (RF-FINDING-001 ~ RF-FINDING-061) |
| 좋은 패턴(기준 패턴) | 14건 |
| 원본 "확인 필요" 보존 항목 | 30건 (5장 raw 34건 중 요약↔세부 alias 중복 표기 4건을 제외한 수치) |
| P0 선행 트랙 대상 | 4건: RF-FINDING-032(job polling guard), RF-FINDING-061(URL NaN parser), RF-FINDING-036(attachment rollback), RF-FINDING-051(env helper) |

### 7.3 심각도 분포 (통합 후, 최고 심각도 채택 기준)

| 심각도 | 건수 | 해당 RF |
|---|---:|---|
| High | 15 | 001, 002, 003, 021, 029, 030, 032, 036, 038, 039, 041, 042, 047, 051, 061 |
| Medium | 31 | 005, 006, 007, 009, 010, 011, 013, 014, 016, 017, 018, 019, 020, 022, 026, 027, 028, 033, 034, 035, 037, 040, 043, 044, 045, 048, 049, 050, 052, 053, 058 |
| Low | 11 | 012, 023, 024, 025, 031, 046, 054, 055, 056, 057, 059 |
| Suggestion | 4 | 004, 008, 015, 060 |

### 7.4 영역별 분포 (통합 후)

| 영역 | 건수 | 영역 | 건수 |
|---|---:|---|---:|
| architecture | 2 | server state | 7 |
| API/service | 4 | global state | 2 |
| testability | 1 | hook | 3 |
| routing/page | 2 | local state | 1 |
| layout/container | 2 | polling | 2 |
| error/loading/retry | 5 | async flow | 1 |
| props/state flow | 1 | type safety | 5 |
| component | 3 | DTO/API contract | 4 |
| performance | 2 | util/helper | 4 |
| validation/formatter | 2 | config/env/constant | 6 |
| import/export | 2 | **합계** | **61** |

> (추론) Phase 배정·난이도/위험도 평가는 본 문서 범위가 아니며, 확정된 Phase 0~8 구조에 따라 후속 Phase 문서에서 RF-FINDING ID를 참조해 수립한다.
