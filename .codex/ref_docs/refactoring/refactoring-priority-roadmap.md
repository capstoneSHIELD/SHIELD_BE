# 리팩토링 우선순위 로드맵 (Refactoring Priority Roadmap)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001 ~ RF-FINDING-061, 총 61건)
> 원본 코드리뷰: `C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6` (각 세션 `refactoring-brief.md` 우선순위 참조)
> 확정 Phase 구조: Phase 0(준비 + P0 핫픽스 선행 트랙) → Phase 1(type/DTO) → Phase 2(API client/service) → Phase 3(async/polling/error) → Phase 4(hook/state) → Phase 5(component) → Phase 6(route/page/container) → Phase 7(util/config) → Phase 8(검증/정리)

**서두 고지 (반드시 읽을 것)**

- 본 문서의 파일 경로·라인·문제 요약·심각도는 코드리뷰 원본 문서와 `consolidated-findings.md`에 적힌 **근거**를 그대로 옮긴 것이다.
- **난이도/위험도(Low/Medium/High) 평가와 우선순위(P0~P3) 배정은 계획 수립 과정의 (추론)이다.** 코드리뷰 원본에 없는 새 판단이며, 실행 전 각 Phase 문서에서 재검증한다.
- consolidated-findings의 RF-FINDING-001~061 **61건 전부가 아래 로드맵 표에 정확히 한 번씩 배정**되어 있다(누락/중복 배정 없음, 5장 합계 검증 참조).
- consolidated-findings 6장의 **좋은 패턴 14건**(S4-ABORT-001, S5-CANCEL-002, S5-TIMEOUT-002, S4-CLEANUP-004, S3-BOUNDARY-001, S3-COMP-003, S5-ERRORNORM-001, S5-ENDPOINT-001, S5-CONTRACT-003, S5-FALLBACK-001, S2-BOUNDARY-003, S4-CONTEXT-002, S4-STORE-001, S6-FORMATTER-002)은 이슈가 아니므로 로드맵 배정에서 **제외**하며, 리팩토링 시 기준 패턴으로 활용한다.
- 원본 문서에 없는 사항은 단정하지 않고 "확인 필요"로 표기한다. 원본의 "확인 필요" 항목은 그대로 유지한다.

---

## 1. 우선순위 기준 정의

| 우선순위 | 정의 | 판단 기준 | 주 수행 Phase (추론) |
|---|---|---|---|
| **P0** | 즉시 수정 필요 | 버그, 장애, 데이터 불일치, polling 중복, race condition 등 사용자/데이터에 실질 피해가 가능한 결함. 구조 리팩토링과 독립적으로 핫픽스 가능해야 함 | Phase 0 (선행 트랙, 3장) |
| **P1** | 구조 개선 | 책임 분리(거대 컨테이너 분해), API/service 계층 분리, 상태 관리 구조 개선, 타입/DTO/status union 단일화 등 이후 모든 리팩토링의 전제가 되는 구조 작업 | Phase 1~6 |
| **P2** | 코드 품질 | 중복 제거, type 정리, util/formatter 분리, component 분리, request timeout/signal, 개별 error handling 보강, config/constant 정리 | Phase 2~7 |
| **P3** | 장기 개선 | strict 옵션 강화, 테스트/boundary 검사 확장, 성능, 접근성·UX 정책 결정이 선행되어야 하는 항목, 별도 도구 실행이 필요한 검증 | Phase 7~8 및 로드맵 이후 |

보조 규칙 (추론):

- 우선순위는 **긴급도/중요도 축**이고, Phase는 **실행 순서 축**이다. 두 축은 1:1 대응이 아니다(예: P1인 RF-FINDING-039는 Phase 1, P1인 RF-FINDING-001은 Phase 4~5에서 수행).
- 각 세션 `refactoring-brief.md`의 원본 우선순위(P0~P3)를 기본값으로 존중한다. 세션 간 우선순위가 다르거나 조정한 경우 2.5절에 사유를 기록했다.
- 통합 심각도(High/Medium/Low/Suggestion)는 우선순위 판단의 입력이지 결정자가 아니다. 예: RF-FINDING-038(strict 옵션, High)은 단번에 수행 불가하여 P3.

---

## 2. 우선순위 로드맵 표

> 관련 이슈 ID는 RF-FINDING-*만 사용한다. 원본 ID(S1-*~S6-*) 매핑은 `consolidated-findings.md` 2장 참조. **난이도/위험도는 전부 (추론)**.

### 2.1 P0 — 즉시 수정 (4건)

