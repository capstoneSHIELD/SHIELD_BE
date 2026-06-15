# Phase 0. Contract Map

## 목표

`backend_api.md`의 43개 endpoint를 기준으로 현재 프론트 구현을 endpoint, DTO, 화면 소비 지점, 테스트 단위까지 매핑한다. Phase 0의 결과는 이후 phase에서 어떤 API를 먼저 고치고, 어떤 UI는 건드리지 않으며, 어떤 직접 외부 연동은 별도 adapter로 격리할지 결정하는 기준표가 된다.

## 비판적 검토

기존 Phase 0 문서는 방향은 맞지만 실행 문서로 쓰기에는 추상적이었다.

- endpoint별 상태 판정 기준이 없어 `implemented`와 `partial`이 사람마다 다르게 해석될 수 있다.
- 사용자 화면, 관리자 화면, legacy 컴포넌트를 구분하지 않아 실제 수정 우선순위가 흐려진다.
- 직접 `apiRequest` 호출, `authFetch`, raw `fetch`, `WebSocket`, Lab Server Gateway 호출을 같은 수준으로 보지 않아 누락 call site가 생길 수 있다.
- request DTO, response DTO, view model, upstream DTO의 분리 여부를 확인하는 열이 없다.
- `sync=true`, `sync=false`, WS fallback, token refresh 같은 동작 계약이 endpoint 표에 녹아 있지 않다.
- 산출물이 어디에 남아야 하는지 불명확하다. 이 파일은 사용자 요청에 따른 참고 문서이며, 프로젝트 공식 추적 문서는 필요 시 `docs/` 아래에 둬야 한다.

## 우선순위 규칙

- 사용자 현재 요청을 최우선으로 한다.
- 구현 판단 기준은 `.codex/ai_rule_developer` 규칙을 따른다.
- `.codex/ref_docs/backend_api.md`는 외부/사용자 참고 API 명세로 사용한다.
- 프로젝트 공식 문서가 필요해지는 시점에는 `docs/api` 또는 `docs/architecture` 아래에 작성한다.
- `.codex/ref_docs`의 문서를 backend 계약 원본처럼 수정하지 않는다. 명세 이상 징후는 `spec-anomaly`로 표시하고 별도 질문 또는 docs 이관 대상으로 둔다.

## 범위

포함:

- backend API 명세의 모든 REST/WS endpoint 매핑
- active route에서 실제 사용되는 화면 기준 구현 상태
- API helper, 직접 fetch, websocket URL 생성, binary download call site
- 관리자 화면과 일반 사용자 화면의 기능 차이
- Lab Server Gateway 직접 연동의 명세 밖 사용 현황

제외:

- 코드 수정
- UI 신기능 추가
- backend API 명세 자체 수정
- legacy 화면을 활성 화면처럼 간주하는 판단

## Active Surface 기준

active surface는 `app` route에서 직접 연결되는 화면만 기준으로 한다.

| Surface | Route | 주요 컴포넌트 | Phase 0 판단 |
| --- | --- | --- | --- |
| 로그인/계정 신청 | `app/pfm_chat/login/page.tsx` | `components/pages/LoginPage.tsx` | active |
| 사용자 시뮬레이션 | `app/simulation2/page.tsx` | `components/pages/Simulation2Page.tsx` | active |
| 관리자 콘솔 | `app/cmsl20043/page.tsx` | `components/pages/AdminPage3.tsx` | active |
| legacy simulation | route 연결 확인 필요 | `components/pages/PFMSimulationPage.tsx`, `components/pages/SimulationPage.tsx` | legacy 후보 |

legacy 후보는 직접 API 호출이 있어도 우선 수정 대상에 넣지 않는다. 다만 중복 API 로직 제거 phase에서 삭제/격리 여부를 별도 판단한다.

## 상태 분류

| Status | 의미 | 예시 판단 |
| --- | --- | --- |
| `implemented` | active surface에서 사용되고, API helper 또는 공통 client를 통해 명세의 method/path/query/body/error 흐름이 대체로 맞는다. | auth login, refresh, admin users |
| `partial` | 호출은 있으나 helper가 없거나, query/body/response 일부가 누락됐거나, 사용자/관리자 중 한쪽에만 있다. | job polling without `sync=false`, visualization PATCH camera fields missing |
| `missing` | 명세 endpoint에 대응하는 active 구현이 없다. | job monitor WS, chat session PATCH |
| `admin-only` | 관리자 surface에는 구현됐지만 일반 사용자 흐름에는 없다. | result field files, screenshot |
| `out-of-contract` | backend API 명세가 아니라 별도 upstream/Gateway 계약을 직접 호출한다. | `NEXT_PUBLIC_LAB_SERVER_URL` 기반 Trame Gateway |
| `spec-anomaly` | backend 명세에 도메인상 어색하거나 중복으로 보이는 항목이 있다. | account request의 `sync` query 후보 |

