# 리팩토링 위험 및 영향 맵 (Risk and Impact Map)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001~061)
> 원본 근거: `C:\pfm-FE\.codex\ref_docs\codereview\session1~session6` (각 세션 refactoring-brief.md의 민감영역/우선순위 포함)
> 본 문서는 Phase 0~8 리팩토링 실행 전반에서 참조하는 위험 기준 문서이며, 위험 ID는 `RF-RISK-001`부터 채번한다.
> 파일 경로·라인·원본 ID(S1-\*~S6-\*)는 코드리뷰 원본 문서의 근거를 그대로 보존했고, 위험도 평가·Phase 연계 판단은 계획 수립 과정의 추론이므로 "(추론)" 표기를 따른다.

---

## 1. 위험 평가 기준

**(추론) 아래 위험도 등급은 코드리뷰 원본의 심각도(High/Medium/Low/Suggestion)와는 별개로, "리팩토링 작업 자체가 유발할 수 있는 회귀 위험"을 평가하기 위해 본 문서에서 새로 정의한 기준이다.** 원본 심각도는 "현재 코드의 문제 심각도"이고, 본 문서의 위험도는 "그 문제를 고치는 과정의 위험"이다.

| 위험도 | 정의 (추론) | 판단 요소 (추론) |
|---|---|---|
| **High** | 회귀 발생 시 핵심 사용자 플로우(시뮬레이션 실행/결과 조회, admin 운영, 게시판 CRUD)가 중단되거나 데이터 불일치가 생길 수 있음. 자동 테스트 안전망이 약하거나 없음 | ① 영향 범위가 앱 전역 또는 핵심 플로우 전체 ② 비동기/lifecycle/인증 등 타이밍 의존 로직 ③ 회귀를 컴파일/테스트가 잡아주지 못함 (tsconfig strict 계열 off — RF-FINDING-038) |
| **Medium** | 회귀 발생 시 특정 화면/기능 단위 오동작이 생기지만 우회 경로가 있거나 발견이 빠름 | ① 영향 범위가 도메인/탭/페이지 단위 ② 동기 로직 중심이거나 기준 패턴(consolidated-findings 6장)이 존재 ③ 수동 검증 경로가 명확 |
| **Low** | 회귀 발생 시 표시/스타일/메시지 수준의 문제. 데이터/플로우 손상 없음 | ① 영향 범위가 단일 component/util ② pure function 중심 ③ lint/build로 대부분 검출 가능 |

**검증 수단 (package.json 확인 기준):** `npm run lint`, `npm run build`, `npm run test:run`(vitest run), `npm run test:coverage`, `npm run test:boundaries`(PFM API boundary 정적 검사). 전용 typecheck 스크립트는 없으므로 `npx tsc --noEmit`을 후보로 기재하되, tsconfig strict 계열 off 상태라 검출력이 약함을 전제한다 (확인 필요: `tsc --noEmit` 단독 실행 시 실효성 — RF-FINDING-038의 "strict 시 오류량 측정 필요"와 동일 맥락). 수동 검증: local `http://localhost:3000`, production `https://pfm.cmsl-kookmin.com/simulation2` (Playwright MCP 사용 가능).

---

## 2. 위험 맵

> 위험도는 전부 (추론). "관련 이슈"는 consolidated-findings.md의 RF-FINDING ID.