| 우선순위 | 리팩토링 항목 | 관련 이슈 ID | 대상 파일/영역 | 기대 효과 | 난이도 | 위험도 |
|---|---|---|---|---|---|---|
| P0 | job polling fallback in-flight guard 도입 (single-flight polling loop 또는 `pollingInFlightRef`) | RF-FINDING-032 | `components/pages/Simulation2Page.tsx:1605` (1599, 1611-1615) | tick 겹침으로 인한 `getJob`→`listJobEvents`→`listSimulationResults` 중복 호출·상태 순서 꼬임 제거 | Low | Medium |
| P0 | admin URL query NaN-safe parser (`Number(searchParams...)` 안전화 + correction 로직 정리) | RF-FINDING-061 | `components/pages/AdminPage3.tsx:498` (489, 918) | invalid query에서 page/size·query key 불안정 제거, deep link 안정화 | Low | Medium |
| P0 | attachment 저장 실패 보상(rollback) 처리 (storage remove/upload 후 DB update 실패 시) | RF-FINDING-036 | `components/pages/EditNoticePage.tsx:107` | 파일-DB attachment 불일치(데이터 불일치) 방지 | Medium | Medium |
| P0 | required public env helper (`getRequiredPublicEnv`) 도입, non-null assertion 제거 | RF-FINDING-051 | `lib/supabaseClient.ts:5-6`, `components/pages/ContactPage.tsx:26-29` | env 배포 누락 시 불명확한 runtime failure를 명확한 초기화 오류/disabled fallback으로 전환 | Low | Low |

### 2.2 P1 — 구조 개선 (16건)