## Gap 심각도

| Severity | 기준 | 처리 phase |
| --- | --- | --- |
| `P0` | 인증, 실행, 결과 접근이 깨지거나 보안/권한 경계를 우회할 위험이 있다. | 즉시 또는 Phase 1-3 |
| `P1` | 핵심 사용자 흐름이 명세와 다르게 동작하거나 운영 비용/장애 가능성이 커진다. | Phase 1, 3, 5 |
| `P2` | 기능은 동작하지만 명세 기능 일부가 화면이나 타입에 반영되지 않았다. | Phase 2, 4, 5, 6 |
| `P3` | 정리, 테스트, legacy 제거, 문서 이관 성격이다. | Phase 7 또는 별도 cleanup |

## 매핑표 작성 형식

모든 endpoint는 아래 열을 가진다.

| 열 | 작성 기준 |
| --- | --- |
| Endpoint | `METHOD path` 또는 `WS path` |
| Auth | `public`, `authenticated`, `admin-only` |
| Spec Contract | 핵심 request body/query/response/error |
| Current Owner | 현재 helper 또는 직접 호출 파일 |
| Active Consumer | 실제 route에 연결된 UI 소비 지점 |
| Status | 위 상태 분류 중 하나 |
| Gap | 누락 필드, 직접 호출, query 누락, 화면 누락 등 |
| Severity | `P0`-`P3` |
| Target Phase | 후속 phase 번호 |
| Test Target | 추가/수정할 테스트 파일 |

## Phase 0 실행 결과

- 실행일: 2026-06-08
- 작업 유형: 문서화 / API 계약 감사
- 기준 명세: `.codex/ref_docs/backend_api.md`
- 기준 endpoint 수: 43개 REST/WS endpoint
- 코드 변경 범위: 없음
- 문서 변경 범위: 이 Phase 0 매핑 문서

이번 Phase 0은 backend API 명세를 수정하지 않고, 현재 프론트엔드 active surface의 소비 상태만 매핑한다. `.codex/ref_docs/backend_api.md`는 사용자 관리 참고자료이므로 원본 계약처럼 고치지 않는다.

## Active Surface 확정

| Surface | Route | 주요 컴포넌트 | Phase 0 판단 | 비고 |
| --- | --- | --- | --- | --- |
| 로그인/계정 신청 | `app/pfm_chat/login/page.tsx` | `components/pages/LoginPage.tsx` | active | `lib/auth.ts` helper를 통해 account/auth API를 사용한다. |
| 사용자 시뮬레이션 | `app/simulation2/page.tsx` | `components/pages/Simulation2Page.tsx` | active | 일부 `lib/api/*` helper를 쓰지만 직접 `apiRequest`, raw `fetch`, WebSocket URL 조립이 남아 있다. |
| 관리자 콘솔 | `app/cmsl20043/page.tsx` | `components/pages/AdminPage3.tsx` | active | `lib/api/admin.ts`와 TanStack Query를 통해 PFM admin/API를 사용한다. |
| CMS legacy admin | `app/cmsl2004/page.tsx`, `app/cmsl20042/page.tsx` | `AdminPage`, `AdminPage2`, `LegacyLoginPage` | non-PFM active | Supabase CMS 영역으로 PFM backend contract 매핑 대상에서 제외한다. |
| legacy simulation | route 연결 없음 | `PFMSimulationPage.tsx`, `SimulationPage.tsx` | legacy inactive | 직접 PFM/legacy API 호출이 있으나 active route가 아니므로 Phase 1 필수 대상이 아니다. |

## Endpoint Contract Map

