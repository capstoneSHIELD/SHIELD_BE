# 건드리면 안 되는 영역 / 주의 영역 (Do-Not-Touch & Caution Areas)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001~061)
> 출처: `C:\pfm-FE\.codex\ref_docs\codereview\session1~session6\refactoring-brief.md`의 "먼저 건드리면 안 되는 민감한 영역" / "안전하게 먼저 개선 가능한 영역" 섹션 통합.
> 본 문서의 파일 경로·라인·이유는 코드리뷰 원본 문서의 근거를 그대로 옮긴 것이며, Phase 연결·위험도 해석 등 계획 수립 과정의 판단은 "(추론)" 표기를 붙인다. 코드리뷰 문서에 없는 내용은 "확인 필요"로 표시한다.

## 0. 출처 섹션 현황

| 세션 | "먼저 건드리면 안 되는 민감한 영역" 섹션 | "안전하게 먼저 개선 가능한 영역" 섹션 |
|---|---|---|
| Session 1 (전체구조/아키텍처) | 있음 (표 형식, 5개 영역 + 근거 라인) | 있음 (4개 대상) |
| Session 2 (route/page/container) | 명시 섹션 없음 — "건드리기 전 추가 확인이 필요한 영역"(4건)과 우선순위 표의 주의사항 칼럼으로 존재 | "먼저 개선하면 좋은 구조"(4건)로 존재 |
| Session 3 (component) | 있음 (3개 영역) | 있음 (4개 대상) |
| Session 4 (hook/state) | 있음 (3개 영역) | 있음 (4개 대상) |
| Session 5 (API/service/async) | 명시 섹션 없음 — 우선순위 표의 주의사항 칼럼으로만 존재 | 명시 섹션 없음 |
| Session 6 (type/util/config) | 있음 (3개 영역) | 있음 (3개 대상) |

(추론) Session 2/5의 주의사항 칼럼 내용도 아래 1장 표에 동일 기준으로 통합했다.

---

## 1. 주의 영역 표 (민감 영역 — 사전 확인 없이 건드리지 말 것)

