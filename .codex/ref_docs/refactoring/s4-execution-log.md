# S4 실행 기록 (2026-06-12)

> 실행 범위: `refactoring-execution-order.md`의 S4 API client 단독 세션 중 RF-TASK-016~018.
> `lib/apiClient.ts`는 token refresh / 401 retry / error normalization 민감 영역이므로 기본 호출 동작을 유지하고 opt-in 옵션만 추가했다.

---

## 1. 작업 결과

| Task | 상태 | 결과 |
|---|---|---|
| RF-TASK-016 | 완료(재검증 이월) | `ApiRequestOptions.timeoutMs`를 추가하고 `authFetch`/`apiRequest`/내부 `doFetch`가 기존 `RequestInit.signal`과 timeout signal을 합성하도록 했다. timeout은 opt-in이며 기존 call site에는 기본 동작 변화가 없다. refresh 요청에는 hang 방지 timeout을 적용했고, refresh timeout 시 기존 protected 요청의 401 응답 경로를 유지하도록 테스트했다. |
| RF-TASK-017 | 완료(재검증 이월) | `lib/apiClient.ts`에 retryable error policy helper를 노출하고 `api-retry-policy.md`에 분류 기준을 기록했다. 기존 호출 동작은 변경하지 않고 future query/hook이 opt-in으로 소비할 수 있게 했다. |
| RF-TASK-018 | 완료(재검증 이월) | `lib/authTokenStorage.ts` adapter를 신설하고 `lib/auth.ts`/`lib/apiClient.ts`의 PFM token read/write/clear를 통합했다. Supabase session storage는 변경하지 않고 `auth-token-storage-policy.md`에 별도 경계로 기록했다. |

## 2. 보존한 민감 동작

- `apiRequest<T = any>` 기본 generic은 W3 방침대로 변경하지 않았다.
- protected 요청의 401 → refresh → retry 경로를 보존했다.
- refresh 실패/timeout 시 caller가 기존 401 응답을 처리하는 경로를 유지했다.
- `X-New-Access-Token` 응답 헤더 저장 및 proactive refresh schedule 동작을 유지했다.
- `readErrorBody`와 FastAPI validation normalization 동작은 변경하지 않았다.

## 3. 검증

| 검증 | 결과 |
|---|---|
| `npx tsc --noEmit` | 성공 |
| `npm run test:run -- lib/apiClient.test.ts lib/auth.test.ts` | 성공, 2 files / 10 tests passed |
| `npm run test:run` | 성공, 27 files / 164 tests passed |
| `npm run test:coverage` | 성공, 27 files / 164 tests passed. 전체 coverage: statements 59.59%, branches 52.16%, functions 56.38%, lines 63.05% |
| `npm run test:boundaries` | 성공 |
| `npm run build` (dummy public Supabase env) | 성공. lint는 build 중 skip, Tailwind arbitrary class ambiguity warning 4건은 기존과 동일 |

## 4. 남은 게이트

- Playwright 로그인 → token refresh → 401 retry 수동 플로우는 현재 브라우저 검증 도구/실제 backend 환경 부재로 재검증 항목으로 남긴다.
- `npx eslint lib/apiClient.ts lib/apiClient.test.ts`는 `apiRequest<T = any>` 기본 generic 유지 때문에 `no-explicit-any`에서 실패한다. 이는 RF-TASK-014/016의 비범위 결정과 일치하며, 기본 generic 전환은 후속 장기 단계로 둔다.
- RF-TASK-017과 RF-TASK-018은 같은 S4 단독 세션에서 이어서 완료했다. S5 진입 전 Playwright G2 auth flow 재검증은 환경 확보 후 수행한다.

---

## 5. RF-TASK-017 update

- Code: `lib/apiClient.ts` now exposes `getApiRetryDecision`, `isRetryableApiError`, `shouldRetryApiRequest`, and `DEFAULT_API_RETRY_LIMIT`.
- Policy: `api-retry-policy.md` records retryable/non-retryable criteria. Backend `details.retryable` wins over local status heuristics.
- Behavior: existing `apiRequest`, `authFetch`, and protected `401 -> refresh -> retry once` behavior is unchanged. The retry policy is opt-in for future query/hook refactoring.
- Verification: `npx tsc --noEmit` passed. `npm run test:run -- lib/apiClient.test.ts lib/auth.test.ts` passed, 2 files / 14 tests.

RF-TASK-018 is now complete. Next Wave entry is S5 pure helper/test hardening, with G2 Playwright auth flow revalidation still pending when browser/backend access is available.

---

## 6. RF-TASK-018 update

- Code: `lib/authTokenStorage.ts` now owns the PFM `access_token` / `refresh_token` storage keys.
- Consumers: `lib/auth.ts` and `lib/apiClient.ts` now call the adapter instead of direct `sessionStorage` access.
- Boundary: `auth-token-storage-policy.md` records that Supabase session storage remains separate in `lib/supabaseClient.ts` and was not changed.
- Verification: `npx tsc --noEmit` passed. `npm run test:run -- lib/authTokenStorage.test.ts lib/apiClient.test.ts lib/auth.test.ts` passed, 3 files / 17 tests.

## 7. S4 final verification

| Check | Result |
|---|---|
| `npx tsc --noEmit` | passed |
| `npm run test:run` | passed, 28 files / 171 tests |
| `npm run test:coverage` | passed, 28 files / 171 tests. Overall coverage: statements 59.99%, branches 52.59%, functions 57.23%, lines 63.5% |
| `npm run test:boundaries` | passed |
| `npm run build` (dummy public Supabase env) | passed. Build still skips lint by `next.config.ts`; existing Tailwind arbitrary-class ambiguity warnings remain unchanged. |
| `git diff --check` | passed with LF-to-CRLF warnings only |

Manual G2 gate still pending: Playwright login -> token refresh -> protected 401 retry flow requires browser/backend access.

---

## 8. RF-TASK-079 update

- Code: `getPfmApiBaseUrl` now documents `NEXT_PUBLIC_PFM_API_URL` as the canonical public env while intentionally preserving `NEXT_PUBLIC_PFM_LLM_URL` as a legacy fallback until Vercel deployment settings are confirmed.
- Behavior: URL resolution is unchanged. Missing env still raises `CONFIGURATION_ERROR` before any frontend-origin fetch, and the error message continues to point deployments to `NEXT_PUBLIC_PFM_API_URL`.
- Test: `lib/apiClient.test.ts` now covers legacy fallback resolution and trailing slash normalization.
- Verification: `npm run test:run -- lib/apiClient.test.ts` passed, 1 file / 12 tests. `npx tsc --noEmit` passed.
- Carried forward: actual Vercel production/preview env inventory is still required before removing the fallback.
