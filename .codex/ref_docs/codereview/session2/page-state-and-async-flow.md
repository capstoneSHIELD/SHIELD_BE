# Page/Container 상태 및 비동기 흐름

| 파일 경로 | 라인 | 처리 유형 | 코드 역할 | 실행 조건 | 문제 가능성 |
|---|---:|---|---|---|---|
| `app/simulation2/page.tsx` | 9 | route-state | `useSearchParams()`로 `session` query 추출 | route render | query param validation 기준 확인 필요 |
| `app/simulation2/page.tsx` | 12 | page-state | `authenticated`, `loading`, `user` 상태 관리 | route render | route guard state가 page에 직접 존재 |
| `app/simulation2/page.tsx` | 16 | async | token 확인 후 `getMe()` 호출 | mount | auth guard 재사용 어려움 |
| `app/simulation2/page.tsx` | 20 | redirect | token 없으면 `/pfm_chat/login` 이동 | mount, token 없음 | `window.location.href` 직접 사용 |
| `app/simulation2/page.tsx` | 31 | error | `getMe()` 실패 시 `clearTokens()` | auth 실패 | 실패 원인별 UX 없음 |
| `app/simulation2/page.tsx` | 38 | cleanup | `cancelled` flag 설정 | unmount | promise race 최소화는 있으나 hook 추출 대상 |
| `app/simulation2/page.tsx` | 43 | loading | auth 확인 전 loading 표시 | loading 또는 unauthenticated | Suspense fallback과 중복 |
| `app/pfm_chat/login/page.tsx` | 8 | async | token 존재 시 `getMe()` 호출 | login route mount | login route에도 auth redirect 로직 분산 |
| `app/pfm_chat/login/page.tsx` | 15 | redirect | token valid 시 `/simulation2` 이동 | `getMe()` 성공 | redirect 정책 공통화 필요 |
| `app/cmsl2004/page.tsx` | 10 | page-state | Supabase `session`, `loading` 상태 | route render | `cmsl20042`와 중복 |
| `app/cmsl2004/page.tsx` | 14 | async | `supabase.auth.getSession()` 호출 | mount | route에서 infrastructure 직접 호출 |
| `app/cmsl2004/page.tsx` | 19 | async | `onAuthStateChange` subscription | mount | cleanup은 있음 |
| `app/cmsl2004/page.tsx` | 23 | cleanup | auth subscription 해제 | unmount | 중복 guard 추출 가능 |
| `app/cmsl20042/page.tsx` | 14 | async | `supabase.auth.getSession()` 호출 | mount | `cmsl2004`와 동일 패턴 |
| `components/pages/AdminPage3.tsx` | 489 | route-state | status/tab/selected ids/page/size query 읽기 | container render | URL state 파싱이 container에 집중 |
| `components/pages/AdminPage3.tsx` | 539 | route-state | `replaceQuery`로 URL query 갱신 | filter/tab/selection 변경 | helper 분리 가능 |
| `components/pages/AdminPage3.tsx` | 551 | async | `meQuery`로 current admin account 조회 | container mount | 권한 gate의 기준 query |
| `components/pages/AdminPage3.tsx` | 559 | async | health query | `canUseAdmin` | overview/admin 상태 조회 |
| `components/pages/AdminPage3.tsx` | 573 | async | account requests query | `canUseAdmin` | tab별 enabled 조건 관리 |
| `components/pages/AdminPage3.tsx` | 597 | async | jobs query | simulation 선택 | active job refetch interval 포함 |
| `components/pages/AdminPage3.tsx` | 601 | async | active job polling interval 계산 | jobs query active | polling 정책 container 내부 |
| `components/pages/AdminPage3.tsx` | 666 | async | job sync mutation | 사용자 액션 | onSuccess/onError와 invalidation 집중 |
| `components/pages/AdminPage3.tsx` | 697 | async | job cancel mutation | 사용자 액션 | query invalidation과 toast 포함 |
| `components/pages/AdminPage3.tsx` | 782 | async | visualization create mutation | 사용자 액션 | URL state update까지 수행 |
| `components/pages/AdminPage3.tsx` | 919 | route-state | invalid status query 정규화 | activeTab/rawStatus 변경 | URL correction side effect가 container 내부 |
| `components/pages/AdminPage3.tsx` | 936 | route-state | simulation 변경 시 job/result/viz query 제거 | selectedSimulationId 변경 | URL state cascade가 복잡 |
| `components/pages/AdminPage3.tsx` | 1139 | loading | `meQuery.isLoading` early return | current account loading | route fallback과 역할 중복 가능 |
| `components/pages/AdminPage3.tsx` | 1147 | error | `meQuery.isError` early return | current account error | error boundary와 별도 |
| `components/pages/AdminPage3.tsx` | 1157 | error | inactive account early return | account inactive | 권한/상태 presenter 분리 가능 |
| `components/pages/Simulation2Page.tsx` | 592 | page-state | messages/refresh/loading/error/workflow 등 다수 state | container render | 상태가 대형 container에 집중 |
| `components/pages/Simulation2Page.tsx` | 611 | cleanup | job/viz WebSocket refs 보유 | container render | lifecycle 복잡도 높음 |
| `components/pages/Simulation2Page.tsx` | 1017 | cleanup | `beforeunload` listener 등록 | active resource 존재 시 | cleanup 정책 hook 추출 필요 |
| `components/pages/Simulation2Page.tsx` | 1354 | cleanup | poll/WS/timer interval 정리 | unmount | 핵심 회귀 위험 영역 |
| `components/pages/Simulation2Page.tsx` | 1444 | async | job status polling | job monitor | polling fallback과 WS 경계 복잡 |
| `components/pages/Simulation2Page.tsx` | 1674 | async | job monitor WebSocket 연결 | job start/restore | reconnect/race 관리 필요 |
| `components/pages/Simulation2Page.tsx` | 1949 | async | visualization WebSocket 연결 | visualization active | reconnect/error 상태 관리 |
| `components/pages/Simulation2Page.tsx` | 2238 | async | chat message send | 사용자 입력/프롬프트 | chat, simulation action, error 흐름 결합 |
| `components/pages/Simulation2Page.tsx` | 2437 | async | simulation start | start button | job 생성/monitoring과 연결 |
| `components/pages/NoticeBoardPage.tsx` | 23 | page-state | session/notices/loading/error/pagination/search 상태 | container render | list state가 container에 집중 |
| `components/pages/NoticeBoardPage.tsx` | 38 | async | client Supabase session 재조회 | session prop 없음 | server session과 중복 source |
| `components/pages/NoticeBoardPage.tsx` | 58 | async | Supabase notices query | page/search 변경 | service 계층 없음 |
| `components/pages/NoticeBoardPage.tsx` | 99 | async | notice pin update | 사용자 액션 | mutation 후 refresh 정책 확인 필요 |
| `components/pages/NoticeBoardPage.tsx` | 105 | async | notice delete | 사용자 액션 | 권한/confirm/error 표준 확인 필요 |
| `components/pages/GalleryDetailPage.tsx` | 61 | async | gallery detail select | `id` 변경 | id validation/not-found 표준 없음 |
| `components/pages/GalleryDetailPage.tsx` | 70 | async | gallery delete | 사용자 액션 | service/권한 경계 없음 |
| `components/pages/NoticeDetailPage.tsx` | 62 | async | notice detail select | `id` 변경 | id validation/not-found 표준 없음 |
| `components/pages/NoticeDetailPage.tsx` | 71 | async | notice delete | 사용자 액션 | service/권한 경계 없음 |
| `components/pages/EditGalleryPage.tsx` | 27 | sync | route id를 `Number(id)`로 변환 | container render | NaN 처리 확인 필요 |
| `components/pages/EditGalleryPage.tsx` | 44 | async | gallery select | mount/postId 변경 | invalid id error UX 확인 필요 |
| `components/pages/EditGalleryPage.tsx` | 76 | async | editor image upload | editor action | storage adapter 없음 |
| `components/pages/EditGalleryPage.tsx` | 112 | async | gallery update | form submit | form/service 책임 결합 |
| `components/pages/EditNoticePage.tsx` | 30 | sync | route id를 `Number(id)`로 변환 | container render | NaN 처리 확인 필요 |
| `components/pages/EditNoticePage.tsx` | 48 | async | notice select | mount/noticeId 변경 | invalid id error UX 확인 필요 |
| `components/pages/EditNoticePage.tsx` | 85 | async | editor image upload | editor action | storage adapter 없음 |
| `components/pages/EditNoticePage.tsx` | 122 | async | notice update | form submit | form/service 책임 결합 |

## race/cleanup 주의 영역

- `Simulation2Page`는 polling, WebSocket, reconnect timer, beforeunload cleanup이 얽혀 있어 분리 전 테스트가 필요하다.
- `AdminPage3`는 URL query correction effect와 query enabled 조건이 얽혀 있어 URL state helper 분리 시 회귀 테스트가 필요하다.
- CMS edit/detail container는 invalid id, unauthorized user, Supabase error 처리가 일관적인지 추가 확인이 필요하다.
