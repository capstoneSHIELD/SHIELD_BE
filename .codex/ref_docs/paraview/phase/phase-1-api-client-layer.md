# Phase 1. API Client Layer

## 목표

Phase 1의 목표는 active UI에 흩어진 backend API 직접 호출을 명세 기반 API client 계층으로 이동하는 것이다. 이 phase는 사용자 기능을 확장하는 단계가 아니라, Phase 3-6에서 job, result, visualization 기능을 안전하게 고칠 수 있도록 호출 경계를 정리하는 준비 단계다.

핵심 결과는 다음과 같다.

- active UI 컴포넌트가 `/api/v1/...` URL을 직접 조합하지 않는다.
- path/query/body 조립은 `lib/api/*` helper 내부에서 처리한다.
- `apiClient.ts`는 인증, refresh, error envelope, raw response 처리만 담당한다.
- endpoint DTO와 UI view model을 섞지 않는다.
- 관리자 화면에 이미 있는 result/job/visualization helper를 일반 사용자 helper로 재사용 가능한 구조로 옮긴다.

## 비판적 검토

기존 Phase 1 문서는 방향은 맞지만 구현자가 바로 작업하기에는 부족했다.

- 어떤 직접 호출을 먼저 옮길지 우선순위가 없다.
- `admin.ts`에 이미 존재하는 helper와 type을 어떻게 분리할지 기준이 없다.
- `withQuery`, `encodePathSegment`, `downloadBinary`가 `admin.ts` 내부에 갇혀 있어 중복 구현 위험이 있다.
- websocket URL 생성과 binary download가 일반 JSON API와 같은 방식으로 적혀 있어 책임 경계가 흐릿하다.
- `Simulation2Page.tsx`의 raw `fetch` keepalive 삭제 호출을 어떻게 대체할지 빠져 있다.
- API helper 추가와 UI 기능 확장이 섞여 있다. Phase 1은 helper 이동과 call site 교체까지만 담당해야 한다.
- 테스트 대상이 구체적이지 않아 path encoding, query 생성, body shape 회귀를 놓칠 수 있다.

## 적용 규칙

- UI, Page, Route는 API URL 조립과 HTTP 세부사항을 갖지 않는다.
- 외부/backend HTTP 호출은 `lib/api/*` 또는 `lib/apiClient.ts` 뒤에 둔다.
- Request DTO, Response DTO, UI state, Lab Gateway DTO를 분리한다.
- 명세에 없는 기능을 Phase 1에서 추가하지 않는다.
- `.codex/ref_docs`는 참고 문서이며, 실제 프로젝트 공식 문서가 필요하면 `docs/`에 작성한다.

## 범위

포함:

- API helper 추가/이동
- 직접 `apiRequest` 호출 제거
- raw `fetch` 제거 또는 API helper 뒤로 이동
- WebSocket URL 생성 helper 추가
- path/query/binary 공통 helper 정리
- helper 단위 테스트 추가
- active UI가 새 helper를 사용하도록 import 교체

제외:

- job monitor WS 실제 연결 전환
- result explorer UI 추가
- visualization camera control UI 추가
- 사용자 screenshot 버튼 추가
- Lab Server Gateway 직접 연동 제거
- legacy 화면 대규모 리팩토링

## Active 수정 대상

| 파일 | 현재 문제 | Phase 1 처리 |
| --- | --- | --- |
| `components/pages/Simulation2Page.tsx` | job, result, visualization, chat, simulation 직접 `apiRequest` 호출과 raw `fetch`, WS URL 조립이 존재한다. | helper import로 교체한다. UI 상태 전이는 유지한다. |
| `components/simulation/JobResultListCard.tsx` | `lib/api/simulations.ts` helper를 쓰지만 job/result DTO가 좁고 `sync` 옵션이 없다. | 새 simulations/jobs/results helper signature로 교체한다. |
| `components/simulation/SessionListCard.tsx` | 기존 chat helper를 사용하지만 path encoding이 helper 내부에 없다. | helper 내부 encoding 보강 후 call site는 최소 변경한다. |
| `lib/api/chatSessions.ts` | create/update/send helper가 없고 path encoding이 없다. | chat endpoint helper를 명세 기준으로 완성한다. |
| `lib/api/simulations.ts` | create/update/input-preview helper가 없고 jobs/results가 하위 책임을 포함한다. | simulation 소유 endpoint만 남기고 job/result 상세는 전용 모듈로 이동한다. |
| `lib/api/admin.ts` | 일반 API helper, admin API helper, binary helper가 한 파일에 섞여 있다. | 공통 helper/type을 단계적으로 전용 모듈로 이동하고 admin은 재사용하도록 바꾼다. |
| `lib/apiClient.ts` | raw JSON API에는 충분하지만 binary/WS helper는 없다. | core fetch 책임은 유지하고, binary/WS 유틸은 `lib/api/http.ts` 후보로 분리한다. |

