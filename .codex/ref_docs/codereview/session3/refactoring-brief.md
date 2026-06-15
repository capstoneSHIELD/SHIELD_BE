# Session 3 Refactoring Brief

| 우선순위 | 리팩토링 대상 | 현재 문제 | 개선 방향 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|---|
| P1: 구조 개선 필요 | `components/pages/Simulation2Page.tsx` | workflow, chat, job monitor, result/viz, rendering이 한 component에 집중 | hook 단위(`useSimulationWorkflow`, `useJobMonitor`, `useVisualizationSession`)와 presenter 단위(`ChatPanel`, `WorkspacePanel`, `VisualizationPanel`)로 단계적 분리 | 매우 큼 | 한 번에 전체 분리 금지. 테스트가 있는 경계부터 작게 이동 |
| P1: 구조 개선 필요 | `components/pages/AdminPage3.tsx` | admin tab, query/mutation, table, dialog가 한 파일에 집중 | `AdminOverviewPanel`, `AccountRequestsPanel`, `UsersPanel`, `SimulationAdminPanel`, dialog component 분리 | 큼 | URL query key와 React Query invalidation이 얽혀 있어 Session 4 검토 후 진행 |
| P1: 구조 개선 필요 | `components/simulation/ResultExplorerPanel.tsx` | result detail/files/filter/download state와 API 호출이 component 내부에 집중 | `useResultDetail`, `useResultFieldCatalog`, `useResultFieldFiles`, `useResultDownload` 분리 | 중간 | stale response/race guard를 함께 설계 |
| P1: 구조 개선 필요 | `components/simulation/SessionListCard.tsx` | list/search/delete/rename/dialog를 한 component에서 처리 | `useChatSessions`, `SessionListView`, `SessionRenameForm`, `SessionDeleteDialog` 분리 | 중간 | parent callbacks(`onDeleted`, `onRenamed`) contract 유지 |
| P1: 구조 개선 필요 | `components/simulation/JobResultListCard.tsx`, `SimulationListCard.tsx` | server state와 list UI가 결합 | query hook 또는 request sequence guard 도입 후 presenter 분리 | 중간 | refreshKey와 기존 테스트 영향 확인 |
| P1: 구조 개선 필요 | CMS list/edit containers | Supabase query/storage/DB mutation이 component 내부에 있음 | `useNoticeBoard`, `useGalleryBoard`, `useNoticeEditor`, storage adapter 도입 | 큼 | Supabase RLS/권한 정책 확인 필요 |
| P2: 코드 품질 개선 | `components/pages/EditNoticePage.tsx` invalid id/loading | invalid route param에서 loading이 유지될 수 있음 | id parser와 error/not-found UI 도입 | 작음 | route guard/params 정책과 맞춰야 함 |
| P2: 코드 품질 개선 | file sanitizer/download helper 중복 | `sanitizeForStorage`, blob download helper가 중복 | 공통 util 또는 service helper로 이동 | 작음 | 파일명 정책이 backend/storage 정책과 충돌하지 않는지 확인 |
| P2: 코드 품질 개선 | list/pagination presentation | `NewsPage`, `GalleryListPage`, admin tables가 유사한 loading/empty/pagination UI를 반복 | 공통 `ListState`, `PaginationControls`, table empty row pattern 정리 | 중간 | 디자인 변경 범위를 작게 유지 |
| P2: 코드 품질 개선 | `MemberDetailModal` | custom modal 접근성 확인 필요 | Radix Dialog 또는 접근성 보강 | 작음 | 기존 스타일/모바일 레이아웃 회귀 확인 |
| P3: 장기 개선 | `components/reactbits/*`, heavy visual components | 시각효과 component는 성능 비용 가능성이 있으나 이번 Session에서 깊게 검토하지 않음 | 별도 visual/performance audit | 중간 | product workflow 리팩토링 후 진행 |

## 먼저 건드리면 안 되는 민감한 영역

- `Simulation2Page`의 WebSocket/polling/visualization cleanup 흐름.
- `AdminPage3`의 React Query invalidation과 URL query correction 흐름.
- CMS edit form의 storage delete/upload 순서.

## 안전하게 먼저 개선 가능한 영역

- `EditNoticePage` invalid id loading 처리.
- 중복 `sanitizeForStorage` helper 추출.
- `ImageCarousel`와 `Simulation2Page` list key 안정화.
- `MemberDetailModal` 접근성 속성 보강.

## Session 4와 연결할 항목

- `ResultExplorerPanel`, `JobResultListCard`, `SessionListCard`, `SimulationListCard`의 server state/client state 분리.
- `Simulation2Page` polling/WebSocket state와 stale closure/race condition.
- `AdminPage3` React Query key, invalidation, URL state parsing.
