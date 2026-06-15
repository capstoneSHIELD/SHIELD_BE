# W12 Open Gates Decision Packet (2026-06-13)

> Scope: RF-TASK-084 through RF-TASK-089 closure preparation.
> Purpose: list the external approvals, runtime evidence, and product decisions required before this refactoring cycle can be fully closed.
> This document does not approve any gated work by itself.
> This document does not change runtime configuration, API contracts, database behavior, or CMS/board behavior by itself; the implemented non-gated application changes are recorded in the W0-W12 execution logs and commit-boundary audit.

## 2026-06-13 User Decisions

| Item | User decision | Implementation meaning |
|---|---|---|
| 1 | notice 도메인만 먼저 승인 | CMS/board Track C work is approved only for the notice domain first; other CMS domains remain deferred. |
| 2 | 승인 | `EditNoticePage` attachment rollback/storage path cleanup may proceed for notice. |
| 3 | 승인 | Contact/env helper usage replacement may proceed. |
| 4 | 승인 | Backend/session/browser regression is approved when usable runtime credentials and backend conditions are available. |
| 5 | 유지 | Legacy simulation/admin/login surfaces are kept, not archived or removed. |
| 6 | 전체 수정 | Remaining lint debt is fixed globally; no lint carry-forward bucket remains. |
| 7 | 스킵 | External image domain tightening / `remotePatterns` narrowing is skipped. |
| 8 | 스킵 | Global `error.tsx` / middleware fallback policy is skipped. |

## Current Closure State

| Area | Current state | Blocking gate |
|---|---|---|
| PFM/Admin/Q implementation | Implemented through the approved non-CMS waves with tests and scripted W12 route smoke recorded. | Backend-authenticated browser regression still required for full confidence. |
| CMS/board Track C | Partially opened: notice domain first. Other CMS domains remain deferred. `npm.cmd run test:approval-gates` still guards approval-gated paths against accidental unapproved scope expansion. | Supabase RLS/storage policy, backup/restore path, test data, and explicit approval for domains beyond notice. |
| Contact/env P0 follow-up | Approved and implemented with `getRequiredPublicEnv` for Supabase public env and Contact EmailJS public env usage. | Runtime verification with real deployed env values remains recommended. |
| Legacy surfaces | Preserved/isolated per user decision; lint cleanup was allowed while keeping the surfaces. | Backend/session/browser evidence for actively used legacy flows remains runtime-dependent. |
| Circular import final check | Complete with local equivalent: `npm.cmd run test:circular` passed with 282 source files, 792 internal dependency edges, and 0 circular dependencies. | None. |
| Global lint/strict closure | Global lint is now clean after the user approved full lint cleanup. `npm.cmd run test:lint-carry-forward` now enforces 0 lint problems instead of permitting the old 112-problem carry-forward bucket. | Continued scoped strict/unused expansion remains separate from lint closure. |
| Final cycle closure | Not declared. Lint is closed, but backend-authenticated browser regression and non-notice CMS domain decisions remain runtime/scope dependent. | RF-TASK-084/086/088 plus CMS/runtime/product-policy dispositions need completion or explicit deferral decisions. |

## Decisions And Gate Outcomes

