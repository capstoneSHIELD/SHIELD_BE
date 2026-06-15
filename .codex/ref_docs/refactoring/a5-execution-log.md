# A5 execution log (2026-06-12)

> Scope: `refactoring-execution-order.md` A5 AdminPage3 URL-state correction and tab presenter decomposition pass.
> This log covers RF-TASK-069, RF-TASK-070, and RF-TASK-071 passes for Users, the Simulation list, the Simulation detail summary/input-preview shell, Jobs, Results, and Visualization. Manual admin browser smoke remains carried forward.

## Task Status

| Task | Status | Result |
|---|---|---|
| RF-TASK-069 | complete, manual admin deep-link smoke carried forward | Added `useAdminUrlState` in `components/pages/adminUrlState.ts` and rewired `AdminPage3` to consume normalized URL state plus a shared `replaceQuery` helper. URL parsing, invalid tab-specific status cleanup, selected simulation nested selection reset, and 404 query cleanup now live in pure helpers backed by unit tests. Existing query names, default page/size behavior, tab values, status filters, selected entity params, and `router.replace(..., { scroll: false })` behavior are preserved. |
| RF-TASK-070 | complete, manual admin tab smoke carried forward | Extracted shared admin presenters to `components/pages/adminSharedPresenters.tsx`, moved the System/Overview tab to `components/pages/adminOverviewPanel.tsx`, and moved the Account Requests tab to `components/pages/adminAccountRequestsPanel.tsx`. `AdminPage3` still owns auth, query state, URL updates, review dialog state, and mutation orchestration; presenters receive display state plus intent callbacks only. Added `adminPanels.test.tsx` coverage for overview rendering and account request review/pagination intents. |
| RF-TASK-071 | complete, manual admin tab smoke carried forward | Moved the Users tab list/table UI to `components/pages/adminUsersPanel.tsx`, the Simulation list card to `components/pages/adminSimulationsPanel.tsx`, the Simulation detail summary/input-preview shell to `components/pages/adminSimulationDetailPanel.tsx`, the Jobs section to `components/pages/adminSimulationJobsPanel.tsx`, and the Results/Visualization section to `components/pages/adminSimulationResultsPanel.tsx`. `AdminPage3` still owns `usersQuery`, user update dialog state, `updateAdminUser` mutation orchestration, `simulationsQuery`, URL query updates, selected simulation reset behavior, input preview mutation, cache refresh/refetch, sync/cancel job mutations, result field-file query orchestration, result file download mutation, visualization create/update/close/screenshot mutations, and visualization body/screenshot parameter parsing; presenters receive display state plus intent callbacks only. Added `adminPanels.test.tsx` coverage for Users rendering/edit intent, Simulation list filter/pagination/selection intents, Simulation detail input-preview intent, job refresh/sync/cancel/selection intents, and result/visualization presenter intents. |

## Preserved Behavior

- `/cmsl20043` query params keep the same public contract: `tab`, `status`, `ownerUserId`, `simulationId`, `jobId`, `resultId`, `visualizationId`, `page`, and `size`.
- Invalid `page` falls back to `1`; invalid or oversized `size` falls back/caps to the existing safe bounds.
- Invalid status cleanup still only applies to tab-specific filters: simulation statuses on the simulations tab and account request statuses on the account-requests tab.
- Changing the selected simulation still clears nested job/result/visualization selections only when at least one nested selection is present.
- Not-found cleanup keeps the previous query behavior: simulation clears `simulationId`, job clears `jobId`, result clears `resultId` plus `visualizationId`, and visualization clears `visualizationId`.
- System/Overview and Account Requests tab copy, table columns, status labels, pagination behavior, and review dialog entry points are preserved. The approve path still resets `reviewRole` to `user`; both approve and reject still clear `rejectReason` before opening the dialog.
- Users tab copy, table columns, role/status badges, created date formatting, and edit dialog entry point are preserved. User update dialog and mutation behavior remain in `AdminPage3`.
- Simulation list filters, table columns, loading/error/empty copy, row selection, page-size behavior, pagination guard, and nested `jobId`/`resultId`/`visualizationId` reset on simulation selection are preserved.
- Simulation detail copy, status/metadata fields, missing fields, composition/parameter JSON blocks, input-preview loading/error/disabled states, and stale preview matching by `simulationId` are preserved. Input preview mutation and job/result/visualization orchestration remain in `AdminPage3`.
- Jobs section copy, cache-only `sync=false` guidance, cache refresh, Sync Lab, Cancel Job, list/detail/event loading/error/empty states, active-job polling badge, and no-`labJobId` cancel notice are preserved. Job refetching, sync/cancel mutations, and URL selection updates remain in `AdminPage3`.
- Results and Visualization copy, result refresh/list/detail states, result file download buttons, explicit field catalog load/reload/pagination, visualization create/refresh/close/viewer/control/screenshot UI, iframe safety behavior, and active visualization notice are preserved. Result field-file query state, download mutation, visualization mutations, body construction, screenshot parameter parsing, and URL selection updates remain in `AdminPage3`.

