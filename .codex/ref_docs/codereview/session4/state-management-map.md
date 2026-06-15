# State Management Map

| 상태 유형 | 파일 경로 | 라인 | 상태 이름 | 소유 위치 | 변경 위치 | 사용 component/hook | 비고 |
|---|---|---:|---|---|---|---|---|
| local state | `components/pages/Simulation2Page.tsx` | 592 | `messages`, `input`, `error`, `pendingAction` | Simulation2Page | chat submit/assistant response/workflow handlers | Simulation2Page 하위 chat UI | chat UI 상태와 backend action 상태가 같은 owner에 존재 |
| server state | `components/pages/Simulation2Page.tsx` | 656 | `workflow` | Simulation2Page | job submit/cancel/poll/WS/result/viz handlers | Simulation2Page 전체 | `docs/architecture/state.md`에서 source of truth가 backend API로 정의됨 |
| form state | `components/pages/Simulation2Page.tsx` | 641 | `editableParams`, `manualParameterInputs` | Simulation2Page | parameter edit/sync effects | parameter editor UI | workflow generated params와 form draft가 결합 |
| local state | `components/pages/Simulation2Page.tsx` | 647 | `selectedResultSummary`, `selectedResultField` | Simulation2Page | result row click / ResultExplorer callback | result explorer, visualization open | selected result view state |
| server state | `components/pages/Simulation2Page.tsx` | 649 | visualization control state | Simulation2Page | visualization create/update/sync handlers | visualization control/viewer | PATCH 이후 detail sync로 서버 상태 재동기화 |
| derived state | `components/pages/Simulation2Page.tsx` | 658 | `workflowErrorCategory` | Simulation2Page | `useMemo` | error presenter/input validation UI | workflow error view model |
| URL/query state | `components/pages/AdminPage3.tsx` | 498 | `requestPage`, `requestSize` | AdminPage3 | URL search params parse/correction | admin list queries | `Number(...)` 결과가 `NaN`일 수 있음 |
| cache state | `components/pages/AdminPage3.tsx` | 551 | `meQuery` 등 admin queries | TanStack Query cache | `useQuery`/invalidate/refetch | AdminPage3 | React Query를 사용하지만 container가 query orchestration까지 소유 |
| form state | `components/pages/AdminPage3.tsx` | 501 | review/user/viz/dialog forms | AdminPage3 | dialog handlers/mutations | AdminPage3 tab/dialog UI | tab별 owner 분리 후보 |
| server state | `components/pages/AdminPage3.tsx` | 510 | `fieldFilesData` | AdminPage3 local state | `loadFieldFilesMutation.onSuccess` | result field files UI | `fetchQuery` 결과를 local state에 별도 복사 |
| server state | `components/simulation/ResultExplorerPanel.tsx` | 286 | `detail`, `fieldCatalog`, `fieldFiles` | ResultExplorerPanel | `loadResultDetail`, `loadFieldCatalog`, `loadFieldFiles` | result explorer UI | detail만 sequence guard 있음 |
| form state | `components/simulation/ResultExplorerPanel.tsx` | 295 | `filters` | ResultExplorerPanel | filter input/change handlers | field file query | server state와 같은 component에 존재 |
| server state | `components/simulation/JobResultListCard.tsx` | 93 | `jobs`, `results` | JobResultListCard | `fetchAll` | job/result list UI | local state + refreshKey 기반 |
| server state | `components/simulation/SimulationListCard.tsx` | 56 | `items` | SimulationListCard | `fetchItems` | simulation list UI | local state + refreshKey 기반 |
| server state | `components/simulation/SessionListCard.tsx` | 73 | `sessions` | SessionListCard | `loadSessions` | session list UI | search/delete/rename 상태와 결합 |
| localStorage/sessionStorage state | `lib/auth.ts` | 67 | access/refresh token | auth helper | `saveTokens`, `clearTokens` | auth callers | `lib/apiClient.ts`에도 token helper 중복 존재 |
| localStorage/sessionStorage state | `lib/apiClient.ts` | 38 | access/refresh token | api client | token read/write/refresh | API request pipeline | token refresh 시 access token 직접 저장 |
| localStorage/sessionStorage state | `lib/supabaseClient.ts` | 21 | Supabase session storage | Supabase browser client | Supabase auth client | CMS/auth pages | PFM token storage와 별도 sessionStorage 사용 |
| global state | `components/LanguageProvider.tsx` | 230 | `language` | LanguageProvider Context | `setLanguage` | 전역 language consumer | localStorage persistence 포함 |
| local state | `hooks/use-toast.ts` | 133 | `memoryState` | module-level custom store | `dispatch` | toast hook/toaster | 전역 store처럼 동작하지만 React 외부 mutable state |
| local state | `components/ResearchHighlightsSlider.tsx` | 27 | `currentIndex`, `isAutoPlaying` | slider component | interval/button handlers | slider UI | empty highlights race 후보 |
| server state | `components/pages/HomePage.tsx` | 18 | `pageContent`, `achievements`, `latestNews` | HomePage | `fetchPageData` effect | home sections | `any`와 local server state |
| server state | `components/pages/NoticeBoardPage.tsx` | 24 | `notices`, counts | NoticeBoardPage | Supabase query/mutation | NewsPage props | CMS direct data access |
| server state | `components/pages/GalleryBoardPage.tsx` | 22 | `posts`, counts | GalleryBoardPage | Supabase query | Gallery list UI | race guard 없음 |
| server state | `components/pages/PublicationsPage.tsx` | 34 | `publications` | PublicationsPage | Supabase query | publication list/detail | list/detail UI state와 결합 |

## 메모

- 서버에서 온 데이터가 local state로 관리되는 지점이 많다. React Query를 이미 도입한 admin 영역과 그렇지 않은 simulation/CMS 영역의 전략이 갈라져 있다.
- 전역 상태 남용은 확인되지 않았다. 오히려 server state가 여러 component local state에 흩어져 있어 cache/refetch 정책이 분산된 것이 더 큰 문제다.
