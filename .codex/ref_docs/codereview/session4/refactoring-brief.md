# Session 4 Refactoring Brief

| 우선순위 | 리팩토링 대상 | 현재 문제 | 개선 방향 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|---|
| P0: 즉시 수정 필요 | `Simulation2Page` job polling fallback | async interval에 in-flight guard가 없음 | single-flight polling loop 또는 `pollingInFlightRef` 적용 | High | WebSocket fallback, terminal status, result availability와 함께 테스트 |
| P0: 즉시 수정 필요 | `AdminPage3` URL query parser | `page`/`size`가 `NaN` 가능 | safe integer parser와 correction hook 도입 | Medium | URL behavior 변경이 admin list query key에 영향 |
| P1: 구조 개선 필요 | `Simulation2Page` state owner 분리 | workflow/job/viz/form/chat state 집중 | `useSimulationWorkflow`, `useJobMonitorSession`, `useVisualizationSession`, `useSimulationDraft` 단계적 추출 | High | 한 번에 전체 분리하지 말고 lifecycle 단위로 분할 |
| P1: 구조 개선 필요 | `AdminPage3` query/mutation hook 분리 | query/mutation/cache side effect가 container에 집중 | account/system/simulation/job/result/viz tab별 hook 분리 | High | query key helper를 먼저 안정화 |
| P1: 구조 개선 필요 | `ResultExplorerPanel` async state | catalog/files stale response guard 없음 | detail/catalog/files hook 분리와 request sequence 적용 | Medium | field selection callback과 visualization field preference 유지 |
| P1: 구조 개선 필요 | job/simulation/session list cards | server state를 local state + refreshKey로 관리 | React Query 또는 request sequence guard가 있는 custom hook | Medium | Lab sync 비용 때문에 `sync:false` 정책 유지 |
| P2: 코드 품질 개선 | `HomePage` CMS fetch | catch/finally/error state 없음 | `useHomeContent` hook과 typed view model 도입 | Medium | CMS schema/view model 확인 필요 |
| P2: 코드 품질 개선 | token storage helpers | `lib/auth.ts`와 `lib/apiClient.ts`에 중복 | `authTokenStorage` adapter로 통합 | Medium | Supabase sessionStorage와 PFM token storage 경계 분리 |
| P2: 코드 품질 개선 | `ResearchHighlightsSlider` interval | empty data guard가 effect 이전에 없음 | effect 내부 length guard 추가 | Low | UI 회귀 범위 작음 |
| P2: 코드 품질 개선 | `hooks/use-toast.ts` subscribe effect | `[state]` dependency로 재구독 반복 | mount-only subscription 검토 | Low | shadcn 패턴과 기존 toast 테스트 확인 |
| P3: 장기 개선 | 전역 store 도입 여부 | 전역 상태 남용은 확인되지 않고 local server state 분산이 문제 | store보다 query/hook/service 경계를 먼저 정리 | Medium | Zustand/Redux 도입은 명확한 공유 상태가 생긴 뒤 검토 |

## 먼저 건드리면 안 되는 민감한 영역

- `Simulation2Page`의 WebSocket, polling fallback, visualization sync는 상태 전이가 복잡하므로 guard 테스트 없이 대규모 이동 금지.
- `AdminPage3` query key와 invalidation은 admin UI 전반에 영향이 커서 helper 정리 후 이동.
- token storage는 로그인/refresh/401 retry와 연결되므로 Session 5 API client 리뷰와 함께 변경 계획 수립.

## 안전하게 먼저 개선 가능한 영역

- `ResearchHighlightsSlider` empty length guard.
- `hooks/use-toast.ts` listener effect dependency 검토.
- `HomePage` fetch error/finally 보강.
- list card request sequence guard 추가.

## 다음 세션 연결

- Session 5에서는 hook/component에서 호출되는 API/service를 추적하고, request cancellation, timeout, token refresh, response type 안정성을 검토해야 한다.
