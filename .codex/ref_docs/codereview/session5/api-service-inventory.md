# API Service Inventory

| 구분 | 파일 경로 | 라인 | 함수/모듈 이름 | 역할 | 호출 위치 | 비고 |
|---|---|---:|---|---|---|---|
| API client | `lib/apiClient.ts` | 380 | `apiRequest` | PFM JSON request, authFetch, error envelope 처리 | `lib/api/*`, `lib/auth.ts` | 기본 generic이 `any` |
| API client | `lib/apiClient.ts` | 265 | `authFetch` | Authorization header, 401 refresh/retry 처리 | `apiRequest`, `downloadBinary`, keepalive | request timeout 없음 |
| retry handler | `lib/apiClient.ts` | 139 | `refreshAccessToken` | refresh token single-flight 처리 | `authFetch`, WS reconnect | `/api/v1/auth/refresh` 직접 fetch |
| error handler | `lib/api/errors.ts` | 255 | `normalizeApiError` | ApiError/unknown error를 UI 표시 모델로 변환 | `Simulation2Page`, common error UI | redaction 포함 |
| service | `lib/api/chatSessions.ts` | 115 | `listChatSessions` | chat session list API wrapper | `SessionListCard` | endpoint helper 경계 있음 |
| service | `lib/api/chatSessions.ts` | 153 | `sendChatSessionMessage` | chat message 전송 | `Simulation2Page` | simulation draft 응답 처리 |
| service | `lib/api/simulations.ts` | 173 | `listSimulations` | simulation list API wrapper | `SimulationListCard` | local state 호출 |
| service | `lib/api/simulations.ts` | 179 | `createSimulation` | simulation 생성 | `Simulation2Page` | request body typed |
| service | `lib/api/simulations.ts` | 190 | `updateSimulation` | simulation PATCH | `Simulation2Page` | body 일부 `Record<string, any>` 흐름 존재 |
| service | `lib/api/jobs.ts` | 79 | `submitSimulationJob` | simulation job 제출 | `Simulation2Page`, `PFMSimulationPage` | `autoVisualization` body |
| service | `lib/api/jobs.ts` | 98 | `getJob` | job detail 조회 | polling, AdminPage3 | `sync=false` 정책 중요 |
| service | `lib/api/jobs.ts` | 108 | `listJobEvents` | job events 조회 | polling, AdminPage3 | polling 대상 |
| polling function | `lib/api/jobs.ts` | 115 | `createJobMonitorWebSocketUrl` | job monitor WS URL 생성 | `Simulation2Page` | backend WS helper 사용 |
| service | `lib/api/results.ts` | 104 | `listSimulationResults` | simulation result list | `JobResultListCard`, `Simulation2Page` | result availability 판단 |
| service | `lib/api/results.ts` | 110 | `getResult` | result detail | `ResultExplorerPanel`, visualization create | field 후보 계산 |
| service | `lib/api/results.ts` | 120 | `listResultFieldFiles` | result field file list | `ResultExplorerPanel`, `AdminPage3` | field/filter request safety 필요 |
| service | `lib/api/visualizations.ts` | 85 | `createVisualization` | result visualization 생성 | `Simulation2Page`, `AdminPage3` | user explicit action |
| service | `lib/api/visualizations.ts` | 99 | `updateVisualization` | visualization PATCH | control bar, AdminPage3 | 빈 PATCH 방지 |
| service | `lib/api/visualizations.ts` | 122 | `deleteVisualizationKeepalive` | unload cleanup best-effort DELETE | `Simulation2Page` | refresh disabled |
| service | `lib/api/admin.ts` | 343 | admin wrappers | admin auth/system/users/sim/job/result/viz wrappers | `AdminPage3` | 공용 helper 재사용과 admin-specific wrapper 혼재 |
| React Query query | `components/pages/AdminPage3.tsx` | 551 | `useQuery` group | admin server state 조회 | AdminPage3 | query가 한 container에 집중 |
| React Query mutation | `components/pages/AdminPage3.tsx` | 648 | `useMutation` group | input preview/job/viz/account/user mutation | AdminPage3 | cache side effect가 component에 노출 |
| fetch | `lib/api/legacyAiChat.ts` | 8 | `askLegacySimulationAssistant` | legacy `/api/chat` 호출 | `AIChatAssistant` | 표준 ApiError 미사용 |
| fetch | `lib/api/legacySimulation.ts` | 17 | `runLegacySimulation` | legacy backend simulation 실행 | `SimulationPage` | env base URL 조합 |
| 기타 | `api/chat.js` | 56 | Next API handler | Gemini SDK 호출 | `/api/chat` | timeout/error envelope 확인 필요 |
| 기타 | `components/pages/ContactPage.tsx` | 25 | `emailjs.sendForm` | EmailJS browser SDK 직접 호출 | ContactPage | 외부 adapter 없음 |
| 기타 | `components/pages/HomePage.tsx` | 40 | Supabase query | CMS home data 조회 | HomePage | service/query hook 없음 |
| 기타 | `components/pages/NoticeBoardPage.tsx` | 58 | Supabase query/mutation | notice list/pin/delete | NoticeBoardPage | UI-persistence 결합 |
| API client | `lib/api/labserverTrameClient.ts` | 286 | `LabserverTrameClient` | Labserver gateway/trame API client | trame components | timeout/signal이 일부 흐름에 있음 |
| polling function | `lib/api/labserverTrameClient.ts` | 466 | `pollExportJob` | export job polling | `TrameExportCenter` | AbortSignal, timeout 적용 |

## 확인 필요

- axios, SWR 직접 사용은 `rg` 기준 확인되지 않았다.
- CMS Supabase 호출을 React Query로 통합할지, domain service만 둘지는 제품 정책 확인 필요.
