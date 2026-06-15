# Auth token storage policy (S4 / RF-TASK-018)

> Date: 2026-06-12
> Scope: `lib/authTokenStorage.ts`, `lib/auth.ts`, `lib/apiClient.ts`
> Related finding: RF-FINDING-022 / S4-PERSIST-001, S4-PERSIST-002, S5-PERSIST-001

## Purpose

Unify PFM token persistence behind `authTokenStorage` while preserving the existing browser storage keys and auth behavior.

## PFM Token Boundary

PFM auth tokens are stored only through `lib/authTokenStorage.ts`.

- Access token key: `access_token`
- Refresh token key: `refresh_token`
- Storage: browser `window.sessionStorage`
- Consumers: `lib/auth.ts`, `lib/apiClient.ts`

The adapter deliberately does not schedule refresh timers. `lib/auth.ts` and `lib/apiClient.ts` still own refresh scheduling/cancellation because those concerns belong to the PFM API auth flow, not raw storage.

## Supabase Boundary

Supabase session storage is separate and is not part of RF-TASK-018.

- Current Supabase storage remains in `lib/supabaseClient.ts`.
- Do not route Supabase tokens through `authTokenStorage`.
- Do not change Supabase storage behavior without CMS/board approval, RLS confirmation, and Track C validation.

## Preserved Behavior

- Login writes the same token keys.
- Logout clears the same token keys.
- `X-New-Access-Token` rotates only the access token.
- Protected request `401 -> refresh -> retry once` keeps the same flow.
- Refresh failure still clears the PFM token pair.

## Verification

- `npx tsc --noEmit`
- `npm run test:run -- lib/authTokenStorage.test.ts lib/apiClient.test.ts lib/auth.test.ts`

Playwright login/token-refresh/401 retry remains a G2 manual revalidation item because no browser/backend job environment was available in this session.