| 우선순위 | 리팩토링 항목 | 관련 이슈 ID | 대상 파일/영역 | 기대 효과 | 난이도 | 위험도 |
|---|---|---|---|---|---|---|
| P1 | Simulation2Page 거대 컨테이너 단계적 분해 (`useSimulationWorkflow`/`useJobMonitorSession`/`useVisualizationSession`/`useSimulationDraft` + presenter 분리) | RF-FINDING-001 | `components/pages/Simulation2Page.tsx` (3347 lines, 비공백 라인 기준) | 코드베이스 최대 회귀 위험 지점의 변경 영향도 축소, 단위 테스트 가능화 | High | High |
| P1 | AdminPage3 거대 컨테이너 분해 (tab별 container/dialog/table + tab별 query·mutation hook) | RF-FINDING-002 | `components/pages/AdminPage3.tsx` (2942 lines, 비공백 라인 기준) | admin 기능 추가 시 결합도·회귀 위험 감소, 대형 JSX tree 재평가 비용 축소 | High | High |
| P1 | CMS/게시판 Supabase 직접 호출 제거 — domain service/query hook + storage adapter (`useNoticeBoard`/`useGalleryBoard`/`useNoticeEditor` 등) | RF-FINDING-003 | `components/pages/HomePage.tsx`, `NoticeBoardPage.tsx`, `NoticeDetailPage.tsx`, `EditNoticePage.tsx`, `EditGalleryPage.tsx`, `GalleryBoardPage.tsx`, `components/ResearchPageTemplate.tsx` | UI-persistence 결합 해소, RLS/스토리지 정책 변경·mutation 회귀에 강한 구조. RLS/권한 정책 확인 필요 | High | High |
| P1 | route 인증/redirect guard 표준화 (`usePfmAuthGate`/`ProtectedPfmRoute`/`RedirectIfAuthenticated`, `LegacyAdminGate`/`useSupabaseSessionGate`, board edit 권한 기준 명시) | RF-FINDING-005 | `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx`, `app/cmsl2004/page.tsx`, `app/cmsl20042/page.tsx`, `app/board/news/[id]/edit/page.tsx`, `app/board/gallery/[id]/edit/page.tsx` | 인증 정책 재사용·테스트 가능화, 신규 route guard 위치 일관성. RLS 확인 전 과도한 차단 금지 | Medium | Medium |
| P1 | CMS HTML sanitize 정책 확인 및 sanitizer/service 경계 이동 | RF-FINDING-013 | `components/pages/ResearchPageTemplate.tsx:65` (`dangerouslySetInnerHTML`) | 잠재 XSS 경로 차단(확인 필요: 저장 시점 sanitize 여부, trusted content 정책) | Low | Medium |
| P1 | ResultExplorerPanel server state 분리 + stale response guard (`useResultDetail`/`useResultFieldCatalog`/`useResultFieldFiles`/`useResultDownload`, request token 또는 AbortController) | RF-FINDING-016 | `components/simulation/ResultExplorerPanel.tsx:286, 392, 411` | result/field 전환 시 이전 응답이 새 UI에 반영되는 문제 제거, API/cache/race 정책의 UI 결합 해소 | Medium | Medium |
| P1 | JobResultListCard server state hook화 + stale guard/cleanup (`useSimulationJobResults`, React Query 또는 sequence guard) | RF-FINDING-017 | `components/simulation/JobResultListCard.tsx:93, 98, 121` | 다른 simulation의 job/result 목록 표시·unmount 후 state 갱신 제거. `sync:false` 정책 유지 | Medium | Medium |
| P1 | SimulationListCard list state hook화 + stale guard (`useSimulationList`, server pagination 전환 여부 확인) | RF-FINDING-018 | `components/simulation/SimulationListCard.tsx:50, 56, 61, 65, 77` | 이전 page/list 응답이 현재 UI를 덮는 문제 제거, `FETCH_SIZE` 일괄 조회 비용 검토 | Medium | Medium |
| P1 | SessionListCard 책임 분리 (`useChatSessions` + `SessionListView`/`SessionRenameForm`/`SessionDeleteDialog`, action별 mutation state 분리) | RF-FINDING-019 | `components/simulation/SessionListCard.tsx:63, 73, 90, 122, 164, 212` | 검색/페이지 이동/rename/delete reload race 제거. parent callbacks contract 유지 | Medium | Medium |
| P1 | HomePage CMS fetch 안정화 (`useHomeContent` hook: try/catch/finally + typed error state + typed view model) | RF-FINDING-021 | `components/pages/HomePage.tsx:18, 36, 66` | Supabase throw 시 home loading 고착 제거, error UI 도입 | Low | Low |
| P1 | AdminPage3 mutation/cache/polling 정책 캡슐화 (`useSyncAdminJobMutation` 등 mutation hook, enabled query 전환, `buildAdminQueryKeys`, tab별 refresh hook) | RF-FINDING-029 | `components/pages/AdminPage3.tsx:510/744/729, 666/676, 697, 1080, 597, 617, 1187, 648` | cache 정책 변경 영향의 container 집중 해소, key rename 시 invalidation 누락·cache/local 불일치·중복 요청 방지 | High | High |
| P1 | job monitor WS·visualization WS/sync interval lifecycle 격리 (`useJobMonitorSession`/`useVisualizationSession`) — RF-FINDING-001 분해의 핵심 축 (추론) | RF-FINDING-033 | `components/pages/Simulation2Page.tsx:1674, 1949` | WebSocket race/cleanup 회귀 위험 격리, viz 상태 동기화 안정화 | High | High |
| P1 | API status union/DTO shared module 단일화 (shared DTO + admin 확장 type, workflow stage는 mapper 파생) | RF-FINDING-039 | `lib/api/admin.ts`, `lib/api/simulations.ts`, `lib/api/jobs.ts`, `lib/api/results.ts`, `lib/api/visualizations.ts`, `components/pages/simulation2/workflowTypes.ts` | backend contract 변경 시 계층 간 drift 제거, admin/job/result 화면 상태 불일치 방지. admin API가 의도적으로 다른 계약인지 백엔드 명세 확인 필요 | High | Medium |
| P1 | API response parser 전략 — call site 타입 명시 강화, 핵심 endpoint parser/guard/schema 도입 (`apiRequest<T = any>`·`as T` 단정 축소) | RF-FINDING-040 | `lib/apiClient.ts:380, 395`, `lib/api/labserverTrameClient.ts:500-502` | 응답 shape 불일치의 조기 발견. 기본값 `unknown` 전환은 전체 call site 영향이 커서 장기 단계 적용 | Medium | Medium |
| P1 | `WorkflowState.parameters` 타입 분리 (`SimulationParametersDto`/`WorkflowParameters`/`EditableSimulationParameters`) | RF-FINDING-041 | `components/pages/simulation2/workflowTypes.ts:72` | simulation parameter 계약 변경을 컴파일 타임에 검출. workflow 리팩토링과 함께 진행 | Medium | Medium |
| P1 | PATCH body DTO builder/mapper 분리 (`buildUpdateSimulationBody(formState): UpdateSimulationBody` + schema/guard) | RF-FINDING-042 | `components/pages/Simulation2Page.tsx:2378` | form/view state와 API request DTO 결합 해소. job submit/update/restore 흐름과 함께 테스트 | Medium | Medium |

### 2.3 P2 — 코드 품질 (32건)