| Decision ID | Needed decision | Recommended default | Unlocks | Why it matters |
|---|---|---|---|---|
| DG-001 | Approve or defer CMS/board Track C implementation. | User approved notice domain first only. | Notice-domain RF-TASK-005 work and notice lint cleanup. Other CMS domain work remains deferred. | These files touch live CMS/board data, storage delete/upload, permissions, and visual content. |
| DG-002 | Confirm Supabase storage path parsing plus backup/restore procedure for attachment rollback. | User approved notice attachment rollback. | RF-TASK-005, RF-FINDING-036, RF-FINDING-050 for notice. | Attachment rollback can create or prevent file/DB mismatches; wrong storage path handling can affect real data. |
| DG-003 | Confirm Contact/EmailJS env names and whether Contact is part of the CMS approval gate. | User approved Contact/env helper replacement. | RF-TASK-006, RF-FINDING-031, RF-FINDING-051. | Env helper changes should match deployed variable names and user-visible contact behavior. |
| DG-004 | Decide legacy simulation/admin/login scope: keep, archive, or remove. | User chose keep. | RF-TASK-029, 080 and legacy lint cleanup while preserving behavior. | Broad cleanup here may be wasted if removed, or risky if still used. |
| DG-005 | Provide backend/session/browser conditions for full Playwright regression. | User approved, but runtime credentials/backend conditions are still required. | RF-TASK-084 manual gate, Simulation2 job flow, WS fallback, Admin authenticated operations, strict console/resource review, screenshot/visual checks. | Current local smoke proves route status/body, uncaught page-error absence, expected sandbox resource classification, and unexpected console/resource failure only; it does not prove authenticated workflows, backend job behavior, strict console/resource cleanliness against real external resources, or visual output. |
| DG-006 | Resolved in this pass: use a local TypeScript AST circular-import checker instead of installing `madge`. | No further user decision needed unless the project specifically requires `madge` parity. | RF-TASK-085 final W12 remeasure, RF-FINDING-060 closure. | `npm.cmd run test:circular` now checks source imports without network access and passed with 0 cycles. |
| DG-007 | Decide whether remaining lint in gated areas should be fixed now, deferred, or explicitly waived. | User chose full fix. | RF-TASK-083 completion, RF-TASK-084 lint baseline, RF-TASK-089 final closure. | The previous 112 lint issues are closed; `w12-lint-carry-forward.md` now records 0 remaining lint debt. |
| DG-008 | Decide strict/unused tightening policy after latest measurement. | Continue expanding `test:strict-scope`; do not enable global strict/unused in one step. | RF-TASK-088 and RF-FINDING-038. | Latest global measurement still has 20 unused-pair diagnostics and 38 effective strict+unused diagnostics, while the expanded scoped strict gate passes. |
| DG-009 | Confirm actual external image domains before tightening `next.config` `remotePatterns`. | User skipped this change. | RF-TASK-081, RF-FINDING-053 remain deferred. | Domain allowlist changes can break CMS/public images if the real host list is incomplete. |
| DG-010 | Decide whether a global `error.tsx` / middleware guard strategy is desired. | User skipped this change. | RF-FINDING-007, NC-006, NC-008 remain deferred. | Adding global route behavior can change error/fallback UX across the app. |

## Evidence Already Available

