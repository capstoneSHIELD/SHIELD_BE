# Server / Client State Review

| ID | 심각도 | 파일 경로 | 라인 | 상태/데이터 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S4-STATE-001 | High | `components/pages/Simulation2Page.tsx` | 592 | chat/workflow/job/viz/form state | client UI state, server-derived workflow state, WebSocket lifecycle refs가 한 component에 집중되어 있다 | 상태 변경의 영향 범위가 크고 테스트가 어렵다 | `useSimulationWorkflow`, `useJobMonitorSession`, `useVisualizationSession`, `useSimulationDraft`로 분리 |
| S4-SERVER-001 | High | `components/simulation/JobResultListCard.tsx` | 93 | `jobs`, `results` | job/result server state를 component local state로 저장하고 `refreshKey`로 재조회한다 | 빠른 simulation 전환/refresh에서 stale 응답 반영 가능 | query key 기반 cache 또는 request sequence guard 도입 |
| S4-SERVER-002 | Medium | `components/simulation/SimulationListCard.tsx` | 56 | `items`, `page`, `loading`, `error` | simulation list server state와 pagination UI state가 같은 component에 있다 | list 정책 변경 시 UI component 수정 필요 | `useSimulationList` hook 또는 React Query 전환 |
| S4-SERVER-003 | Medium | `components/simulation/SessionListCard.tsx` | 73 | `sessions`, delete/rename/search state | server list state와 action/form state가 list component에 집중되어 있다 | delete/rename 후 reload 흐름 테스트가 어렵다 | `useChatSessions`와 `SessionRenameForm`/`SessionDeleteDialog` 분리 |
| S4-SERVER-004 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 286 | `detail`, `fieldCatalog`, `fieldFiles`, `filters` | result detail/catalog/files server state와 field filter form state가 한 component에 있다 | catalog/files stale data와 UI 상태 꼬임 가능 | `useResultDetail`, `useResultFieldCatalog`, `useResultFieldFiles` 분리 |
| S4-CLIENT-001 | Medium | `components/pages/AdminPage3.tsx` | 501 | admin dialog/form state | account/job/result/viz dialog form state가 query/mutation owner와 같은 component에 있다 | tab 하나 변경이 admin 전체 render와 충돌할 수 있음 | tab별 container와 dialog-specific form hook 분리 |
| S4-CACHE-001 | Medium | `components/pages/AdminPage3.tsx` | 510 | `fieldFilesData` | React Query `fetchQuery` 결과를 local state에 별도 복사한다 | cache와 local state가 불일치할 수 있다 | query key를 UI state와 직접 연결하거나 selected query data를 cache에서 읽기 |
| S4-STATE-002 | Medium | `components/pages/HomePage.tsx` | 18 | CMS home data | CMS server data를 `any` local state로 보관하고 error state가 없다 | 실패 시 loading/error UX가 불안정하고 타입 drift를 놓칠 수 있음 | `useHomeContent` query hook과 typed view model 도입 |
| S4-SERVER-005 | Medium | `components/pages/NoticeBoardPage.tsx` | 24 | notices/counts | Supabase query 결과와 pagination/search UI state가 page component에 결합되어 있다 | server data 정책과 presentation props contract가 함께 흔들림 | notice query hook/service로 server state 분리 |
| S4-SERVER-006 | Low | `components/pages/GalleryBoardPage.tsx` | 22 | posts/counts | gallery list server state를 local state로 관리한다 | search/page 전환 중 stale data 반영 가능 | notice와 같은 list query abstraction 검토 |

## 실제 코드 근거

- `docs/architecture/state.md`는 Simulation2 job/result/visualization 상태의 source of truth를 backend API로 정의한다.
- `app/providers.tsx:15`에서 TanStack Query provider가 존재한다.
- admin 영역은 React Query를 사용하지만, simulation list/result explorer/CMS list는 local state 중심이다.

## 추론

- React Query를 전체 영역에 일괄 도입해야 한다는 뜻은 아니다. 다만 server state의 생명주기, stale response 방지, loading/error 일관성을 hook/service 경계에서 통일할 필요가 있다.