| 위험 ID | 대상 영역 | 위험 내용 | 영향 범위 | 위험도 | 완화 전략 | 관련 이슈 |
|---|---|---|---|---|---|---|
| RF-RISK-001 | simulation2 사용자 워크플로우 | `Simulation2Page.tsx`(3347 line) 분해(hook/presenter 추출) 과정에서 chat → simulation 생성 → parameter 편집 → job 제출 → 모니터링(WS/polling) → 결과/시각화로 이어지는 단일 플로우의 상태 전이·ref·cleanup이 끊어질 수 있음. 원본 브리프가 "WebSocket cleanup, polling, refresh key, session/job/result ordering 회귀 위험. 테스트 선행"을 명시 (session1 brief) | `/simulation2` 전체. `components/pages/Simulation2Page.tsx`, `components/simulation/*` 카드/패널 전부 | High | "한 번에 전체 분리 금지"(원본 개선 방향) 준수. Phase 0에서 안전장치 확인 후 Phase 3~5에서 lifecycle 단위(useJobMonitorSession → useVisualizationSession → useSimulationWorkflow)로 단계 분리 (추론). 각 단계 후 production URL에서 Playwright로 job 제출~결과 조회 수동 검증. 좋은 패턴 `visualizationSyncInFlightRef`(Simulation2Page.tsx:2057, 2100)를 기준 패턴으로 사용 | RF-FINDING-001, 032, 033, 035, 041, 042, 047 |
| RF-RISK-002 | admin 운영 플로우 | `AdminPage3.tsx`(2942 line)의 tab별 container/query hook 분리 시 권한 early return(AdminPage3.tsx:1157, 1166 — session1 brief 민감영역), URL state, query/mutation/invalidation이 얽혀 있어 admin 계정 승인/사용자 관리/job sync·cancel 운영 작업이 중단될 수 있음 | admin 화면 전체(`components/pages/AdminPage3.tsx`), admin 운영 업무 | High | query key helper(`buildAdminQueryKeys`) 안정화 선행(원본 개선 방향). 권한/early return 로직은 동작 동결 후 마지막에 이동 (추론). tab 1개씩 분리하고 분리마다 admin 주요 작업(승인/sync/cancel) 수동 확인 (추론) | RF-FINDING-002, 029, 048, 061 |
| RF-RISK-003 | CMS 게시판 플로우 | CMS service/hook 분리(RF-FINDING-003) 및 attachment rollback 도입(RF-FINDING-036) 시 notice/gallery CRUD, 고정글(pin), storage upload/remove 순서가 바뀌어 운영 데이터 손실 가능. 원본 브리프 민감영역: "Supabase delete/upload/update 흐름 — 운영 데이터 손실 및 권한 정책과 연결"(EditMemberPage.tsx:71, 74, AdminPage2.tsx:66), "CMS edit form의 storage delete/upload 순서"(session3 brief) | board/news/gallery 라우트, `HomePage.tsx`, `NoticeBoardPage.tsx`, `NoticeDetailPage.tsx`, `EditNoticePage.tsx`, `EditGalleryPage.tsx`, `GalleryBoardPage.tsx`, `ResearchPageTemplate.tsx` | High | 도메인별 점진 이관(원본 개선 방향: "한 번에 전체 이관 금지"). RLS/권한/storage path 정책 확인 필요(원본 유지). 프로젝트 CLAUDE.md의 "별도 요청 없으면 게시판 앱 수정 지양" 제약상 게시판 영역 변경은 최소 범위로 한정하고 사전 합의 (추론, RF-RISK-011과 연계) | RF-FINDING-003, 020, 036, 037, 050 |
| RF-RISK-004 | API request/response contract | DTO 단일화(shared module화) 시 backend 계약 미확인 영역에서 잘못된 통합이 발생할 수 있음. 특히 **admin DTO가 일반 DTO와 의도적으로 다른 계약인지 "확인 필요" 상태**(session6: "admin DTO와 일반 DTO가 의도적으로 다른 계약인지 백엔드 API 명세 확인 필요"). nullable/optional field 일치 여부도 미확인(session5) | `lib/api/admin.ts`, `lib/api/simulations.ts`, `jobs.ts`, `results.ts`, `visualizations.ts`, `components/pages/simulation2/workflowTypes.ts` 및 이를 소비하는 모든 화면 | High | Phase 1에서 백엔드 명세(`.codex/ref_docs/backend_api.md` 등) 대조를 통합 작업의 선행 조건으로 둠 (추론, 명세 문서의 최신성은 확인 필요). 계약 확인 전에는 alias/mapper로 명확화만 하고 필드 삭제/통합 금지(session1 brief: "backend contract 확인 후 alias/mapper를 먼저 명확화"). 응답 shape 차이 발견 시 shared DTO + admin extension type 구조 적용(원본 개선 방향) | RF-FINDING-039, 042, 044, 045 |
| RF-RISK-005 | 상태 관리 변경(UI 불일치) | refreshKey 기반 local server state를 React Query/custom hook으로 전환할 때(JobResultListCard.tsx:93·98·121, SimulationListCard.tsx:50-77, SessionListCard.tsx:63-212, ResultExplorerPanel.tsx:286·392·411) 갱신 트리거가 누락되면 목록이 stale 상태로 고정되거나, 반대로 cache 정책 차이로 화면 간 데이터 불일치 발생 가능. session3 brief: "refreshKey와 기존 테스트 영향 확인" | simulation2 워크스페이스 목록/결과 카드 전부, `WorkspaceTabsCard.tsx` props contract(RF-FINDING-010) | High | refreshKey 제거는 hook 전환과 동시에 하지 말고, 먼저 stale guard(request sequence/AbortController)만 추가 → 동작 동일 확인 → 이후 cache 전환 (추론). `sync:false` 정책 유지(원본: Lab sync 비용). QueryClient 전역 기본값(providers.tsx:15-16)과 도메인별 정책 충돌 여부를 Phase 4 전 문서화(RF-FINDING-027) | RF-FINDING-010, 016, 017, 018, 019, 027, 029 |
| RF-RISK-006 | polling 중단 조건 | P0 in-flight guard 도입(Simulation2Page.tsx:1605) 및 polling 구조 변경 시 terminal status 판정·WS fallback 진입/복귀 조건을 잘못 건드리면 job이 끝났는데 폴링이 계속되거나, 반대로 조기 중단되어 결과 조회가 실패할 수 있음. 원본 주의사항: "WS fallback/terminal status/result availability와 함께 테스트" | job 모니터링~결과 표시 구간(`Simulation2Page.tsx` 1469, 1605, 1674), legacy `PFMSimulationPage.tsx:468` | High | guard는 "요청 겹침 방지"만 추가하고 중단 조건(terminal status 판정식, fallback 분기)은 변경하지 않는 최소 diff로 적용 (추론). 좋은 패턴(TrameExportCenter.tsx:147-149 AbortController, labserverTrameClient.ts:466-484 timeout+AbortSignal polling) 준용. 적용 후 job 정상 완료/실패/취소 3개 시나리오 수동 검증 (추론) | RF-FINDING-032, 033, 034, 035 |
| RF-RISK-007 | type 수정(compile error 광역화) | strict 계열 옵션(tsconfig.json 8, 10, 29, 32) 일괄 활성화 또는 `apiRequest<T = any>` 기본 generic의 `unknown` 전환(lib/apiClient.ts:380, 395) 시 전체 call site에 컴파일 오류가 한꺼번에 발생해 빌드 불능 또는 임시방편 assertion(`as any`) 양산 가능. 원본: "전체 call site 영향이 크므로 단계적 적용 계획이 필요", "단번에 전체 strict 전환 금지" | 코드베이스 전체(tsconfig), 모든 PFM API call site(apiRequest) | High | strict는 디렉터리/feature 단위 단계 강화(원본 개선 방향). 전환 전 `npx tsc --noEmit` + strict 임시 활성화로 오류량 측정(원본 "확인 필요" 유지). `apiRequest` 기본값 변경은 call site 타입 명시 강화를 먼저 완료한 뒤 마지막에 수행 (추론). 오류량이 임계 초과 시 Phase 1 범위를 신규/리팩토링 파일로 한정 (추론) | RF-FINDING-038, 040, 058 |
| RF-RISK-008 | component 분리(props 누락) | SessionListCard 등 분리 시 parent callback contract(`onDeleted`, `onRenamed` — session3 brief가 contract 유지를 명시) 또는 WorkspaceTabsCard의 15개+ pass-through props 중 일부가 누락/오연결되면, 컴파일은 통과해도(strict off + 옵셔널 props) 삭제/이름변경 후 목록 미갱신 같은 silent 회귀 발생 | `SessionListCard.tsx`, `JobResultListCard.tsx`, `SimulationListCard.tsx`, `ResultExplorerPanel.tsx`, `WorkspaceTabsCard.tsx:14`와 그 parent(`Simulation2Page.tsx`) | Medium | 분리 전 props/callback contract를 타입으로 고정(필수 props는 optional로 두지 않음) (추론). callback 호출 경로별 vitest 단위 테스트를 분리와 동시에 추가 (추론). ResultExplorerPanel은 "field selection callback과 visualization field preference 유지"(session4 brief) 확인 | RF-FINDING-001, 010, 016, 019 |
| RF-RISK-009 | route/page 구조(navigation 오류) | auth gate 추출(`usePfmAuthGate` 등), board id parser 표준화, admin URL state hook 도입 시 redirect 방식·deep link·query param 호환이 깨질 수 있음. 원본 주의사항: "redirect 방식 변경 시 login/simulation UX 회귀 확인", "기존 deep link/query 호환성 보존"(AdminPage3 URL state), "Next `notFound()` 사용 여부는 route/server boundary 확인" | `app/simulation2/page.tsx`(16/18/20/43), `app/pfm_chat/login/page.tsx`(8/10), `app/cmsl2004`·`cmsl20042`(13/14, 13), `app/board/news/[id]`·`gallery/[id]`(9) 및 edit route(7), `AdminPage3.tsx:489-498` URL parsing | Medium | guard 추출은 redirect 대상/조건을 1:1로 보존하는 순수 이동부터 (추론). admin URL은 기존 query param 이름/형식 유지 + NaN 보정만 추가(RF-FINDING-061 원본 개선 방향: "기존 deep link/query 호환성 보존"). 변경 후 직접 URL 진입(deep link), 새로고침, 뒤로가기 시나리오를 Playwright로 검증 (추론) | RF-FINDING-005, 006, 007, 061 |
| RF-RISK-010 | config/env(환경별 동작 차이) | env helper 도입(RF-FINDING-051)·canonical env 정리(RF-FINDING-052)·`images.remotePatterns` 제한(RF-FINDING-053) 시 local에서는 정상이나 Vercel 배포 env에 해당 변수가 없거나 이름이 달라 production에서만 실패할 수 있음. `NEXT_PUBLIC_*`는 빌드 타임 인라인이므로 env 변경은 재배포가 필요하고 서버 전용 env와 동작이 다름 (추론: Next.js 일반 동작에 근거한 보충, 코드리뷰 원본에는 명시 없음) | `lib/supabaseClient.ts:5-6`, `lib/apiClient.ts:217-231`, `components/pages/ContactPage.tsx:26-29`, `next.config.ts:4-8`, Vercel 배포 환경 전체 | High | 원본 개선 방향 준수: "env 이름/배포 설정 확인 후 적용". Vercel 프로젝트 env 목록과 코드 사용 env 대조표를 Phase 0/7에서 작성 (추론). `getRequiredPublicEnv`는 누락 시 명확한 에러 메시지 + 사용자 화면 disabled/error fallback(원본 개선 방향). remotePatterns 제한은 "전체 host 허용이 CMS 요구사항인지 확인 필요"(원본 유지) 해소 전 보류 (추론). 배포 후 production URL 검증 필수 | RF-FINDING-051, 052, 053, 054 |
| RF-RISK-011 | 게시판 앱/시뮬레이션 앱 경계 | CMSL 게시판(Supabase 백엔드)과 PFM 시뮬레이션(자체 백엔드)이 한 프론트에 공존하며 인증 체계가 분리되어 있음(프로젝트 CLAUDE.md 제약). 리팩토링 중 공통화(auth storage adapter, error model, service layer, env helper)가 두 앱의 인증/스토리지/에러 정책을 한 모듈로 섞으면 경계가 흐려지고 충돌 가능. RF-FINDING-022 원본도 "PFM token과 Supabase session storage 경계 문서화"를 요구 | `lib/auth.ts`(66-67), `lib/apiClient.ts`(38), `lib/supabaseClient.ts`(21), 공통화 대상 util/error/env 모듈 전반 | High | 상세 기준은 5장 참조. "공유 가능"(pure util, UI primitive)과 "공유 금지"(auth/session storage, API client, error envelope, service layer) 목록을 Phase 0에서 확정 (추론). `npm run test:boundaries` 유지 + CMS boundary guard 확대(RF-FINDING-004)는 경계 확정 후 추가(원본 순서) | RF-FINDING-003, 004, 022, 051 |
| RF-RISK-012 | 인증 token/refresh 흐름 | token storage 단일화(RF-FINDING-022)와 apiClient timeout/retry 도입(RF-FINDING-028)이 로그인/refresh/401 retry 흐름을 건드림. session1 brief 민감영역: "`lib/apiClient.ts` token refresh/error normalization — 모든 PFM API 호출의 공통 기반"(apiClient.ts:265, 278, 304). 회귀 시 PFM 앱 전체가 인증 실패 | 모든 PFM API 호출, `/simulation2` · admin 로그인 세션 유지 | High | 원본 주의 준수: "민감 영역이므로 신중히 진행", "로그인/refresh/401 retry와 연결되므로 API client 변경과 함께 계획". storage adapter는 기존 키/포맷을 그대로 읽고 쓰는 wrapper로 시작(데이터 마이그레이션 없음) (추론). refresh timeout 추가 시 401 retry 1회 정책은 변경하지 않음 (추론). 로그인 → 장시간 사용 → 토큰 만료 → 자동 refresh 시나리오 수동 검증 (추론) | RF-FINDING-022, 028 |
| RF-RISK-013 | WebSocket lifecycle | job monitor WS(Simulation2Page.tsx:1674)와 visualization WS/sync interval(:1949)을 hook으로 추출할 때 ref 기반 cleanup·reconnect timer·stale token guard·`beforeunload` 처리가 끊어지면 연결 중복, 메모리 누수, 시각화 동기화 회귀 발생. session4 "확인 필요" 유지: "전체 lifecycle이 한 component에 있어 분리 전 재검증 필요" | job 모니터링·시각화 실시간 갱신 구간, `lib/api/http.ts`(56, 90, 103 — WebSocket/binary/keepalive helper, session1 brief 민감영역) | High | `lib/api/http.ts` helper는 변경하지 않고 소비 측만 이동 (추론). 추출 전 현재 cleanup 경로(어떤 ref가 어떤 cleanup을 담당하는지) 문서화 후 진행(원본 "분리 전 재검증 필요" 이행). reconnect/fallback 조건은 RF-RISK-006과 동일하게 동작 보존 (추론) | RF-FINDING-001, 033, 045 |
| RF-RISK-014 | Supabase RLS/storage 정책 | CMS service 분리·attachment rollback·파일명 sanitizer 공통화가 RLS/스토리지 정책과 충돌할 수 있음. 원본 "확인 필요" 다수: "Supabase RLS/권한 정책과 현재 UI 직접 호출 구조가 의도된 설계인지", "edit route 접근 제어가 RLS로 충분히 보호되는지", "파일명 정책이 backend/storage 정책과 충돌하지 않는지", "실제 storage path/URL parsing 확인 필요" | CMS 게시판 전 영역(쓰기 경로), Supabase storage 버킷 | Medium | RLS/storage 정책 확인을 CMS 영역 Phase 착수 조건으로 설정 (추론). 확인 전에는 읽기 경로(list/detail hook)만 이관하고 쓰기/삭제 경로는 보류 (추론). "RLS 확인 전 과도한 UI 차단 금지"(RF-FINDING-005 원본) 준수 | RF-FINDING-003, 005, 036, 050 |
| RF-RISK-015 | admin query cache/invalidation | literal key와 builder key 혼재(AdminPage3.tsx:597, 617), invalidation fan-out(:697), `fetchQuery` 결과 local 복사(:510/744/729) 정리 시 key rename·invalidation 누락으로 admin 화면이 stale data를 보여주거나 중복 요청 발생 | admin 전 탭의 데이터 갱신(me/health/ready/account/users/simulation/jobs/results/viz) | Medium | `buildAdminQueryKeys` root helper를 먼저 추가해 기존 key 문자열과 1:1 대응 확인 후 치환(원본: "query key helper 정리 선행") (추론). mutation hook 캡슐화는 key 안정화 다음 단계 (추론). sync/cancel 후 목록 갱신 여부를 탭별로 수동 확인 (추론) | RF-FINDING-002, 029, 061 |
| RF-RISK-016 | 테스트/검증 안전망 | strict 계열 off(검출력 약함), 전용 typecheck 스크립트 없음, boundary 검사는 일부 PFM page 한정(scripts/check-pfm-api-boundaries.mjs:6), 기존 테스트 커버리지 미확인(session1 brief "테스트 커버리지와 주요 회귀 시나리오" 추가 조사 필요) — 이 상태에서 대형 분해를 진행하면 회귀가 머지 후에야 발견됨 | 리팩토링 전 Phase | High | Phase 0에서 `npm run test:run`/`test:coverage` 현황 측정과 회귀 시나리오 목록화를 선행 (추론). 각 Phase 완료 게이트: `npm run lint` + `npm run build` + `npm run test:run` + `npm run test:boundaries` + (후보) `npx tsc --noEmit`(확인 필요: strict off라 검출력 약함) (추론). pure helper 분리 시 테스트 동시 추가(원본: "행동 변경 없이 pure function부터 이동 후 테스트 추가") | RF-FINDING-004, 038, 058 |
| RF-RISK-017 | legacy 영역 처리 | legacy 유지 여부가 미확정인 영역(`api/chat.js`+`lib/api/legacyAiChat.ts`, `PFMSimulationPage.tsx`, legacy admin `cmsl2004`/`cmsl20042`, legacy admin/workbench component)을 유지 전제로 고치거나 제거하면 양쪽 모두 헛작업/회귀 위험. 원본 "확인 필요" 유지: "legacy AI assistant와 `api/chat.js`가 현재 제품 범위에 포함되는지", "legacy admin/editor component가 현재 운영 범위인지", "legacy `/api/chat` 유지 여부" | `api/chat.js`(70-103), `lib/api/legacyAiChat.ts`(17, 21), `components/pages/PFMSimulationPage.tsx`(158, 196-219, 468), legacy admin 라우트 | Medium | 유지/제거 결정을 해당 RF-FINDING 착수 조건으로 설정하고, 결정 전에는 최소 안전 패치(예: RF-FINDING-034 polling guard)만 적용 (추론). 제거 결정 시에도 라우트 접근 로그/사용 여부 확인 후 진행 (추론, 확인 필요) | RF-FINDING-030, 034, 049 |

