# Session 2 리팩토링 브리프

| 우선순위 | 리팩토링 대상 | 현재 문제 | 개선 방향 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|---|
| P1 | PFM auth gate | `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx`에 auth 확인/redirect가 분산 | `usePfmAuthGate`, `ProtectedPfmRoute`, `RedirectIfAuthenticated` 같은 좁은 guard로 표준화 | Medium | redirect 방식 변경 시 login/simulation UX 회귀 확인 |
| P1 | `Simulation2Page` job monitor 책임 | WebSocket, polling fallback, reconnect, cleanup이 container 내부에 집중 | `useJobMonitorSession` 또는 service hook으로 분리 | High | `beforeunload`, reconnect timer, stale token guard 테스트 필요 |
| P1 | `Simulation2Page` visualization 책임 | visualization WebSocket, sync interval, error 상태가 container 내부에 집중 | `useVisualizationSession`으로 분리 | High | visualization lifecycle과 result selection 회귀 주의 |
| P1 | `AdminPage3` URL state | query parsing/correction/selection cascade가 container 내부에 집중 | `useAdminUrlState` 또는 pure parser/helper로 분리 | Medium-High | 기존 deep link/query 호환성 보존 |
| P1 | `AdminPage3` query/mutation hook | tab별 query/mutation이 한 container에 집중 | `useAdminOverviewQueries`, `useAdminSimulationQueries`, `useAdminVisualizationMutations` 등으로 분리 | Medium-High | query key/invalidation/refetch interval 유지 |
| P1 | legacy CMS admin guard | `cmsl2004`, `cmsl20042`의 Supabase session gate 중복 | `LegacyAdminGate` 또는 `useSupabaseSessionGate` 도입 | Medium | 기존 `LegacyLoginPage` 분기와 subscription cleanup 보존 |
| P1 | board edit route 권한 기준 | edit route에서 명시적 guard가 확인되지 않음 | route 또는 container 초입에 권한/unauthorized 처리 기준 명시 | Medium | RLS 정책 확인 전 과도한 UI 차단 금지 |
| P2 | board dynamic id parser | `[id]` route와 edit container에서 id validation이 분산 | `parseBoardId`, invalid id notFound/redirect 정책 정리 | Low-Medium | Next `notFound()` 사용 여부는 route/server boundary 확인 |
| P2 | CMS board data access | board/detail/edit container가 Supabase 직접 호출 | notice/gallery service 또는 query hook 도입 | Medium-High | Session 1 `S1-ARCH-002`와 연결. RLS/스토리지 path 확인 필요 |
| P2 | loading/error presenter | route fallback, container early return, inline loading이 혼재 | 보호 route/loading/error presenter 공통화 | Medium | UI 문구/디자인 변경 최소화 |
| P3 | route group layout | 모든 route가 동일 global Header/Footer를 사용 | admin/workbench/viewer route group layout 필요성 검토 | Medium | 제품 UX 결정 필요, 확인 필요 |
| P3 | global error boundary | `app/error.tsx` 부재 | global error fallback 도입 검토 | Low-Medium | 오류 로깅/복구 UX 정책 필요 |

## 먼저 개선하면 좋은 구조

- PFM auth gate: 파일 범위가 작고 `simulation2`/login 양방향 정책을 정리할 수 있다.
- legacy CMS admin guard 중복 제거: `cmsl2004`, `cmsl20042`가 거의 동일한 구조라 추출 효과가 명확하다.
- `AdminPage3` URL parser/helper: pure function으로 먼저 분리하면 query/mutation 분리보다 위험이 낮다.
- board id parser: invalid id 처리 기준을 먼저 정하면 detail/edit container 리팩토링의 안전성이 올라간다.

## 건드리기 전 추가 확인이 필요한 영역

- Supabase RLS와 admin 권한 정책.
- board edit route가 로그인 사용자만 접근해야 하는지, 작성자/admin 구분이 필요한지.
- `app/layout.tsx`의 모든 route 공통 Header/Footer가 의도된 UX인지.
- global error boundary를 추가할 때 로깅/복구 동작을 어떻게 할지.

## component/hook/service 계층으로 내려보내야 할 로직

- route auth effect: guard hook/component.
- admin URL query parsing/correction: route-state helper/hook.
- admin tab별 query/mutation: feature hook.
- simulation job monitor lifecycle: job monitor hook.
- simulation visualization lifecycle: visualization hook.
- CMS Supabase query/mutation/storage: service 또는 domain hook.

## Session 3 이후 연결 항목

- component 계층 리뷰에서 `Simulation2Page`가 호출하는 feature components의 props drilling과 conditional rendering을 추적한다.
- `AdminPage3` 렌더링 하위 section/component 분리 가능성을 검토한다.
- CMS board/detail/edit component의 form state와 Supabase side effect 분리 후보를 검토한다.
