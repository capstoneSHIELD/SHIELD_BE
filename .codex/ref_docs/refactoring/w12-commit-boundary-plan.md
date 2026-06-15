# W12 Commit Boundary Plan (2026-06-13)

> Scope: plan v2 "1 Task = 1 independent commit" rollback requirement.
> Current status: the assembled non-gated change set has been split into rollback-oriented commits matching the matrix below. This does not approve gated work or declare final cycle closure.

## Commit Boundary Evidence

The implementation was staged from the reviewed change-group manifest and committed in independent rollback slices. The implementation split is represented by the core commits below; later W12 documentation-only evidence corrections are intentionally not enumerated one-by-one in this table because doing so creates a self-referential audit update loop. Use `git log --oneline b2282a2..HEAD -- .codex/ref_docs/refactoring/w12-*.md` for the exact current list of W12 evidence-correction commits.

| Commit | Scope |
|---|---|
| `bbfd20b` | W0/W12 verification guards and package scripts. |
| `689b254` | W2/W3 API DTO, shared type, and mapper boundaries. |
| `1d5c625` | S4 API client/auth retry and token storage boundary. |
| `5f604c1` | S5-S9 Simulation2 workflow decomposition. |
| `0e3aaf2` | A4/A5 AdminPage3 presenter/query/url-state decomposition. |
| `3c13823` | Q/W11 shared UI and hook cleanup. |
| `1c12052` | W11 JS route, config, legacy adapter, and architecture docs. |
| `b2282a2` | Refactoring execution logs and W12 audit records. |

This is group-level rollback evidence for the already-assembled refactoring set. It does not reconstruct every RF-TASK as its own historical commit, but it removes the previous uncommitted-worktree gap and gives each sensitive area a distinct revert boundary.

## Split Matrix

| Proposed commit | Scope | Representative paths | Verification before commit |
|---|---|---|---|
| W0 verification scripts and baseline records | Baseline/route/circular scripts plus W0/W1 decision evidence. | `scripts/check-circular-imports.mjs`, `scripts/check-local-route-smoke.mjs`, `package.json`, `w0-execution-log.md`, `w1-decision-log.md` | `npm.cmd run test:circular`, `npm.cmd run test:route-smoke`, baseline command notes reviewed. |
| W2/W3 API and DTO mapper foundation | Shared API/status aliases, workflow mapper, parameter mapper, update body builder, parser/call-site typing. | `lib/api/*`, `components/pages/simulation2/workflowTypes.ts`, `components/pages/simulation2/workflowMappers.ts`, `components/pages/simulation2/simulationParameterMappers.ts`, related tests. `lib/api/admin.ts` and its tests are classified here because the Admin DTO/API alias boundary was settled before A4/A5 presenter work. | API/mapper targeted tests, `npx.cmd tsc --noEmit`, `npm.cmd run test:boundaries`. |
| S4 API client and token/retry boundary | Timeout/signal opt-in, retryable policy, token storage adapter, auth policy docs. | `lib/apiClient.ts`, `lib/authTokenStorage.ts`, `lib/auth.ts`, `api-retry-policy.md`, `auth-token-storage-policy.md`, related tests | `npm.cmd run test:run -- lib/apiClient.test.ts lib/authTokenStorage.test.ts lib/auth.test.ts`, backend auth smoke when available. |
| S5/S6 Simulation data hooks/helpers | Simulation2 pure helpers, list/session/result hooks, stale guards, query policy. | `components/pages/Simulation2Page.tsx`, `components/pages/simulation2/*`, `components/simulation/*`, `docs/architecture/query-policy.md`, S5/S6 logs | Simulation/list/session/result targeted tests, coverage guard, backend polling overlap follow-up. |
| S7 Simulation lifecycle | Job monitor parser, `useJobMonitorSession`, `useVisualizationSession`, WS/polling fallback lifecycle. | `components/pages/simulation2/jobMonitorSession*`, `components/pages/Simulation2Page.tsx`, related tests/logs | S7 targeted tests, Playwright/backend WS checks when available, immediate revert readiness. |
| S8/S9 Simulation presenters/auth | ResultWorkspace, ChatPanel, ParameterPanel, generated-input presenter, PFM auth gate. | `components/pages/simulation2/*Panel*`, `components/simulation/*`, `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx` | Simulation2 targeted tests, route smoke, full backend workflow when available. |
| A4/A5 Admin | Admin query keys, mutation hook, enabled query, formatter/file utils, URL state, tab presenters, NaN parser. | `components/pages/AdminPage3.tsx`, `components/pages/admin*`, `app/cmsl20043/page.tsx`. Admin API files are kept in the W2/W3 API/DTO slice to avoid duplicate ownership. | Admin targeted tests, route smoke for `/cmsl20043?page=abc`, authenticated admin smoke when available. |
| Q/W11 non-gated UI/common lint cleanup | Non-CMS visual/common UI, hooks, reactbits, VTK/trame helpers, app chrome, route wrappers. | `components/ImageCarousel.tsx`, `components/MemberDetailModal.tsx`, `components/Header.tsx`, `components/ui/*`, `components/reactbits/*`, `hooks/*`, `components/VtkViewer.tsx` | Targeted eslint/tests, visual/manual smoke when available, no CMS/public content paths. |
| W11 JS route and config/docs | JS route type-check policy, legacy chat adapter, config/comment cleanup, labserver docs helpers. | `api/chat.js`, `api/chat.test.ts`, `lib/api/legacy*`, `next.config.ts`, `eslint.config.mjs`, `tailwind.config.ts`, `docs/labserver-trame-paraview/*` | `npm.cmd run test:run -- api/chat.test.ts lib/api/legacyAdapters.test.ts`, targeted eslint, build. |
| W12 verification and closure docs | Strict-scope gate, strict-scope coverage gate, approval-gated diff check, lint carry-forward guard, change-group check/list/checklist/commands/json outputs, diff hygiene guard, circular/route-smoke docs, W12 aggregate/full regression guards, open gate packet, disposition/audit records. | `tsconfig.strict-scope.json`, `scripts/check-approval-gated-diffs.mjs`, `scripts/check-lint-carry-forward.mjs`, `scripts/check-refactoring-change-groups.mjs`, `scripts/check-strict-scope-coverage.mjs`, `scripts/check-w12-guards.mjs`, `scripts/check-w12-full-regression.mjs`, `w12-*.md`, `refactoring-task-backlog.md`, `session-to-refactoring-traceability.md`, `verification-strategy.md` | `npm.cmd run test:w12-full`, `npm.cmd run test:w12-guards`, `npm.cmd run test:change-groups:list`, `npm.cmd run test:change-groups:checklist`, `npm.cmd run test:change-groups:commands`, `npm.cmd run test:change-groups:json`, `npm.cmd run test:strict-scope-coverage`, `npm.cmd run test:diff-check`, stale-count search. |

