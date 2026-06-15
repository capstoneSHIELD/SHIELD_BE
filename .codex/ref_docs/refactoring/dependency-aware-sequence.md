# 의존성 기반 리팩토링 순서 (Dependency-Aware Sequence)

> 기반 문서: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001~061)
> 원본 근거: `C:\pfm-FE\.codex\ref_docs\codereview\session1~session6` (각 refactoring-brief.md 및 findings)
> 목적: **하위 계층을 먼저 정리해야 상위 계층 리팩토링이 안전해지는 선후관계**를 정의한다. 모든 Phase 문서(Phase 0~8)는 본 순서를 전제로 작업을 배치한다.
> 구체적 실행 순서는 `refactoring-execution-order.md`(Wave 설계)가 본 문서의 원칙을 backlog Task 단위로 확정한 것이며, 순서 기술이 다를 경우 execution-order 문서를 우선한다.

---

## 1. 의존성 원칙 — 왜 이 순서인가

### 1.1 계층 의존 방향

권장 작업 순서는 코드의 **import/사용 방향의 역순**, 즉 "가장 많이 의존받는 계층부터"이다.

```text
type/DTO/config  →  API client/service  →  query/mutation/async flow
→  hook/state management  →  component  →  page/container/routing
→  test/verification (최종 회귀 확인)
```

- **타입이 바뀌면 API 계층이 영향받는다.** `SimulationStatus`/`JobStatus` 등 status union과 `JobSummary`/`ResultDetail` 등 DTO는 `lib/api/admin.ts`·`simulations.ts`·`jobs.ts`·`results.ts`·`visualizations.ts`와 `workflowTypes.ts`에 중복 정의되어 있다(RF-FINDING-039). 단일화 전에 API/hook을 먼저 분리하면, 분리된 모듈들이 **중복 타입을 그대로 복사**해 가게 되어 단일화 시 분리 작업을 다시 손대야 한다. (추론)
- **API 계층이 바뀌면 hook이 영향받는다.** `apiRequest`에 signal/timeout 옵션이 추가되면(RF-FINDING-028) polling/stale-guard hook의 시그니처(AbortController 전달 방식)가 달라진다. hook을 먼저 만들고 API 계층을 나중에 고치면 모든 hook의 cancellation 코드를 재작성해야 한다. (추론)
- **hook이 바뀌면 component/page가 영향받는다.** `Simulation2Page.tsx`(3347 line)와 `AdminPage3.tsx`(2942 line)의 분해(RF-FINDING-001/002)는 추출될 hook(`useJobMonitorSession`, tab별 query hook 등)의 계약이 안정된 뒤에야 presenter/container 경계를 확정할 수 있다.
- **test/verification은 각 단계마다 수행하되**, boundary guard 확대(RF-FINDING-004)는 service 경계가 먼저 정해져야 의미가 있다 — session1 brief 원문: "service 경계를 먼저 정한 뒤 검사 추가".

### 1.2 원칙의 예외 두 가지

1. **P0 핫픽스는 의존성 없이 즉시 가능.** 운영 결함(RF-FINDING-032, 061, 036, 051)은 계층 정리를 기다리지 않고 최소 수정으로 선행한다. 단, 핫픽스는 "최소 guard/parser/helper"로 한정하고 구조 개선은 해당 Phase로 미룬다. (추론: 범위 비대화 방지)
2. **pure function은 의존성이 없어 언제든 선행 가능.** `Simulation2Page` 내부 `normalizeComposition`/`formatAssistantContent`/`extractWarnings` 등 pure helper 분리(RF-FINDING-047, S6-UTIL-001)는 행동 변경 없는 파일 이동이므로 hook 추출(RF-FINDING-001/033)보다 먼저 할 수 있고, 먼저 해야 hook 추출 시 page와 함께 이동할 짐이 줄어든다. session6 brief 원문: "행동 변경 없이 pure function부터 이동", session1 brief 원문: "리팩토링은 테스트 추가 또는 pure helper 분리부터 시작한다".

### 1.3 확정 Phase 구조와의 매핑

