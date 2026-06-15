# Store and Context Review

| ID | 심각도 | 상태 관리 방식 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---|---:|---|---|---|
| S4-STORE-001 | Suggestion | Zustand/Redux/SWR | `package.json` | 1 | `zustand`, `redux`, `swr` 의존성 및 직접 사용이 확인되지 않는다 | 전역 store 남용 문제보다는 local server state 분산 문제가 더 중요 | store 도입은 문제 해결 후 필요성이 명확할 때만 검토 |
| S4-CONTEXT-001 | Low | Context API | `components/LanguageProvider.tsx` | 230 | language context가 localStorage persistence를 직접 포함한다 | 전역 provider 변경 시 전체 consumer render 영향 가능 | provider value `useMemo` 여부와 consumer 범위 확인 |
| S4-PERSIST-001 | Medium | sessionStorage | `lib/auth.ts` | 67 | auth token 저장 helper가 `lib/auth.ts`에 있다 | token 저장 정책 변경 시 여러 모듈 수정 가능 | token storage adapter 단일화 |
| S4-PERSIST-002 | Medium | sessionStorage | `lib/apiClient.ts` | 38 | API client에도 token read/write/remove helper가 중복 존재한다 | refresh/token persistence 정책 drift 가능 | `authTokenStorage` 같은 좁은 adapter로 통합 |
| S4-PERSIST-003 | Low | sessionStorage | `lib/supabaseClient.ts` | 21 | Supabase browser client도 `window.sessionStorage`를 storage로 사용한다 | PFM token과 CMS/Supabase session storage 정책을 혼동할 수 있음 | PFM auth와 Supabase auth 경계를 문서화 |
| S4-GLOBAL-001 | Low | custom store | `hooks/use-toast.ts` | 131 | `listeners`와 `memoryState`를 module-level mutable store로 관리한다 | React tree 바깥 상태라 테스트/SSR 추적이 어렵다 | shadcn 패턴 유지 가능하나 listener effect dependency 정리 |
| S4-CONTEXT-002 | Low | UI Context | `components/ui/sidebar.tsx` | 117 | sidebar context는 `useMemo`로 context value를 만든다 | 현재 Session 4 핵심 문제는 아님 | UI primitive로 유지 |

## 실제 코드 근거

- `rg` 기준 `useReducer`, `zustand`, `react-redux`, `useSWR` 직접 사용은 확인되지 않았다.
- `package.json`에는 `@tanstack/react-query`가 존재하나 `zustand`, `redux`, `swr` 패키지는 확인되지 않았다.

## 추론

- 현재 리팩토링의 우선순위는 전역 store 도입이 아니라 server state/cache 정책을 hook 단위로 분리하는 것이다.
- Context는 language/sidebar/toast 수준으로 제한되어 있어 전역 상태 남용 증거는 낮다.