---

## 3. 필수 점검 8개 항목 ↔ 위험 ID 매핑

| # | 필수 점검 항목 | 위험 ID |
|---|---|---|
| ① | 기존 사용자 플로우 깨짐 가능성 (simulation2 워크플로우 / admin 운영 플로우 / CMS 게시판) | RF-RISK-001 / RF-RISK-002 / RF-RISK-003 |
| ② | API request/response contract 변경 위험 (admin DTO 별도 계약 여부 "확인 필요") | RF-RISK-004 |
| ③ | 상태 관리 변경으로 인한 UI 불일치 (refreshKey 제거/React Query 전환) | RF-RISK-005 |
| ④ | polling 중단 조건 변경으로 인한 결과 조회 실패 (terminal status, WS fallback) | RF-RISK-006 |
| ⑤ | type 수정으로 인한 광범위한 compile error (strict 옵션, apiRequest generic) | RF-RISK-007 |
| ⑥ | component 분리로 인한 props 누락 (onDeleted/onRenamed 등 parent callback contract) | RF-RISK-008 |
| ⑦ | route/page 구조 변경으로 인한 navigation 오류 (deep link/query param 호환성) | RF-RISK-009 |
| ⑧ | config/env 변경으로 인한 환경별 동작 차이 (Vercel 배포 env, NEXT_PUBLIC_* 구분) | RF-RISK-010 |