| 우선순위 | 리팩토링 항목 | 관련 이슈 ID | 대상 파일/영역 | 기대 효과 | 난이도 | 위험도 |
|---|---|---|---|---|---|---|
| P2 | board dynamic id parser/not-found 정책 표준화 (`parseBoardId`, invalid id error/not-found UI) | RF-FINDING-006 | `app/board/news/[id]/page.tsx:9`, `app/board/gallery/[id]/page.tsx:9`, `components/pages/EditNoticePage.tsx:46` | invalid URL 처리 UX 일관화, editor 무한 loading 제거 | Low | Low |
| P2 | global error boundary/route fallback 전략 정리, admin guard 상태 presenter 분리 | RF-FINDING-007 | `app`(error.tsx 부재), `app/cmsl20043/page.tsx:14`, `components/pages/AdminPage3.tsx:1139` | route별 오류 fallback 일관화. 로깅/복구 UX 정책 필요(확인 필요) | Medium | Low |
| P2 | board session ownership 단일화 (route/server 또는 client gate 중 하나, 공통 session hook 검토) | RF-FINDING-009 | `components/pages/NoticeBoardPage.tsx:23`, `components/pages/GalleryDetailPage.tsx:35` | session source 중복으로 인한 상태 불일치·불필요한 auth 호출 제거 | Low | Low |
| P2 | WorkspaceTabsCard props pass-through 정리 (workspace domain hook/presenter 경계 재정의) | RF-FINDING-010 | `components/simulation/WorkspaceTabsCard.tsx:14` | 15개 이상 props/callback contract 비대화 차단. 상위 컨테이너 책임 분리(RF-FINDING-001) 선행 | Medium | Medium |
| P2 | MemberDetailModal 접근성 보강 (Radix Dialog 전환 또는 `role="dialog"`/`aria-modal`/focus trap) | RF-FINDING-011 | `components/MemberDetailModal.tsx:18` | keyboard/screen reader 접근성 확보 | Low | Low |
| P2 | NewsPage list/pagination presenter 정리 (row action model/action slot, 공통 pagination presenter) | RF-FINDING-012 | `components/pages/NewsPage.tsx:29, 65` | list UI 재사용성 확보, action policy의 props contract 노출 제거 | Low | Low |
| P2 | list index key 제거 — stable id 기반 key (message id, event timestamp/type, item.url, CMS section id) | RF-FINDING-014 | `components/pages/Simulation2Page.tsx:3355/3360/3368/3419`, `components/ImageCarousel.tsx:32-33`, `components/ResearchPageTemplate.tsx:142` | reconciliation 안정화, reorder/삽입 시 row/slide state 오재사용 방지 | Low | Low |
| P2 | CMS list fetch stale response guard (notice/gallery 공통 list query hook + request sequence) | RF-FINDING-020 | `components/pages/NoticeBoardPage.tsx:75`, `components/pages/GalleryBoardPage.tsx:42` | page/search 변경 중 이전 응답 반영·count 불일치 방지. RF-FINDING-003(P1)과 함께 진행 (추론) | Low | Low |
| P2 | PFM token storage helper 단일화 (`authTokenStorage` adapter, Supabase storage 경계 문서화) | RF-FINDING-022 | `lib/auth.ts:66-67`, `lib/apiClient.ts:38`, `lib/supabaseClient.ts:21` | token persistence 정책 drift 방지. 로그인/refresh/401 retry와 연결된 민감 영역 — Phase 2 API client 변경과 함께 계획 | Low | Medium |
| P2 | use-toast listener effect dependency 정리 (mount-only subscription 검토) | RF-FINDING-024 | `hooks/use-toast.ts:131, 176` | 불필요한 effect 재구독 제거. shadcn 패턴·기존 toast 테스트 확인 | Low | Low |
| P2 | use-mobile 초기값 tri-state 또는 mounted guard | RF-FINDING-025 | `hooks/use-mobile.ts:20` | SSR/초기 render의 desktop 오판 제거 | Low | Low |
| P2 | ResearchHighlightsSlider empty guard (effect 초기 `highlights.length === 0` guard) | RF-FINDING-026 | `components/ResearchHighlightsSlider.tsx:32` | empty array에서 interval tick 후 index `NaN` 방지 | Low | Low |
| P2 | QueryClient 도메인별 query 정책 명시/문서화 (admin/simulation/CMS freshness·retry·gcTime) | RF-FINDING-027 | `app/providers.tsx:15-16` | 정책 의도 가시화. retry/gcTime이 제품 요구와 맞는지 확인 필요 | Low | Low |
| P2 | `apiRequest` timeout/AbortSignal 옵션 + refresh timeout + retryable error 정책 명시 | RF-FINDING-028 | `lib/apiClient.ts:151, 236, 278` | refresh hang으로 인한 보호 요청 지연·loading 장기화 방지, caller별 cancellation 중복 제거. token refresh/error normalization 민감 영역 — 신중히 진행 | Medium | High |
| P2 | legacy `/api/chat` error envelope/validation 표준화 (request schema + 최소 response parser, TS route handler 전환 검토) | RF-FINDING-030 | `api/chat.js:70/80/88/103`, `lib/api/legacyAiChat.ts:17/21` | error UX/보안/로깅 정책 일치, 잘못된 입력의 external API 직접 전달 차단. legacy 유지 여부 확인 필요(선행) | Medium | Medium |
| P2 | EmailJS 호출 adapter 분리 (`sendContactEmail` wrapper + error mapping) | RF-FINDING-031 | `components/pages/ContactPage.tsx:13, 25` | 외부 연동 실패 처리의 UI 분산 해소. env 문제는 RF-FINDING-051(P0)에서 선행 처리 | Low | Low |
| P2 | legacy simulation polling in-flight guard (유지 대상이면 guard 추가, 아니면 제거/격리) | RF-FINDING-034 | `components/pages/PFMSimulationPage.tsx:468` | legacy 흐름 중복 요청 방지. RF-FINDING-032(P0)와 동일 패턴이나 legacy 유지 여부 확인 필요 (추론: 확인 후 P0 패턴 이식) | Low | Low |
| P2 | job polling `getJob` 실패 처리 — 연속 실패 카운트 + inline notice | RF-FINDING-035 | `components/pages/Simulation2Page.tsx:1469` | 반복 실패/인증 실패의 사용자 가시화 | Low | Low |
| P2 | notice pin/delete mutation 실패 피드백 (mutation hook + toast/error state) | RF-FINDING-037 | `components/pages/NoticeBoardPage.tsx:98` | 실패 원인 파악 가능한 UX. RF-FINDING-003 service 분리와 함께 | Low | Low |
| P2 | `extractWarnings` 구조 우회 제거 (`isRecord`/`isWarningPayload` guard 기반 narrowing) | RF-FINDING-043 | `components/pages/Simulation2Page.tsx:414-422, 417` | error details shape 변경 시 warning 누락/오표시 방지 | Low | Low |
| P2 | CMS content DTO/view model/form model 타입화 (pageKey별 또는 discriminated union + localized getter) | RF-FINDING-044 | `components/pages/HomePage.tsx`, `EditPageContentForm.tsx`, `components/ResearchPageTemplate.tsx`, `EditHomePageForm.tsx`, `introduction/Section2_CoreCapabilites.tsx`, `Section3_ResearchAreas.tsx` | CMS schema drift·form field 오타의 컴파일 타임 검출. CMS 데이터 shape 확인 필요 | Medium | Medium |
| P2 | WS payload DTO 정의 (`JobMonitorMessageDto` union + parser result) | RF-FINDING-045 | `components/pages/simulation2/workflowMappers.ts:38, 53, 63` | websocket contract 변경 시 조용한 fallback 누락 방지. RF-FINDING-039와 연계 (추론) | Low | Medium |
| P2 | Simulation2Page pure helper/constant 모듈 분리 (`workflowMapper`/`parameterMapper`/`errorMapper`/`downloadUtil` + workflow config constant) | RF-FINDING-047 | `components/pages/Simulation2Page.tsx:221-534, 545-547` | page 변경과 domain/API 변환 변경의 결합 해소, 단위 테스트 가능화. 행동 변경 없이 pure function부터 이동 | Medium | Low |
| P2 | AdminPage3 formatter/file util 분리 (`formatDate`/`formatBytes`/numeric parser/`saveBlobDownload` → admin view util 또는 common formatter) | RF-FINDING-048 | `components/pages/AdminPage3.tsx:262-339` | admin tab 분리(RF-FINDING-002) 시 중복 복사 방지 | Low | Low |
| P2 | legacy `validateParams`/`parseLLMResponse` 모듈 분리 + parse result 명시 | RF-FINDING-049 | `components/pages/PFMSimulationPage.tsx:158, 196-219` | form UI와 LLM/domain parser 결합 해소. legacy 유지 여부 확인 필요 | Low | Low |
| P2 | `sanitizeForStorage`/blob download helper 중복 제거 (`lib/storage/filename` 또는 공통 util) | RF-FINDING-050 | `components/pages/AdminPage.tsx:43`, `EditNoticePage.tsx:14`, `EditGalleryPage.tsx:13`, `components/simulation/ResultExplorerPanel.tsx:197` (+ `Simulation2Page`/`AdminPage3` 유사 helper) | 파일명/다운로드 정책 변경 시 수정 누락 방지. backend/storage 정책 충돌 여부 확인 | Low | Low |
| P2 | PFM API env canonical 정리 (required error 안내와 실제 사용 env 일치, legacy fallback 문서화) | RF-FINDING-052 | `lib/apiClient.ts:217-231` | env 설정 오류 시 안내 정확화 | Low | Low |
| P2 | `images.remotePatterns` allowlist 제한 | RF-FINDING-053 | `next.config.ts:4-8` | 이미지 출처 정책 관리 강화. 전체 host 허용이 CMS 요구사항인지 확인 필요 | Low | Medium |
| P2 | next.config 주석 인코딩 정리 (UTF-8 한국어 또는 영어) | RF-FINDING-054 | `next.config.ts:14-19` | 설정 의도 가독성 회복 | Low | Low |
| P2 | colormap/page size/polling interval constant 정리 (shared constant 또는 feature config/props 기본값) | RF-FINDING-055 | `components/simulation/VisualizationControlBar.tsx:29`, `trame/TrameControlPanel.tsx:33`, `trame/CompositeDialog.tsx:37`, `SessionListCard.tsx:44`, `SimulationListCard.tsx:28-30`, `components/pages/adminPolling.ts:4` | 옵션/정책 파일별 drift 방지. 실제로 도메인별 옵션이 다른지 확인 | Low | Low |
| P2 | `withQuery` 등 API boundary helper 인자 타입 좁히기 (`QueryParams` 일관 적용) | RF-FINDING-057 | `lib/api/http.ts:12, 90, 103` | query param 직렬화 대상의 컴파일 타임 제한 | Low | Low |
| P2 | admin API 파일의 helper 재-export 정리 (호출부가 `lib/api/http` 직접 import) | RF-FINDING-059 | `lib/api/admin.ts:31` | admin API boundary 역할 축소 | Low | Low |