| 영역/파일 | 주의 이유 | 필요한 사전 확인 | 관련 이슈 |
|---|---|---|---|
| `lib/apiClient.ts` token refresh / error normalization (`lib/apiClient.ts:265`, `:278`, `:304`) | 모든 PFM API 호출의 공통 기반 (S1 brief). 401 refresh retry 흐름 변경 시 전체 보호 요청에 영향. timeout/signal 도입(RF-FINDING-028)도 "민감 영역이므로 신중히 진행"으로 원본에 명시 | refresh hang/401 retry 시나리오 테스트 선행. `lib/api/errors.ts:255`의 normalized error model(S5-ERRORNORM-001, 좋은 패턴)을 깨지 않는지 확인. token storage 변경(RF-FINDING-022)과 같은 계획으로 묶어 진행(S4 brief: "Session 5 API client 리뷰와 함께 변경 계획 수립") | RF-FINDING-022, RF-FINDING-028, RF-FINDING-040, RF-FINDING-052 (S4-PERSIST-001/002, S5-TIMEOUT-001, S5-CANCEL-001, S5-INTERCEPTOR-001, S5-RETRY-001) — (추론) Phase 2 |
| `lib/api/http.ts` WebSocket/binary/keepalive helper (`lib/api/http.ts:56`, `:90`, `:103`) | job/viz/download/unload cleanup과 연결 (S1 brief 민감 영역) | WS 연결·바이너리 다운로드·beforeunload(keepalive) 경로 회귀 테스트. `withQuery` 인자 타입 정리(RF-FINDING-057)는 시그니처만 좁히고 동작 불변 확인 | RF-FINDING-057 (S6-UTIL-002) — (추론) Phase 2 |
| `Simulation2Page` WebSocket refs/lifecycle — job monitor WS + polling fallback + visualization WS/sync (`components/pages/Simulation2Page.tsx:611`, `:1674`, `:1949`, `:2057`) | 연결 중복/cleanup/상태 전파 회귀 위험 (S1 brief). `beforeunload`, reconnect timer, stale token guard 테스트 필요 (S2 brief). "상태 전이가 복잡하므로 guard 테스트 없이 대규모 이동 금지" (S4 brief). visualization lifecycle과 result selection 회귀 주의 (S2 brief) | S4 원본 확인 필요 항목: "WebSocket cleanup 자체는 여러 ref와 cleanup helper로 구성되어 있으나 전체 lifecycle이 한 component에 있어 분리 전 재검증 필요". 기존 in-flight guard 좋은 패턴(`visualizationSyncInFlightRef`, S4-ABORT-001, `:2057/:2100`) 보존. P0 polling guard(RF-FINDING-032)는 guard 추가만 하고 lifecycle 이동은 금지 (추론) | RF-FINDING-001, RF-FINDING-032(P0), RF-FINDING-033, RF-FINDING-035 (S2-ASYNC-001/002, S4-RACE-001, S5-POLLING-001, S5-ERROR-003) — (추론) Phase 0(P0 guard)/Phase 3~4(lifecycle 분리) |
| `AdminPage3` React Query invalidation + URL query correction 흐름 (`components/pages/AdminPage3.tsx:489-498`, `:597`, `:617`, `:666/:676`, `:697`, `:918`, `:1080`) | "URL query key와 React Query invalidation이 얽혀 있어 Session 4 검토 후 진행" (S3 brief). "query key와 invalidation은 admin UI 전반에 영향이 커서 helper 정리 후 이동" (S4 brief). literal key/builder key 혼재로 key rename 시 invalidation 누락 위험 | query key helper(`buildAdminQueryKeys`) 정리 선행 (S4/S5 brief). 기존 deep link/query 호환성 보존 (S2 brief). refetch interval 정책 유지 (S2 brief). P0 NaN-safe parser(RF-FINDING-061)는 parsing만 고치고 correction 흐름 재배치는 별도 단계 (추론) | RF-FINDING-002, RF-FINDING-029, RF-FINDING-061(P0) (S4-URL-001, S2-STATE-001, S4-EFFECT-005, S4-CACHE-001, S5-MUTATION-001/002, S4-INVALIDATE-001 등) — (추론) Phase 0(P0 parser)/Phase 4 |
| `AdminPage3` 권한/early return (`components/pages/AdminPage3.tsx:1157`, `:1166`, `:1139`) | admin 접근 제어 UX와 직접 연결 (S1 brief 민감 영역). guard 상태 fallback이 container early return에 분산(RF-FINDING-007) | admin 권한 정책(누가 접근 가능한지) 확인. presenter 분리 시 차단 동작 자체는 불변 유지 (추론) | RF-FINDING-002, RF-FINDING-007 (S2-CONTAINER-008) — (추론) Phase 6 |
| Supabase delete/upload/update 흐름 (`components/pages/EditMemberPage.tsx:71`, `:74`, `AdminPage2.tsx:66`, `EditNoticePage.tsx:85/:107/:122`, `EditGalleryPage.tsx:112` 등) | 운영 데이터 손실 및 권한 정책과 연결 (S1 brief 민감 영역). "CMS edit form의 storage delete/upload 순서" (S3 brief 민감 영역). RLS/권한 정책 미확인 상태 (S1~S5 확인 필요 항목) | Supabase RLS/권한/스토리지 path 정책 확인 필요 (원본 유지). 실제 storage path/URL parsing 확인 필요 (RF-FINDING-036 원본). 프로젝트 규칙상 게시판 앱 수정은 별도 요청 없으면 지양 — 수정 범위 사전 합의 (추론). 운영 데이터 백업/스테이징 검증 경로 확인 필요 | RF-FINDING-003, RF-FINDING-036(P0), RF-FINDING-050 (S1-ARCH-002/003, S5-ROLLBACK-001) — (추론) Phase 0(P0 rollback)/Phase 2~3 |
| token storage (`lib/auth.ts:66-67`, `lib/apiClient.ts:38`, `lib/supabaseClient.ts:21`) | "로그인/refresh/401 retry와 연결되므로 Session 5 API client 리뷰와 함께 변경 계획 수립" (S4 brief 민감 영역). helper 중복으로 persistence 정책 drift 가능 | 로그인→refresh→401 retry 전체 시나리오 테스트. PFM token storage와 Supabase sessionStorage 경계 문서화 선행 (RF-FINDING-022 개선 방향) | RF-FINDING-022 (S4-PERSIST-001/002/003, S5-PERSIST-001=S5-AUTH-001) — (추론) Phase 2 |
| env/config (`lib/supabaseClient.ts:5-6`, `components/pages/ContactPage.tsx:26-29`, `lib/apiClient.ts:217-231`, `next.config.ts:4-8`) | non-null assertion env 접근은 배포 누락 시 runtime failure (RF-FINDING-051, P0). "env 이름과 배포 설정 확인 후 적용" (S6 brief 주의사항). NEXT_PUBLIC_* 값은 Vercel 배포 설정과 연동 (추론: CLAUDE.md env 목록 기준) | Vercel(및 로컬 `.env.local`) env 이름/설정 대조 확인 필요. `NEXT_PUBLIC_PFM_API_URL`/`NEXT_PUBLIC_PFM_LLM_URL` canonical env 결정(RF-FINDING-052). S6 원본 확인 필요: `NEXT_PUBLIC_LAB_SERVER_API_KEY`/`NEXT_PUBLIC_PFM_AUTH_TOKEN`의 runtime 사용 여부, images remote pattern 전체 host 허용이 CMS 요구사항인지 | RF-FINDING-051(P0), RF-FINDING-052, RF-FINDING-053 (S6-ENV-001/002, S6-CONFIG-001/002) — (추론) Phase 0(P0 helper)/Phase 7 |
| 인증/권한 이원 체계: PFM auth(자체 백엔드, token) vs Supabase auth(게시판, session) | 두 앱은 같은 프론트에 공존하지만 인증 체계가 완전히 분리되어야 함 (프로젝트 CLAUDE.md 규칙: "인증부터 모든 부분에 있어서 두 앱은 충돌해선 안된다"). 둘 다 sessionStorage를 사용해 경계 혼동 여지 (RF-FINDING-022 / S4-PERSIST-003) | guard 통합 리팩토링(RF-FINDING-005) 시 PFM gate(`usePfmAuthGate`)와 Supabase gate(`useSupabaseSessionGate`)를 하나의 추상화로 합치지 말 것 (추론). 두 storage 경계 문서화 선행 | RF-FINDING-005, RF-FINDING-009, RF-FINDING-022 — (추론) Phase 2/6 |
| API contract 미확인 파일: `lib/api/admin.ts` admin DTO vs 일반 DTO | "admin DTO 통합은 백엔드 admin response가 일반 response와 같은지 확인한 뒤 진행해야 한다" (S6 brief 민감 영역). admin/일반 DTO 분리가 백엔드 의도인지 **확인 필요** (S6 원본 확인 필요 항목) | 백엔드 API 명세(OpenAPI 등)에서 admin endpoint response field 차이 확인 필요. 확인 전에는 통합 대신 alias/mapper 명확화 우선 (S1 brief 우선순위 4) | RF-FINDING-039, RF-FINDING-059 (S5-DTO-001, S6-DTO-001, S6-DUPTYPE-001~005) — (추론) Phase 1 |
| backend와 강하게 연결된 type: `SimulationStatus`/`JobStatus`/`VisualizationStatus` status union (`lib/api/simulations.ts`, `jobs.ts`, `visualizations.ts`, `admin.ts`, `workflowTypes.ts`) | status union이 일반 API·admin API·workflow에 중복 정의되어 단일화 시 backend enum과의 일치가 전제됨. 백엔드 enum과 실제 일치 여부는 코드리뷰에서 비교하지 않음 — **확인 필요** (S5 원본: "backend OpenAPI/계약에서 nullable/optional field와 frontend DTO가 모두 일치하는지는 추가 확인 필요") | 백엔드 enum/계약 문서와 union 값 대조 확인 필요. 단일화 전 mapper(workflow stage 파생) 경계 먼저 정의 (RF-FINDING-039 개선 방향) | RF-FINDING-039, RF-FINDING-045 (S6-ENUM-001, S6-MAPPER-001) — (추론) Phase 1 |
| `apiRequest<T>` 기본 generic 전역 변경 (`lib/apiClient.ts:380`, `:395`) | "기본값을 전역에서 즉시 바꾸는 작업은 전체 API call site에 영향을 준다" (S6 brief 민감 영역). S6 원본 확인 필요: "전체 call site 영향이 크므로 단계적 적용 계획이 필요하다" | call site 타입 명시를 먼저 보강하고 기본값 `unknown` 전환은 영향 측정 후 단계 적용 | RF-FINDING-040 (S1-TYPE-002, S5-TYPE-001, S6-ANY-003, S6-ASSERT-001/002) — (추론) Phase 1~2 |
| `Simulation2Page` parameter mapper / PATCH body 조립 (`Simulation2Page.tsx:2378`, `workflowTypes.ts:72`) | "parameter mapper 변경은 job submit/update/restore 흐름과 함께 테스트해야 한다" (S6 brief 민감 영역). form state와 API request DTO가 결합되어 있어 mapper 도입 시 계약 회귀 위험 | job submit/update/restore 흐름 통합 테스트 준비 후 진행. workflow 리팩토링(RF-FINDING-041)과 같은 단계로 묶기 (S6 brief) | RF-FINDING-041, RF-FINDING-042, RF-FINDING-047 (S6-ANY-001/002, S5-CONTRACT-001) — (추론) Phase 1/4 |
| routing 변경 영향 큰 page: `AdminPage3` URL query param (deep link), `app/simulation2/page.tsx` redirect, board `[id]` dynamic route | admin URL query는 deep link로 사용되어 "기존 deep link/query 호환성 보존" 필요 (S2 brief). redirect 방식 변경 시 login/simulation UX 회귀 (S2 brief). board id parser는 "Next `notFound()` 사용 여부는 route/server boundary 확인" (S2 brief) | 실제 deep link 사용 현황 확인 필요 (코드리뷰 문서에는 호환성 보존 지시만 존재). redirect 변경 전 login↔simulation2 양방향 UX 수동 검증. `middleware.ts` 부재가 의도인지 확인 필요 (S2 원본) | RF-FINDING-005, RF-FINDING-006, RF-FINDING-061(P0) (S2-GUARD-001~004, S2-ROUTE-001/002/003) — (추론) Phase 6 |
| 테스트가 부족한 핵심 플로우 (대형 컨테이너 분리 대상 전반) | "테스트 커버리지와 주요 회귀 시나리오" 조사가 미완 (S1 brief 추가 조사 영역). Session 7(테스트/품질 세션)은 미수행으로 실제 커버리지 미상 — **확인 필요**. 현재 자동 안전망은 PFM boundary 정적 검사(`scripts/check-pfm-api-boundaries.mjs`)와 일부 테스트에 한정(RF-FINDING-004) | `npm run test:coverage`로 현재 커버리지 측정 선행 (추론). 대형 컨테이너(Simulation2Page/AdminPage3) 분리 전 characterization test 또는 pure helper 분리부터 시작 (S1 brief: "리팩토링은 테스트 추가 또는 pure helper 분리부터 시작") | RF-FINDING-004 (S1-TEST-001, S5-BOUNDARY-001) — (추론) Phase 0/8 |
| legacy 흐름: `api/chat.js`, `lib/api/legacyAiChat.ts`, `components/pages/PFMSimulationPage.tsx`, legacy admin/editor component | 유지/제거/격리 결정이 안 된 상태에서 수정하면 낭비·회귀 위험. "legacy AI assistant와 `api/chat.js`가 현재 제품 범위에 포함되는지 확인 필요" (S1 원본). "legacy 유지 여부 확인 필요" (RF-FINDING-030/034 원본). "legacy admin/editor component가 현재 운영 범위인지 확인 필요" (S3 원본) | 제품 범위(유지 여부) 결정 확인 필요 — 결정 전에는 guard 추가 등 최소 수정만 (추론) | RF-FINDING-030, RF-FINDING-034, RF-FINDING-049 (S1-EXTERNAL-001, S5-POLLING-002, S6-VALIDATOR-001) — (추론) Phase 2~3 |
| CMS HTML content 렌더링 (`components/pages/ResearchPageTemplate.tsx:65`) | sanitize 정책이 component에서 확인되지 않아, 렌더링 경로 변경 시 XSS 노출면이 달라질 수 있음 | "HTML content가 모두 관리자 trusted input인지, sanitizer가 저장 시점에 적용되는지 확인 필요" (S3 원본 그대로 유지) | RF-FINDING-013 (S3-QUAL-008) — (추론) Phase 5 |

