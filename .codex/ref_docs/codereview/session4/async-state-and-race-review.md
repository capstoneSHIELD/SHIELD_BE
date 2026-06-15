# Async State and Race Review

| ID | 심각도 | 파일 경로 | 라인 | 흐름 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S4-RACE-001 | High | `components/pages/Simulation2Page.tsx` | 1605 | job polling fallback interval | async interval에 in-flight guard가 없어 이전 tick이 끝나기 전에 다음 tick이 시작될 수 있다 | job status/events/result availability가 중복 호출되고 순서가 꼬일 수 있음 | single-flight polling loop 또는 `pollingInFlightRef` 도입 |
| S4-RACE-002 | Medium | `components/simulation/JobResultListCard.tsx` | 98 | `fetchAll` | `simulationId`/`refreshKey` 변경 시 이전 `Promise.all` 응답이 나중에 반영될 수 있다 | 다른 simulation의 job/result 목록이 잠깐 표시될 가능성 | request sequence guard 또는 React Query key 적용 |
| S4-RACE-003 | Medium | `components/simulation/SimulationListCard.tsx` | 61 | `fetchItems` | page/refresh 변경에 대한 stale response guard가 없다 | 이전 page/list 응답이 현재 UI를 덮을 수 있음 | request token 또는 query hook 도입 |
| S4-RACE-004 | Medium | `components/simulation/SessionListCard.tsx` | 90 | `loadSessions` | 검색, 페이지 이동, rename/delete 후 reload가 동시에 발생할 수 있지만 stale guard가 없다 | 목록/total/page가 마지막 사용자 의도와 달라질 수 있음 | request id와 action별 mutation state 분리 |
| S4-STALE-001 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 392 | field catalog load | result detail request는 sequence guard가 있으나 catalog는 없다 | result 전환 중 이전 catalog가 유지/표시될 수 있음 | catalog request sequence를 resultId 기준으로 검증 |
| S4-STALE-002 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 411 | field files load | selected field/filter 변경 요청이 순서 보장 없이 `setFieldFiles`를 호출한다 | field files UI가 이전 필드/필터 결과를 표시할 수 있음 | field/filter snapshot을 응답 반영 전 비교 |
| S4-ASYNCSTATE-001 | Medium | `components/pages/HomePage.tsx` | 36 | homepage CMS fetch | try/catch/finally가 확인되지 않아 실패 시 loading 종료가 보장되지 않는다 | home 화면 loading 고착 가능 | `useHomeContent` hook에서 error/finally 처리 |
| S4-ASYNCSTATE-002 | Medium | `components/pages/NoticeBoardPage.tsx` | 75 | notice query `Promise.all` | page/search 변경 중 이전 응답 guard가 없다 | 게시글/고정글 count가 search state와 어긋날 수 있음 | query hook 또는 request sequence 적용 |
| S4-ASYNCSTATE-003 | Low | `components/pages/GalleryBoardPage.tsx` | 42 | gallery fetch | try/finally는 있으나 stale response guard는 없다 | 빠른 search/page 변경에서 이전 응답 반영 가능 | notice와 같은 list hook으로 통일 |
| S4-ABORT-001 | Low | `components/pages/Simulation2Page.tsx` | 2057 | visualization sync | `visualizationSyncInFlightRef`와 sequence guard가 존재한다 | 현재 패턴은 참고 가능 | job/result/catalog 쪽에도 유사 패턴 적용 |

## 우선 확인할 흐름

1. Simulation2 job polling fallback과 WebSocket fallback 전환.
2. ResultExplorerPanel의 field catalog/files 요청.
3. list card들의 refreshKey 기반 server state 갱신.
4. CMS list/home page의 Supabase fetch 실패 및 stale response 처리.
