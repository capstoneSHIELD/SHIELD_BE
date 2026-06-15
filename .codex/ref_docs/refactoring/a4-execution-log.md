# A4 execution log (2026-06-12)

> Scope: `refactoring-execution-order.md` A4 AdminPage3 query/mutation/guard fallback/formatter stabilization pass.
> This log covers RF-TASK-040, RF-TASK-041, RF-TASK-042, RF-TASK-067, and RF-TASK-068. RF-TASK-069 is tracked separately in `a5-execution-log.md`.

## Task Status

| Task | Status | Result |
|---|---|---|
| RF-TASK-040 | complete, manual admin smoke carried forward | `AdminPage3` now consumes `buildAdminQueryKeys` for admin read queries, exact job/detail/event/result/visualization keys, account-request list prefix invalidation, and invalidation call sites. |
| RF-TASK-041 | complete, manual admin mutation smoke carried forward | Extracted `useSyncAdminJobMutation` and `useCancelAdminJobMutation` into `components/pages/adminJobMutations.ts`. The hooks own Lab sync/cancel mutation functions, cache `setQueryData`, exact invalidation fan-out, and toast error handling. `AdminPage3` keeps only the mutation instances and dialog state. |
| RF-TASK-042 | complete, manual field-file network smoke carried forward | Replaced the field-file `useMutation(fetchQuery) -> local state copy` flow with an enabled `useQuery` keyed by selected result, field name, and normalized filter params. The explicit Load/Reload buttons now select/update query params and refetch the current key when needed. |
| RF-TASK-067 | complete, manual admin fallback smoke carried forward | Extracted `/cmsl20043` route fallback and `AdminPage3` auth guard loading/error/inactive/non-admin messages to `components/pages/adminGuardPresenters.tsx`. Admin permission conditions and login redirect action are unchanged; global `error.tsx` remains deferred pending logging/recovery UX policy. Detailed strategy is recorded in `phase6-execution-log.md`. |
| RF-TASK-068 | complete, manual display smoke carried forward | Extracted admin formatter/file helpers to `components/pages/adminFormatters.ts`: date/unknown/bytes/MPI formatting, numeric form parsing, page-size clamp, HTTP viewer URL guard, and blob download helper. Added unit tests for preserved formatting/parsing/download behavior. |

## Preserved Behavior

- Admin query keys remain centralized through `buildAdminQueryKeys`; `sync: false` cache-only admin job polling/read behavior remains unchanged.
- Job sync still reads fresh Lab data with `sync: true`, writes job detail/events into cache, and invalidates jobs plus simulation detail.
- Job cancel still closes the cancel dialog and invalidates jobs, job detail, job events, simulation detail, and simulation results for the selected simulation.
- Field-file loading remains user-triggered by the Load/Reload buttons; selecting a field or changing pagination/filter values drives the query key.
- Admin guard behavior is unchanged: admin data queries still require an active account with the admin role, inactive/non-admin accounts are still blocked, and auth errors still offer the PFM login action.
- Formatter output intentionally preserves the previous `'-'` fallback, `toLocaleString()` date formatting, raw byte suffix, JSON object display, and filename/download behavior.

## Verification

| Check | Result |
|---|---|
| `npm run test:run -- components/pages/adminFormatters.test.ts components/pages/adminUrlState.test.tsx lib/api/admin.test.ts` | passed, 3 files / 26 tests |
| `npx eslint components/pages/adminFormatters.ts components/pages/adminFormatters.test.ts components/pages/adminJobMutations.ts components/pages/AdminPage3.tsx` | passed |
| `npm run test:run -- components/pages/adminGuardPresenters.test.tsx components/pages/adminPanels.test.tsx` | passed, 2 files / 10 tests |
| `npx eslint components/pages/adminGuardPresenters.tsx components/pages/adminGuardPresenters.test.tsx components/pages/AdminPage3.tsx app/cmsl20043/page.tsx` | passed |
| `npx tsc --noEmit` | passed |
| `npm run test:run` after RF-TASK-067 | passed, 42 files / 233 tests |
| `npm run test:coverage` after RF-TASK-067 | passed, 42 files / 233 tests. Coverage: statements 64.82%, branches 58.09%, functions 60.56%, lines 68.18% |
| `npm run test:boundaries` after RF-TASK-067 | passed |
| `npm run build` after RF-TASK-067 | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `git diff --check` after RF-TASK-067 | passed; CRLF normalization warnings only |
| `npm run test:run` | passed, 32 files / 195 tests |
| `npm run test:coverage` | passed, 32 files / 195 tests. Coverage: statements 63.95%, branches 56.54%, functions 60.02%, lines 67.60% |
| `npm run test:boundaries` | passed |
| `npm run build` | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_ANON_KEY`; existing Tailwind arbitrary class ambiguity warnings remain |
| `git diff --check` | passed |

## Remaining A4/A5 Work

- RF-TASK-071: Admin Simulation/Job/Result/Viz tab presenter decomposition, 1-2 tabs per session. RF-TASK-070 and the Users tab portion of RF-TASK-071 are tracked in `a5-execution-log.md`.
- Manual/admin browser checks carried forward: `/cmsl20043` deep links, auth/error/inactive/non-admin fallback states, account request review, user mutation, job sync/cancel invalidation, result field-file Load/Reload/pagination, and tab switching.