---

## 2. 안전하게 먼저 개선 가능한 영역 (대비용)

각 세션 brief의 "안전하게 먼저 개선 가능한 영역"/"먼저 개선하면 좋은 구조" 통합. 이 표의 항목도 작업 전 3장 공통 체크리스트는 적용한다.

| 대상 | 출처 | 예상 작업 | 관련 이슈 | 남는 주의점 |
|---|---|---|---|---|
| 중복 `sanitizeForStorage` helper 추출 (`AdminPage.tsx:43`, `EditNoticePage.tsx:14`, `EditGalleryPage.tsx:13`, `ResultExplorerPanel.tsx:197`) | S3 | 공통 util(`lib/storage/filename` 등)로 이동 + 테스트 | RF-FINDING-050 | 파일명 정책이 backend/storage 정책과 충돌하지 않는지 확인 (원본) |
| list key 안정화 (`ImageCarousel.tsx:32-33`, `Simulation2Page.tsx:3355` 등, `ResearchPageTemplate.tsx:142`) | S3 | index key → stable id 기반 key | RF-FINDING-014 | message id/event timestamp 조합 등 stable id 소스 선정 |
| `ResearchHighlightsSlider` empty length guard (`:32`) | S4 | effect 초기 `highlights.length === 0` guard 추가 | RF-FINDING-026 | UI 회귀 범위 작음 (S4 brief). variants 재생성(RF-FINDING-015, `:80`)도 함께 정리 가능 |
| `EditNoticePage` invalid id loading 처리 (`:46`) | S3 | id parser + error/not-found UI 도입 | RF-FINDING-006 | route guard/params 정책과 맞춰야 함 (원본) |
| `HomePage` fetch error/finally 보강 (`:18`, `:36`, `:66`) | S4 | try/catch/finally + typed error state (`useHomeContent`) | RF-FINDING-021 | CMS schema/view model 확인 필요 (S4 brief). 게시판 앱 영역이므로 수정 범위 최소화 (추론) |
| `hooks/use-toast.ts` listener effect dependency 검토 (`:131`, `:176`) | S4 | mount-only subscription 검토 | RF-FINDING-024 | shadcn 패턴과 기존 toast 테스트 확인 (원본) |
| `MemberDetailModal` 접근성 보강 (`:18`) | S3 | Radix Dialog 전환 또는 aria/focus 보강 | RF-FINDING-011 | 기존 스타일/모바일 레이아웃 회귀 확인 (원본) |
| list card request sequence guard 추가 (`JobResultListCard`, `SimulationListCard`, `SessionListCard`, `ResultExplorerPanel`) | S4 | request token/sequence guard 추가 (구조 이동 없이) | RF-FINDING-016~019 | `sync:false` 정책 유지(Lab sync 비용), refreshKey와 기존 테스트 영향 확인 (원본) |
| `components/pages/simulation2/workflowMappers.ts` 테스트 보강 | S1 | 순수 함수 중심 — status/stage mapper 테스트 추가 | RF-FINDING-045 (DTO 정의는 별도) | WS payload DTO 도입은 Phase 1 계약 확인과 연결 (추론) |
| `components/pages/simulation2/jobMonitorSession.ts` lifecycle 테스트 | S1 | token helper로 범위가 작음 — 테스트 추가 | (RF-FINDING-001 분해 기반 작업) | — |
| `Simulation2Page` 내부 pure formatter/parser 파일 분리 (`:221-534`) | S6 | 동작 변경 없이 pure function부터 이동 + 테스트 | RF-FINDING-047 | 행동 변경 금지 (S6 brief). polling/reconnect constant 이동 시 참조 누락 주의 (추론) |
| env config helper 추가 후 한두 파일부터 교체 | S6 | `getRequiredPublicEnv` + integration별 config module | RF-FINDING-051(P0), RF-FINDING-052 | env 이름/배포 설정 확인 후 적용 (원본) — P0 선행 트랙 항목이지만 작업 자체는 안전 영역 |
| 단순 constant 위치 정리 (colormap, page size, polling interval) | S6 | shared/feature constant로 이동 | RF-FINDING-055 | 실제로 도메인별 옵션이 다른지 확인 (원본) |
| `AdminPage3` URL pure parser 분리 | S2 | pure function으로 먼저 분리 (query/mutation 분리보다 위험 낮음 — S2 brief) | RF-FINDING-061(P0) | deep link/query 호환성 보존 (원본) |
| legacy CMS admin gate 중복 제거 (`cmsl2004`, `cmsl20042`) | S2 | `LegacyAdminGate`/`useSupabaseSessionGate` 추출 | RF-FINDING-005 | 기존 `LegacyLoginPage` 분기와 subscription cleanup 보존 (원본). 게시판 앱 영역 — 수정 지양 규칙 고려 (추론) |
| board dynamic id parser (`parseBoardId`) | S2 | invalid id 처리 기준 정의 후 parser 추출 | RF-FINDING-006 | `notFound()` 사용 여부는 route/server boundary 확인 (원본) |
| 문서/아키텍처 boundary guard 확대 검토 | S1 | 현재 경계 기준 문서화, boundary script 확대 검토 | RF-FINDING-004 | service 경계를 먼저 정한 뒤 검사 추가 (원본) |