## 모듈 구조

Phase 1 완료 시 권장 구조는 다음과 같다.

```text
lib/
  apiClient.ts
  api/
    http.ts
    chatSessions.ts
    simulations.ts
    jobs.ts
    results.ts
    visualizations.ts
    admin.ts
```

각 파일의 책임:

| 파일 | 책임 |
| --- | --- |
| `apiClient.ts` | base URL, auth header, token refresh, `X-New-Access-Token`, JSON error envelope, `apiRequest` |
| `api/http.ts` | `withQuery`, `encodePathSegment`, binary download, content-disposition filename parsing, WS URL base 생성 |
| `chatSessions.ts` | `/chat-sessions` 리소스 helper와 DTO |
| `simulations.ts` | `/simulations` 리소스 helper와 DTO |
| `jobs.ts` | `/jobs` 리소스 helper, job events, cancel, monitor WS URL |
| `results.ts` | `/results` 리소스 helper, field/file catalog, binary result file download |
| `visualizations.ts` | `/visualizations` 리소스 helper, screenshot download, visualization WS URL |
| `admin.ts` | admin-only endpoint와 admin query keys. 일반 리소스 helper를 중복 구현하지 않고 재사용 |

## 공통 Helper 기준

`admin.ts` 내부에 있는 다음 유틸은 Phase 1에서 공통화한다.

| 기존 위치 | 새 위치 후보 | 이유 |
| --- | --- | --- |
| `withQuery` | `lib/api/http.ts` | 모든 API helper가 동일한 query 생성 규칙을 써야 한다. |
| `encodePathSegment` | `lib/api/http.ts` | user/admin helper의 path encoding 차이를 없앤다. |
| `downloadBinary` | `lib/api/http.ts` | result download와 screenshot download가 같은 binary/error 처리 규칙을 사용한다. |
| `getFilenameFromContentDisposition` | `lib/api/http.ts` | binary download 테스트를 공통화한다. |
| WS URL base 생성 | `lib/api/http.ts` | visualization/job WS URL 생성에서 base URL, protocol 변환, token query를 중복하지 않는다. |

주의:

- `apiClient.ts`에 모든 helper를 몰아넣지 않는다.
- `authFetch`는 `apiClient.ts`에 남긴다.
- binary helper는 `authFetch`를 사용하되 JSON parsing을 강제하지 않는다.
- WS helper는 연결을 생성하지 않고 URL만 만든다. 실제 reconnect 정책은 Phase 3/5의 UI orchestration에서 다룬다.

## DTO 네이밍 기준

DTO 이름은 endpoint 행위와 맞춘다.

| 패턴 | 예시 |
| --- | --- |
| Request body | `CreateSimulationBody`, `UpdateSimulationBody`, `SubmitSimulationJobBody` |
| Query params | `ListSimulationJobsParams`, `GetJobParams`, `ListResultFieldFilesParams` |
| Response body | `CreateChatSessionResponse`, `JobDetailResponse`, `CreateVisualizationResponse` |
| Item summary | `SimulationSummary`, `JobSummary`, `ResultFieldFile` |
| Binary result | `BinaryDownload`, `ResultFileDownload`, `VisualizationScreenshotDownload` |

금지:

- UI state 이름을 response DTO로 재사용하지 않는다.
- Lab Gateway DTO와 backend API DTO를 같은 타입으로 공유하지 않는다.
- `any` 반환을 새 helper에 추가하지 않는다. 불명확하면 `Record<string, unknown>`으로 좁혀 둔다.

## Endpoint Helper 계획

### `lib/api/chatSessions.ts`

추가/수정:

- `createChatSession(body: CreateChatSessionBody): Promise<CreateChatSessionResponse>`
- `listChatSessions(params?: ListChatSessionsParams): Promise<ListChatSessionsResponse>`
- `getChatSession(sessionId: string): Promise<ChatSessionDetail>`
- `updateChatSession(sessionId: string, body: UpdateChatSessionBody): Promise<ChatSessionDetail>`
- `deleteChatSession(sessionId: string): Promise<DeleteChatSessionResponse>`
- `getChatSessionMessages(sessionId: string): Promise<ListChatSessionMessagesResponse>`
- `sendChatSessionMessage(sessionId: string, body: SendChatSessionMessageBody): Promise<SendChatSessionMessageResponse>`