### Auth And Account

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `POST /api/v1/account-requests` | public | body `name`, `userId`, `password`, `organization?`, `purpose`; 201 account request | `lib/auth.ts` | `LoginPage.tsx` | `implemented` | 명세의 `sync` query는 public account request 문맥과 맞지 않는 `spec-anomaly` 후보. helper는 보내지 않는다. | `P3` | Phase 0 | `lib/auth.test.ts`, `LoginPage.test.tsx` |
| `GET /api/v1/account-requests/me` | public | query `userId`; 200 latest request status | `lib/auth.ts` | `LoginPage.tsx` | `implemented` | `userId`는 `encodeURIComponent`로 query encode한다. | `P3` | Phase 7 | `lib/auth.test.ts`, `LoginPage.test.tsx` |
| `POST /api/v1/auth/login` | public | body `userId`, `password`; 200 access/refresh token + user | `lib/auth.ts` | `LoginPage.tsx` | `implemented` | 명세의 `sync` query는 login 문맥상 `spec-anomaly` 후보. helper는 보내지 않는다. | `P3` | Phase 0 | `lib/auth.test.ts`, `LoginPage.test.tsx` |
| `POST /api/v1/auth/refresh` | public token rotation | body `refreshToken`; 200 rotated token pair | `lib/apiClient.ts` | global `authFetch` / `apiRequest` | `implemented` | single-flight refresh, 401 retry, proactive refresh가 구현되어 있다. | `P3` | Phase 7 | `lib/apiClient.test.ts` |
| `POST /api/v1/auth/logout` | authenticated | optional body `refreshToken`; 200 `{ loggedOut, userId }` | `lib/auth.ts` | login/session cleanup | `implemented` | 서버 호출 실패 시에도 local token cleanup을 수행하는 best-effort 정책. | `P3` | Phase 7 | `lib/auth.test.ts` |
| `GET /api/v1/auth/me` | authenticated | 200 current account; optional `X-New-Access-Token` header | `lib/auth.ts`, `lib/api/admin.ts` | `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx`, `AdminPage3.tsx` | `implemented` | `AuthUser`/`CurrentAccount`는 명세의 `loginId` 구조를 반영한다. | `P3` | Phase 7 | `lib/auth.test.ts`, `lib/api/admin.test.ts`, `lib/apiClient.test.ts` |

### Chat Sessions

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `POST /api/v1/chat-sessions` | authenticated | body `title`; 201 session | direct `apiRequest` in `Simulation2Page.tsx` | user simulation chat start | `partial` | active UI가 URL/body를 직접 조립한다. `createChatSession` helper가 없다. | `P2` | Phase 1 | `lib/api/chatSessions.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/chat-sessions` | authenticated | query `title?`, `page?`, `size?`; paginated sessions | `lib/api/chatSessions.ts` | `SessionListCard.tsx` | `implemented` | 검색과 page/size query는 helper에서 처리한다. | `P3` | Phase 7 | `lib/api/chatSessions.test.ts`, `SessionListCard.test.tsx` |
| `GET /api/v1/chat-sessions/{sessionId}` | authenticated owner/admin | path `sessionId`; session detail | `lib/api/chatSessions.ts` | session restore in `Simulation2Page.tsx` | `partial` | path segment encoding이 없다. | `P2` | Phase 1 | `lib/api/chatSessions.test.ts`, `Simulation2Page.test.tsx` |
| `PATCH /api/v1/chat-sessions/{sessionId}` | authenticated owner/admin | body `title`; updated session | 없음 | 없음 | `missing` | rename helper/UI가 없다. | `P2` | Phase 2 | `lib/api/chatSessions.test.ts`, `SessionListCard.test.tsx` |
| `DELETE /api/v1/chat-sessions/{sessionId}` | authenticated owner/admin | delete cascade response | `lib/api/chatSessions.ts` | `SessionListCard.tsx` | `partial` | path segment encoding이 없다. 삭제 UX는 존재한다. | `P2` | Phase 1 | `lib/api/chatSessions.test.ts`, `SessionListCard.test.tsx` |
| `GET /api/v1/chat-sessions/{sessionId}/messages` | authenticated owner/admin | path `sessionId`; messages list | `lib/api/chatSessions.ts` | session restore in `Simulation2Page.tsx` | `partial` | path segment encoding이 없다. | `P2` | Phase 1 | `lib/api/chatSessions.test.ts`, `Simulation2Page.test.tsx` |
| `POST /api/v1/chat-sessions/{sessionId}/messages` | authenticated owner/admin | body `content`; assistant response + optional simulation draft | direct `apiRequest` in `Simulation2Page.tsx` | user chat | `partial` | active UI가 URL/body를 직접 조립한다. response DTO와 view state 변환이 한 컴포넌트에 섞여 있다. | `P1` | Phase 1 | `lib/api/chatSessions.test.ts`, `Simulation2Page.test.tsx` |

