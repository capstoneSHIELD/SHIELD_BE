# Endpoint Map

| ID | 파일 경로 | 라인 | 호출 함수 | HTTP method | endpoint | request payload | response type | 호출 주체 | 사용 목적 |
|---|---|---:|---|---|---|---|---|---|---|
| S5-ENDPOINT-001 | `lib/auth.ts` | 93 | `login` | POST | `/api/v1/auth/login` | `LoginRequest` JSON | `LoginResponse` | `LoginPage` | 로그인/token 저장 |
| S5-ENDPOINT-002 | `lib/auth.ts` | 102 | `getMe` | GET | `/api/v1/auth/me` | 없음 | `AuthUser` | route guard/admin | 현재 사용자 조회 |
| S5-ENDPOINT-003 | `lib/auth.ts` | 106 | `createAccountRequest` | POST | `/api/v1/account-requests` | `AccountRequest` 기반 JSON | `AccountRequestResponse` | `LoginPage` | 가입 신청 |
| S5-ENDPOINT-004 | `lib/auth.ts` | 121 | `getMyAccountRequestStatus` | GET | `/api/v1/account-requests/me?userId=...` | query `userId` | `AccountRequestStatus` | `LoginPage` | 가입 상태 조회 |
| S5-ENDPOINT-005 | `lib/auth.ts` | 132 | `logout` | POST | `/api/v1/auth/logout` | optional `{ refreshToken }` | 확인 필요 | logout caller | refresh token 폐기 |
| S5-ENDPOINT-006 | `lib/apiClient.ts` | 139 | `refreshAccessToken` | POST | `${PFM_API_BASE_URL}/api/v1/auth/refresh` | `{ refreshToken }` | `RefreshResponse` | `authFetch` | token refresh |
| S5-ENDPOINT-007 | `lib/api/chatSessions.ts` | 115 | `listChatSessions` | GET | `/api/v1/chat-sessions` | query `title/page/size` | `ListChatSessionsResponse` | `SessionListCard` | session 목록 |
| S5-ENDPOINT-008 | `lib/api/chatSessions.ts` | 126 | `createChatSession` | POST | `/api/v1/chat-sessions` | `CreateChatSessionBody` | `CreateChatSessionResponse` | `Simulation2Page` | session 생성 |
| S5-ENDPOINT-009 | `lib/api/chatSessions.ts` | 153 | `sendChatSessionMessage` | POST | `/api/v1/chat-sessions/{sessionId}/messages` | `SendChatSessionMessageBody` | `SendChatSessionMessageResponse` | `Simulation2Page` | LLM message 처리 |
| S5-ENDPOINT-010 | `lib/api/simulations.ts` | 173 | `listSimulations` | GET | `/api/v1/simulations` | query params | `ListSimulationsResponse` | `SimulationListCard` | simulation 목록 |
| S5-ENDPOINT-011 | `lib/api/simulations.ts` | 179 | `createSimulation` | POST | `/api/v1/simulations` | `CreateSimulationBody` | `SimulationDetail` | `Simulation2Page` | manual simulation draft |
| S5-ENDPOINT-012 | `lib/api/simulations.ts` | 190 | `updateSimulation` | PATCH | `/api/v1/simulations/{simulationId}` | `UpdateSimulationBody` | `SimulationDetail` | `Simulation2Page` | parameter 저장 |
| S5-ENDPOINT-013 | `lib/api/simulations.ts` | 200 | `getSimulationInputPreview` | GET | `/api/v1/simulations/{simulationId}/input-preview` | 없음 | `InputPreview` | `Simulation2Page`, `AdminPage3` | input preview 조회 |
| S5-ENDPOINT-014 | `lib/api/jobs.ts` | 79 | `submitSimulationJob` | POST | `/api/v1/simulations/{simulationId}/jobs` | `SubmitSimulationJobBody` | `SubmitSimulationJobResponse` | `Simulation2Page` | job 제출 |
| S5-ENDPOINT-015 | `lib/api/jobs.ts` | 89 | `listSimulationJobs` | GET | `/api/v1/simulations/{simulationId}/jobs` | query `sync` | `{ items: JobSummary[] }` | list/polling/admin | job 목록 |
| S5-ENDPOINT-016 | `lib/api/jobs.ts` | 98 | `getJob` | GET | `/api/v1/jobs/{jobId}` | query `sync` | `JobDetail` | polling/admin | job status 조회 |
| S5-ENDPOINT-017 | `lib/api/jobs.ts` | 102 | `cancelJob` | POST | `/api/v1/jobs/{jobId}/cancel` | 없음 | `CancelJobResponse` | Simulation/Admin | job 취소 |
| S5-ENDPOINT-018 | `lib/api/jobs.ts` | 108 | `listJobEvents` | GET | `/api/v1/jobs/{jobId}/events` | query `sync` | `{ items: JobEvent[] }` | polling/admin | job event 조회 |
| S5-ENDPOINT-019 | `lib/api/jobs.ts` | 115 | `createJobMonitorWebSocketUrl` | WS | `/api/v1/jobs/{jobId}/monitor/ws` | query token params | WS message 구조 확인 필요 | `Simulation2Page` | job monitor WS |
| S5-ENDPOINT-020 | `lib/api/results.ts` | 104 | `listSimulationResults` | GET | `/api/v1/simulations/{simulationId}/results` | 없음 | `{ items: ResultSummary[] }` | Result list/polling | result 목록 |
| S5-ENDPOINT-021 | `lib/api/results.ts` | 110 | `getResult` | GET | `/api/v1/results/{resultId}` | 없음 | `ResultDetail` | ResultExplorer/viz create | result detail |
| S5-ENDPOINT-022 | `lib/api/results.ts` | 114 | `listResultFields` | GET | `/api/v1/results/{resultId}/fields` | 없음 | `ResultFieldsResponse` | ResultExplorer/Admin | field catalog |
| S5-ENDPOINT-023 | `lib/api/results.ts` | 120 | `listResultFieldFiles` | GET | `/api/v1/results/{resultId}/fields/{field}/files` | query `page/size/timestep/range/refresh` | `ResultFieldFilesResponse` | ResultExplorer/Admin | field files |
| S5-ENDPOINT-024 | `lib/api/results.ts` | 133 | `downloadResultFile` | GET | `/api/v1/results/{resultId}/files/{fileId}/download` | 없음 | `ResultFileDownload` | ResultExplorer/Admin | binary download |
| S5-ENDPOINT-025 | `lib/api/visualizations.ts` | 85 | `createVisualization` | POST | `/api/v1/results/{resultId}/visualizations` | `CreateVisualizationBody` | `CreateVisualizationResponse` | Simulation/Admin | visualization 생성 |
| S5-ENDPOINT-026 | `lib/api/visualizations.ts` | 95 | `getVisualization` | GET | `/api/v1/visualizations/{visualizationId}` | 없음 | `VisualizationDetail` | Simulation/Admin | visualization detail sync |
| S5-ENDPOINT-027 | `lib/api/visualizations.ts` | 99 | `updateVisualization` | PATCH | `/api/v1/visualizations/{visualizationId}` | `UpdateVisualizationBody` | `UpdateVisualizationResponse` | control/Admin | visualization control |
| S5-ENDPOINT-028 | `lib/api/visualizations.ts` | 114 | `deleteVisualization` | DELETE | `/api/v1/visualizations/{visualizationId}` | 없음 | `DeleteVisualizationResponse` | Simulation/Admin | session close |
| S5-ENDPOINT-029 | `lib/api/visualizations.ts` | 128 | `downloadVisualizationScreenshot` | GET | `/api/v1/visualizations/{visualizationId}/screenshot` | query format/width/height | `VisualizationScreenshotDownload` | Simulation/Admin | screenshot |
| S5-ENDPOINT-030 | `lib/api/visualizations.ts` | 143 | `createVisualizationWebSocketUrl` | WS | `/api/v1/visualizations/{visualizationId}/ws` | query token params | WS message 구조 확인 필요 | `Simulation2Page` | visualization WS |
| S5-ENDPOINT-031 | `lib/api/admin.ts` | 355 | `listAdminAccountRequests` | GET | `/api/v1/admin/account-requests` | query params | `PaginatedResponse<AdminAccountRequest>` | `AdminPage3` | admin account request list |
| S5-ENDPOINT-032 | `lib/api/admin.ts` | 361 | `reviewAccountRequest` | PATCH | `/api/v1/admin/account-requests/{requestId}` | `ReviewAccountRequestBody` | `ReviewAccountRequestResponse` | `AdminPage3` | account approve/reject |
| S5-ENDPOINT-033 | `lib/api/admin.ts` | 371 | `listAdminUsers` | GET | `/api/v1/admin/users` | 없음 | `{ items: AdminUser[] }` | `AdminPage3` | user list |
| S5-ENDPOINT-034 | `lib/api/admin.ts` | 375 | `updateAdminUser` | PATCH | `/api/v1/admin/users/{userId}` | `UpdateAdminUserBody` | `UpdateAdminUserResponse` | `AdminPage3` | user update |
| S5-ENDPOINT-035 | `lib/api/legacyAiChat.ts` | 8 | `askLegacySimulationAssistant` | POST | `/api/chat` | `{ message }` | `LegacyAiChatResponse` | `AIChatAssistant` | legacy AI assistant |
| S5-ENDPOINT-036 | `api/chat.js` | 80 | `model.generateContent` | SDK | Gemini API via SDK | prompt string | JSON parsed SDK text | `/api/chat` handler | LLM response |
| S5-ENDPOINT-037 | `lib/api/legacySimulation.ts` | 17 | `runLegacySimulation` | POST | `${NEXT_PUBLIC_BACKEND_URL}/api/run-simulation` | `LegacySimulationRequestBody` | `LegacySimulationRunResult` | `SimulationPage` | legacy simulation run |
| S5-ENDPOINT-038 | `lib/api/labserverTrameClient.ts` | 345 | `createVisualizationSession` | POST | `/api/v1/trame/api/v1/sessions` | `CreateVisualizationSessionRequest` | `VisualizationSession` | trame UI | Labserver session 생성 |
| S5-ENDPOINT-039 | `lib/api/labserverTrameClient.ts` | 466 | `pollExportJob` | GET 반복 | `/api/v1/trame/api/v1/exports/{jobId}` | 없음 | `ExportJobStatusResponse` | `TrameExportCenter` | export polling |
| S5-ENDPOINT-040 | `components/pages/HomePage.tsx` | 40 | `supabase.from('pages')...` | Supabase SDK | table `pages` | query builder | Supabase data shape 확인 필요 | HomePage | CMS home content |

## baseURL 메모

- PFM API base URL은 `lib/apiClient.ts:215`의 `NEXT_PUBLIC_PFM_API_URL` 또는 `NEXT_PUBLIC_PFM_LLM_URL`에서 온다.
- legacy simulation base URL은 `lib/api/legacySimulation.ts:1`의 `NEXT_PUBLIC_BACKEND_URL`에서 온다.
- Labserver base URL은 `lib/api/labserver.ts:5`의 `NEXT_PUBLIC_LAB_SERVER_URL`에서 온다.
- Supabase URL/key는 `lib/supabaseClient.ts:5`~`6`에서 온다.
