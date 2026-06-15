# Component Hierarchy

## 주요 흐름

```text
Route
-> Page / Container
-> Feature Component
-> Section/List/Form/Viewer Component
-> UI Primitive
```

## 확인한 component 호출 구조

| 상위 component | 하위 component | 파일 경로 | 전달 props | 전달 handler | 책임 |
|---|---|---|---|---|---|
| `app/simulation2/page.tsx` | `Simulation2Page` | `app/simulation2/page.tsx:47` | `initialSessionId`, `nickname` | 없음 | 인증 완료 후 simulation2 container 진입 |
| `Simulation2Page` | `BugReportButton` | `components/pages/Simulation2Page.tsx:2615` | `className` | 없음 | 단순 외부 form 링크 UI |
| `Simulation2Page` | `WorkspaceTabsCard` | `components/pages/Simulation2Page.tsx:2622` | active tab, current simulation/result ids, refresh keys, disabled 상태 | select/cancel/open handlers | sidebar workspace 조립 |
| `WorkspaceTabsCard` | `SimulationListCard` | `components/simulation/WorkspaceTabsCard.tsx:66` | `currentSimulationId`, `refreshKey`, `bare` | `onSelect` | simulation list 표시/선택 |
| `WorkspaceTabsCard` | `JobResultListCard` | `components/simulation/WorkspaceTabsCard.tsx:74` | `simulationId`, `simulationTitle`, `refreshKey`, `selectedResultId`, `actionsDisabled` | cancel/select/open handlers | job/result list 표시/선택 |
| `Simulation2Page` | `ResultExplorerPanel` | `components/pages/Simulation2Page.tsx:3070` | `resultId`, `initialResult`, `selectedField`, `visualizationPending`, disabled 상태 | field change/open visualization | result detail, file, field explorer |
| `Simulation2Page` | `VisualizationControlBar` | `components/pages/Simulation2Page.tsx:3140` | fields, selected field/colormap/timestep, pending/error | field/colormap/timestep/camera/screenshot handlers | viewer control intent UI |
| `Simulation2Page` | `AdvancedTramePanel` | `components/pages/Simulation2Page.tsx:3190` | session id, fields | 확인 필요 | advanced Trame 기능 |
| `Simulation2Page` | `SessionListCard` | `components/pages/Simulation2Page.tsx:3309` | `currentSessionId`, `actionsDisabled`, `refreshKey`, `bare` | select/new/delete/rename handlers | chat session list/search/edit |
| `Simulation2Page` | `MarkdownMessage` | `components/pages/Simulation2Page.tsx:3380` | assistant message content | 없음 | assistant markdown 렌더링 |
| `Simulation2Page` | `ApiErrorNotice` | `components/pages/Simulation2Page.tsx:3474` | workflow/global error | 없음 | normalized error 표시 |
| `app/cmsl20043/page.tsx` | `AdminPage3` | `app/cmsl20043/page.tsx:15` | 없음 | 없음 | admin console container |
| `AdminPage3` | inline tables | `components/pages/AdminPage3.tsx:1327` | query data | row click/action handlers | account/user/simulation/job/result table 렌더링 |
| `AdminPage3` | inline dialogs | `components/pages/AdminPage3.tsx:2906` | dialog state | review/user/cancel/close handlers | admin mutation confirmation |
| `NoticeBoardPage` | `NewsPage` | `components/pages/NoticeBoardPage.tsx:110` | notices/loading/error/session/page/search | search/page/edit/delete/pin handlers | notice list presentation |
| `GalleryBoardPage` | `GalleryListPage` | `components/pages/GalleryBoardPage.tsx:81` | posts/loading/error/session/page/search | search/page/select handlers | gallery list presentation |
| `ResearchPageTemplate` | `ImageCarousel` | `components/ResearchPageTemplate.tsx:161` | mapped media items | 없음 | research section media carousel |
| `HomePage` | `ResearchHighlightsSlider` | `components/pages/HomePage.tsx:10` | highlights | 확인 필요 | homepage research highlight carousel |
| `EditNoticePage` | `TiptapEditor` | `components/pages/EditNoticePage.tsx:170` | content value | content change/image upload | rich text edit form |

## Props 흐름 요약

- PFM simulation2는 `Simulation2Page`가 workflow/job/result/viz 상태와 handlers를 소유하고 하위 feature component로 전달한다.
- `WorkspaceTabsCard`는 직접 도메인 로직을 거의 갖지 않고 `SimulationListCard`, `JobResultListCard`로 props/handler를 전달한다.
- `NoticeBoardPage -> NewsPage`, `GalleryBoardPage -> GalleryListPage`는 container/presentation 분리가 있으나, presentation component가 pagination/search/edit/delete handler를 다수 받는다.
- `AdminPage3`는 tab, table, dialog를 파일 내부에서 직접 렌더링해 component tree가 한 파일에 집중되어 있다.

## 확인 필요

- `AdvancedTramePanel` 하위의 `TrameControlPanel`, `TrameExportCenter`, `CompositeDialog`는 별도 Lab/Trame 고급 기능으로 보이며 Session 4/5에서 hook/service 경계를 추가 확인해야 한다.
- legacy `AdminPage`, `AdminPage2`, `PFMSimulationPage`가 현재 제품 범위에 포함되는지 확인 필요하다.
