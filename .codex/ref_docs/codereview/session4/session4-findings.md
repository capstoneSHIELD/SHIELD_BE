# Session 4 Findings

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S4-STATE-001 | High | local state | `components/pages/Simulation2Page.tsx` | 592 | workflow, chat, job monitor, visualization, form state가 한 component에 집중 | 회귀 위험과 테스트 비용 증가 | workflow/job/viz/form 단위 hook으로 분리 |
| S4-RACE-001 | High | race condition | `components/pages/Simulation2Page.tsx` | 1605 | async polling interval에 in-flight guard가 없음 | 중복 API 호출과 상태 순서 꼬임 가능 | single-flight polling loop 도입 |
| S4-STATE-002 | High | server state | `components/pages/AdminPage3.tsx` | 551 | admin server state query/mutation이 한 container에 집중 | query dependency와 cache side effect 파악 어려움 | tab별 query/mutation hook 분리 |
| S4-URL-001 | High | URL state | `components/pages/AdminPage3.tsx` | 498 | `Number(searchParams...)` 결과가 `NaN`일 수 있음 | invalid query에서 page/size와 query key가 불안정 | safe integer parser 도입 |
| S4-CACHE-001 | Medium | React Query/SWR | `components/pages/AdminPage3.tsx` | 510 | React Query cache 결과를 `fieldFilesData` local state로 복사 | cache/local state 불일치 가능 | selected field files query로 전환 |
| S4-MUTATION-001 | Medium | cache invalidation | `components/pages/AdminPage3.tsx` | 676 | mutation cache side effect가 page component에 노출 | cache 정책 변경 영향이 UI에 집중 | mutation hook에서 invalidate/setQueryData 캡슐화 |
| S4-SERVER-001 | Medium | server state | `components/simulation/JobResultListCard.tsx` | 93 | job/result server state를 local state와 refreshKey로 관리 | stale response와 loading 중복 가능 | React Query 또는 sequence guard |
| S4-SERVER-002 | Medium | server state | `components/simulation/SimulationListCard.tsx` | 56 | simulation list server state가 component local state | stale list 반영 가능 | `useSimulationList` hook |
| S4-SERVER-003 | Medium | server state | `components/simulation/SessionListCard.tsx` | 73 | session list, search, delete, rename state가 한 component에 집중 | action 후 reload race 가능 | `useChatSessions` hook과 dialog presenter 분리 |
| S4-STALE-001 | Medium | stale closure | `components/simulation/ResultExplorerPanel.tsx` | 392 | field catalog/files 요청에 stale response guard가 없음 | result/field 전환 시 이전 응답 반영 가능 | request sequence 또는 AbortController |
| S4-EFFECT-001 | Medium | useEffect | `components/pages/HomePage.tsx` | 36 | homepage fetch에 catch/finally/error state가 없음 | 실패 시 loading 고착 가능 | typed content hook과 error/finally |
| S4-PERSIST-001 | Medium | persistence | `lib/auth.ts` | 67 | token storage helper가 auth와 apiClient에 중복 | refresh/storage 정책 drift 가능 | token storage adapter 단일화 |
| S4-PERSIST-002 | Medium | persistence | `lib/apiClient.ts` | 38 | API client가 sessionStorage를 직접 읽고 쓴다 | 테스트와 auth 정책 변경 영향 확대 | token storage boundary 도입 |
| S4-DEPENDENCY-001 | Medium | dependency | `components/ResearchHighlightsSlider.tsx` | 32 | empty `highlights`에서도 interval effect가 등록될 수 있음 | modulo 0으로 `NaN` index 가능 | effect guard 추가 |
| S4-CONTEXT-001 | Low | Context | `components/LanguageProvider.tsx` | 230 | language context와 localStorage persistence가 같은 provider에 있음 | provider render 영향 범위 확인 필요 | value memoization과 persistence hook 검토 |
| S4-HOOK-001 | Low | custom hook | `hooks/use-toast.ts` | 176 | `[state]` dependency로 listener 재구독 반복 | 불필요한 effect 재실행 | dependency `[]` 검토 |
| S4-HOOK-002 | Low | custom hook | `hooks/use-mobile.ts` | 20 | 초기 `undefined`가 `false`로 반환됨 | SSR/초기 render에서 desktop으로 잠깐 판단 가능 | tri-state 반환 또는 mounted guard 검토 |

## Session 1/2/3 연결 요약

- `S4-STATE-001`은 Session 1 `S1-ARCH-001`, Session 2 `S2-CONTAINER-001`, Session 3 `S3-COMP-001`의 hook/state 계층 근거이다.
- `S4-STATE-002`는 Session 1 `S1-ARCH-004`, Session 2 `S2-CONTAINER-002`, Session 3 `S3-COMP-002`의 query/cache 관점 구체화이다.
- `S4-SERVER-*`는 Session 3에서 확인한 component-local server state 문제를 race/cache 관점으로 확장한다.

## 확인 필요

- product 요구사항상 어떤 데이터가 항상 fresh 해야 하는지, 어떤 데이터는 cache-only가 허용되는지 확인 필요.
- Supabase CMS 영역의 server state를 React Query로 통합할지, domain-specific hook으로만 분리할지 결정 필요.