---

## 3. 작업 전 체크리스트

### 3.1 공통 (모든 리팩토링 작업 전)

- [ ] `consolidated-findings.md`에서 대상 RF-FINDING과 원본 세션 문서(해당 session{N}-findings.md, 세부 리뷰)를 정독했다.
- [ ] 대상이 1장 민감 영역 표에 있는지 확인했다. 있다면 "필요한 사전 확인" 항목을 모두 해소(또는 책임자 합의)했다.
- [ ] 베이스라인 기록: `npm run lint`, `npm run build`, `npm run test:run`, `npm run test:boundaries` 통과 상태를 작업 전에 기록했다.
- [ ] 타입 체크: 전용 typecheck 스크립트는 없음 → `npx tsc --noEmit` 실행 후보 (확인 필요). tsconfig strict 계열 off라 검출력이 약함을 전제한다 (RF-FINDING-038).
- [ ] 변경 단위를 작게 유지: "한 번에 전체 분리 금지" (S3/S4/S5 brief 공통), pure function 이동/테스트 추가부터 시작 (S1 brief).
- [ ] 작은 commit 단위 + feature branch에서 작업하여 롤백 가능성을 확보했다 (추론).
- [ ] 게시판 앱(/cmsl*, board, Supabase 기반) 코드 변경이 포함되면: 프로젝트 규칙상 별도 요청 없으면 수정 지양 — 범위 합의 여부를 확인했다.

