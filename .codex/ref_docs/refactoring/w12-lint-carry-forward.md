# W12 Lint Carry-Forward Ledger (2026-06-13)

> Scope: RF-TASK-083 / RF-TASK-084 / RF-TASK-089 follow-up after user gate decisions.
> Latest command: `npm.cmd run lint`.
> Result: passed with 0 problems.
> Guard command: `npm.cmd run test:lint-carry-forward`.

## Decision Update

The user approved fixing the remaining lint debt with "전체 수정" on 2026-06-13. The previous 112-problem carry-forward bucket is now closed.

The same script name is kept for continuity, but its behavior changed:

- Previous behavior: allow the documented 112 remaining lint problems and fail only on new or larger lint debt.
- Current behavior: require global lint to remain clean and fail on any lint warning or error.

## Closed Buckets

| Previous bucket | Previous count | Current disposition |
|---|---:|---|
| Legacy CMS admin editors | 11 | Fixed while preserving legacy surfaces. |
| Contact external integration | 1 | Fixed after Contact/env replacement approval. |
| CMS edit forms | 52 | Fixed; notice rollback was implemented only for the approved notice domain first. |
| CMS board/list/public data surfaces | 39 | Fixed without tightening `next.config` remote image domains. |
| Legacy auth/simulation surfaces | 9 | Fixed while preserving the legacy keep decision. |

Total remaining lint issues: 0.

## Guard Semantics

`scripts/check-lint-carry-forward.mjs` now runs ESLint in JSON mode and fails if any severity 1 or severity 2 message exists. It no longer contains per-file carry-forward allowlists.

This keeps W12 closure honest: lint debt is no longer an approved deferral bucket.

## Verification

| Command | Result |
|---|---|
| `npm.cmd run lint` | Passed, 0 problems. |
| `npx.cmd tsc --noEmit` | Passed after the lint cleanup typing pass. |
| `npm.cmd run test:run` | Passed, 41 files / 232 tests. |
| `npm.cmd run test:coverage` | Passed, 41 files / 232 tests. |
| `npm.cmd run test:boundaries` | Passed. |
| `npm.cmd run test:circular` | Passed. |
| `npm.cmd run test:diff-check` | Passed; only line-ending warnings were emitted. |

## Remaining Non-Lint Decisions

- External image domain tightening remains skipped per user decision.
- Global `error.tsx` / middleware policy remains skipped per user decision.
- Backend-authenticated Playwright verification is approved, but still requires usable backend/session/test account conditions before it can prove authenticated workflows.
