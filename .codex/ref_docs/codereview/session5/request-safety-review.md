# Request Safety Review

| ID | 심각도 | 파일 경로 | 라인 | 요청 흐름 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S5-RACE-001 | High | `components/pages/Simulation2Page.tsx` | 1605 | job polling fallback | async interval에 in-flight guard 없음 | 상태/이벤트/result availability 순서 꼬임 가능 | `pollingInFlightRef` 또는 async loop |
| S5-CANCEL-001 | Medium | `lib/apiClient.ts` | 236 | `doFetch` | 공통 request에 AbortSignal/timeout 정책이 없음 | unmount/long request cancellation을 caller마다 따로 처리해야 함 | `apiRequest` 옵션에 signal/timeout 도입 |
| S5-TIMEOUT-001 | Medium | `lib/apiClient.ts` | 151 | refresh fetch | refresh request에 timeout 없음 | refresh hang 시 보호 요청 지연 | refresh timeout 적용 |
| S5-STALE-001 | Medium | `components/simulation/JobResultListCard.tsx` | 98 | `Promise.all` list fetch | simulationId/refreshKey 변경 시 이전 응답 guard 없음 | 다른 simulation list 반영 가능 | request sequence guard 또는 React Query |
| S5-STALE-002 | Medium | `components/simulation/ResultExplorerPanel.tsx` | 392 | field catalog/files | detail에는 guard가 있으나 catalog/files에는 없음 | 오래된 field/file 응답 반영 가능 | resultId+field+filters request token |
| S5-DUPREQ-001 | Medium | `components/pages/AdminPage3.tsx` | 1187 | manual refresh | 여러 query refetch를 수동 fan-out | 중복 요청과 누락 가능 | tab별 refresh hook |
| S5-CANCEL-002 | Low | `components/simulation/trame/TrameExportCenter.tsx` | 147 | export polling | AbortController cleanup 있음 | 좋은 기준 | PFM polling에 패턴 이전 |
| S5-TIMEOUT-002 | Low | `lib/api/labserverTrameClient.ts` | 466 | export polling | timeout 존재 | 좋은 기준 | 일반 request timeout도 검토 |

## 실제 코드 근거

- `lib/api/labserverTrameClient.ts:466`~`484`는 timeout과 AbortSignal을 갖춘 polling loop다.
- `lib/apiClient.ts:248`의 fetch wrapper는 현재 `fetch(url, { ...options, headers })`로 timeout을 직접 제공하지 않는다.
