# Session 3 Findings

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S3-COMP-001 | High | component responsibility | `components/pages/Simulation2Page.tsx` | 589 | simulation2 핵심 component가 3347 lines로 workflow, API, WebSocket, state, UI를 모두 가진다 | 회귀 위험과 테스트 비용이 가장 큼 | workflow/job/viz/chat 단위 hook + presenter 분리 |
| S3-COMP-002 | High | component responsibility | `components/pages/AdminPage3.tsx` | 483 | admin component가 2942 lines로 query/mutation/table/dialog/tab UI를 모두 가진다 | admin 기능 수정 영향 범위가 과도함 | tab별 container와 dialog/table component 분리 |
| S3-STATE-001 | Medium | local state | `components/simulation/ResultExplorerPanel.tsx` | 286 | result detail/catalog/files/filter/download state가 한 component에 집중 | API/cache/race 정책 변경이 UI에 결합 | result explorer hook 세트로 분리 |
| S3-STATE-002 | Medium | local state | `components/simulation/SessionListCard.tsx` | 73 | session list/search/delete/rename 상태와 API 호출이 list component 내부에 집중 | 기능 추가와 테스트가 어려움 | `useChatSessions`와 list/dialog presenter 분리 |
| S3-STATE-003 | Medium | local state | `components/simulation/JobResultListCard.tsx` | 93 | job/result server state와 loading/error가 local state로 관리됨 | refreshKey 기반 재조회와 UI가 결합 | query hook 또는 request sequence guard 도입 |
| S3-PROPS-001 | Medium | props drilling | `components/simulation/WorkspaceTabsCard.tsx` | 14 | workspace wrapper가 active tab, ids, refresh keys, 여러 callbacks를 pass-through한다 | props contract가 계속 커질 수 있음 | workspace domain hook/presenter 경계 재정의 |
| S3-CMS-001 | Medium | feature component | `components/pages/NoticeBoardPage.tsx` | 58 | notice container가 Supabase query/mutation을 직접 수행 | UI와 persistence 결합 | CMS service/query hook 도입 |
| S3-CMS-002 | Medium | form component | `components/pages/EditNoticePage.tsx` | 73 | edit form이 file picker, storage upload/remove, DB update, redirect를 모두 수행 | storage/form 정책 변경 영향이 큼 | editor hook과 storage adapter 분리 |
| S3-CMS-003 | Medium | props type | `components/ResearchPageTemplate.tsx` | 53 | CMS content access가 `any`와 field string 기반 | schema drift를 타입으로 잡기 어려움 | CMS view model과 localized getter 타입화 |
| S3-RENDER-001 | Medium | re-render | `components/pages/Simulation2Page.tsx` | 3355 | chat message list에서 index key 사용 | list reconciliation 불안정 가능 | stable message id/key 도입 |
| S3-RENDER-002 | Low | re-render | `components/ImageCarousel.tsx` | 33 | carousel item에 index key 사용 | media reorder 시 slide state 재사용 위험 | url 기반 key 사용 |
| S3-ACCESS-001 | Medium | accessibility | `components/MemberDetailModal.tsx` | 18 | custom modal에 dialog role/focus trap/escape 처리 확인되지 않음 | keyboard/screen reader 접근성 저하 가능 | Radix Dialog 기반 전환 또는 접근성 보강 |
| S3-QUALITY-001 | Medium | loading/error/empty | `components/pages/EditNoticePage.tsx` | 46 | invalid id에서 loading이 종료되지 않을 수 있음 | 잘못된 URL에서 무한 loading 가능 | id parsing/error UI 도입 |
| S3-QUALITY-002 | Medium | loading/error/empty | `components/pages/HomePage.tsx` | 36 | homepage data fetch에 catch/finally/error state가 없음 | fetch 실패 시 loading 유지 가능 | `useHomeContent` hook과 error state 추가 |
| S3-DUP-001 | Medium | duplication | `components/pages/AdminPage.tsx` | 43 | file name sanitizer와 download helper류가 여러 파일에 중복 | 정책 변경 누락 가능 | storage/download util로 이동 |
| S3-BOUNDARY-001 | Low | common component | `components/common/ApiErrorNotice.tsx` | 19 | error presenter는 normalized error view model을 사용해 경계가 명확함 | 재사용 기준으로 삼을 수 있음 | page-local error UI를 같은 패턴으로 통일 |

## Session 1/2 연결 요약

- `S3-COMP-001`은 Session 1 `S1-ARCH-001`, Session 2 `S2-CONTAINER-001`의 component 계층 근거이다.
- `S3-COMP-002`는 Session 1 `S1-ARCH-004`, Session 2 `S2-CONTAINER-002`의 component 계층 근거이다.
- `S3-CMS-*`는 Session 1/2에서 지적한 CMS/Supabase 직접 의존 문제를 form/list component 단위로 구체화한다.

## 확인 필요

- CMS HTML content sanitize 정책.
- legacy admin/workbench component 유지 여부.
- Trame advanced panel 하위 component의 API/service 경계.