(추론) ①은 사용자 플로우가 3개의 서로 다른 백엔드/화면 집합에 걸쳐 있어 완화 전략이 달라지므로 3개 위험으로 분리했다.

---

## 4. 영역별 영향 전파 요약

> "이 파일/영역을 변경하면 어디까지 영향이 가는가"의 요약. 전파 경로는 코드리뷰 원본의 의존 관찰에 근거하되, 범위 판단은 (추론).

| 변경 지점 | 영향 전파 | 근거/관련 |
|---|---|---|
| `lib/apiClient.ts` (token refresh, error normalization, base URL, `apiRequest`) | **모든 PFM API 호출**(simulation/job/result/viz/admin/chat 세션)에 전파. refresh 회귀 시 PFM 앱 전체 인증 실패 | session1 brief 민감영역(apiClient.ts:265, 278, 304), RF-FINDING-022, 028, 040, 052 / RF-RISK-007, 012 |
| `lib/api/http.ts` (WebSocket/binary/keepalive/`withQuery`) | job monitor WS, visualization WS, 결과 다운로드, unload cleanup에 전파 | session1 brief 민감영역(http.ts:56, 90, 103), RF-FINDING-057, 059 / RF-RISK-013 |
| `lib/api/admin.ts` 및 shared DTO(`simulations.ts`/`jobs.ts`/`results.ts`/`visualizations.ts`/`workflowTypes.ts`) | DTO 변경이 AdminPage3 전 탭 + simulation2 워크플로우 + 목록 카드의 표시/판정 로직에 전파. 일부 계층만 갱신 시 drift(원본 영향 그대로) | RF-FINDING-039, 041, 045 / RF-RISK-004 |
| `lib/supabaseClient.ts` | **CMS/게시판 전 페이지**(board, people, research, home CMS)와 legacy admin gate(cmsl2004/20042)에 전파. PFM 앱에는 영향 없어야 정상(경계 기준) | RF-FINDING-003, 022, 051 / RF-RISK-011, 014 |
| `components/pages/Simulation2Page.tsx` | simulation2 핵심 플로우 전체. 내부 helper(221-534)·constant(545-547)가 page에 결합되어 있어 page 변경이 domain/API 변환 변경과 결합(원본 영향 그대로) | RF-FINDING-001, 047 / RF-RISK-001, 006, 013 |
| `components/pages/AdminPage3.tsx` | admin 운영 전체. URL state(:489-498)가 query key에 연결되어 URL 변경이 query/mutation 흐름에 직접 영향(원본 영향 그대로) | RF-FINDING-002, 029, 048, 061 / RF-RISK-002, 009, 015 |
| `app/providers.tsx` (QueryClient 전역 기본값) | admin polling/user workflow/CMS가 같은 staleTime/refetch 정책 공유 — 전역값 변경이 세 도메인에 동시 전파(원본 관찰 그대로) | RF-FINDING-027 / RF-RISK-005 |
| `tsconfig.json` (strict 계열, unused check, allowJs) | 코드베이스 전체 컴파일. 옵션 강화 시 오류 광역 발생(오류량 확인 필요 — 원본 유지) | RF-FINDING-038, 058 / RF-RISK-007 |
| `next.config.ts` (images.remotePatterns 등) | 빌드/이미지 로딩 전역. 환경(local/Vercel)별 차이로 나타날 수 있음 | RF-FINDING-053, 054 / RF-RISK-010 |
| `components/pages/simulation2/workflowMappers.ts`·`workflowTypes.ts` | workflow stage 표시·WS payload 해석에 전파. 단, 순수 함수 중심이라 "안전하게 먼저 개선 가능한 영역"(session1 brief)이기도 함 | RF-FINDING-041, 045 / RF-RISK-001 |
| 공통 util (`lib/utils.ts`, `hooks/use-toast.ts`, `hooks/use-mobile.ts`) | 게시판 앱·시뮬레이션 앱 양쪽에서 사용될 수 있는 전역 공용 계층 — 변경 시 두 앱 동시 회귀 가능 (추론: 정확한 소비처 목록은 확인 필요) | RF-FINDING-024, 025, 056 / RF-RISK-011 |
| env 변수 (`NEXT_PUBLIC_*`) | 빌드 타임 인라인이므로 변경은 재배포 필요. local/Vercel 값 차이가 환경별 동작 차이로 직결 (추론 보충) | RF-FINDING-051, 052 / RF-RISK-010 |

