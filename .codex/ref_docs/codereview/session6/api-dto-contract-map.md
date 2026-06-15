# API DTO Contract Map

Session 5의 API/service 리뷰와 연결해, API 함수와 request/response type, 내부 변환 타입의 연결 관계를 정리했다.

| API/함수 | 파일 경로 | 라인 | request type | response type | 내부 변환 type | 사용 위치 | 문제 가능성 |
|---|---|---:|---|---|---|---|---|
| `apiRequest<T>` | `lib/apiClient.ts` | 380 | `RequestInit` | `T = any` | 없음 | 대부분의 `lib/api/*` | 기본 타입이 `any`라 call site에서 타입 명시 누락 가능 |
| `getRequiredPfmApiBaseUrl` | `lib/apiClient.ts` | 223 | 없음 | `string` | env fallback | API client | `NEXT_PUBLIC_PFM_LLM_URL` fallback과 에러 메시지의 기준 env가 다름 |
| `createSimulation` | `lib/api/simulations.ts` | 179 | `CreateSimulationBody` | `SimulationDetail` | `normalizeSimulationCompositionDto` | `Simulation2Page` | request/form state 분리 확인 필요 |
| `getSimulation` | `lib/api/simulations.ts` | 186 | `simulationId` | `SimulationDetail` | `normalizeSimulationCompositionDto` | workflow restore | `parameters: Record<string, unknown>`를 UI에서 다시 단정 |
| `updateSimulation` | `lib/api/simulations.ts` | 190 | `UpdateSimulationBody` | `SimulationDetail` | `normalizeSimulationCompositionDto` | `Simulation2Page` | component에서 `Record<string, any>` patch body 생성 |
| `submitSimulationJob` | `lib/api/jobs.ts` | 79 | `SubmitSimulationJobBody` | `SubmitSimulationJobResponse` | 없음 | job submit workflow | `warnings: Array<Record<string, unknown>>`로 warning shape 검증 약함 |
| `listSimulationJobs` | `lib/api/jobs.ts` | 89 | `SyncOption` | `JobSummary[]` | query helper | job list/polling | admin API의 `JobSummary`와 유사 타입 중복 |
| `getJob` | `lib/api/jobs.ts` | 98 | `SyncOption` | `JobDetail` | 없음 | polling/detail | status union은 명시되어 있으나 admin DTO와 중복 |
| `listJobEvents` | `lib/api/jobs.ts` | 108 | query params | `JobEvent[]` | 없음 | job event panel | workflow local `JobEvent`와 경계 확인 필요 |
| `listSimulationResults` | `lib/api/results.ts` | 104 | query params | `ResultSummary[]` | query helper | result list | admin `ResultSummary`와 유사 타입 중복 |
| `getResult` | `lib/api/results.ts` | 110 | `resultId` | `ResultDetail` | 없음 | result explorer | `summary: Record<string, unknown>` 사용 |
| `listResultFields` | `lib/api/results.ts` | 114 | `resultId` | `ResultFieldsResponse` | 없음 | result explorer | field contract 확인 필요 |
| `createVisualization` | `lib/api/visualizations.ts` | 85 | `CreateVisualizationBody` | `CreateVisualizationResponse` | 없음 | visualization control | colormap/field 옵션과 DTO 경계 확인 필요 |
| `updateVisualization` | `lib/api/visualizations.ts` | 99 | `UpdateVisualizationBody` | `UpdateVisualizationResponse` | 없음 | visualization control | UI control form과 request DTO 분리 확인 필요 |
| `listAdminSimulations` | `lib/api/admin.ts` | 385 | `ListAdminSimulationsParams` | `PaginatedResponse<AdminSimulationSummary>` | admin query key | AdminPage3 | admin DTO와 일반 simulation DTO 혼재 |
| `getSimulation` | `lib/api/admin.ts` | 391 | `simulationId` | `SimulationDetail` | 없음 | AdminPage3 | 일반 API의 `getSimulation`과 이름 동일, import context 의존 |
| `getJob` | `lib/api/admin.ts` | 408 | `SyncOption` | `JobDetail` | 없음 | AdminPage3 | 일반 `jobs.ts` DTO와 유사 |
| `getResult` | `lib/api/admin.ts` | 435 | `resultId` | `ResultDetail` | 없음 | AdminPage3 | 일반 `results.ts` DTO와 유사 |
| `runLegacySimulation` | `lib/api/legacySimulation.ts` | 20 | `LegacySimulationRequestBody` | `LegacySimulationRunResult` | `task_id`/`taskId` normalize | legacy PFM page | `Record<string, unknown>` 요청, legacy 계약 확인 필요 |
| `sendLegacyAiChatMessage` | `lib/api/legacyAiChat.ts` | 11 | `{ message }` | `LegacyAiChatResponse` | `reasoning` string normalize | AI assistant | `res.json() as ...` 단정 |
| `handler` | `api/chat.js` | 56 | `req.body.message` | Gemini JSON | `JSON.parse(text)` | legacy chat API route | JS route, schema validation 확인 필요 |
| `requestJson<T>` | `lib/api/labserverTrameClient.ts` | 500 | `RequestInit` | `Promise<T>` | 없음 | Trame client methods | response JSON shape를 제네릭으로 단정 |

## 실제 코드 근거

- `lib/apiClient.ts:380`에서 `apiRequest<T = any>`가 기본 응답 타입을 `any`로 둔다.
- `components/pages/Simulation2Page.tsx:2378`에서 `UpdateSimulationBody`로 바로 연결되지 않는 `Record<string, any>` patch body가 생성된다.
- `api/chat.js:70`, `api/chat.js:85`에서 요청 body와 JSON parse 결과가 JS 라우트 안에서 스키마 없이 사용된다.

## 확인 필요

- 백엔드 API 명세와 `SimulationDetail`, `JobDetail`, `ResultDetail`의 optional/nullable 필드가 완전히 일치하는지 확인 필요.
- admin API DTO가 일반 API DTO와 일부러 분리된 계약인지, 중복 제거 가능한 shared DTO인지 확인 필요.
