# S6 execution log (2026-06-12)

> Scope: `refactoring-execution-order.md` S6 query/hook preparation.
> This log currently covers RF-TASK-035, RF-TASK-036, RF-TASK-037, RF-TASK-038, and RF-TASK-039.

## Task Status

| Task | Status | Result |
|---|---|---|
| RF-TASK-035 | complete | Added `docs/architecture/query-policy.md` to define domain-specific React Query policy for PFM simulation, Admin, and CMS/board areas. No code behavior or global QueryClient defaults were changed. |
| RF-TASK-036 | complete, manual revalidation carried forward | Extracted `useSimulationList` from `SimulationListCard`. The hook owns list loading, local pagination, refresh, and request sequence stale-guarding. Added `SimulationListCard.test.tsx` for local pagination, selection intent, and stale response suppression after `refreshKey` changes. |
| RF-TASK-037 | complete, manual revalidation carried forward | Extracted `useSimulationJobResults` from `JobResultListCard`. The hook owns job/result loading, refresh, `{ sync: false }` job listing, and request sequence stale-guarding. Extended `JobResultListCard.test.tsx` to cover simulation selection changes where older job/result responses resolve after the newer selection. |
| RF-TASK-038 | complete, manual revalidation carried forward | Extracted `useResultDetail`, `useResultFieldCatalog`, `useResultFieldFiles`, and `useResultDownload` into `useResultExplorerData`. `ResultExplorerPanel` now keeps UI rendering and user intent wiring while hook state owns request sequence guards. Extended `ResultExplorerPanel.test.tsx` to cover stale field-file responses after quick field changes. |
| RF-TASK-039 | complete, manual revalidation carried forward | Extracted `useChatSessions` from `SessionListCard`. The hook owns session list/search/page/delete/rename state, request sequence stale-guarding, and action-specific mutation state. Extended `SessionListCard.test.tsx` to cover refresh-key stale responses while preserving search/page/rename/delete callback contracts. |

## Preserved Behavior

- `app/providers.tsx` global defaults remain unchanged: `staleTime: 30_000`, `refetchOnWindowFocus: false`.
- No query retry, `gcTime`, polling interval, or invalidation behavior was changed in code.
- CMS/board/Supabase files remain untouched and approval-gated.
- `SimulationListCard` still fetches up to 100 simulations once and paginates locally in pages of 5.
- `JobResultListCard` still lists jobs with `{ sync: false }`, preserves `refreshKey`, and keeps selection/cancel/visualization callbacks in the presenter boundary.
- `ResultExplorerPanel` still loads field catalog only after user action, preserves field selection callbacks and visualization preferred-field behavior, and keeps failed downloads from clearing visible field-file results.
- `SessionListCard` still preserves search/page behavior, delete/rename dialog flows, `onDeleted`/`onRenamed` callbacks, and input `maxLength={200}` while moving API and stale response logic into `useChatSessions`.

## Policy Decisions

| Area | Decision |
|---|---|
| Global defaults | Do not change global QueryClient defaults during feature refactors. Any global change needs a dedicated task and both PFM/Admin and CMS/board track review. |
| PFM | Preserve WebSocket-first monitoring, `{ sync: false }` polling fallback, single-flight guards, and stale response protection during hook extraction. |
| Admin | Introduce/consume query key helpers before moving queries/mutations into hooks. Mutations own exact invalidation. |
| CMS/board | Define service/hook boundaries and confirm RLS/storage rollback requirements before changing data access or cache policy. |

## Verification

| Check | Result |
|---|---|
| `npm run lint` | failed due existing repo-wide lint debt (`no-explicit-any`, unused vars, image warnings, etc.). No T035-T039 hook file is listed in the lint output |
| `npx tsc --noEmit` | passed |
| `npm run test:run -- components/simulation/SimulationListCard.test.tsx` | passed, 1 file / 2 tests |
| `npm run test:run -- components/simulation/JobResultListCard.test.tsx` | passed, 1 file / 9 tests |
| `npm run test:run -- components/simulation/ResultExplorerPanel.test.tsx` | passed, 1 file / 6 tests |
| `npm run test:run -- components/simulation/SessionListCard.test.tsx` | passed, 1 file / 18 tests |
| `npm run test:run -- components/simulation/SimulationListCard.test.tsx components/pages/Simulation2Page.test.tsx` | passed, 2 files / 19 tests |
| `npm run test:run` | passed, 30 files / 184 tests. Note: a concurrent test+coverage run first timed out in `LoginPage.test.tsx`; rerunning sequentially passed |
| `npm run test:coverage` | passed, 30 files / 184 tests. Overall coverage: statements 62.70%, branches 55.12%, functions 60.19%, lines 66.26% |
| `npm run test:boundaries` | passed |
| `npm run build` | passed with dummy public Supabase env. Existing Tailwind ambiguous arbitrary-class warnings remain unchanged |

Manual S6 checks still pending: browser smoke for fast simulation list refresh/switching, fast job/result simulation switching, fast result/field switching, and repeated chat session search/rename/delete once a backend/browser environment is available.