### Simulations And Jobs

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `POST /api/v1/simulations` | authenticated | body `title`, optional `chatSessionId`; simulation detail | direct `apiRequest` in `Simulation2Page.tsx` | manual simulation create | `partial` | `createSimulation` helper가 없다. | `P2` | Phase 1 | `lib/api/simulations.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/simulations` | authenticated | query `status?`, `page?`, `size?`; paginated simulations | `lib/api/simulations.ts` | `SimulationListCard.tsx` | `implemented` | user list는 `size=100` 후 client pagination을 수행한다. | `P3` | Phase 7 | `lib/api/simulations.test.ts` |
| `GET /api/v1/simulations/{simulationId}` | authenticated owner/admin | simulation detail | `lib/api/simulations.ts`, `lib/api/admin.ts`, direct `apiRequest` | restore, simulation select, admin detail, chat follow-up | `partial` | user helper와 direct call path는 encoding이 없다. admin helper만 encoding한다. | `P2` | Phase 1 | `lib/api/simulations.test.ts`, `lib/api/admin.test.ts`, `Simulation2Page.test.tsx` |
| `PATCH /api/v1/simulations/{simulationId}` | authenticated owner/admin | body `title?`, `parameters?`; updated detail | direct `apiRequest` in `Simulation2Page.tsx` | parameter update | `partial` | `updateSimulation` helper가 없다. validation detail 표시가 제한적이다. | `P1` | Phase 1, Phase 6 | `lib/api/simulations.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/simulations/{simulationId}/input-preview` | authenticated owner/admin | input preview detail | direct `apiRequest`, `lib/api/admin.ts` | user input preview, admin preview | `partial` | user helper가 없다. admin helper만 encoding한다. | `P2` | Phase 1 | `lib/api/simulations.test.ts`, `lib/api/admin.test.ts` |
| `POST /api/v1/simulations/{simulationId}/jobs` | authenticated owner/admin | body `autoVisualization?`; submitted job | direct `apiRequest` in `Simulation2Page.tsx` | job submit | `partial` | `submitSimulationJob` helper가 없다. 422/502 details는 일부 errorDetails로만 보존된다. | `P1` | Phase 1, Phase 6 | `lib/api/jobs.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/simulations/{simulationId}/jobs` | authenticated owner/admin | query `sync?`; jobs list | `lib/api/simulations.ts`, `lib/api/admin.ts` | `JobResultListCard`, restore, admin jobs | `partial` | user helper는 `sync` query와 MPI fields가 없다. admin helper는 explicit sync를 지원한다. | `P1` | Phase 3 | `lib/api/jobs.test.ts`, `lib/api/admin.test.ts`, `JobResultListCard.test.tsx` |
| `GET /api/v1/jobs/{jobId}` | authenticated owner/admin | query `sync?`; job detail | direct `apiRequest`, `lib/api/admin.ts` | user polling, admin detail/sync | `partial` | user polling은 `sync=false`를 명시하지 않고 response DTO가 좁다. | `P1` | Phase 3 | `lib/api/jobs.test.ts`, `lib/api/admin.test.ts`, `Simulation2Page.test.tsx` |
| `POST /api/v1/jobs/{jobId}/cancel` | authenticated owner/admin | cancel response | direct `apiRequest`, `lib/api/admin.ts` | user stop/cancel, admin cancel | `partial` | user helper가 없고 cancellable 상태 정책이 UI별로 분산되어 있다. | `P1` | Phase 3 | `lib/api/jobs.test.ts`, `lib/api/admin.test.ts` |
| `WS /api/v1/jobs/{jobId}/monitor/ws` | authenticated owner/admin | WebSocket status stream, `accessToken?` query | 없음 | 없음 | `missing` | job monitor WS가 없고 user workflow는 3초 polling만 사용한다. | `P1` | Phase 3 | `lib/api/jobs.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/jobs/{jobId}/events` | authenticated owner/admin | query `sync?`; job events | direct `apiRequest`, `lib/api/admin.ts` | user event polling, admin events | `partial` | user helper와 explicit sync policy가 없다. admin helper는 explicit sync를 지원한다. | `P2` | Phase 3 | `lib/api/jobs.test.ts`, `lib/api/admin.test.ts` |