---

## 5. 게시판 앱 / 시뮬레이션 앱 분리 제약 (프로젝트 CLAUDE.md 제약)

프로젝트 CLAUDE.md가 명시하는 구조적 제약을 리팩토링 전 Phase의 불변 조건으로 둔다.

### 5.1 제약 내용 (CLAUDE.md 원문 근거)

- `/cmsl*` 경로 = **게시판 앱** (Supabase 기반 백엔드), `/simulation2` 경로 = **PFM 시뮬레이션 앱** (자체 개발 백엔드).
- 두 앱은 같은 도메인의 **전혀 다른 앱**이며, **인증부터 모든 부분에서 충돌해선 안 된다**. 단 cmsl이라는 하나의 프론트 상에는 존재해야 한다.
- **별도의 요청이 없다면 게시판 앱에 대한 수정은 지양한다.**

### 5.2 리팩토링 관점의 위험 (RF-RISK-011 상세)

| 위험 시나리오 | 설명 | 관련 이슈 |
|---|---|---|
| auth/storage 통합 오염 | `authTokenStorage` adapter(RF-FINDING-022) 설계 시 PFM token과 Supabase session을 한 adapter/정책으로 묶으면 인증 충돌. 원본도 "PFM token과 Supabase session storage 경계 문서화"를 별도 요구 | RF-FINDING-022 |
| error model 공통화 오염 | `lib/api/errors.ts`(PFM normalized error — 좋은 패턴 S5-ERRORNORM-001)를 CMS에 "확장 검토"(원본)할 때, 검토 없이 공유하면 두 앱의 error 정책이 결합 | RF-FINDING-030, 031 |
| service layer 경계 침범 | CMS domain service(RF-FINDING-003)와 PFM API layer(`lib/api/*`)를 같은 디렉터리/모듈 구조로 합치면 boundary 검사(`npm run test:boundaries`)의 전제가 무너짐 | RF-FINDING-003, 004 |
| env/config 모듈 결합 | `getRequiredPublicEnv` helper(RF-FINDING-051) 자체는 공유 가능하지만, integration별 config module(supabase/emailjs/pfm)은 앱별로 분리 유지 필요 (추론) | RF-FINDING-051, 052 |
| 게시판 앱 무단 수정 | CMS 영역 RF-FINDING(003, 020, 021, 036, 037, 044, 050 등) 작업은 "게시판 앱 수정 지양" 제약과 충돌 — **착수 전 명시적 합의가 선행 조건** (추론) | CMS 영역 RF 전체 |