| 의존성 계층 | 해당 Phase |
|---|---|
| (선행) P0 핫픽스 | Phase 0 |
| type/DTO/API contract | Phase 1 |
| API client/service | Phase 2 |
| async flow/polling/error | Phase 3 |
| hook/state | Phase 4 |
| component | Phase 5 |
| page/container/routing | Phase 6 |
| util/config/constant/validation | Phase 7 (단, 일부는 선행 — 4.1 참조) |
| test/build/회귀 검증 | Phase 8 (+ 각 Phase 종료 시 반복) |

> 주의: util/config는 Phase 7이지만, 의존성 관점에서 **enabler 역할을 하는 util 작업**(RF-FINDING-047의 pure helper 분리, RF-FINDING-051/052의 env helper)은 앞 Phase의 준비 단계로 앞당겨 수행한다. 충돌 처리 기준은 4.1 참조. (추론)

---

## 2. 순서 표

각 행은 "먼저 해야 할 작업"이 완료(또는 계약 확정)되어야 "이후 가능한 작업"이 안전해지는 관계다. 같은 순서 번호 내 항목은 상호 의존이 없다. 순서 배정 자체는 계획 수립 과정의 판단이다. (추론)

| 순서 | 먼저 해야 할 작업 | 이후 가능한 작업 | 이유 | 관련 이슈 |
|---|---|---|---|---|
| 0 | **P0 핫픽스 4건** — ① Simulation2Page job polling in-flight guard(`Simulation2Page.tsx:1605`) ② AdminPage3 URL NaN-safe parser(`AdminPage3.tsx:498`) ③ EditNoticePage attachment rollback(`EditNoticePage.tsx:107`) ④ env required helper(`lib/supabaseClient.ts:5-6`, `ContactPage.tsx:26-29`) | 모든 후속 Phase (안전한 기반 위에서 진행) | **의존성 없이 즉시 가능.** 운영 결함 수정이며 다른 계층 정리를 기다릴 이유가 없음. 단 ①은 최소 `pollingInFlightRef` 수준으로, ②는 safe parser 함수 수준으로 한정하고 구조화(WS hook 분리, `useAdminUrlState`)는 Phase 3/4로 미룸 (추론) | RF-FINDING-032, RF-FINDING-061, RF-FINDING-036, RF-FINDING-051 |
| 1 | **pure helper 분리 (Simulation2Page / AdminPage3 / PFMSimulationPage)** — page 파일 내 parser/formatter/mapper/constant를 `simulation2` feature 모듈·admin view util·legacy parser 모듈로 행동 변경 없이 이동 + 단위 테스트 추가 | Simulation2Page hook 추출(RF-001/033), AdminPage3 tab 분리(RF-002), helper 중복 통합(RF-050) | **구체 사례 ③: `Simulation2Page` helper 분리(S6-UTIL-001)는 hook 추출보다 먼저 가능 — 순수 함수라 의존성이 없고**, 먼저 분리해야 hook 추출 시 이동 단위가 작아지고 테스트 가능한 경계가 생김. session6 brief: "행동 변경 없이 pure function부터 이동" | RF-FINDING-047, RF-FINDING-048, RF-FINDING-049, RF-FINDING-050 |
| 1 | **env/config canonical화** — `getRequiredPublicEnv` helper(P0 ④에서 도입)를 기반으로 PFM API URL env 안내 일치화, integration별 config module 정리 | API client 정리(RF-028), 외부 연동 adapter(RF-030/031) | env 접근이 config module로 모여야 API client/adapter 분리 시 env 참조가 한 곳에서 관리됨 (추론). env 이름/배포 설정 확인 후 적용(원본 주의사항) | RF-FINDING-051, RF-FINDING-052, RF-FINDING-053(독립, 병행 가능) |
| 2 | **status union / 공유 DTO 단일화** — `SimulationStatus`/`JobStatus`/`VisualizationStatus`와 `Composition`/`JobSummary·Detail·Event`/`ResultSummary·Detail` 계열을 shared module로 모으고 admin은 확장 type, workflow stage는 mapper로 파생. **선행 조건: admin API가 의도적으로 다른 계약인지 백엔드 명세 확인 필요(원본 보존)** | admin/job hook 분리(RF-002, RF-029), list card hook(RF-017), workflow mapper DTO(RF-045), CMS DTO 작업과는 독립 | **구체 사례 ①: status union 단일화(S6-DUPTYPE-001~005)가 선행되어야 admin/job hook 분리가 안전.** hook을 먼저 분리하면 중복 타입이 hook 모듈로 복사되어 단일화 시 hook 시그니처를 전부 재수정해야 함 (추론). session6 brief P1: "API status union을 shared DTO로 모으고 workflow stage는 mapper로 분리" | RF-FINDING-039 → RF-FINDING-002, RF-FINDING-029, RF-FINDING-017, RF-FINDING-045 |
| 2 | **workflow parameter / PATCH body DTO 정의** — `SimulationParametersDto`/`EditableSimulationParameters`/`UpdateSimulationBody` builder 분리 | Simulation2Page workflow hook 추출(RF-001), parameter edit presenter 분리 | `WorkflowState.parameters: Record<string, any>`(workflowTypes.ts:72)와 PATCH body 직접 조립(Simulation2Page.tsx:2378)이 타입화되어야 workflow hook의 입출력 계약이 확정됨. 원본 주의사항: "job submit/update/restore 흐름과 함께 테스트" | RF-FINDING-041, RF-FINDING-042, RF-FINDING-043 → RF-FINDING-001 |
| 2 | **CMS content DTO/view model 정의** — pageKey별 DTO/form model, localized getter 타입화. **CMS 데이터 shape 확인 필요(원본 보존)** | CMS hook(RF-020/021), CMS service layer(RF-003)의 typed 반환 | CMS `any` state가 타입화되어야 service/hook 분리 시 반환 타입을 두 번 작업하지 않음 (추론). PFM DTO 작업(RF-039)과 백엔드가 달라 상호 독립 — 병렬 가능(3장) | RF-FINDING-044 → RF-FINDING-003, RF-FINDING-020, RF-FINDING-021 |
| 3 | **apiRequest 공통 request safety** — `apiRequest` 옵션에 AbortSignal/timeout 도입, refresh fetch timeout, retryable error 정책 명시, call site 타입 명시 강화(generic `any` 축소는 단계 적용 — 원본 주의사항). 기준 패턴: `lib/api/labserverTrameClient.ts:466-484`(S5-TIMEOUT-002). **민감 영역**(token refresh/error normalization, session1 brief)이므로 신중히 진행 | polling guard 패턴 통일(RF-032 영구 패턴화, RF-034), stale response guard hook(RF-016~019), WS/polling session hook(RF-033) | **구체 사례 ②: apiRequest timeout/signal 정리가 선행되어야 polling guard 패턴 통일이 가능.** signal 전달 경로가 없으면 각 hook이 cancellation을 caller마다 자체 구현하게 되어(원본 영향 기술) 패턴이 다시 갈라짐 (추론) | RF-FINDING-028, RF-FINDING-040 → RF-FINDING-032(영구화), RF-FINDING-034, RF-FINDING-016, RF-FINDING-017, RF-FINDING-018, RF-FINDING-019, RF-FINDING-033 |
| 3 | **token storage 단일화** — `authTokenStorage` adapter로 `lib/auth.ts`/`lib/apiClient.ts` 중복 제거, PFM token과 Supabase session 경계 문서화 | API client 후속 변경, PFM auth gate 추출(RF-005) | 원본 개선 방향: "로그인/refresh/401 retry와 연결되므로 API client 변경과 함께 계획" — RF-028과 같은 파일(`lib/apiClient.ts`)을 건드리므로 같은 시기에 묶어 충돌 방지 (추론) | RF-FINDING-022 → RF-FINDING-005 |
| 3 | **CMS service/repository layer 도입** — notice/gallery/home/research 도메인별 service 또는 query hook + storage adapter. **RLS/권한 정책 확인 필요(원본 보존)**. 도메인별 점진 이관 | CMS list/edit hook(RF-020/021/037), board container session 정리(RF-009), CMS boundary guard(RF-004) | UI-persistence 결합 해소가 선행되어야 CMS hook이 service를 호출하는 구조가 됨. P0 ③(attachment rollback)에서 만든 보상 정책을 adapter로 흡수 (추론) | RF-FINDING-003 → RF-FINDING-020, RF-FINDING-021, RF-FINDING-037, RF-FINDING-009, RF-FINDING-004 |
| 3 | **legacy/외부 연동 adapter 표준화** — legacy `/api/chat` error envelope/zod schema, EmailJS `sendContactEmail` adapter. **legacy 유지 여부 확인 필요(원본 보존)** | legacy 흐름 관련 page 정리(PFMSimulationPage 등) | PFM error normalization(`lib/api/errors.ts`, S5-ERRORNORM-001 좋은 패턴)을 기준으로 확장하는 작업이라 API 계층 시기에 함께 진행 (추론). 다른 트랙과 독립 — 병렬 가능 | RF-FINDING-030, RF-FINDING-031 |
| 4 | **admin query key helper + query 정책 문서화** — `buildAdminQueryKeys` root helper로 literal key/builder key 혼재 해소, 도메인별(admin/simulation/CMS) freshness/retry 정책 문서화 | AdminPage3 tab별 query/mutation hook 분리, mutation cache side effect 캡슐화 | **구체 사례 ④: query key helper 정리가 AdminPage3 hook 분리보다 선행.** key가 안정되지 않은 채 hook을 분리하면 invalidation 누락 위험(원본 영향 기술)이 hook 단위로 분산·확대됨. session4 brief 민감 영역: "AdminPage3 query key와 invalidation은 admin UI 전반에 영향이 커서 helper 정리 후 이동" | RF-FINDING-029(key helper 부분), RF-FINDING-027 → RF-FINDING-029(mutation hook 부분), RF-FINDING-002 |
| 4 | **polling/WS lifecycle hook 격리** — `useJobMonitorSession`/`useVisualizationSession`으로 WebSocket/polling fallback/reconnect/sync interval 분리. 기준 패턴: `visualizationSyncInFlightRef`(S4-ABORT-001), `TrameExportCenter` AbortController(S5-CANCEL-002) | Simulation2Page 본체 분해(RF-001), chat/workflow presenter 분리 | RF-001 분해의 핵심 축(원본 (추론) 표기). RF-028의 signal/timeout 계약 위에서 구현해야 cancellation이 일관됨. **민감 영역**: "WebSocket cleanup, polling, refresh key 회귀 위험. 테스트 선행"(session1 brief) | RF-FINDING-033, RF-FINDING-035 → RF-FINDING-001 |
| 4 | **list/catalog stale guard hook** — `useSimulationJobResults`/`useSimulationList`/`useChatSessions`/`useResultFieldCatalog` 등 request sequence guard 또는 React Query 전환 | 해당 card/panel의 presenter 분리(RF-019의 View/Form/Dialog 분리 등), Simulation2Page 하위 영역 정리 | local server state + stale guard 부재가 hook으로 캡슐화되어야 component 분리 시 데이터 계층을 재설계하지 않음 (추론). `sync:false` 정책 유지(원본 주의사항) | RF-FINDING-016, RF-FINDING-017, RF-FINDING-018, RF-FINDING-019 → RF-FINDING-010 |
| 4 | **CMS data hook** — `useHomeContent`(try/catch/finally + typed error state), notice/gallery 공통 list query hook, `useAdminUrlState`(P0 ② parser의 hook화 — **확정 배정은 Phase 6/Wave A5의 RF-TASK-069**(4.2 참조), 본 행에는 의존 계층 위치 참고로만 기재) | CMS page/board container 정리(Phase 6), NewsPage 등 list presenter(RF-012) | service layer(순서 3)와 DTO(순서 2)가 있어야 hook이 typed 계약으로 작성됨 | RF-FINDING-021, RF-FINDING-020, RF-FINDING-061(hook화) → RF-FINDING-012 |
| 5 | **component 책임 분리** — presenter 추출(ChatPanel/ParameterPanel/ResultWorkspace, admin dialog/table), key 안정화, 접근성/렌더 최적화. 기준 패턴: `VisualizationControlBar`(S3-COMP-003 — intent callback만 전달) | page/container 계층 정리(Phase 6) | hook 계약(순서 4)이 확정된 뒤에야 presenter props contract를 한 번에 확정 가능 (추론). 단 RF-014(index key)·RF-011(modal 접근성)·RF-026(slider guard)은 독립 소형 작업으로 앞당겨도 무방 (추론) | RF-FINDING-010, RF-FINDING-011, RF-FINDING-012, RF-FINDING-013, RF-FINDING-014, RF-FINDING-015, RF-FINDING-023, RF-FINDING-024, RF-FINDING-025, RF-FINDING-026 |
| 6 | **page/container/routing 정리** — Simulation2Page/AdminPage3 본체 분해 완결, auth gate(`usePfmAuthGate`/`LegacyAdminGate`) 추출, board id parser/not-found 정책, error boundary/fallback 전략 | 최종 회귀 검증(Phase 8) | 하위 계층이 전부 안정된 뒤 가장 위험한 대형 파일 분해를 완결. guard 추출은 token storage(RF-022) 단일화 이후가 안전 (추론). edit route 권한은 "RLS 확인 전 과도한 차단 금지"(원본) | RF-FINDING-001, RF-FINDING-002, RF-FINDING-005, RF-FINDING-006, RF-FINDING-007, RF-FINDING-008, RF-FINDING-009 |
| 7 | **잔여 util/config/constant 정리** — colormap/page size constant, formatter locale, `withQuery` 타입, admin API re-export, 인코딩 주석 | 최종 정리 | 의존받는 쪽이 거의 없는 말단 정리 항목 (추론). 단 일부 util(순서 1)은 이미 선행됨 — 4.1 참조 | RF-FINDING-054, RF-FINDING-055, RF-FINDING-056, RF-FINDING-057, RF-FINDING-058, RF-FINDING-059, RF-FINDING-046 |
| 8 | **test/verification 확대** — CMS boundary guard 추가, madge/dependency-cruiser 순환 import 검증, strict 옵션 단계 강화 검토 | (최종) | boundary guard는 service 경계 확정(순서 3) 후에만 의미 있음(원본: "service 경계를 먼저 정한 뒤 검사 추가"). strict 강화는 "오류량 측정 필요(확인 필요)" | RF-FINDING-004, RF-FINDING-060, RF-FINDING-038 |