### Results

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /api/v1/simulations/{simulationId}/results` | authenticated owner/admin | result summaries for simulation | `lib/api/simulations.ts`, `lib/api/admin.ts` | visualization availability, `JobResultListCard`, admin results | `partial` | user helper에는 result detail로 이어지는 계약과 sync 정책이 없다. | `P2` | Phase 4 | `lib/api/results.test.ts`, `JobResultListCard.test.tsx` |
| `GET /api/v1/results/{resultId}` | authenticated owner/admin | result detail with files/summary | `lib/api/admin.ts` | admin only | `admin-only` | user result detail/explorer가 없다. | `P2` | Phase 4 | `lib/api/results.test.ts`, `lib/api/admin.test.ts` |
| `GET /api/v1/results/{resultId}/fields` | authenticated owner/admin | field catalog summary | `lib/api/admin.ts` | admin only | `admin-only` | user field selector가 없다. | `P2` | Phase 4 | `lib/api/results.test.ts`, `lib/api/admin.test.ts` |
| `GET /api/v1/results/{resultId}/fields/{fieldName}/files` | authenticated owner/admin | field file catalog with pagination/filter | `lib/api/admin.ts` | admin only | `admin-only` | user file catalog가 없다. | `P2` | Phase 4 | `lib/api/results.test.ts`, `lib/api/admin.test.ts` |
| `GET /api/v1/results/{resultId}/files/{fileId}/download` | authenticated owner/admin | attachment binary response | `lib/api/admin.ts` | admin only | `admin-only` | binary download helper는 admin에만 있다. user download UI가 없다. | `P2` | Phase 4 | `lib/api/results.test.ts`, `lib/api/admin.test.ts` |

### Visualizations

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `POST /api/v1/results/{resultId}/visualizations` | authenticated owner/admin | body `field`, `colormap`, `viewAngle`; 201 visualization | direct `apiRequest`, `lib/api/admin.ts` | user create, admin create | `partial` | user flow hardcodes `phase/coolwarm/xz` and lacks user helper. | `P1` | Phase 5 | `lib/api/visualizations.test.ts`, `Simulation2Page.test.tsx`, `lib/api/admin.test.ts` |
| `GET /api/v1/visualizations/{visualizationId}` | authenticated owner/admin | visualization detail + Trame metadata | direct `apiRequest`, `lib/api/admin.ts` | user metadata sync, admin detail | `partial` | user helper가 없다. admin helper는 encoding한다. | `P2` | Phase 5 | `lib/api/visualizations.test.ts`, `lib/api/admin.test.ts` |
| `PATCH /api/v1/visualizations/{visualizationId}` | authenticated owner/admin | body field/colormap/view/timestep/camera controls | `lib/api/admin.ts` | admin only | `partial` | admin helper/UI는 일부 control만 다루고 camera body fields(`deltaAzimuth`, `zoom`, `reset` 등)가 빠져 있다. user backend control UI는 없다. | `P1` | Phase 5 | `lib/api/visualizations.test.ts`, `lib/api/admin.test.ts` |
| `DELETE /api/v1/visualizations/{visualizationId}` | authenticated owner/admin | close visualization session | direct `apiRequest`, raw `fetch keepalive`, `lib/api/admin.ts` | user cleanup, admin close | `partial` | normal cleanup과 unload cleanup이 UI 컴포넌트 안에서 URL을 조립한다. | `P2` | Phase 5 | `lib/api/visualizations.test.ts`, `lib/api/admin.test.ts`, `Simulation2Page.test.tsx` |
| `GET /api/v1/visualizations/{visualizationId}/screenshot` | authenticated owner/admin | PNG binary response | `lib/api/admin.ts` | admin only | `admin-only` | user screenshot UX 여부가 미결정이다. | `P3` | Phase 5 | `lib/api/visualizations.test.ts`, `lib/api/admin.test.ts` |
| `WS /api/v1/visualizations/{visualizationId}/ws` | authenticated owner/admin | WebSocket proxy, `accessToken?` query | direct `WebSocket` in `Simulation2Page.tsx` | user visualization | `partial` | URL builder/helper가 없다. close code `1008` refresh reconnect는 구현됐지만 `1013` retry 정책과 테스트가 부족하다. | `P1` | Phase 5 | `lib/api/visualizations.test.ts`, `Simulation2Page.test.tsx` |

### Admin And System

| Endpoint | Auth | Spec Contract | Current Owner | Active Consumer | Status | Gap | Severity | Target Phase | Test Target |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /health` | public | backend liveness | 없음 | 없음 | `missing` | frontend route/consumer가 없다. backend operational endpoint라 user workflow gap은 아니다. | `P3` | Phase 0 | 없음 |
| `GET /api/v1/system/health` | admin-only | system health | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | 없음 | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `GET /api/v1/system/ready` | admin-only | system readiness | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | 없음 | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `GET /api/v1/admin/account-requests` | admin-only | query status/page/size; account request list | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | 없음 | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `PATCH /api/v1/admin/account-requests/{requestId}` | admin-only | body approve/reject; review response | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | path encoding 구현됨. | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `GET /api/v1/admin/users` | admin-only | admin user list | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | 없음 | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `PATCH /api/v1/admin/users/{userId}` | admin-only | body role/status; update response | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | path encoding 구현됨. | `P3` | Phase 7 | `lib/api/admin.test.ts` |
| `GET /api/v1/admin/simulations` | admin-only | query status/userId/page/size; items-only response | `lib/api/admin.ts` | `AdminPage3.tsx` | `implemented` | 명세는 page/size query를 받지만 response pagination은 없다. `spec-anomaly` 후보로 추적한다. | `P3` | Phase 0, Phase 7 | `lib/api/admin.test.ts` |

