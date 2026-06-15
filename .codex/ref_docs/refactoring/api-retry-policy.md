# API retry policy (S4 / RF-TASK-017)

> Date: 2026-06-12
> Scope: `lib/apiClient.ts`
> Related finding: RF-FINDING-028 / S5-RETRY-001

## Purpose

Expose a single retryability policy for future query/hook refactoring without changing existing request behavior.

This policy does not enable automatic retries by itself. Callers such as React Query hooks may opt in by using `shouldRetryApiRequest(failureCount, error)`.

## Public API

- `getApiRetryDecision(error)`
- `isRetryableApiError(error)`
- `shouldRetryApiRequest(failureCount, error, maxRetries?)`
- `DEFAULT_API_RETRY_LIMIT`

## Classification

| Source | Retryable | Notes |
|---|---:|---|
| `ApiError.details.retryable === true` | yes | Backend policy wins over local status heuristics. |
| `ApiError.details.retryable === false` | no | Backend policy wins even for 5xx responses. |
| HTTP 408, 425, 429, 500, 502, 503, 504 | yes | Treated as transient unless backend policy says otherwise. |
| HTTP 400, 401, 403, 404, 409, 422 | no | Caller/user/auth/validation conflicts should not be retried generically. |
| `TimeoutError` | yes | Applies to opt-in request timeout from RF-TASK-016. |
| `AbortError` | no | Caller cancellation and unmount cleanup should not be retried. |
| `TypeError` from fetch/network failure | yes | Browser fetch surfaces network failures this way. |
| Unknown errors | no | Avoid hidden behavior changes. |

## Integration Guidance

- Do not add global React Query retry defaults until domain freshness/retry policy is documented in RF-TASK-035.
- Simulation/list/session hooks may opt in after S4 completes by passing `retry: shouldRetryApiRequest`.
- Keep auth refresh retry separate from this policy. The existing protected request `401 -> refresh -> retry once` path remains unchanged.
- Do not retry non-idempotent mutations generically. Mutation-specific retry requires an explicit use-case decision.

## Verification

- `npx tsc --noEmit`
- `npm run test:run -- lib/apiClient.test.ts lib/auth.test.ts`

Playwright login/token-refresh/401 retry remains a G2 manual revalidation item because no browser/backend job environment was available in this session.
