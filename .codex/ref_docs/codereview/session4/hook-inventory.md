# Hook Inventory

| 구분 | 파일 경로 | 라인 | hook 이름 | 역할 | 사용 위치 | 비고 |
|---|---|---:|---|---|---|---|
| custom hook | `hooks/useIdleTimer.ts` | 15 | `useIdleTimer` | idle timer event listener와 timeout cleanup 관리 | idle 상태 감지 UI | dependency와 cleanup이 명시되어 비교적 안전한 패턴 |
| custom hook | `hooks/use-toast.ts` | 173 | `useToast` | module-level memory state와 listener 기반 toast store 구독 | toast UI | `useEffect` dependency가 `[state]`라 상태 변경마다 재구독 |
| custom hook | `hooks/use-mobile.ts` | 10 | `useIsMobile` | media query 기반 mobile 여부 계산 | responsive component | 최초 render에서 `!!undefined`가 `false`가 됨 |
| Context hook | `components/LanguageProvider.tsx` | 230 | `useLanguage` provider state | language를 localStorage와 동기화 | 앱 전역 language context | provider value memoization 여부 확인 필요 |
| React Query hook | `app/providers.tsx` | 15 | `QueryClientProvider` | TanStack Query client 제공 | 앱 provider | 기본 `staleTime` 30초, `refetchOnWindowFocus: false` |
| React Query hook | `components/pages/AdminPage3.tsx` | 551 | `useQuery` | admin me/health/ready/account/users/sim/jobs/results/viz 조회 | AdminPage3 | query가 한 container에 집중됨 |
| React Query hook | `components/pages/AdminPage3.tsx` | 648 | `useMutation` | input preview/job sync/cancel/result file/viz/account/user mutation | AdminPage3 | mutation과 dialog/form state가 같은 component에 집중 |
| useState | `components/pages/Simulation2Page.tsx` | 592 | `messages` 등 다수 | chat/workflow/UI/server-derived 상태 소유 | Simulation2Page | 상태 종류가 한 component에 과도하게 집중 |
| useRef | `components/pages/Simulation2Page.tsx` | 609 | `pollIntervalRef`, WS refs, sync refs | polling, WebSocket, request sequence lifecycle 관리 | Simulation2Page | lifecycle 격리 hook 후보 |
| useMemo | `components/pages/Simulation2Page.tsx` | 658 | `workflowErrorCategory` | workflow error에서 파생 상태 계산 | Simulation2Page | derived state 자체는 적절하나 owner가 큼 |
| useCallback | `components/pages/Simulation2Page.tsx` | 743 | `checkVisualizationAvailability` 등 | API 호출과 workflow 상태 변경 결합 | Simulation2Page | hook/service 분리 후보 |
| useEffect | `components/pages/Simulation2Page.tsx` | 1266 | editable params sync | workflow generated params를 form draft로 동기화 | Simulation2Page | form state와 workflow state 결합 |
| useEffect | `components/pages/Simulation2Page.tsx` | 2100 | visualization periodic sync | active visualization detail 주기 동기화 | Simulation2Page | in-flight/sequence guard 존재 |
| useState | `components/pages/AdminPage3.tsx` | 501 | dialog/form state | review/user/job/viz form/dialog state 소유 | AdminPage3 | tab/dialog별 hook 분리 후보 |
| useMemo | `components/pages/AdminPage3.tsx` | 520 | query params memo | URL query 기반 API params 생성 | AdminPage3 | URL parser helper/hook 후보 |
| useEffect | `components/pages/AdminPage3.tsx` | 918 | URL correction/reset effects | invalid selection 정리와 page correction | AdminPage3 | URL state와 server state effect가 집중 |
| useState | `components/simulation/ResultExplorerPanel.tsx` | 286 | detail/catalog/files/filter state | result detail, field catalog, files, filters 관리 | ResultExplorerPanel | server state와 form state가 같은 component에 집중 |
| useRef | `components/simulation/ResultExplorerPanel.tsx` | 299 | `detailRequestSeqRef` | result detail stale response 방지 | ResultExplorerPanel | detail에는 guard 존재 |
| useCallback | `components/simulation/ResultExplorerPanel.tsx` | 392 | `loadFieldCatalog` | field catalog API 호출 | ResultExplorerPanel | catalog/files에는 request sequence guard가 없음 |
| useState | `components/simulation/JobResultListCard.tsx` | 93 | `jobs`, `results`, `loading`, `error` | job/result server state local 관리 | JobResultListCard | React Query 또는 sequence guard 후보 |
| useState | `components/simulation/SimulationListCard.tsx` | 56 | `items`, `page`, `loading`, `error` | simulation list server state local 관리 | SimulationListCard | cache key 기반 hook 후보 |
| useState | `components/simulation/SessionListCard.tsx` | 73 | session/search/delete/rename state | session list와 form/action state 관리 | SessionListCard | hook + presenter 분리 후보 |
| useEffect | `components/ResearchHighlightsSlider.tsx` | 32 | autoplay interval | highlight slider 자동 전환 | ResearchHighlightsSlider | empty array에서 interval이 먼저 등록될 수 있음 |
| useEffect | `components/pages/HomePage.tsx` | 36 | homepage CMS fetch | home content/news/achievement 로드 | HomePage | 실패 시 catch/finally 없음 |
| useReducer | 전체 코드 | 0 | 없음 | `rg` 기준 직접 사용 없음 | 확인 범위: app/components/hooks/lib | 확인 필요: node_modules 제외 |
| SWR hook | 전체 코드 | 0 | 없음 | `useSWR` 직접 사용 없음 | 확인 범위: app/components/hooks/lib/package | 패키지 의존성도 확인되지 않음 |
| store hook | 전체 코드 | 0 | 없음 | Zustand/Redux 직접 사용 없음 | 확인 범위: app/components/hooks/lib/package | 전용 store directory는 확인되지 않음 |

## 확인 필요

- `components/ui/sidebar.tsx`의 shadcn Context는 UI primitive 성격이므로 Session 4 핵심 문제에서는 제외했다.
- legacy admin/page 일부는 현재 제품 범위 포함 여부 확인 필요.