## Direct Call Inventory

| Surface | File | Direct call type | Contract area | Phase 0 finding | Target Phase |
| --- | --- | --- | --- | --- | --- |
| user simulation | `components/pages/Simulation2Page.tsx` | direct `apiRequest` | chat create/send, simulation create/update/detail/input-preview, job submit/get/events/cancel, visualization create/get/delete | active UI가 `/api/v1/...` URL과 일부 body를 직접 조립한다. | Phase 1 |
| user simulation | `components/pages/Simulation2Page.tsx` | raw `fetch keepalive` | `DELETE /api/v1/visualizations/{visualizationId}` | unload cleanup 때문에 raw fetch가 UI에 남아 있다. helper 뒤로 감싸야 한다. | Phase 5 |
| user simulation | `components/pages/Simulation2Page.tsx` | direct `new WebSocket` URL | `WS /api/v1/visualizations/{visualizationId}/ws` | URL/protocol/token query 조립이 UI에 남아 있다. | Phase 5 |
| user simulation | `components/simulation/SessionListCard.tsx` | helper call | chat session list/delete | UI는 helper를 쓰지만 helper path encoding 보강 필요. | Phase 1 |
| user simulation | `components/simulation/SimulationListCard.tsx` | helper call | simulation list | helper 사용. user-side client pagination 정책은 유지하되 테스트로 고정 필요. | Phase 7 |
| user simulation | `components/simulation/JobResultListCard.tsx` | helper call | simulation jobs/results | helper 사용. `sync` query와 result detail 연결은 부족하다. | Phase 3, Phase 4 |
| admin console | `components/pages/AdminPage3.tsx` | admin helper call | admin/system/job/result/visualization | 직접 URL 조립은 helper에 모여 있다. 일반 resource helper와 admin-only helper가 `admin.ts`에 섞여 있다. | Phase 1, Phase 7 |
| login | `components/pages/LoginPage.tsx` | auth helper call | account/auth | helper 사용. | Phase 7 |

## Out-of-contract 매핑

| Call Surface | Current Owner | 이유 | 처리 방향 | Severity | Target Phase |
| --- | --- | --- | --- | --- | --- |
| Lab Server Trame Gateway viewer/control/export/composite | `lib/api/labserverTrameClient.ts`, `lib/api/labserver.ts`, `components/simulation/trame/*` | `backend_api.md`의 `/api/v1/visualizations/*` 계약이 아니라 `NEXT_PUBLIC_LAB_SERVER_URL` 기반 Lab Gateway 직접 호출 | backend visualization API와 별도 Lab Gateway adapter로 유지하되, 기본 사용자 흐름에서는 backend `/visualizations/*`를 우선한다. | `P1` | Phase 5 |
| Advanced Trame panel in user simulation | `components/pages/Simulation2Page.tsx`, `AdvancedTramePanel.tsx` | backend visualization session에서 추출한 Trame session id로 Lab Gateway 고급 제어를 수행한다. | Phase 5에서 backend control UI와 Lab Gateway advanced UI의 경계를 문서화한다. | `P1` | Phase 5 |
| Legacy websocket `/api/ws/status/{taskId}` | `components/pages/SimulationPage.tsx` | backend API 명세에 없는 legacy path이며 active route에 연결되지 않았다. | active route가 아니므로 Phase 1 필수 변경 대상에서 제외하고 cleanup 후보로 둔다. | `P3` | Phase 7 |
| Legacy local PFM API client | `components/pages/PFMSimulationPage.tsx` | `lib/apiClient.ts`와 별도 `apiRequest` 구현을 가진 inactive legacy component다. | active route가 아니므로 우선순위는 낮다. 삭제/격리는 Phase 7 또는 별도 cleanup에서 판단한다. | `P3` | Phase 7 |
| Local CMS chat API | `components/AIChatAssistant.tsx`, `api/chat.js` | `/api/chat`은 PFM backend contract 밖의 legacy/site assistant endpoint다. | PFM contract phase에서는 제외한다. | `P3` | 제외 |