### 2.4 P3 — 장기 개선 (9건)

| 우선순위 | 리팩토링 항목 | 관련 이슈 ID | 대상 파일/영역 | 기대 효과 | 난이도 | 위험도 |
|---|---|---|---|---|---|---|
| P3 | CMS/Supabase boundary 정적 검사/테스트 확대 (service 경계 확정 후) | RF-FINDING-004 | `scripts/check-pfm-api-boundaries.mjs:6` | CMS 리팩토링 중 UI-persistence 결합 재발 방지. RF-FINDING-003 완료 후 추가 | Medium | Low |
| P3 | route group layout 필요성 검토 (admin/workbench/viewer) | RF-FINDING-008 | `app/layout.tsx:87` | layout 분리 필요 여부 확인 필요 — 제품 UX 결정 선행 | Medium | Medium |
| P3 | slider motion variants 재생성 정리 (static 이동 또는 `useMemo`) | RF-FINDING-015 | `components/ResearchHighlightsSlider.tsx:80` | motion subtree 확대 대비 memoization 용이성 확보 (현재 영향 낮음) | Low | Low |
| P3 | LanguageProvider value `useMemo` 확인 및 persistence hook 분리 검토 | RF-FINDING-023 | `components/LanguageProvider.tsx:230` | provider 변경 시 consumer render 영향 축소 (확인 필요) | Low | Low |
| P3 | tsconfig strict 계열 옵션 단계적 강화 (`strict`/`noImplicitAny`/`strictNullChecks`) | RF-FINDING-038 | `tsconfig.json:8, 10, 29, 32` | any/null/API 계약 위반의 컴파일 타임 검출. 단번 전체 전환 금지, strict 시 오류량 측정 필요(확인 필요) | High | High |
| P3 | Three 버전 호환 `as any` 정리 (wrapper type 또는 좁은 assertion) | RF-FINDING-046 | `components/reactbits/ColorBends.tsx:180` | 라이브러리 API 변경의 타입 보호 | Low | Low |
| P3 | `formatRelativeTime` locale 인자/i18n boundary 연결 | RF-FINDING-056 | `lib/utils.ts:10` | 다국어 UI에서 locale 정책 일치 (확인 필요) | Low | Low |
| P3 | unused check/lint rule 보완, JS API route TS 전환 또는 별도 lint/typecheck 정책 | RF-FINDING-058 | `tsconfig.json:30-31, 8` (`noUnusedParameters`/`noUnusedLocals`/`allowJs`) | 미사용 type/util 누적 방지, import/export 정리 일관화 | Medium | Low |
| P3 | barrel export/circular import 도구 검증 (madge/dependency-cruiser 등) | RF-FINDING-060 | 전체 (라인 확인 필요) | 순환 import 존재 시 런타임 초기화 순서 문제 사전 발견 (확인 필요 — 정적 rg로 미확정) | Low | Low |