### 2.1 단계별 검증 (공통)

각 순서 완료 시: `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:boundaries`(PFM API boundary 정적 검사). 타입 검증은 `npx tsc --noEmit` 후보로 사용하되 **확인 필요** — 전용 typecheck 스크립트가 없고 tsconfig strict 계열이 꺼져 있어(RF-FINDING-038) 검출력이 약하다. 수동 검증: local `http://localhost:3000`, production `https://pfm.cmsl-kookmin.com/simulation2` (Playwright MCP 사용 가능).

---

## 3. 병렬 수행 가능한 작업 그룹

CMS(게시판 앱, Supabase 백엔드)와 PFM(시뮬레이션 앱, 자체 백엔드)은 같은 프론트에 있으나 백엔드·인증이 분리된 서로 다른 앱이므로(프로젝트 규칙), 두 트랙은 대부분 병렬 진행 가능하다. (추론: 트랙 구분은 계획 판단, 파일 소속은 원본 근거)

> **명칭 주의**: 본 장의 병렬 그룹 **A/B/C**는 `refactoring-execution-order.md`의 트랙 **S/A/C/Q**와 다른 체계다. 대응 관계: 그룹 A(PFM) ≈ 트랙 S+A, 그룹 B(CMS) ≈ 트랙 C, 그룹 C(독립 소형) ≈ 트랙 Q. **특히 본 문서의 "그룹 C(독립 소형, 충돌 없음)"와 execution-order의 "트랙 C(CMS·게시판 — 승인 게이트 필수)"를 혼동하면 게시판 앱을 승인 없이 수정하는 사고로 이어질 수 있다.**

