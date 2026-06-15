# Session 5 Findings

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S5-POLLING-001 | High | polling | `components/pages/Simulation2Page.tsx` | 1605 | job polling fallback에 in-flight guard 없음 | 중복 API 호출과 상태 순서 꼬임 가능 | single-flight polling loop |
| S5-SERVICE-001 | High | component-service coupling | `components/pages/Simulation2Page.tsx` | 2255 | chat/simulation/job/result/viz API orchestration이 component에 집중 | 회귀 위험과 테스트 비용 증가 | workflow/job/viz hook/service 분리 |
| S5-SERVICE-002 | High | component-service coupling | `components/pages/AdminPage3.tsx` | 551 | admin query/mutation/cache policy가 container에 집중 | admin 유지보수성 저하 | tab별 query/mutation hook |
| S5-SERVICE-003 | High | service layer | `components/pages/NoticeBoardPage.tsx` | 58 | Supabase query/mutation 직접 호출 | UI와 persistence 결합 | notice service/query hook |
| S5-ROLLBACK-001 | High | error handling | `components/pages/EditNoticePage.tsx` | 107 | attachment storage 변경과 DB update에 rollback 없음 | 파일/DB 불일치 가능 | attachment adapter/use-case |
| S5-TYPE-001 | Medium | response type | `lib/apiClient.ts` | 380 | `apiRequest<T = any>` 기본 타입 | 타입 누락 전파 | 타입 명시 강화 |
| S5-TIMEOUT-001 | Medium | timeout | `lib/apiClient.ts` | 151 | refresh/API fetch timeout 없음 | loading 장기 지속 가능 | timeout/signal 옵션 |
| S5-CACHE-001 | Medium | cache invalidation | `components/pages/AdminPage3.tsx` | 666 | mutation cache side effect가 component에 노출 | cache 정책 변경 영향 확대 | mutation hook으로 이동 |
| S5-STALE-001 | Medium | race condition | `components/simulation/JobResultListCard.tsx` | 98 | list request stale guard 없음 | 이전 응답이 최신 상태 덮을 수 있음 | query key 또는 sequence guard |
| S5-STALE-002 | Medium | race condition | `components/simulation/ResultExplorerPanel.tsx` | 392 | catalog/files stale guard 없음 | 이전 field/result 응답 반영 가능 | request token/AbortController |
| S5-ERROR-001 | Medium | error handling | `api/chat.js` | 88 | legacy Gemini error가 표준 envelope와 다름 | error UX/보안 정책 불일치 | adapter/error mapping |
| S5-PERSIST-001 | Medium | API client | `lib/auth.ts` | 66 | token storage helper 중복 | refresh/storage 정책 drift | token storage adapter |
| S5-BOUNDARY-001 | Suggestion | endpoint | `scripts/check-pfm-api-boundaries.mjs` | 6 | boundary check가 일부 PFM page에 한정 | CMS direct dependency 재발 보호 부족 | CMS boundary check 확장 |

## 이전 세션 연결 요약

- `S5-SERVICE-001`은 Session 1 `S1-ARCH-001`, Session 2 `S2-CONTAINER-001`, Session 3 `S3-COMP-001`, Session 4 `S4-STATE-001`과 같은 문제의 API/service 관점이다.
- `S5-SERVICE-002`는 Session 2/3/4의 AdminPage3 대형 container, query/mutation 집중 문제를 API/cache 관점으로 구체화한다.
- `S5-SERVICE-003`과 `S5-ROLLBACK-001`은 Session 1~3의 CMS/Supabase 직접 의존 문제를 async/transaction safety 관점으로 확장한다.
