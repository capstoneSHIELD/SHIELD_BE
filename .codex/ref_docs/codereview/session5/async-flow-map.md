# Async Flow Map

| 흐름 ID | 트리거 | 시작 위치 | 호출 경로 | 상태 반영 위치 | UI 반영 위치 | 비고 |
|---|---|---|---|---|---|---|
| S5-FLOW-001 | simulation2 page 진입 with session | `Simulation2Page.tsx:796` | `getChatSession` -> `getChatSessionMessages` -> `getSimulation` -> `listSimulationJobs` | `setMessages`, `setWorkflow` | Simulation2Page chat/workflow UI | `cancelled` flag는 있음 |
| S5-FLOW-002 | 사용자 chat submit | `Simulation2Page.tsx:2255` | `createChatSession` -> `sendChatSessionMessage` -> `getSimulation` -> `checkVisualizationAvailability` | `messages`, `workflow`, refresh keys | chat, parameter card, workspace | API orchestration이 component 내부에 큼 |
| S5-FLOW-003 | 수동 parameter 저장 | `Simulation2Page.tsx:2371` | `ensureManualSimulation` -> `createSimulation` -> `updateSimulation` | `workflow`, `editableParams` | parameter/editor UI | PATCH body 일부가 component에서 조립됨 |
| S5-FLOW-004 | job 제출 | `Simulation2Page.tsx:1844` | `submitSimulationJob` -> job monitor WS/polling 시작 | `workflow.jobId/status`, polling refs | workflow/progress UI | 상세는 Session 4/5 polling 이슈 |
| S5-FLOW-005 | job polling fallback | `Simulation2Page.tsx:1605` | `getJob` -> `listJobEvents` -> `listSimulationResults` | `workflow`, loading, job monitor refs | progress/events/result availability | in-flight guard 없음 |
| S5-FLOW-006 | result row visualization open | `Simulation2Page.tsx:1211` | `closeVisualizationSession` -> `getResult` -> `createVisualization` -> `getVisualization` | `workflow.visualization*`, control state | viewer/control UI | user explicit action |
| S5-FLOW-007 | visualization control | `Simulation2Page.tsx:2133` | `updateVisualization` -> `syncVisualizationFromServer` -> `getVisualization` | control state, `workflow` | control bar, viewer status | sync에는 sequence/in-flight guard 있음 |
| S5-FLOW-008 | result detail mount | `ResultExplorerPanel.tsx:369` | `getResult` | `detail`, selected field | result explorer | detail request sequence guard 존재 |
| S5-FLOW-009 | field catalog/files click | `ResultExplorerPanel.tsx:392` | `listResultFields` 또는 `listResultFieldFiles` | `fieldCatalog`, `fieldFiles` | field/file panels | stale guard 없음 |
| S5-FLOW-010 | job/result list refresh | `JobResultListCard.tsx:98` | `Promise.all(listSimulationJobs, listSimulationResults)` | `jobs`, `results`, loading/error | workspace result tab | sequence/cancel 없음 |
| S5-FLOW-011 | session list page/search/delete | `SessionListCard.tsx:90` | `listChatSessions`, `deleteChatSession` | `sessions`, `page`, `total` | session list UI | action 후 reload race 가능 |
| S5-FLOW-012 | admin tab render | `AdminPage3.tsx:551` | React Query `useQuery` group -> `lib/api/admin.ts` | TanStack Query cache, local dialog state | admin tabs/tables/dialogs | query/mutation이 component 내부 집중 |
| S5-FLOW-013 | admin job sync | `AdminPage3.tsx:666` | `getJob(sync:true)` + `listJobEvents(sync:true)` -> setQueryData/invalidate | Query cache | admin job detail/events | cache side effect가 component에 노출 |
| S5-FLOW-014 | CMS home mount | `HomePage.tsx:36` | Supabase `pages/publications/projects/notices/gallery` | local state | homepage sections | catch/finally 없음 |
| S5-FLOW-015 | notice list/search/delete | `NoticeBoardPage.tsx:50` | Supabase query/mutation | local state | board list | service boundary 없음 |
| S5-FLOW-016 | edit notice submit | `EditNoticePage.tsx:97` | storage remove/upload -> DB update -> router push | form/loading/message | edit form | rollback 없음 |
| S5-FLOW-017 | Trame export | `TrameExportCenter.tsx:160` | `createExportJob` -> `pollExportJob(signal)` -> download | job/error/loading | export panel | 좋은 기준: AbortController/timeout |

## 추론

- PFM helper 자체는 비교적 명확하지만, use-case orchestration이 component에 남아 있어 hook/service extraction이 리팩토링 중심이 된다.