| 그룹 | 범위 | 포함 RF-FINDING | 병렬 가능 근거 |
|---|---|---|---|
| **A. PFM 계열** | `Simulation2Page`, `AdminPage3`, `components/simulation/*`, `lib/api/*`, `lib/apiClient.ts`, workflow types/mappers | 001, 002, 010, 016, 017, 018, 019, 028, 029, 032, 033, 034, 035, 039, 040, 041, 042, 043, 045, 047, 048, 049, 055, 057, 059, 061 | 자체 백엔드 + PFM token 인증. CMS 파일과 import 접점 없음 (확인 필요: 정적 분석 미실행 — RF-060) |
| **B. CMS 계열** | `HomePage`, `NoticeBoardPage`, `GalleryBoardPage`, `EditNoticePage`, `EditGalleryPage`, `ResearchPageTemplate`, board routes | 003, 004(CMS guard), 006, 009, 012, 013, 020, 021, 036, 037, 044 | Supabase 백엔드 + Supabase session. **"별도의 요청이 없다면 게시판 앱 수정은 지양"(프로젝트 규칙) — CMS 트랙 착수 전 수행 범위 합의 필요 (확인 필요)** |
| **C. env/config + 독립 소형** | `lib/supabaseClient.ts`(env 부분), `next.config.ts`, `ContactPage`(EmailJS), legacy `/api/chat`, `hooks/use-toast.ts`, `use-mobile.ts`, `MemberDetailModal`, `ResearchHighlightsSlider`, `ColorBends` | 051, 052, 053, 054, 056, 058, 030, 031, 011, 014(carousel/template 부분), 015, 023, 024, 025, 026, 046 | 파일 단위로 닫혀 있어 A/B 트랙과 충돌 없음 (추론) |

