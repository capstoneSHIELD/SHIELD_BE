# S5 execution log (2026-06-12)

> Scope: `refactoring-execution-order.md` S5 Simulation2 preparation.
> This log covers RF-TASK-045, RF-TASK-046, RF-TASK-028, and RF-TASK-030.

## Task Status

| Task | Status | Result |
|---|---|---|
| RF-TASK-045 | complete, manual revalidation carried forward | Moved Simulation2 pure helpers/constants into `components/pages/simulation2/workflowHelpers.ts`: `normalizeComposition`, assistant/missing-field formatting, warning extraction, MPI process count, workflow error mapping, visualization websocket error mapping, blob download, and job polling/WS timing constants. Added `workflowHelpers.test.ts`. |
| RF-TASK-046 | complete, manual revalidation carried forward | Replaced the old `(obj.details as any)?.warnings` path with `isRecord(details)` guard-based narrowing inside `extractWarnings`. Added tests for top-level and nested warning payloads. |
| RF-TASK-028 | code + automated regression complete, Playwright revalidation carried forward | Added a consecutive `getJob` failure counter for job polling fallback. After 3 consecutive failures the workflow shows an `ApiErrorNotice` inline retry notice and keeps retrying. A later successful `getJob` clears only that polling failure notice. Failed status polls no longer fan out to `listJobEvents`/availability checks for the failed tick. |
| RF-TASK-030 | complete | Added/extended PFM stabilization regression coverage for Simulation2 polling guard/failure recovery and AdminPage3 NaN-safe URL parser. |

## Preserved Behavior

- `Simulation2Page` remains the owner of orchestration and UI state.
- No backend endpoint, request body, response wire shape, token behavior, or WebSocket lifecycle behavior was changed.
- Polling/reconnect timing values are unchanged: `3000`, `2500`, `[1000, 2000, 5000]`.
- `getJob` polling still uses `{ sync: false }`.
- Download behavior still creates an object URL, clicks an anchor, and revokes the URL.
- Warning display uses the same payload sources: top-level `warnings` and `details.warnings`.

## Verification

| Check | Result |
|---|---|
| `npx tsc --noEmit` | passed |
| `npm run test:run -- components/pages/simulation2/workflowHelpers.test.ts components/pages/Simulation2Page.test.tsx` | passed, 2 files / 24 tests |
| `npm run test:run` | passed, 29 files / 179 tests |
| `npm run test:coverage` | passed, 29 files / 179 tests. Overall coverage: statements 61.51%, branches 54.69%, functions 58.66%, lines 65.03% |
| `npm run test:boundaries` | passed |
| `npm run build` | passed with dummy public Supabase env. Existing Tailwind ambiguous arbitrary-class warnings remain unchanged |
| `npm run lint` | failed on existing repository-wide lint debt (`no-explicit-any`, unused vars, `<img>`, etc.). No new lint category was introduced by RF-TASK-028/030 |

Manual G3 checks still pending: warning display and screenshot/download filename smoke through the browser, plus backend-backed Playwright network block/recovery and `browser_network_requests` polling-overlap observation for RF-TASK-028.