### 3.2 민감 영역별 추가 확인

- [ ] **PFM API client/token**: 로그인 → token refresh → 401 retry 시나리오를 변경 전후 동일하게 검증할 테스트/수동 절차를 준비했다 (RF-FINDING-022/028).
- [ ] **WebSocket/polling (Simulation2Page)**: WS fallback, terminal status, result availability, `beforeunload`, reconnect timer, stale token guard를 함께 테스트할 계획이 있다 (RF-FINDING-032/033 원본). 기존 좋은 패턴(`visualizationSyncInFlightRef`, TrameExportCenter AbortController, labserverTrameClient timeout polling)을 기준 패턴으로 참조했다.
- [ ] **AdminPage3 query/cache**: query key helper 정리를 선행했고, invalidation/refetch interval/deep link 호환성을 보존하는지 확인했다 (RF-FINDING-029/061).
- [ ] **Supabase mutation/storage**: RLS/권한/스토리지 path 정책 확인 필요 항목을 해소했다. 운영 데이터에 대한 delete/upload 순서 변경은 실패 보상(rollback) 정책과 함께 설계했다 (RF-FINDING-003/036).
- [ ] **env/config**: 변경하는 env 이름이 Vercel 배포 설정 및 `.env.local`과 일치하는지 확인했다 (RF-FINDING-051/052).
- [ ] **DTO/status union**: 백엔드 명세(admin vs 일반 response, status enum)와 대조 확인 필요 항목을 해소하기 전에는 통합 대신 mapper/alias 명확화에 머무른다 (RF-FINDING-039).
- [ ] **auth 이원 체계**: 변경이 PFM auth와 Supabase auth 경계를 넘나들지 않는지 확인했다 (RF-FINDING-005/022, 프로젝트 규칙).
- [ ] **legacy 흐름**: 유지/제거 결정이 확인되지 않았다면 최소 수정(guard 추가 등)에 머무른다 (RF-FINDING-030/034/049).

### 3.3 작업 후 검증

- [ ] `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:boundaries` 재실행, 베이스라인과 비교.
- [ ] 수동 검증: local `http://localhost:3000`, production `https://pfm.cmsl-kookmin.com/simulation2` (Playwright MCP 사용 가능 — 렌더링 snapshot, console error, network request 확인).
- [ ] 민감 영역 변경 시 해당 핵심 플로우(로그인/job 제출/polling/visualization/admin 목록/게시판 CRUD 중 해당 항목) 수동 시나리오 통과를 기록했다.