### 3.1 그룹 간 공유 접점 (병렬 시 조정 필요)

| 접점 | 관련 RF | 주의 |
|---|---|---|
| `app/providers.tsx` QueryClient 전역 기본값 | RF-FINDING-027 | admin polling/user workflow/CMS가 같은 정책 공유(원본). 병렬 진행 중 전역 기본값 변경 금지 — 도메인별 정책은 query 단위로 명시하고, 전역값 변경은 양 트랙 합의 후 단일 커밋으로 (추론) |
| `lib/auth.ts` / `lib/apiClient.ts` / `lib/supabaseClient.ts` storage 경계 | RF-FINDING-022, RF-FINDING-051 | PFM token과 Supabase session이 모두 sessionStorage 사용으로 경계 혼동 여지(원본). A 트랙(token adapter)과 C 트랙(env helper)이 같은 파일을 건드림 — 순서 합의 필요 |
| `sanitizeForStorage`/blob download helper | RF-FINDING-050 | CMS edit pages(B)와 `ResultExplorerPanel`/`Simulation2Page`/`AdminPage3`(A)에 중복(원본). 공통 util 추출은 한 트랙이 소유하고 다른 트랙은 소비만 — 양쪽이 따로 추출하면 중복이 재생산됨 (추론) |
| `tsconfig.json` / lint 설정 | RF-FINDING-038, RF-FINDING-058 | 전역 설정이라 모든 트랙에 영향. 옵션 변경은 Phase 경계에서만, 단일 변경으로 (추론) |
| `lib/utils.ts` (`formatRelativeTime` 등) | RF-FINDING-056 | 공통 util — 변경 시 양 트랙 사용처 확인 (확인 필요: 사용처 전수는 원본에 없음) |