### 2.5 원본 refactoring-brief 우선순위 대비 조정 기록 (추론)

각 세션 brief의 원본 우선순위를 기본값으로 채택했다. 아래는 조정했거나 세션 간 우선순위가 달랐던 항목이다.

| RF ID | 원본 brief 우선순위 | 로드맵 배정 | 조정 사유 (추론) |
|---|---|---|---|
| RF-FINDING-003 | S2 brief P2 (CMS board data access) / S1 brief 2순위·S3 brief P1·S5 brief P1 | **P1** | 통합 심각도 High. 라인 단위로 구체화한 S3/S5 및 S1 상위 순위 채택. S2의 P2는 board 영역에 한정된 관점 |
| RF-FINDING-007 | S2 brief에서 loading/error presenter=P2, global error boundary=P3로 분리 기재 | **P2** | 통합 RF가 하나의 우선순위만 가지므로 P2로 묶음. global `error.tsx` 도입 하위 작업은 원본대로 P3 성격(로깅/복구 UX 정책 확인 필요)임을 유지 |
| RF-FINDING-013 | 세션 brief 우선순위표 미등재 (S3 findings Medium) | **P1** | 잠재 XSS 경로이므로 sanitize 정책 "확인 필요"를 조기에 해소할 필요. 확인 결과 trusted/저장 시점 sanitize가 확인되면 P2로 강등 가능 |
| RF-FINDING-016~019 | S3 brief P1·S4 brief P1 / S5 brief P2 (ResultExplorer/List cards) | **P1** | hook 추출(구조 분리)을 동반하므로 라인 단위 지적이 구체적인 S3/S4의 P1 채택. S5의 P2는 guard 추가만 본 관점 |
| RF-FINDING-021 | S4 brief P2 (HomePage CMS fetch) | **P1** | 통합 심각도 High(SEV-DIFF-02, S5-LOADING-001 High). 사용자 노출 첫 화면의 loading 고착 위험 + `useHomeContent` 구조 도입 동반 |
| RF-FINDING-030 | S5 brief P2 (legacy `/api/chat`) / 통합 심각도 High | **P2 유지** | legacy 유지 여부 확인(원본 "확인 필요")이 선행되어야 하므로 brief의 P2 존중. 유지 확정 시 P1 승격 검토 |
| RF-FINDING-034 | 세션 brief 우선순위표 미등재 (S5 polling-review Medium) | **P2** | RF-FINDING-032(P0)와 동일 패턴이나 legacy 화면. legacy 유지 여부 확인 후 P0 패턴 이식 또는 제거 |
| RF-FINDING-040 | S5 brief P2 (API response typing) / S6 brief P1 (API response parser 전략) | **P1** | Phase 1(type/DTO/contract)과 정합. 단, 기본값 `unknown` 전환은 S6 민감 영역 경고대로 단계 적용 |
| RF-FINDING-047 | S6 brief P2 / 통합 심각도 High(SEV-DIFF-04) | **P2 유지** | 행동 변경 없는 pure function 이동이라 위험이 낮고, brief가 "안전하게 먼저 개선 가능한 영역"으로 분류. 심각도보다 작업 성격 우선 |

