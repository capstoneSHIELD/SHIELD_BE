# Polling Review

| ID | 심각도 | 파일 경로 | 라인 | polling 방식 | 시작 조건 | 중단 조건 | 문제 | 개선 방향 |
|---|---|---|---:|---|---|---|---|---|
| S5-POLLING-001 | High | `components/pages/Simulation2Page.tsx` | 1605 | `setInterval(async)` | job monitor WS fallback 또는 active job monitor | terminal status/result check 후 stop | in-flight guard가 없어 중복 요청 가능 | single-flight polling loop 도입 |
| S5-POLLING-002 | Medium | `components/pages/PFMSimulationPage.tsx` | 468 | `setInterval(async)` | legacy job submit 후 | completed/failed/cancelled | legacy polling도 in-flight guard 없음 | 유지 대상이면 guard 추가 |
| S5-REFETCH-001 | Medium | `components/pages/AdminPage3.tsx` | 597 | React Query `refetchInterval` | active job status/list | interval callback이 false 반환할 때 | 정책이 container 내부에 흩어짐 | admin jobs/events query hook으로 이동 |
| S5-REFETCH-002 | Medium | `components/pages/AdminPage3.tsx` | 1187 | manual `refetch()` fan-out | Refresh button click | 없음 | tab별 refetch 대상 수동 나열 | query key group helper 또는 tab refresh hook |
| S5-INTERVAL-001 | Medium | `components/pages/Simulation2Page.tsx` | 2100 | visualization metadata interval | active visualizationId | failed/closed/unmount cleanup | 이 흐름은 in-flight/sequence guard가 있어 기준 패턴으로 활용 가능 | job/result polling에도 같은 패턴 적용 |
| S5-POLLING-003 | Low | `lib/api/labserverTrameClient.ts` | 466 | async polling loop | export job 생성 후 | terminal status/timeout/abort signal | timeout/abort가 있어 비교적 안전 | PFM polling 리팩토링 기준으로 참고 |

## 실제 코드 근거

- `components/pages/Simulation2Page.tsx:1611`~`1615`에서 interval tick마다 `getJob`, `listJobEvents`, `listSimulationResults` 계열이 순차 실행된다.
- `components/simulation/trame/TrameExportCenter.tsx:147`~`149`는 AbortController cleanup을 갖고 있다.