---

## 4. 순환 의존·순서 충돌 주의점

### 4.1 Phase 순서 vs 의존성 순서의 충돌 — util이 Phase 7인데 enabler임

확정 Phase 구조에서 util/config 정리는 Phase 7이지만, 의존성 관점에서는 **pure helper 분리(RF-047/048/049)와 env helper(RF-051/052)가 Phase 1~4 작업의 선행 조건**이다(순서 표 1행). 충돌 해소 기준 (추론): enabler 성격의 util 작업은 "이를 필요로 하는 Phase의 준비 단계"로 앞당겨 수행하고, Phase 7은 잔여 말단 정리(RF-054~059 등)만 담당한다. Phase 문서 작성 시 같은 RF가 두 Phase에 걸치면 "선행 부분/잔여 부분"을 명시해 이중 작업을 방지한다.

### 4.2 P0 핫픽스와 본 리팩토링의 이중 작업 (의도된 2-pass)

- RF-FINDING-032: P0에서 최소 in-flight guard → Phase 3에서 RF-028 기반 single-flight 패턴으로 재정리. **두 번 손대는 것은 의도된 설계**이며, P0 시점에 패턴 통일까지 시도하면 RF-028(미완) 위에 임시 계약을 쌓게 됨 (추론).
- RF-FINDING-061: P0에서 safe parser 함수 → Phase 6 (RF-TASK-069)에서 `useAdminUrlState` hook화. P0 범위에서 correction effect 재배치까지 하지 않는다 (추론). 원본 주의사항: "기존 deep link/query 호환성 보존".
- RF-FINDING-036: P0에서 실패 보상 처리 → Phase 2~3에서 CMS storage adapter(RF-003)로 흡수. "실제 storage path/URL parsing 확인 필요"(원본 보존).