Phase 1에서는 `updateChatSession` helper만 추가하고 UI 연결은 Phase 2로 넘겨도 된다.

### `lib/api/simulations.ts`

추가/수정:

- `createSimulation(body: CreateSimulationBody): Promise<SimulationDetail>`
- `listSimulations(params?: ListSimulationsParams): Promise<ListSimulationsResponse>`
- `getSimulation(simulationId: string): Promise<SimulationDetail>`
- `updateSimulation(simulationId: string, body: UpdateSimulationBody): Promise<SimulationDetail>`
- `getSimulationInputPreview(simulationId: string): Promise<InputPreview>`
- `submitSimulationJob(simulationId: string, body: SubmitSimulationJobBody): Promise<SubmitSimulationJobResponse>`

경계:

- `listSimulationJobs`는 `jobs.ts`로 이동하거나 `jobs.ts`에서 re-export한다.
- `listSimulationResults`는 `results.ts`로 이동하거나 `results.ts`에서 re-export한다.
- Phase 1에서 UI import churn을 줄이려면 기존 이름을 deprecated wrapper로 남기고 내부에서 새 helper를 호출할 수 있다.

### `lib/api/jobs.ts`

추가:

- `listSimulationJobs(simulationId: string, params?: ListSimulationJobsParams): Promise<ListSimulationJobsResponse>`
- `getJob(jobId: string, params?: GetJobParams): Promise<JobDetailResponse>`
- `cancelJob(jobId: string): Promise<CancelJobResponse>`
- `listJobEvents(jobId: string, params?: ListJobEventsParams): Promise<ListJobEventsResponse>`
- `createJobMonitorWebSocketUrl(jobId: string, params: JobMonitorWebSocketParams): string`

Phase 1의 기본값:

- `getJob`과 `listJobEvents`의 `sync` 기본값은 명시적으로 정한다.
- 사용자 자동 polling call site는 Phase 3에서 `sync=false`로 고정하지만, Phase 1 helper는 `sync` query를 받을 수 있어야 한다.

### `lib/api/results.ts`

추가:

- `listSimulationResults(simulationId: string, params?: ListSimulationResultsParams): Promise<ListSimulationResultsResponse>`
- `getResult(resultId: string): Promise<ResultDetail>`
- `listResultFields(resultId: string, params?: ListResultFieldsParams): Promise<ResultFieldsResponse>`
- `listResultFieldFiles(resultId: string, fieldName: string, params?: ListResultFieldFilesParams): Promise<ResultFieldFilesResponse>`
- `downloadResultFile(resultId: string, fileId: string, fallbackFilename?: string): Promise<ResultFileDownload>`

Phase 1에서는 사용자 UI에 result explorer를 추가하지 않는다. 다만 API helper는 Phase 4에서 바로 쓸 수 있게 둔다.

### `lib/api/visualizations.ts`

추가:

- `createVisualization(resultId: string, body: CreateVisualizationBody): Promise<CreateVisualizationResponse>`
- `getVisualization(visualizationId: string): Promise<VisualizationDetail>`
- `updateVisualization(visualizationId: string, body: UpdateVisualizationBody): Promise<UpdateVisualizationResponse>`
- `deleteVisualization(visualizationId: string): Promise<CloseVisualizationResponse>`
- `deleteVisualizationKeepalive(visualizationId: string): Promise<void> | void`
- `downloadVisualizationScreenshot(visualizationId: string, params?: VisualizationScreenshotParams): Promise<VisualizationScreenshotDownload>`
- `createVisualizationWebSocketUrl(visualizationId: string, params: VisualizationWebSocketParams): string`

주의:

- `deleteVisualizationKeepalive`는 unload cleanup의 특수 케이스다. raw `fetch`를 UI에 남기지 않기 위한 얇은 helper로 둔다.
- Phase 1에서는 hardcoded field/colormap 기본값을 바꾸지 않는다. 이는 Phase 5의 기능 정합화 범위다.

## Direct Call Migration Table