## Verification

| Check | Result |
|---|---|
| `npm run test:run -- components/pages/adminUrlState.test.tsx` | passed, 1 file / 9 tests |
| `npm run test:run -- components/pages/adminPanels.test.tsx components/pages/adminUrlState.test.tsx components/pages/adminFormatters.test.ts lib/api/admin.test.ts` | passed, 4 files / 29 tests |
| `npm run test:run -- components/pages/adminPanels.test.tsx` | passed, 1 file / 4 tests after Simulation list presenter extraction |
| `npx eslint components/pages/adminPanels.test.tsx components/pages/AdminPage3.tsx components/pages/adminSimulationsPanel.tsx components/pages/adminSharedPresenters.tsx` | passed after Simulation list presenter extraction |
| `npm run test:run -- components/pages/adminPanels.test.tsx` | passed, 1 file / 5 tests after Simulation detail presenter extraction |
| `npx eslint components/pages/adminPanels.test.tsx components/pages/AdminPage3.tsx components/pages/adminSimulationDetailPanel.tsx components/pages/adminSimulationsPanel.tsx components/pages/adminSharedPresenters.tsx` | passed after Simulation detail presenter extraction |
| `npm run test:run -- components/pages/adminPanels.test.tsx` | passed, 1 file / 6 tests after Jobs presenter extraction |
| `npx eslint components/pages/adminPanels.test.tsx components/pages/AdminPage3.tsx components/pages/adminSimulationJobsPanel.tsx components/pages/adminSimulationDetailPanel.tsx components/pages/adminSimulationsPanel.tsx components/pages/adminSharedPresenters.tsx` | passed after Jobs presenter extraction |
| `npm run test:run -- components/pages/adminPanels.test.tsx` | passed, 1 file / 7 tests after Results/Visualization presenter extraction |
| `npx eslint components/pages/adminPanels.test.tsx components/pages/AdminPage3.tsx components/pages/adminSimulationResultsPanel.tsx components/pages/adminSimulationJobsPanel.tsx components/pages/adminSimulationDetailPanel.tsx components/pages/adminSimulationsPanel.tsx components/pages/adminSharedPresenters.tsx` | passed after Results/Visualization presenter extraction |
| `npx eslint components/pages/adminPanels.test.tsx components/pages/AdminPage3.tsx components/pages/adminSharedPresenters.tsx components/pages/adminOverviewPanel.tsx components/pages/adminAccountRequestsPanel.tsx components/pages/adminUsersPanel.tsx components/pages/adminUrlState.ts components/pages/adminUrlState.test.tsx` | passed |
| `npx tsc --noEmit` | passed |
| `npm run test:run` | passed, 33 files / 208 tests |
| `npm run test:coverage` | passed, 33 files / 208 tests. Coverage: statements 64.12%, branches 57.50%, functions 60.17%, lines 67.64% |
| `npm run test:boundaries` | passed |
| `npm run build` | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_ANON_KEY`; existing Tailwind arbitrary class ambiguity warnings remain |
| `git diff --check` | passed; CRLF normalization warnings only |
| `npm run lint` | failed on pre-existing repo-wide lint debt outside this A5 change; touched A5 files passed targeted ESLint |

## Remaining A5 Work

- Manual/admin browser checks carried forward: `/cmsl20043` deep links, account request review, user mutation, job sync/cancel invalidation, result field-file Load/Reload/pagination, and tab switching.