## Spec-anomaly 목록

| Source | Anomaly | Phase 0 판단 | 처리 |
| --- | --- | --- | --- |
| `POST /api/v1/account-requests` | public account request에 `sync` query 설명이 포함되어 있다. 설명 내용도 Lab server 상태 동기화에 가깝다. | 복붙 흔적으로 보인다. 프론트 helper는 `sync`를 보내지 않는다. | backend 명세 원본은 수정하지 않고 anomaly로 기록한다. |
| `POST /api/v1/auth/login` | login endpoint에 `sync` query 설명이 포함되어 있다. | 인증 요청과 Lab sync의 결합은 프론트에서 구현하지 않는다. | backend 명세 확인 필요. Phase 0에서는 미구현 유지. |
| `GET /api/v1/admin/simulations` | query는 `page`, `size`를 받지만 응답 예시는 `items`만 포함한다. | 현재 `lib/api/admin.ts`도 `{ items }` 형태로 모델링한다. | Phase 7 테스트에서 items-only 계약을 고정하거나 backend 명세 확인 후 공식 docs로 이관한다. |
| `GET /health` | `docs/api/specification.md`의 health response 설명과 `.codex/ref_docs/backend_api.md` 예시가 완전히 동일하지 않다. | frontend active consumer가 없어 기능 영향은 없다. | 공식 docs 갱신이 필요하면 `docs/api`에서 별도 처리한다. |

## Phase Handoff

| Target Phase | 넘길 작업 | 근거 |
| --- | --- | --- |
| Phase 1 API Client Layer | `Simulation2Page.tsx`의 직접 `apiRequest`, raw visualization DELETE, path encoding 없는 helper를 `lib/api/*` 경계로 이동한다. | UI/Page에 API URL 조립이 남아 있다. |
| Phase 2 Chat Session Contract | `PATCH /api/v1/chat-sessions/{sessionId}` helper와 rename UI를 추가할지 결정한다. | endpoint는 명세에 있으나 active 구현이 없다. |
| Phase 3 Job Monitoring | `jobs.ts` helper, explicit `sync=false`, job monitor WS, polling fallback, cancel 상태 정책을 정리한다. | job endpoint 대부분이 partial이고 WS는 missing이다. |
| Phase 4 Result Explorer | user result detail, fields, field files, download UI/API helper를 추가한다. | 결과 상세/파일 계열 endpoint는 admin-only다. |
| Phase 5 Visualization Contract | visualization helper, WS URL builder, user/backend control UI, screenshot 여부, Lab Gateway advanced 경계를 정리한다. | visualization endpoint는 직접 호출과 out-of-contract Gateway 호출이 섞여 있다. |
| Phase 6 Error Experience | validation/upstream details를 공통 error UI로 표현한다. | `ApiError.details`는 보존되지만 endpoint별 사용자 표시가 제한적이다. |
| Phase 7 Tests And Docs | API helper tests, component regression tests, official `docs/` 이관 후보를 정리한다. | Phase 0은 `.codex/ref_docs` 산출물이므로 공식 프로젝트 명세가 필요하면 `docs/`로 작성해야 한다. |

## Phase 0 실행 절차

1. `backend_api.md`에서 endpoint 목록을 추출했다.
   - 명령 예: `rg -n "^##.*(GET|POST|PATCH|DELETE|WS)" .codex/ref_docs/backend_api.md`
2. frontend call site를 추출했다.
   - 명령 예: `rg -n "api/v1|WebSocket|authFetch|apiRequest|NEXT_PUBLIC_LAB_SERVER_URL" lib components app`
3. `app` route 기준 active surface를 확정했다.
4. 각 endpoint row에 `Auth`, `Spec Contract`, `Current Owner`, `Active Consumer`, `Status`, `Gap`, `Severity`, `Target Phase`, `Test Target`을 채웠다.
5. 직접 호출이 있는 row는 Phase 1 API helper 이동 대상으로 표시했다.
6. `sync` query가 있는 endpoint는 기본값과 화면별 사용 정책을 적었다.
7. binary response와 websocket endpoint는 일반 JSON helper와 분리해서 기록했다.
8. backend 명세 이상 징후는 구현하지 않고 `spec-anomaly`로 표시했다.
9. Phase 1-7로 넘길 작업 목록을 `Phase Handoff`에 기록했다.

