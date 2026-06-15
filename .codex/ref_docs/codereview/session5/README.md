# Session 5 API / Service / Async Flow 리뷰 문서

## 목적

이 디렉터리는 Session 5에서 확인한 frontend API 호출 구조, service/API client 경계, React Query, polling, retry, error handling, request safety 리뷰 결과를 이후 리팩토링 세션에서 재사용하기 위한 기준 문서이다.

## 분석 범위

- PFM API client: `lib/apiClient.ts`, `lib/api/http.ts`, `lib/api/errors.ts`
- PFM domain API helper: `lib/api/chatSessions.ts`, `lib/api/simulations.ts`, `lib/api/jobs.ts`, `lib/api/results.ts`, `lib/api/visualizations.ts`, `lib/api/admin.ts`
- page/component 호출 주체: `components/pages/Simulation2Page.tsx`, `components/pages/AdminPage3.tsx`, `components/simulation/*Card.tsx`, `components/simulation/ResultExplorerPanel.tsx`
- CMS/외부 연동: Supabase 직접 호출 page, `api/chat.js`, `lib/api/legacyAiChat.ts`, `lib/api/legacySimulation.ts`, `components/pages/ContactPage.tsx`
- Labserver/Trame client: `lib/api/labserverTrameClient.ts`, `components/simulation/trame/*`

## Session 1~4 연결

- Session 1~3에서 확인된 `Simulation2Page`, `AdminPage3`, CMS page의 책임 집중 문제가 Session 5에서는 API orchestration과 async flow 관점으로 구체화된다.
- Session 4의 server/client state 분리 문제는 Session 5의 request safety, polling 중복, React Query cache/invalidation 문제와 직접 연결된다.
- PFM API helper 경계는 `docs/api/frontend-backend-api-map.md`와 `scripts/check-pfm-api-boundaries.mjs`로 일부 보호되지만 CMS/Supabase 직접 호출은 같은 보호 범위에 없다.

## 문서 구성

| 파일 | 역할 |
|---|---|
| `api-service-inventory.md` | API client, service 함수, query/mutation, polling 함수 목록 |
| `endpoint-map.md` | frontend에서 호출하는 endpoint, method, payload, response type 맵 |
| `async-flow-map.md` | 사용자 액션/page 진입 후 API 실행과 상태 반영 흐름 |
| `query-mutation-review.md` | React Query query/mutation/cache 전략 리뷰 |
| `polling-review.md` | polling/refetchInterval/manual refresh 구조 리뷰 |
| `error-loading-retry-review.md` | loading/error/success/retry/rollback 처리 리뷰 |
| `request-safety-review.md` | race, cancellation, timeout, stale response 리뷰 |
| `api-type-contract-review.md` | request/response type, DTO, API contract 리뷰 |
| `api-layer-architecture-review.md` | API/service 계층 책임 분리 리뷰 |
| `session5-findings.md` | 주요 문제 후보 종합 |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션용 프롬프트 |

## 리팩토링 세션 참고 순서

1. `session5-findings.md`
2. `refactoring-brief.md`
3. `api-layer-architecture-review.md`
4. `request-safety-review.md`
5. `polling-review.md`
6. `query-mutation-review.md`
7. `endpoint-map.md`
8. `api-service-inventory.md`

## 주의사항

- frontend 소스 코드는 수정하지 않았다.
- `.codex/ref_docs`는 사용자 관리 참고자료 공간이며 프로젝트 명세 위치가 아니다.
- 확인하지 못한 계약은 `확인 필요`로 남겼다.
