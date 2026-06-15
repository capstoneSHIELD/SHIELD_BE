# W12 Gate Correction Log (2026-06-13)

> Scope: restore plan v2 CMS/public-content approval boundaries after the completion audit found CMS-backed public page edits in the working tree.
> This pass intentionally does not implement Track C.

## Correction

The following attempted cleanups were reverted or removed because they touched Supabase-backed public CMS surfaces without an explicit CMS/public-content approval:

| Area | Reverted/removed work | Current disposition |
|---|---|---|
| `components/ResearchPageTemplate.tsx` | Stable section key helper, `getContent` type narrowing, `next/image` representative media conversion, and `defaultContent` effect dependency cleanup. | Deferred behind RF-TASK-048 / RF-TASK-021 / RF-TASK-059 / RF-TASK-061 approval and visual/data-shape verification. |
| Research page wrappers | Type-only import cleanup in research wrapper pages. | Reverted; no unstaged content diff remains in `git diff --name-only`; `test:approval-gates` also checks staged diffs and untracked gated files. |
| Supabase-backed public pages | Small lint/type cleanup in `AlumniPage`, `ProfessorPage`, and `IntroductionPage`. | Reverted; public CMS page lint remains in the gated carry-forward bucket. |
| Strict-scope helper | `components/researchSectionKeys.ts` and `components/ResearchPageTemplate.test.ts`. | Removed from the current working tree and from `tsconfig.strict-scope.json`. |

`git diff --name-only` no longer lists `ResearchPageTemplate`, research wrapper pages, `AlumniPage`, `ProfessorPage`, or `IntroductionPage`, and `npm.cmd run test:approval-gates` now checks staged diffs and untracked gated files too. `git status` may still show line-ending/stat refresh noise for those files because `git update-index --refresh` cannot write to `.git/objects` in the current sandbox, but there is no content diff for those paths.

## Verification

| Check | Result |
|---|---|
| `npm.cmd run test:strict-scope` | Passed after removing the Supabase-backed research template helper from scope. |
| `npx.cmd tsc --project tsconfig.strict-scope.json --listFilesOnly` | Confirmed 231 repo files / 41 `.test.*` files in the scoped strict program after adding the W12 aggregate guard, full regression scripts, strict-scope coverage script, the server Supabase SSR helper, and the reset-password page strict check. |
| `git diff --name-only` | Public CMS files above are absent from the unstaged content-diff list. |
| `npm.cmd run test:approval-gates` | Passed. `scripts/check-approval-gated-diffs.mjs` now guards the approval-gated CMS/board/Contact/legacy component paths and App Router route wrappers against staged/unstaged content diffs, untracked approval-gated files, and the reverted `ResearchPageTemplate` helper/test files. The script also self-tests representative gated route wrappers and non-gated PFM/Admin routes before accepting the diff scan. |

## Effect On Task Status

- RF-TASK-048 is no longer complete end-to-end. The `ImageCarousel` half remains complete; the `ResearchPageTemplate` half is carried forward behind the CMS/public-content approval gate.
- RF-TASK-083 no longer claims `ResearchPageTemplate`, `AlumniPage`, `ProfessorPage`, or `IntroductionPage` cleanup as current implemented work.
- RF-TASK-088 scoped strict count is now 231 repo files / 41 test files.