P0 4건(RF-FINDING-032/036/051/061)은 각각 S4·S5·S6 brief의 P0 항목과 정확히 일치하며 조정 없음.

---

## 3. P0 핫픽스 선행 트랙 (Phase 0)

### 3.1 트랙 원칙

- P0 4건은 **구조 리팩토링과 독립적으로 Phase 0에서 조기 수행 가능**하다. 세션 4/5/6 brief의 P0 권고와 일치한다.
- 각 항목은 기존 코드 구조를 유지한 채 **국소 수정(guard/parser/보상 처리/helper)**으로 처리한다. 거대 컨테이너 분해(P1)를 기다리지 않는다.
- 단, 핫픽스가 이후 구조 리팩토링(Phase 3~4의 polling/hook 분리)에서 이동될 코드 위에 얹히므로, 수정 지점과 테스트를 Phase 문서에 명시해 이중 작업을 방지한다. (추론)

### 3.2 항목별 실행 메모

| 순번 | 항목 | RF ID (원본 ID) | 수정 지점 | 기준 패턴 / 주의 |
|---|---|---|---|---|
| ① | Simulation2Page job polling in-flight guard | RF-FINDING-032 (S4-RACE-001=S4-EFFECT-001(상세), S5-POLLING-001=S5-RACE-001(상세)) | `components/pages/Simulation2Page.tsx:1605` (1599, 1611-1615) | 같은 파일의 visualization sync guard(S4-ABORT-001, :2057/:2100)가 기준 패턴. WS fallback/terminal status/result availability와 함께 테스트 |
| ② | AdminPage3 URL NaN-safe parser | RF-FINDING-061 (S4-URL-001, S2-STATE-001, S4-EFFECT-005(연관)) | `components/pages/AdminPage3.tsx:498` (489, 918) | safe integer parser 우선 도입. `useAdminUrlState` correction hook 전체 도입은 Phase 4~6으로 이연 가능 (추론). 기존 deep link/query 호환성 보존 |
| ③ | EditNoticePage attachment rollback | RF-FINDING-036 (S5-ROLLBACK-001) | `components/pages/EditNoticePage.tsx:107` | storage remove/upload 후 DB update 실패 시 보상 정책. 실제 storage path/URL parsing 확인 필요. Supabase delete/upload 흐름은 S1 brief 민감 영역 — 운영 데이터 주의 |
| ④ | required public env helper | RF-FINDING-051 (S6-ENV-001=S6-NULLABLE-001, S6-ENV-002=S6-NULLABLE-002) | `lib/supabaseClient.ts:5-6`, `components/pages/ContactPage.tsx:26-29` | `getRequiredPublicEnv` + integration별 config module(`lib/config/emailjs.ts` 등) + disabled/error fallback. env 이름/배포 설정 확인 후 적용 |

### 3.3 P0 검증 명령어 (package.json 확인된 실제 스크립트)

```bash
npm run lint              # next lint
npm run build             # next build
npm run test:run          # vitest run
npm run test:coverage     # vitest run --coverage
npm run test:boundaries   # PFM API boundary 정적 검사 (scripts/check-pfm-api-boundaries.mjs)
npx tsc --noEmit          # 후보 — 전용 typecheck 스크립트 없음 (확인 필요). tsconfig strict 계열 off라 검출력 약함
```

