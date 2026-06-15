# Props and State Flow

| 파일 경로 | 라인 | component | 상태/props | 흐름 | 문제 가능성 | 개선 방향 |
|---|---:|---|---|---|---|---|
| `components/pages/Simulation2Page.tsx` | 592 | `Simulation2Page` | `messages`, refresh keys, loading, pending action, workflow, visualization state | page container가 대부분의 client/server/workflow state를 소유하고 하위 feature component로 전달 | 상태 소유자가 지나치게 상위에 집중되어 props/handler가 늘어난다 | workflow/job/viz/chat 단위 hook과 presenter로 분리 |
| `components/pages/Simulation2Page.tsx` | 2622 | `Simulation2Page -> WorkspaceTabsCard` | active tab, simulation id/title, result id, refresh keys | sidebar workspace 관련 props가 한 번에 전달 | `WorkspaceTabsCard`가 pass-through wrapper가 되어 props drilling을 늘린다 | workspace state 전용 hook 또는 context는 신중히 검토. 우선 하위 hook 분리 |
| `components/simulation/WorkspaceTabsCard.tsx` | 14 | `WorkspaceTabsCard` | 15개 이상의 props/callback | `SimulationListCard`, `JobResultListCard`로 재전달 | 탭 wrapper가 직접 소유하지 않는 props가 많다 | simulation/result workspace container로 역할 재정의 |
| `components/simulation/SimulationListCard.tsx` | 56 | `SimulationListCard` | `items`, `error`, `loading`, `page` | 내부 API 조회 결과를 local state로 보유 | server state와 UI pagination state가 한 component에 섞임 | `useSimulationList` 또는 React Query로 server state 분리 |
| `components/simulation/JobResultListCard.tsx` | 93 | `JobResultListCard` | `jobs`, `results`, `error`, `loading` | `simulationId`/`refreshKey` 변경 시 API 조회 후 local state 갱신 | 이전 요청 응답이 나중에 도착하는 race 가능성은 Session 4에서 재검토 필요 | query key 기반 cache 또는 request sequence guard 도입 |
| `components/simulation/SessionListCard.tsx` | 73 | `SessionListCard` | sessions/page/search/delete/rename state | 검색/삭제/rename UI와 server state를 내부에서 함께 관리 | form state와 server state가 한 component에 집중 | `useChatSessions`, `SessionRenameForm`, `SessionDeleteDialog` 분리 |
| `components/simulation/ResultExplorerPanel.tsx` | 286 | `ResultExplorerPanel` | detail/catalog/files/filter/download state | selected result와 selected field를 기준으로 API 호출 및 form filter 관리 | field catalog/files 요청의 stale 응답 가능성은 Session 4에서 재검토 필요 | result detail/files hook과 file list presenter 분리 |
| `components/simulation/VisualizationControlBar.tsx` | 91 | `VisualizationControlBar` | `timestepDraft`, `timestepError` | parent에서 받은 timestep을 local draft로 편집 후 commit callback 호출 | UI form state만 보유하므로 현재 책임은 비교적 명확 | 유지. validation helper는 testable pure function으로 유지 |
| `components/pages/AdminPage3.tsx` | 489 | `AdminPage3` | URL query derived state | query params를 container에서 parsing하고 query key/mutation 흐름에 사용 | invalid query parsing은 Session 4에서 상태 안정성 이슈로 재검토 필요 | `useAdminUrlState`로 parser/correction 분리 |
| `components/pages/AdminPage3.tsx` | 500 | `AdminPage3` | review/user/cancel/viz dialog form state | tab UI와 dialog state가 같은 component에 존재 | dialog state 변경이 대형 admin component 전체와 결합 | dialog별 component/hook 분리 |
| `components/pages/NoticeBoardPage.tsx` | 110 | `NoticeBoardPage -> NewsPage` | notices, loading, error, session, page/search values | container가 data와 handlers를 presentational component에 전달 | props 수가 많고 board list 정책이 NewsPage props contract에 노출 | `NewsListView` props를 view model 기준으로 축소 |
| `components/pages/NewsPage.tsx` | 29 | `NewsPage` | list/search/pagination/action props | search term setter와 edit/delete/pin handler를 직접 받음 | presentation component가 admin action contract까지 알고 있다 | actions slot 또는 row action model 도입 검토 |
| `components/pages/GalleryBoardPage.tsx` | 81 | `GalleryBoardPage -> GalleryListPage` | posts, loading, error, session, page/search values | Gallery list props 전달 | Notice/Gallery list 패턴이 유사하지만 분리되어 있음 | 공통 list pagination/search presenter 추출 후보 |
| `components/pages/EditNoticePage.tsx` | 33 | `EditNoticePage` | title/content/author/attachments/loading/message | form state와 storage/DB mutation 상태를 모두 내부 보유 | validation, upload, update, navigation이 결합 | form state hook과 service mutation 분리 |
| `components/pages/HomePage.tsx` | 18 | `HomePage` | pageContent, achievements, latestNews, selectedCapabilityId | CMS fetch 결과와 UI hover state가 같은 component에 존재 | homepage layout이 커지고 CMS contract가 UI에 직접 노출 | `useHomeContent` hook과 section components 분리 |

## 확인 필요

- 전역 상태 또는 context로 분리하는 것이 항상 개선은 아니다. 현재 우선순위는 server state/API 호출을 hook/service로 내리는 것이다.
- props drilling 해소를 위해 context를 도입하기 전에 `Simulation2Page`와 `AdminPage3`의 책임 분리가 먼저 필요하다.