### 4.3 strict 옵션(RF-038)의 순환 구조

"strict를 켜려면 타입 정리가 필요하고, 타입 정리를 안전하게 하려면 strict가 필요"한 순환이 있다. 원본 개선 방향대로 **단번에 전체 strict 전환 금지**, 신규/리팩토링 영역부터 strict-friendly 타입으로 작성하는 **정책으로 전 Phase에 분산 적용**하고, 옵션 자체의 강화는 Phase 8에서 검토한다. "strict 시 오류량 측정 필요(확인 필요)"(원본 보존).

### 4.4 RF-039 ↔ RF-002/029의 잠금 관계와 외부 차단 요인

admin DTO 단일화(RF-039)는 "admin API가 의도적으로 다른 계약인지 백엔드 명세 확인 필요"(원본)가 풀리기 전에는 확정할 수 없다. 백엔드 확인이 지연되면 AdminPage3 hook 분리(순서 4)가 통째로 막히므로, 차단 시 대안 (추론): 현 중복 타입을 **alias로만 연결**(session1 brief: "alias/mapper를 먼저 명확화")해 hook 분리를 진행하고, 명세 확인 후 alias를 단일 타입으로 수렴한다.

### 4.5 같은 파일을 여러 순서가 건드리는 충돌

- `lib/apiClient.ts`: RF-028(timeout/signal) + RF-022(token storage) + RF-040(generic/parsing) + RF-052(env 안내)가 모두 닿는다. **민감 영역**(session1 brief: "모든 PFM API 호출의 공통 기반")이므로 순서 3에서 한 작업 단위로 묶되 commit은 변경별 분리 (추론).
- `Simulation2Page.tsx`: P0(032) → 순서 1(047) → 순서 2(041/042/043) → 순서 4(033/035/016 연계) → 순서 6(001 완결)이 순차로 닿는다. 병렬 분기 금지 — 이 파일에 대한 작업은 항상 직렬로 진행 (추론). 민감 영역: "WebSocket refs/lifecycle"(session1 brief), "전체 lifecycle이 한 component에 있어 분리 전 재검증 필요"(session4 원본 확인 필요 항목 보존).
- `AdminPage3.tsx`: P0(061) → 순서 1(048) → 순서 4(029 key helper → hook) → 순서 6(002 완결). 민감 영역: "권한/early return"(session1 brief)은 분해 마지막까지 보존.

### 4.6 코드 수준 순환 import은 미확정

barrel export 과다·circular import 여부는 정적 `rg`만으로 확정되지 않았다(RF-FINDING-060, 원본 "확인 필요" 보존). madge/dependency-cruiser 검증은 **Phase 0 baseline(RF-TASK-001/002와 함께 1회 선행 실행) + Phase 8(RF-TASK-085로 재실행·비교)** 시점에 수행해, 만약 순환이 발견되면 본 문서의 병렬 그룹(3장) 가정을 재검토한다. (추론)

### 4.7 RF-004(boundary guard)의 역방향 의존

boundary guard 확대는 검증 도구지만 **service 경계 확정(RF-003)에 의존**하는 역방향 관계다. guard를 먼저 추가하면 아직 정해지지 않은 경계를 강제하게 되어 service 설계를 제약한다 — Phase 8까지 대기. (추론, 원본 근거: "service 경계를 먼저 정한 뒤 검사 추가")

---

> 본 문서의 순서 번호·트랙 구분·충돌 해소 기준은 계획 수립 과정의 판단(추론)이며, 각 행의 파일 경로·라인·민감 영역·주의사항은 코드리뷰 원본 문서의 근거를 보존한 것이다. Phase별 상세 작업 명세는 후속 Phase 문서에서 본 순서를 참조해 수립한다.