| Current call site | API helper로 이동 | Phase 1 작업 |
| --- | --- | --- |
| `Simulation2Page.tsx` `POST /api/v1/chat-sessions` | `createChatSession` | 직접 `apiRequest` 제거 |
| `Simulation2Page.tsx` `POST /api/v1/chat-sessions/{id}/messages` | `sendChatSessionMessage` | response DTO 타입 도입 |
| `Simulation2Page.tsx` `GET /api/v1/simulations/{id}` | `getSimulation` | helper import로 교체 |
| `Simulation2Page.tsx` `POST /api/v1/simulations` | `createSimulation` | body 타입 도입 |
| `Simulation2Page.tsx` `PATCH /api/v1/simulations/{id}` | `updateSimulation` | validation error display는 Phase 6 |
| `Simulation2Page.tsx` `GET /api/v1/simulations/{id}/input-preview` | `getSimulationInputPreview` | helper import로 교체 |
| `Simulation2Page.tsx` `POST /api/v1/simulations/{id}/jobs` | `submitSimulationJob` | 422/502 상세 표시는 Phase 6 |
| `Simulation2Page.tsx` `GET /api/v1/jobs/{id}` | `getJob` | Phase 3에서 `sync=false` 적용 |
| `Simulation2Page.tsx` `GET /api/v1/jobs/{id}/events` | `listJobEvents` | helper import로 교체 |
| `Simulation2Page.tsx` `POST /api/v1/jobs/{id}/cancel` | `cancelJob` | cancel 상태 정책은 Phase 3 |
| `Simulation2Page.tsx` `POST /api/v1/results/{id}/visualizations` | `createVisualization` | hardcoded body는 Phase 5 |
| `Simulation2Page.tsx` `GET /api/v1/visualizations/{id}` | `getVisualization` | helper import로 교체 |
| `Simulation2Page.tsx` `DELETE /api/v1/visualizations/{id}` | `deleteVisualization` | normal cleanup |
| `Simulation2Page.tsx` raw keepalive `fetch` visualization DELETE | `deleteVisualizationKeepalive` | raw URL 조립 제거 |
| `Simulation2Page.tsx` visualization WS URL string | `createVisualizationWebSocketUrl` | 연결/reconnect는 기존 로직 유지 |
| `lib/api/simulations.ts` unencoded path | `encodePathSegment` | helper 내부 수정 |
| `lib/api/chatSessions.ts` unencoded path | `encodePathSegment` | helper 내부 수정 |
| `lib/api/admin.ts` duplicated general helpers | import from `jobs/results/visualizations/simulations` | 점진적 재사용 |

## 구현 순서

### Step 1. 공통 HTTP 유틸 분리

- `lib/api/http.ts`를 만든다.
- `withQuery`, `encodePathSegment`, `downloadBinary`, `getFilenameFromContentDisposition`를 이동한다.
- `admin.ts`가 새 유틸을 import하도록 바꾼다.
- 이 단계에서는 UI 코드를 바꾸지 않는다.

검증:

- `lib/api/admin.test.ts`
- 신규 또는 이동된 `lib/api/http.test.ts`

### Step 2. Chat/Simulation helper 보강

- `chatSessions.ts`에 create/send/update helper를 추가한다.
- 기존 path segment를 encoding하도록 수정한다.
- `simulations.ts`에 create/update/input-preview/submit job helper를 추가한다.
- 기존 list/get helper의 path/query 생성 규칙을 `http.ts`로 통일한다.

검증:

- `lib/api/chatSessions.test.ts`
- 신규 또는 보강 `lib/api/simulations.test.ts`

### Step 3. Jobs/Results/Visualizations 모듈 생성

- `jobs.ts`, `results.ts`, `visualizations.ts`를 만든다.
- 우선 `admin.ts`에 이미 있는 일반 리소스 helper를 복사하지 말고 이동 또는 wrapper 방식으로 분리한다.
- admin-only helper는 `admin.ts`에 남긴다.

검증:

- 신규 `lib/api/jobs.test.ts`
- 신규 `lib/api/results.test.ts`
- 신규 `lib/api/visualizations.test.ts`
- 기존 `lib/api/admin.test.ts`

### Step 4. `Simulation2Page.tsx` call site 교체

- 직접 `apiRequest`를 endpoint 묶음별로 helper 호출로 교체한다.
- UI state reducer, polling timer, reconnect 로직은 유지한다.
- 함수 이름만 바뀌는 수준의 작은 commit 단위로 진행한다.

권장 교체 순서:

1. chat session create/send
2. simulation create/get/update/input-preview
3. job submit/get/events/cancel
4. visualization create/get/delete/ws URL
5. raw keepalive fetch

검증:

- `components/pages/Simulation2Page.test.tsx`
- 타입 체크

### Step 5. Admin 재사용 정리

- `AdminPage3.tsx`가 깨지지 않도록 `admin.ts`의 public export 이름을 유지한다.
- 내부 구현만 공통 helper를 호출하도록 바꾼다.
- admin query key는 `admin.ts`에 남긴다.

검증:

- `lib/api/admin.test.ts`
- 관리자 화면 관련 테스트가 있으면 함께 실행한다.

## Migration Guardrails

