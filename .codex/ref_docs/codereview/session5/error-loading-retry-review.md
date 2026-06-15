# Error / Loading / Retry Review

| ID | 심각도 | 파일 경로 | 라인 | 처리 유형 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S5-LOADING-001 | High | `components/pages/HomePage.tsx` | 36 | loading | async fetch에 catch/finally가 없어 실패 시 `setLoading(false)` 보장 없음 | homepage loading 고착 가능 | `useHomeContent` hook과 try/catch/finally |
| S5-ROLLBACK-001 | High | `components/pages/EditNoticePage.tsx` | 107 | rollback | storage remove/upload 후 DB update 실패 시 보상 처리 없음 | 파일과 DB attachment 불일치 가능 | attachment service와 rollback/cleanup 정책 |
| S5-ERROR-001 | Medium | `api/chat.js` | 88 | error | Gemini handler가 표준 error envelope 대신 `{ error, details: error.message }` 반환 | PFM error UI/normalization과 불일치 | adapter + normalized error response |
| S5-ERROR-002 | Medium | `lib/api/legacyAiChat.ts` | 17 | error | `/api/chat` 실패를 `new Error('AI Server Error')`로 단순화 | upstream diagnostics가 사라짐 | legacy error type 또는 ApiError-like 변환 |
| S5-ERROR-003 | Medium | `components/pages/Simulation2Page.tsx` | 1469 | error | job polling `getJob` 실패를 삼키고 null 반환 | 반복 실패/인증 실패를 사용자에게 알리기 어려움 | 연속 실패 카운트와 inline notice 정책 |
| S5-ERROR-004 | Medium | `components/pages/NoticeBoardPage.tsx` | 98 | error | pin/delete mutation은 실패 처리와 사용자 피드백이 제한적 | 실패해도 사용자가 원인 파악 어려움 | mutation hook + toast/error state |
| S5-RETRY-001 | Medium | `lib/apiClient.ts` | 278 | retry | 401 refresh retry는 있으나 일반 network/5xx retry 정책 없음 | transient error UX가 도메인별로 다름 | retryable error 정책을 query/hook에 명시 |
| S5-LOADING-002 | Medium | `components/pages/AdminPage3.tsx` | 648 | loading | query/mutation pending 상태는 React Query가 제공하지만 UI form/dialog state와 결합 | loading UX 변경 영향이 AdminPage3에 집중 | dialog/mutation hook 분리 |
| S5-FALLBACK-001 | Low | `components/simulation/trame/TrameViewer.tsx` | 30 | fallback UI | `resolveViewerMode` 실패 시 PNG fallback 처리 | 좋은 패턴 | PFM visualization failure UX 기준으로 참고 |

## 확인 필요

- backend error envelope가 모든 PFM endpoint에서 동일한지, legacy `/api/chat` 유지 여부 확인 필요.