### 5.3 경계 보존 원칙 (추론)

1. **공유 허용**: 순수 util(문자열/포맷 — 단 RF-FINDING-056 locale 이슈 확인 필요), `components/ui/*` primitive, 범용 hook(use-mobile 등).
2. **공유 금지(앱별 분리 유지)**: 인증/세션 storage, API client/fetch wrapper, error envelope/normalization 정책, service/repository layer, env config module(integration별).
3. **검증**: 두 앱 경계에 걸치는 변경은 `npm run test:boundaries` 통과 + 게시판(`/board`, `/cmsl*` — 접근 가능 범위 내)과 시뮬레이션(`/simulation2`) 양쪽 수동 스모크를 모두 수행. CMS boundary guard 부재(RF-FINDING-004)는 경계 확정 후 보강.
4. **순서**: P0 핫픽스 중 게시판 영역 항목(RF-FINDING-036 attachment rollback)도 "게시판 앱 수정 지양" 제약 대상이므로, Phase 0에서 합의를 먼저 얻는다.

---

## 6. 위험도 분포 요약

| 위험도 (추론) | 건수 | 위험 ID |
|---|---:|---|
| High | 12 | RF-RISK-001, 002, 003, 004, 005, 006, 007, 010, 011, 012, 013, 016 |
| Medium | 5 | RF-RISK-008, 009, 014, 015, 017 |
| Low | 0 | (없음 — 본 문서는 플로우/계약 단위 위험만 등재. 단건 component 수준 위험은 각 Phase 문서에서 작업 항목별로 평가) (추론) |

> 합계 17건 (RF-RISK-001 ~ RF-RISK-017). High 12건 / Medium 5건.

---

> 본 문서의 위험도·완화 전략·Phase 연계는 모두 계획 수립 추론이며, 코드리뷰 원본 근거(파일/라인/원본 ID)와 구분된다. 원본의 "확인 필요" 항목(consolidated-findings.md 5장, 30건)은 해소 전까지 해당 위험의 완화 전략 적용 조건으로 유지한다.
