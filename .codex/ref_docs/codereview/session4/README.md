# Session 4 Hook / State Management 리뷰 문서

## 목적

이 디렉터리는 Session 4에서 확인한 frontend hook, state management, server/client state, `useEffect`, cache, 비동기 상태 흐름 리뷰 결과를 이후 리팩토링 세션에서 재사용하기 위한 기준 문서이다.

## 분석 범위

- 실제 코드 근거: `components/pages/Simulation2Page.tsx`, `components/pages/AdminPage3.tsx`, `components/simulation/*Card.tsx`, `components/simulation/ResultExplorerPanel.tsx`, `hooks/*`, `app/providers.tsx`, `lib/auth.ts`, `lib/apiClient.ts`, `lib/supabaseClient.ts`, 일부 CMS page.
- 패키지/구조 근거: `package.json`에서 `@tanstack/react-query`는 확인되며, `zustand`, `redux`, `swr` 의존성은 확인되지 않았다.
- 문서 근거: `docs/architecture/state.md`는 Simulation2 workflow/job/result/visualization 상태의 소유자와 source of truth를 정의한다.

## 이전 세션과의 연결

- Session 1: `Simulation2Page`와 `AdminPage3`의 대형 container 문제가 아키텍처 차원에서 확인되었다.
- Session 2: route/page/container 계층에서 page-level state와 async orchestration이 상위 container에 집중된 점이 확인되었다.
- Session 3: component 계층에서 server state, form state, UI state가 대형 component 내부에 집중된 점이 확인되었다.
- Session 4: 위 문제를 hook/state 관점으로 내려와 server/client state 분리, effect dependency, race condition, cache 전략 기준으로 정리한다.

## 문서 구성

| 파일 | 역할 |
|---|---|
| `hook-inventory.md` | custom hook과 주요 React hook 사용 위치 목록 |
| `state-management-map.md` | local/server/form/URL/persistent/cache state의 소유 위치와 변경 위치 |
| `server-client-state-review.md` | server state와 client state 분리 여부 리뷰 |
| `effect-dependency-review.md` | `useEffect` dependency, cleanup, stale closure 후보 리뷰 |
| `store-and-context-review.md` | Context, persistence, 전역 상태 사용 구조 리뷰 |
| `query-cache-review.md` | React Query query key, polling, mutation, invalidation 전략 리뷰 |
| `async-state-and-race-review.md` | race condition, stale data, unmount 이후 update 가능성 리뷰 |
| `session4-findings.md` | Session 4 주요 문제 후보 종합 |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션에서 그대로 사용할 프롬프트 |

## 리팩토링 세션 참고 순서

1. `session4-findings.md`
2. `refactoring-brief.md`
3. `state-management-map.md`
4. `server-client-state-review.md`
5. `effect-dependency-review.md`
6. `async-state-and-race-review.md`
7. `query-cache-review.md`
8. `hook-inventory.md`
9. `store-and-context-review.md`

## 주의사항

- 이 문서는 `.codex/ref_docs`에 위치한 사용자 관리 참고자료이며 프로젝트 명세가 아니다.
- frontend 소스 코드는 수정하지 않았다.
- 실제로 코드에서 확인하지 못한 내용은 `확인 필요`로 표시한다.
