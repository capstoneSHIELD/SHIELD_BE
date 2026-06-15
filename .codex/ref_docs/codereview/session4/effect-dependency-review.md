# Effect Dependency Review

| ID | 심각도 | 파일 경로 | 라인 | effect 역할 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S4-EFFECT-001 | High | `components/pages/Simulation2Page.tsx` | 1599 | job polling fallback 시작 | `setInterval(async () => ...)` 내부에서 `pollJobStatus`, `fetchJobEvents`, `checkVisualizationAvailability`가 순차 실행되지만 in-flight guard가 없다 | interval 주기보다 요청이 길면 polling 요청이 겹칠 수 있음 | `pollInFlightRef` 또는 single-flight async loop로 전환 |
| S4-EFFECT-002 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 392 | field catalog 로드 | result detail에는 sequence guard가 있으나 catalog 요청에는 request sequence/cancel guard가 없다 | result 전환/연속 클릭 시 이전 catalog가 새 result UI에 반영될 수 있음 | catalog/files 요청에도 `requestSeq` 또는 AbortController 적용 |
| S4-EFFECT-003 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 411 | field files 로드 | field files 요청에도 stale response guard가 없다 | field/filter 빠른 변경에서 이전 파일 목록 반영 가능 | `resultId + field + filters` 기준 request token 확인 |
| S4-DEPENDENCY-001 | Medium | `components/ResearchHighlightsSlider.tsx` | 32 | autoplay interval | empty `highlights` check는 render return에 있지만 effect는 먼저 등록될 수 있고 `handleNext`는 modulo `highlights.length`를 사용한다 | empty array에서 interval tick 후 index가 `NaN`이 될 수 있음 | effect 초기에 `highlights.length === 0` guard 추가 |
| S4-DEPENDENCY-002 | Low | `hooks/use-toast.ts` | 176 | toast listener subscribe | dependency가 `[state]`라 state 변경마다 listener unsubscribe/subscribe가 반복된다 | 불필요한 effect 재실행과 store 동작 추적 난도 증가 | dependency를 `[]`로 고정하고 stable listener 구조 유지 |
| S4-CLEANUP-001 | Medium | `components/simulation/JobResultListCard.tsx` | 121 | job/result list fetch | effect cleanup 또는 request sequence guard가 없다 | unmount/props 변경 후 늦은 응답이 state를 갱신할 수 있음 | `AbortController`, mounted flag, sequence guard 중 하나 적용 |
| S4-CLEANUP-002 | Medium | `components/simulation/SimulationListCard.tsx` | 77 | simulation list fetch | effect cleanup 또는 request sequence guard가 없다 | refreshKey/page 전환 중 stale list 반영 가능 | query hook 또는 sequence guard 적용 |
| S4-CLEANUP-003 | Medium | `components/simulation/SessionListCard.tsx` | 122 | session list fetch | effect와 manual reload가 같은 `loadSessions`를 공유하지만 stale response guard가 없다 | 검색/페이지/rename/delete 후 요청 순서가 뒤바뀔 수 있음 | request token과 action별 loading state 분리 |
| S4-EFFECT-004 | Medium | `components/pages/HomePage.tsx` | 36 | homepage CMS fetch | async effect 내부에 catch/finally/error state가 확인되지 않는다 | fetch 실패 시 loading이 종료되지 않을 수 있음 | try/catch/finally와 typed error state 도입 |
| S4-EFFECT-005 | Low | `components/pages/AdminPage3.tsx` | 918 | URL/query correction effects | URL correction, selected entity reset, 404 cleanup이 한 container에 여러 effect로 분산되어 있다 | dependency 안정성 자체보다 유지보수 비용이 높음 | `useAdminUrlState`와 tab별 selection sync hook으로 분리 |
| S4-CLEANUP-004 | Low | `hooks/useIdleTimer.ts` | 15 | idle timer listener/timeout | cleanup과 dependency가 명시되어 있다 | 현재 큰 문제 없음 | 좋은 기준 패턴으로 유지 |

## 확인 필요

- `Simulation2Page`의 WebSocket cleanup 자체는 여러 ref와 cleanup helper로 구성되어 있으나, 전체 lifecycle이 한 component에 있어 분리 전 재검증 필요.
- CMS page들의 Supabase query는 route/page 권한 정책과 함께 Session 5에서 API/service 관점 추가 확인 필요.