수동 검증 (Playwright MCP 사용 가능):

- local: `http://localhost:3000` — ① job 실행→polling 네트워크 요청 겹침 없음, ② `/cmsl20043?page=abc` 류 invalid query 동작, ③ 공지 첨부 수정 실패 시 데이터 정합, ④ env 미설정 시 명확한 실패 메시지
- production: `https://pfm.cmsl-kookmin.com/simulation2`

> 주의: ③④는 게시판 앱(Supabase) 영역을 포함한다. 프로젝트 규칙상 게시판 앱 수정은 지양 대상이므로, P0 결함 수정이라는 근거와 변경 범위를 커밋/PR에 명시하고 최소 수정으로 진행한다. (추론)

---

## 4. 우선순위별 요약 통계

### 4.1 우선순위 분포 (61건 전수 배정)

| 우선순위 | 건수 | 비율 | 해당 RF-FINDING |
|---|---:|---:|---|
| P0 | 4 | 6.6% | 032, 036, 051, 061 |
| P1 | 16 | 26.2% | 001, 002, 003, 005, 013, 016, 017, 018, 019, 021, 029, 033, 039, 040, 041, 042 |
| P2 | 32 | 52.5% | 006, 007, 009, 010, 011, 012, 014, 020, 022, 024, 025, 026, 027, 028, 030, 031, 034, 035, 037, 043, 044, 045, 047, 048, 049, 050, 052, 053, 054, 055, 057, 059 |
| P3 | 9 | 14.8% | 004, 008, 015, 023, 038, 046, 056, 058, 060 |
| **합계** | **61** | 100% | RF-FINDING-001 ~ 061, 누락/중복 없음 |

별도 제외: 좋은 패턴 14건(consolidated-findings 6장) — 이슈가 아니므로 미배정, 기준 패턴으로 활용.

### 4.2 우선순위 × 통합 심각도 교차표 (추론: 배정 결과 집계)

| | High (15) | Medium (31) | Low (11) | Suggestion (4) |
|---|---|---|---|---|
| **P0 (4)** | 032, 036, 051, 061 | - | - | - |
| **P1 (16)** | 001, 002, 003, 021, 029, 039, 041, 042 | 005, 013, 016, 017, 018, 019, 033, 040 | - | - |
| **P2 (32)** | 030, 047 | 006, 007, 009, 010, 011, 014, 020, 022, 026, 027, 028, 034, 035, 037, 043, 044, 045, 048, 049, 050, 052, 053 | 012, 024, 025, 031, 054, 055, 057, 059 | - |
| **P3 (9)** | 038 | 058 | 023, 046, 056 | 004, 008, 015, 060 |

High 심각도 중 P2/P3 배정 3건(030, 047, 038)의 사유는 2.5절 참조 (legacy 확인 선행 / 행동 무변경 이동 / 단계 적용 필수).

### 4.3 난이도/위험도 분포 (추론)

| 구분 | High | Medium | Low |
|---|---:|---:|---:|
| 난이도 | 7 (001, 002, 003, 029, 033, 038, 039) | 18 | 36 |
| 위험도 | 7 (001, 002, 003, 028, 029, 033, 038) | 20 | 34 |

난이도·위험도 모두 High인 항목(001, 002, 003, 029, 033, 038)은 한 번에 수행하지 않고 Phase 계획에서 단계 분할이 필수다. (추론)

### 4.4 우선순위 → 주 수행 Phase 매핑 (추론, 후속 Phase 문서에서 확정)

| 우선순위 | 주 수행 Phase | 비고 |
|---|---|---|
| P0 | Phase 0 선행 트랙 | 3장 참조 |
| P1 | Phase 1 (039, 040, 041, 042) → Phase 2~3 (003, 021, 029 일부, 028 연계) → Phase 3~4 (016~019, 032 후속, 033) → Phase 4~6 (001, 002, 005, 013) | 타입/계약 → 계층 → 비동기 → 구조 순으로 의존 관계 정렬 |
| P2 | 각 영역 Phase에 분산 (Phase 2: 022, 028, 030, 031 / Phase 3: 020, 034, 035, 037 / Phase 4: 024~027 / Phase 5: 010~012, 014, 043~045 / Phase 6: 006, 007, 009 / Phase 7: 047~059) | 해당 영역 P1 작업과 함께 수행 시 비용 절감 |
| P3 | Phase 7~8 및 로드맵 이후 | 038(strict)은 Phase 1~7 전반의 타입 개선 누적 후 Phase 8에서 옵션 강화 시도 |

확정 Phase 배정은 `phased-refactoring-plan.md` 9.1(확정 표)과 9.3(변경 사유)을 따른다.