## Sync 정책 초안

| Context | 권장 sync | 이유 |
| --- | --- | --- |
| 사용자 자동 polling | `sync=false` | 불필요한 Lab 동기화와 upstream 장애 전파를 줄인다. |
| 사용자 수동 새로고침 | `sync=true` 후보 | 사용자가 최신 Lab 상태를 명시적으로 요청하는 상황이다. |
| 관리자 기본 목록/상세 | `sync=false` | 관리자 화면 초기 진입 비용을 낮춘다. |
| 관리자 `Sync Lab` 액션 | `sync=true` | 명시적 운영 동기화 액션이다. |
| WS monitor | `sync` query 지원 여부 명시 | 연결 URL 생성 helper에서 정책을 숨기지 않는다. |

## DTO 점검 기준

- Request DTO는 endpoint body/query/path만 표현한다.
- Response DTO는 backend response field를 빠뜨리지 않되 UI 표시 모델과 섞지 않는다.
- View model은 화면 컴포넌트 또는 feature adapter에서 만든다.
- Upstream Lab Gateway DTO는 backend API DTO와 이름을 공유하지 않는다.
- binary response는 JSON DTO로 모델링하지 않는다.

## 테스트 매핑 기준

| 대상 | 테스트 파일 후보 | 확인 내용 |
| --- | --- | --- |
| 공통 client | `lib/apiClient.test.ts` | auth header, refresh, `X-New-Access-Token`, error envelope |
| auth/account | `lib/auth.test.ts`, `components/pages/LoginPage.test.tsx` | login/account request/status/logout |
| chat sessions | `lib/api/chatSessions.test.ts` | create/list/detail/patch/delete/messages path/body |
| simulations | `lib/api/simulations.test.ts` | list/detail/update/input-preview/job/result helper |
| jobs | 신규 `lib/api/jobs.test.ts` | `sync` query, cancel, events, WS URL |
| results | 신규 `lib/api/results.test.ts` | fields, files query, binary download |
| visualizations | 신규 `lib/api/visualizations.test.ts` | create/update/delete/screenshot/WS URL |
| user workflow | `components/pages/Simulation2Page.test.tsx` | helper 사용, job fallback, error display |
| admin workflow | `components/pages/AdminPage3.test.tsx` 후보 | sync action, result explorer, visualization control |

## 산출물

필수 산출물 상태:

- 완료: 이 문서의 endpoint 매핑표 완성본
- 완료: `spec-anomaly` 목록
- 완료: `out-of-contract` 목록
- 완료: Phase 1-7로 넘길 작업 목록

후속 공식 산출물 후보:

- `docs/api/frontend-backend-api-map.md`
- `docs/architecture/api-client-layer.md`
- `docs/architecture/paraview-visualization-flow.md`

## 완료 기준

- 완료: `backend_api.md`의 모든 endpoint가 매핑표에 존재한다.
- 완료: active surface와 legacy 후보가 분리되어 있다.
- 완료: 모든 `partial`, `missing`, `admin-only`, `out-of-contract` row에 target phase가 있다.
- 완료: 직접 `apiRequest`, raw `fetch`, direct `WebSocket`, `NEXT_PUBLIC_LAB_SERVER_URL` 사용 지점이 누락 없이 기록되어 있다.
- 완료: API helper로 이동할 대상과 UI 기능 보강 대상이 분리되어 있다.
- 완료: 이 phase에서 코드 동작을 바꾸지 않는다.

## 주요 리스크

- backend 명세 자체에 복붙 흔적으로 보이는 항목이 있을 수 있다. 이 경우 프론트에서 임의로 구현하지 말고 `spec-anomaly`로 남긴다.
- 관리자 화면에 구현된 기능을 일반 사용자 화면에도 무조건 노출하면 권한/UX 범위가 커질 수 있다.
- Lab Server Gateway 직접 호출은 제품상 필요한 고급 기능일 수 있으므로 단순 삭제가 아니라 공식 backend API 흐름과의 경계를 먼저 확정해야 한다.
- legacy 컴포넌트의 직접 API 호출을 active 기능으로 오인하면 불필요한 리팩토링 범위가 커진다.
