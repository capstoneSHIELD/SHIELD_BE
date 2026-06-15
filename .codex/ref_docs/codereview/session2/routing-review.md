# Routing/Layout/Boundary 리뷰

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---|---:|---|---|---|
| S2-ROUTE-001 | Low | routing | `app/**/page.tsx` | 1 | 대부분 route가 `components/pages/*Page`를 단순 연결하는 패턴이지만 legacy admin, board, PFM route는 서로 다른 방식으로 session/auth를 처리한다 | route별 책임 기준이 암묵적이라 새 route 추가 시 guard 위치가 흔들릴 수 있다 | route 유형별 패턴을 문서화하고 protected route wrapper 기준을 정한다 |
| S2-ROUTE-002 | Medium | routing | `app/board/news/[id]/page.tsx` | 9 | dynamic route id를 문자열 그대로 container로 전달한다 | id 형식 오류에 대한 not-found/error 처리 위치가 불명확하다 | route 또는 container 초입에서 id validation/notFound 정책을 표준화 |
| S2-ROUTE-003 | Medium | routing | `app/board/gallery/[id]/page.tsx` | 9 | gallery dynamic route도 id validation 표준이 확인되지 않는다 | 잘못된 URL에 대한 UX와 DB query 실패 처리가 route마다 달라질 수 있다 | board detail 공통 params parser 도입 검토 |
| S2-LAYOUT-001 | Suggestion | layout | `app/layout.tsx` | 87 | 전역 Header/Footer가 모든 route에 적용된다 | admin/simulation/viewer처럼 workbench 성격 화면도 동일 layout을 받는 것이 의도인지 확인 필요 | route group별 layout 분리가 필요한지 UX 기준 확인 |
| S2-LAYOUT-002 | Low | layout | `app/providers.tsx` | 16 | React Query 기본 옵션이 전역으로 적용된다 | admin polling, user workflow, CMS 영역이 같은 stale/refetch 정책을 공유한다 | 민감한 server state는 query별 stale/refetch 정책을 명시 |
| S2-GUARD-001 | Medium | route guard | `app/simulation2/page.tsx` | 18 | PFM auth guard가 route page 내부 effect로 구현된다 | 보호 route가 늘어날 경우 인증 정책 중복 가능 | PFM auth gate hook/component 도입 |
| S2-GUARD-002 | Medium | route guard | `app/pfm_chat/login/page.tsx` | 10 | login route도 token 확인 후 `/simulation2`로 이동하는 guard를 직접 구현한다 | simulation guard와 redirect 정책이 양방향으로 분산된다 | auth redirect policy를 `lib/auth` 또는 route guard hook으로 통합 |
| S2-GUARD-003 | Medium | route guard | `app/cmsl2004/page.tsx` | 14 | legacy CMS admin guard가 Supabase client auth에 직접 의존한다 | app route가 infrastructure adapter를 직접 호출한다 | Supabase session gate 또는 server route protection 검토 |
| S2-GUARD-004 | Medium | route guard | `app/board/news/[id]/edit/page.tsx` | 7 | edit route의 route-level 권한 확인이 보이지 않는다 | 수정 화면 접근 제어가 container/Supabase 정책에 숨을 수 있다 | edit route 보호 기준 확인 후 명시적 guard 적용 |
| S2-BOUNDARY-001 | Medium | error boundary | `app` | 1 | global `error.tsx`와 route-level `loading.tsx`가 확인되지 않고 `not-found.tsx`만 존재한다 | 예상치 못한 render/runtime error fallback이 route별로 일관되지 않을 수 있다 | global error boundary와 주요 protected route fallback 전략 검토 |
| S2-BOUNDARY-002 | Low | loading boundary | `app/cmsl20043/page.tsx` | 14 | route Suspense fallback은 있으나 admin 권한 loading/error는 container early return으로 처리된다 | route fallback과 container state UI의 책임 기준이 분산된다 | admin auth/query boundary 기준을 문서화하고 presenter 분리 |
| S2-BOUNDARY-003 | Low | code splitting | `app/viewer/page.tsx` | 6 | viewer는 dynamic import로 SSR 회피가 명시되어 있다 | 긍정적 사례. 다만 fallback/error UX는 확인 필요 | dynamic component fallback/error 표시 기준 확인 |

## 확인 필요

- route group layout을 사용하지 않는 것이 의도인지 확인 필요.
- edit route 접근 제어가 Supabase RLS로 충분히 보호되는지 확인 필요.
- Next.js global error boundary 부재가 의도된 것인지 확인 필요.
- `middleware.ts`는 Session 2에서 확인되지 않았다. 보호 route를 middleware로 처리하지 않는 것이 의도인지 확인 필요.