| Evidence | Current result |
|---|---|
| Typecheck | `npx.cmd tsc --noEmit` passed in W12. |
| Unit tests | `npm.cmd run test:run` passed: 41 files / 232 tests. |
| Coverage | `npm.cmd run test:coverage` passed: statements 64.71%, branches 57.94%, functions 60.52%, lines 68.07%. |
| Boundary test | `npm.cmd run test:boundaries` passed. |
| Approval-gated diff check | `npm.cmd run test:approval-gates` passed; its path-pattern self-test succeeded, and no staged/unstaged content diffs or untracked gated files were found in the approval-gated CMS/board/Contact/legacy component paths or App Router route wrappers checked by `scripts/check-approval-gated-diffs.mjs`. |
| Lint carry-forward guard | `npm.cmd run test:lint-carry-forward` passed; it now requires 0 lint problems and no carry-forward lint debt remains. |
| Change group check | `npm.cmd run test:change-groups` passed before commit splitting with 181 staged/unstaged changed or untracked paths assigned to the W12 split matrix groups, classifier self-test succeeded, git-add command quoting/chunking self-test succeeded, generated command chunks covered grouped paths exactly, and unapproved overlapping group ownership was 0. After the split commits, it passed again with 0 changed paths. `npm.cmd run test:change-groups:list`, `test:change-groups:checklist`, `test:change-groups:commands`, and `test:change-groups:json` remain available for future review capture. |
| Circular import check | `npm.cmd run test:circular` passed: 282 source files / 792 internal dependency edges / 0 cycles. |
| Scoped strict check | `npm.cmd run test:strict-scope` passed, including the local route smoke script, approval-gated diff script, lint carry-forward script, refactoring change group script, W12 aggregate guard script, W12 full regression script, selected AdminPage3 container/presenter helpers, Simulation2 container/list/session/result helpers, ResultExplorer helpers, PFM API adapter/client files, PFM login/reset-password and AI assistant surfaces, common error UI, mobile/toast/idle hooks, `MemberDetailModal`, `ImageCarousel`, `ResearchHighlightsSlider`, `ProjectCard`, `ScrollAnimation`, `ScrollingFocusSection`, introduction visual sections, app chrome/root shell, Footer/language/scroll helpers, config/type files, viewer route/page, selected UI primitives, and the server Supabase SSR helper. |
| Strict-scope coverage check | `npm.cmd run test:strict-scope-coverage` passed; 141 changed non-gated code/config paths were verified inside the 231-file scoped strict TypeScript program while approval-gated paths stayed excluded. |
| W12 full regression bundle | `npm.cmd run test:w12-full` passed; root typecheck, full vitest, coverage, PFM boundary check, production build with dummy public Supabase env fallbacks, and W12 guards, including the change-group checklist, git-add command manifest, JSON manifest, diff hygiene, and strict-scope coverage guards, all completed. |
| Build | `npm.cmd run build` passed with temporary local public Supabase env values. |
| Local route smoke | `npm.cmd run test:route-smoke` passed for core public/PFM/Admin route status/body checks, Admin NaN-safe URL, uncaught page-error detection, expected sandbox resource classifier self-test, expected sandbox resource classification, and unexpected console/resource failure listed in `w12-convergence-log.md`. Recent local runs recorded 0 uncaught page errors, 0 unexpected console/resource issues, 29-30 expected resource-load console messages, and 29-30 expected sandbox resource issues. |
| Diff hygiene check | `npm.cmd run test:diff-check` passed; `git diff --check` is now part of the scripted W12 aggregate guard instead of a manual-only final checklist item. |
| W12 aggregate guard | `npm.cmd run test:w12-guards` passed; it runs approval gates, lint carry-forward, change groups, change-group checklist, change-group JSON manifest, change-group git-add command manifest, diff hygiene, circular import, strict scope, strict-scope coverage, and route smoke in sequence. |
| Lint | `npm.cmd run lint` passed with 0 problems after full lint cleanup approval. |
| RF-FINDING disposition | RF-FINDING-001 through 061 snapshot exists in `w12-disposition-snapshot.md`. |
| Needs-confirmation disposition | NC-001 through NC-030 snapshot exists in `w12-needs-confirmation-snapshot.md`. |
| Active-goal completion audit | Requirement-by-requirement matrix exists in `w12-completion-audit.md`; conclusion remains not complete. |

## Recommended Next Move

For the safest continuation, resolve decisions in this order:

1. DG-005: run backend-authenticated browser regression when credentials/environment are available.
2. DG-001: keep non-notice CMS domains deferred until each domain is explicitly approved with RLS/storage/test data evidence.
3. DG-008: continue scoped strict/unused expansion; do not flip global strict/unused in one step.
4. DG-009/DG-010: remain skipped unless the user reopens image-domain tightening or global route fallback policy.

Until those decisions are made, final cycle closure should remain open and the goal should not be marked complete.

The current commit split reference is `w12-commit-boundary-plan.md`; it records the split commits and the approval-gated exclusions. Use `npm.cmd run test:change-groups:list` to inspect future file-level split manifests, `npm.cmd run test:change-groups:checklist` to inspect per-group verification commands, `npm.cmd run test:change-groups:commands` to print reviewable `git add -- ...` chunks for manual commit slicing, and `npm.cmd run test:change-groups:json` when tooling or review capture needs the same paths, checklists, and git-add command chunks in machine-readable form.