## Do Not Include Without Explicit Approval

- Track C and CMS/board mutation files, including `EditNoticePage.tsx`, notice/gallery/home/project/publication/member edit flows, board/list pages, Supabase storage upload/delete paths, and CMS boundary rules.
- Public Supabase-backed content/template cleanup that was reverted in W12 gate correction: `components/ResearchPageTemplate.tsx`, research page wrappers, `AlumniPage.tsx`, `ProfessorPage.tsx`, and `IntroductionPage.tsx`.
- `lib/supabaseClient.ts` and `ContactPage.tsx` env usage replacement until Contact/CMS approval and deployed env names are confirmed.
- Legacy keep/archive/remove surfaces beyond already documented isolation: `PFMSimulationPage.tsx`, `SimulationPage.tsx`, `LegacyLoginPage.tsx`, `AdminPage.tsx`, and `AdminPage2.tsx`.

## Closure Rule

`npm.cmd run test:change-groups` was used before committing to verify that assembled staged diffs, unstaged diffs, and untracked paths were assigned to the split matrix groups above. Its classifier self-test locks representative path ownership, including the W2/W3 precedence for `lib/api/admin.ts`, keeps approval-gated CMS/board paths intentionally unassigned, and rejects unapproved overlapping group ownership. The only accepted overlaps are documented precedence cases where API/DTO files are matched by a later presenter group but must remain owned by the earlier W2/W3 slice. `npm.cmd run test:change-groups:list` printed the exact file manifest under the same groups for commit-review splitting, `npm.cmd run test:change-groups:checklist` printed the verification commands to run before reviewing or committing each active group, `npm.cmd run test:change-groups:commands` printed reviewable PowerShell-safe `git add -- ...` pathspec chunks per group without staging anything, and `npm.cmd run test:change-groups:json` emitted the same split manifest, group checklists, and git-add command chunks as machine-readable JSON. The command builder self-tests PowerShell quoting/chunking and verifies that generated command chunks cover each active group path list exactly.

After the split commits, `npm.cmd run test:change-groups` passed with 0 staged/unstaged changed or untracked paths and `git status --short` was clean, so there is no remaining uncommitted change-group drift.
