# Session 5 Refactoring Brief

| 우선순위 | 리팩토링 대상 | 현재 문제 | 개선 방향 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|---|
| P0: 즉시 수정 필요 | `Simulation2Page` job polling | async interval in-flight guard 없음 | single-flight polling loop 또는 `pollingInFlightRef` | High | WS fallback/terminal status/result availability 테스트 필요 |
| P0: 즉시 수정 필요 | `EditNoticePage` attachment submit | storage remove/upload와 DB update rollback 없음 | attachment adapter와 실패 보상 정책 | High | 실제 storage path/URL parsing 확인 필요 |
| P1: 구조 개선 필요 | `Simulation2Page` API orchestration | component가 chat/simulation/job/result/viz use-case를 직접 조합 | `useSimulationWorkflow`, `useJobMonitorSession`, `useVisualizationSession` | High | 한 번에 전체 이동 금지 |
| P1: 구조 개선 필요 | `AdminPage3` query/mutation | query/mutation/cache 정책이 container에 집중 | tab별 query/mutation hook | High | query key helper 정리 선행 |
| P1: 구조 개선 필요 | CMS Supabase list/edit pages | UI에서 Supabase 직접 호출 | CMS domain service/query hook | Medium | RLS/권한 정책 확인 필요 |
| P2: 코드 품질 개선 | `apiRequest` request safety | timeout/signal 옵션 없음 | 공통 timeout/AbortSignal 지원 | Medium | fetch call site 타입 영향 검토 |
| P2: 코드 품질 개선 | API response typing | `apiRequest<T = any>` | 호출부 generic 명시, 장기적으로 `unknown` | Medium | strict mode와 함께 단계 적용 |
| P2: 코드 품질 개선 | ResultExplorer/List cards | stale response guard 없음 | request sequence guard 또는 React Query | Medium | Lab sync 비용 유지 |
| P2: 코드 품질 개선 | legacy `/api/chat` | 표준 error envelope와 timeout 없음 | legacy adapter/error mapper | Medium | legacy 유지 여부 확인 필요 |
| P3: 장기 개선 | boundary tests | PFM 일부 page만 API boundary 검사 | CMS/API boundary static check 추가 | Low | service layer 도입 후 검사 추가 |

## 세션 6 연결 항목

- `apiRequest<T = any>`, `Record<string, any>` patch body, CMS `any` state, legacy response assertion은 type/util/config 리뷰에서 이어서 확인해야 한다.
