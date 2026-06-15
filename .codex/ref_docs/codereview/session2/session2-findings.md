# Session 2 주요 Findings

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S2-GUARD-001 | Medium | route guard | `app/simulation2/page.tsx` | 16 | PFM 보호 route의 인증 확인이 page effect에 직접 구현되어 있다 | 인증 정책 재사용과 테스트가 어렵다 | PFM auth gate hook/component로 추출 |
| S2-GUARD-002 | Medium | route guard | `app/pfm_chat/login/page.tsx` | 8 | login route도 token 확인/redirect를 직접 수행한다 | simulation route와 auth redirect 정책이 분산된다 | auth redirect policy를 공통화 |
| S2-GUARD-003 | Medium | route guard | `app/cmsl2004/page.tsx` | 13 | legacy admin route가 Supabase session gate를 page에서 직접 구현한다 | `cmsl20042`와 중복되고 infrastructure 의존이 route에 노출된다 | legacy admin guard component/hook 도입 |
| S2-GUARD-004 | Medium | route guard | `app/board/news/[id]/edit/page.tsx` | 7 | edit route의 route-level 권한 확인이 확인되지 않는다 | 수정 화면 접근 제어가 container 또는 RLS에 숨을 수 있다 | edit route/container 권한 정책 확인 후 명시화 |
| S2-BOUNDARY-001 | Medium | error boundary | `app` | 1 | global `error.tsx`와 route별 `loading.tsx`가 확인되지 않는다 | fallback/error UX가 container마다 분산될 수 있다 | global/route-level error/loading boundary 전략 검토 |
| S2-PAGE-001 | Low | page | `app/simulation2/page.tsx` | 43 | Suspense fallback과 inner loading UI가 중복된다 | loading UX 변경 시 중복 수정 가능 | fallback/loading presenter 단일화 |
| S2-PAGE-002 | Medium | routing | `app/board/news/[id]/page.tsx` | 9 | dynamic id validation/not-found 처리 위치가 명확하지 않다 | 잘못된 URL 처리 UX가 route마다 달라질 수 있다 | board 공통 params parser와 notFound 정책 검토 |
| S2-CONTAINER-001 | High | container | `components/pages/Simulation2Page.tsx` | 589 | simulation2 container가 page-level state와 비동기 orchestration을 과도하게 가진다 | 리팩토링/기능 추가 시 회귀 위험이 가장 큼 | workflow/job/viz/chat hook/service로 분리 |
| S2-CONTAINER-002 | High | container | `components/pages/AdminPage3.tsx` | 483 | admin container가 URL state, 권한, query/mutation, UI를 모두 가진다 | admin 유지보수성과 테스트 용이성 저하 | tab별 container와 query/mutation hook으로 분리 |
| S2-STATE-001 | Medium | page state | `components/pages/AdminPage3.tsx` | 489 | URL query parsing과 correction side effect가 container 내부에 집중되어 있다 | URL state 변경이 query/mutation 흐름에 직접 영향 | `adminUrlState` helper/hook 분리 |
| S2-ASYNC-001 | Medium | async flow | `components/pages/Simulation2Page.tsx` | 1674 | job monitor WebSocket, polling fallback, reconnect 관리가 container 내부에 있다 | race condition/cleanup 회귀 위험 | `useJobMonitorSession`으로 lifecycle 격리 |
| S2-ASYNC-002 | Medium | async flow | `components/pages/Simulation2Page.tsx` | 1949 | visualization WebSocket과 sync interval이 container 내부에 있다 | viz 상태 동기화 회귀 위험 | `useVisualizationSession`으로 분리 |
| S2-DEPENDENCY-001 | Medium | dependency | `components/pages/NoticeBoardPage.tsx` | 58 | board container가 Supabase query/mutation을 직접 수행한다 | UI와 persistence 결합 | notice service/query hook 도입 |
| S2-DEPENDENCY-002 | Medium | dependency | `components/pages/EditNoticePage.tsx` | 122 | edit container가 storage upload/remove와 DB update를 직접 수행한다 | 파일 저장 정책과 UI 결합 | attachment adapter와 edit form hook 분리 |
| S2-DEPENDENCY-003 | Medium | dependency | `components/pages/EditGalleryPage.tsx` | 112 | gallery edit container가 storage/DB update를 직접 수행한다 | gallery storage 정책 변경 영향이 UI에 집중 | gallery service/hook으로 분리 |
| S2-LAYOUT-001 | Suggestion | layout | `app/layout.tsx` | 87 | 모든 route가 동일 Header/Footer layout을 공유한다 | admin/workbench/viewer UX에서 layout 분리 필요 여부 확인 필요 | route group layout 검토 |

## Session 1 연결 요약

- `S2-CONTAINER-001`은 Session 1의 `S1-ARCH-001`과 같은 근본 원인이다.
- `S2-CONTAINER-002`는 Session 1의 admin 대형 컨테이너 문제를 route/query state 관점에서 구체화한다.
- `S2-DEPENDENCY-*`는 Session 1에서 확인한 CMS/Supabase 직접 의존 문제를 page/container 계층으로 확장한 것이다.

## 확인 필요

- edit route 접근 제어가 Supabase RLS와 UI에서 어떻게 보장되는지 확인 필요.
- global `error.tsx` 부재가 의도인지 확인 필요.
- route group layout 부재가 의도인지 확인 필요.