- Phase 1에서는 사용자 관찰 가능 동작을 바꾸지 않는다.
- polling interval, reconnect 횟수, hardcoded visualization body, cancel 버튼 노출 정책은 그대로 둔다.
- 명세와 다르더라도 기능 동작 변경이 필요한 항목은 Phase 3-6으로 넘긴다.
- helper 추가 후 UI 교체 전 테스트를 먼저 만든다.
- `admin.ts` export를 한 번에 제거하지 않는다. 기존 import 경로를 깨지 않도록 wrapper 기간을 둔다.
- legacy 화면은 active route가 확인되기 전까지 필수 교체 대상이 아니다.

## Acceptance Criteria

- active UI에서 `/api/v1/` 문자열 검색 결과가 0개이거나, 허용 목록에만 남아 있다.
- active UI에서 `apiRequest(` 직접 호출이 0개이거나, 허용 목록에만 남아 있다.
- active UI에서 backend API raw `fetch(` 직접 호출이 없다. unload keepalive도 API helper 뒤에 있다.
- active UI에서 backend API WebSocket URL 문자열 조립이 없다.
- `lib/api/chatSessions.ts`와 `lib/api/simulations.ts`의 path parameter가 encoding된다.
- `jobs.ts`, `results.ts`, `visualizations.ts`가 명세 endpoint를 helper로 표현한다.
- binary download는 `authFetch` 기반 공통 helper를 사용한다.
- `admin.ts`는 admin-only API와 admin query key 중심으로 축소된다.
- 기존 사용자 workflow 테스트와 admin API 테스트가 통과한다.

## 허용 잔여 항목

Phase 1 완료 후에도 다음은 남아 있을 수 있다.

- job monitor WS 실제 연결 미적용
- 사용자 result explorer 미구현
- visualization camera control 필드/UI 미반영
- visualization 생성 기본 field/colormap hardcoding
- Lab Server Gateway 직접 adapter 존재
- legacy 컴포넌트 내부의 구 API 호출

이 잔여 항목은 각각 Phase 3, Phase 4, Phase 5, Phase 7에서 다룬다.

## 테스트 계획

| 테스트 | 필수 확인 |
| --- | --- |
| `lib/api/http.test.ts` | query 생성, path encoding, content-disposition filename, binary error envelope |
| `lib/api/chatSessions.test.ts` | create/list/get/update/delete/messages/send path/body |
| `lib/api/simulations.test.ts` | create/list/get/update/input-preview/submit job path/body |
| `lib/api/jobs.test.ts` | list/get/events/cancel path, `sync` query, monitor WS URL |
| `lib/api/results.test.ts` | list/detail/fields/files query, download path |
| `lib/api/visualizations.test.ts` | create/get/update/delete/screenshot path, WS URL |
| `lib/api/admin.test.ts` | 기존 admin path와 binary download 동작 유지 |
| `components/pages/Simulation2Page.test.tsx` | helper 호출 기반으로 기존 workflow 유지 |

## 문서 업데이트 기준

Phase 1에서 실제 코드 변경이 발생하면 다음 문서를 검토한다.

- `docs/architecture/directory.md`: `lib/api/http.ts`, `jobs.ts`, `results.ts`, `visualizations.ts` 추가 시 갱신 후보
- `docs/architecture/architecture.md`: API client 계층 책임 변경 시 갱신 후보
- `docs/architecture/flow.md`: 사용자 관찰 가능 흐름이 바뀌면 갱신
- `docs/api/*`: frontend 내부 helper 정리는 backend public API 변경이 아니므로 원칙적으로 수정 대상이 아니다. 다만 frontend-backend 매핑 문서를 공식화한다면 `docs/api/frontend-backend-api-map.md`를 새로 둔다.

## 리스크

- `admin.ts`에서 공통 helper를 이동할 때 기존 테스트가 많이 깨질 수 있다. export 이름을 유지하는 wrapper 전략을 우선한다.
- `Simulation2Page.tsx`는 상태 전이와 API 호출이 강하게 얽혀 있다. Phase 1에서는 API 호출부만 교체하고 상태 구조는 건드리지 않는다.
- path encoding을 추가하면 테스트 fixture의 기대 URL이 바뀐다. UUID만 쓰는 운영 경로는 영향이 작지만 테스트에서 slash 포함 id를 쓰는 경우 변경을 명확히 반영한다.
- raw keepalive fetch를 helper로 감싸도 unload 시 Promise 완료를 보장할 수 없다. 이 helper는 best-effort cleanup임을 문서와 이름으로 드러낸다.
- WS URL helper는 token을 query로 넣을 수밖에 없는 browser WebSocket 제약이 있다. token 로그 출력 금지를 유지한다.

