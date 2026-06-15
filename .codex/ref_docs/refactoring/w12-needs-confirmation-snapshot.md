# W12 Needs-Confirmation Snapshot (2026-06-13)

> Scope: RF-TASK-089 partial output.
> This is a current closure table for the 30 unique "needs confirmation" items preserved in `consolidated-findings.md` section 5.
> This is not the final refactoring-cycle closure declaration. RF-TASK-084, RF-TASK-086, and RF-TASK-088 still have explicit carry-forward gates.
> No application code, runtime config, API contract, database behavior, or CMS/board behavior changed in this pass.

## Counting Rule

- Source: `consolidated-findings.md` section 5.
- Raw source bullets: 34.
- Alias-only duplicates collapsed: 4.
- Current unique confirmation items: 30.
- Collapsed duplicates:
  - Session 2 edit route RLS/UI access control duplicate.
  - Session 2 global error boundary duplicate.
  - Session 3 CMS HTML sanitize/trusted input duplicate.
  - Session 3 legacy admin/workbench/editor scope duplicate.

## Status Legend

| Status | Meaning |
|---|---|
| Closed | The current plan has a documented decision or implementation path and no additional confirmation is needed for the completed scope. |
| Partial | Work or measurement exists, but live backend/browser/product evidence is still pending. |
| Gated | No implementation should proceed until approval, external contract evidence, or operational data policy is available. |
| Deferred | Intentionally carried forward because the area is legacy, optional, or dependent on unavailable tooling. |

## Confirmation Closure Table

| ID | Source item | Related RF / task | Current status | Current disposition / remaining gate |
|---|---|---|---|---|
| NC-001 | Supabase RLS/permission policy and direct UI calls | RF-003, RF-005, RF-036, RF-050 | Gated | CMS/board service and mutation work remains blocked until approval, RLS/storage path evidence, backup/restore path, and test data exist. |
| NC-002 | Legacy AI assistant and `api/chat.js` product scope | RF-030 | Partial | W1 preserves the legacy chat path and Q track standardized validation/error parsing. Live Gemini/manual UI smoke and final product-scope confirmation remain. |
| NC-003 | Missing `store` directory as intentional state strategy | S4-STORE-001 good-pattern observation | Closed | No global store was introduced. The plan keeps query/hook/service boundaries as the default state strategy and adds store-like state only with explicit need. |
| NC-004 | Route guard policy review through Session 2 | RF-005, RF-007, RF-061 | Partial | PFM auth gate and Admin URL parsing were handled. CMS edit routes, legacy gates, and middleware/global fallback policy remain approval/product decisions. |
| NC-005 | Edit route access control through Supabase RLS and UI | RF-005, RF-006, RF-036 | Gated | Collapses the Session 2 summary/detail duplicate. No CMS edit-route gate was changed before CMS/board approval and RLS policy confirmation. |
| NC-006 | Global `error.tsx` absence / global error boundary intent | RF-007 | Partial | Collapses the Session 2 summary/detail duplicate. Admin guard presenters were improved, but global route error UX remains a product/UX decision. |
| NC-007 | Route group layout absence / shared Header-Footer intent | RF-008, RF-TASK-072 | Closed | Reviewed in W11. No route-group layout was added; extraction is deferred until product UX explicitly requests admin/workbench/viewer chrome differences. |
| NC-008 | `middleware.ts` absence / protected route middleware intent | RF-005, RF-007 | Partial | Middleware was not added. PFM page-level guard work is isolated; CMS/legacy route protection decisions remain carried forward. |
| NC-009 | CMS HTML sanitize / trusted input / save-time sanitizer policy | RF-013 | Gated | Collapses the Session 3 summary/detail duplicate. Sanitizer or render-path changes require CMS content trust policy and approval. |
| NC-010 | Legacy admin/workbench/editor operating scope | RF-034, RF-049 | Deferred | Collapses the Session 3 summary/detail duplicate. Legacy surfaces remain isolated until product access paths are reconfirmed. |
| NC-011 | Trame advanced panel child component API/service boundary | RF-055 and Trame good-pattern references | Partial | No Trame API/service boundary was moved in this pass. Existing timeout/fallback/cleanup patterns are preserved; manual Trame UI smoke remains carried forward. |
| NC-012 | Product freshness vs cache-only requirements | RF-027, RF-029 | Partial | Query policy was documented without changing global defaults. Product-specific freshness decisions remain before any behavior-changing cache policy changes. |
| NC-013 | CMS server state: React Query integration vs domain hook split | RF-003, RF-020, RF-021, RF-044 | Gated | CMS server-state work remains Track C and requires approval plus CMS data-shape/RLS confirmation. |
| NC-014 | `Simulation2Page` WebSocket cleanup/lifecycle revalidation | RF-001, RF-033, RF-045 | Partial | Job monitor parser and session hooks exist with tests. Backend-backed WS/fallback/reconnect/beforeunload/browser leak checks remain. |
| NC-015 | CMS page Supabase query with route/page permission policy | RF-003, RF-005, RF-020, RF-021 | Gated | CMS query and route-policy work remains blocked by CMS/board approval and RLS/session policy evidence. |
| NC-016 | React Query retry/gcTime policy fit from Session 4 query review | RF-027, RF-029 | Partial | Policy is documented and current defaults are preserved. Behavior changes require product freshness/retry confirmation. |
| NC-017 | React Query retry/gcTime defaults and SWR absence from Session 5 | RF-027, RF-029 | Partial | SWR is not used in the confirmed scope. Retry/gcTime changes remain product-policy work, not an implementation default change. |
| NC-018 | Backend error envelope consistency and legacy `/api/chat` scope | RF-030, RF-035 | Partial | Legacy `/api/chat` now has a standard error envelope path; all live PFM endpoint envelope behavior still requires backend/manual smoke. |
| NC-019 | Backend OpenAPI nullable/optional fields vs frontend DTOs | RF-039, RF-041, RF-042, RF-044 | Partial | PFM/shared alias and mapper boundaries were created. Backend OpenAPI drift checks and CMS content shapes remain external evidence gates. |
| NC-020 | `logout` response type and job monitor WS message structure | RF-022, RF-033, RF-045 | Partial | Job monitor parser/tests and token-storage policy exist. Live logout and real WS payload validation remain backend/browser gates. |
| NC-021 | Admin DTO and regular DTO contract difference | RF-039, RF-TASK-007, RF-TASK-010 | Closed | W1/W3 decision: do not wholesale-merge admin DTOs; use shared aliases plus admin-specific extensions/mappers. |
| NC-022 | Actual dead code / circular import status via tools | RF-060, RF-TASK-085 | Closed | W2 madge remeasure found 0 cycles. W12 local equivalent `npm.cmd run test:circular` passed with 278 source files / 757 internal dependency edges / 0 cycles. |
| NC-023 | Strict option error volume and priority | RF-038, RF-TASK-087, RF-TASK-088 | Partial | W12 measured baseline 0, unused pair 20, effective strict-family 18, strict+unused 38. Expanded scoped strict gate (`npm.cmd run test:strict-scope`) passes for selected helpers/scripts, AdminPage3 container/presenter/mutation helpers, Simulation2 container/workflow/lifecycle/presenter/list/session/result files, ResultExplorer display/data helpers, PFM API adapter/client files, PFM login/reset-password and AI assistant surfaces, common error/visual UI/hooks, app chrome/root shell, Footer/language/scroll/idle helpers, introduction visual sections, config/type files, the viewer route/page, UI primitives, server Supabase SSR helper, and W12 verification scripts including the lint carry-forward guard, aggregate guard, and full regression script; global option enablement remains deferred. |
| NC-024 | `apiRequest<T>` default generic staged change plan | RF-040, RF-TASK-014, RF-TASK-083 | Closed | Call sites were typed first; the default was later changed to `unknown` after explicit production call-site coverage. |
| NC-025 | CMS pageKey/free-schema content model | RF-044, RF-TASK-060 | Gated | CMS DTO/view-model work remains blocked until CMS shape confirmation and Track C approval. |
| NC-026 | Runtime use of `NEXT_PUBLIC_LAB_SERVER_API_KEY` / `NEXT_PUBLIC_PFM_AUTH_TOKEN` | RF-052, RF-TASK-079 | Partial | PFM canonical env handling is documented while legacy fallback remains. Vercel/local runtime env confirmation is still needed before removal. |
| NC-027 | `next.config` image `remotePatterns` all-host requirement | RF-053 | Gated | Do not restrict remote image hosts until actual CMS/CDN/external image domains are confirmed and visually smoked. |
| NC-028 | Form validation policy vs backend 422 dependency | RF-030, RF-042, RF-049 | Partial | Legacy chat and Simulation2 mapper validation paths improved. Remaining legacy/CMS form validation policy needs product/API confirmation. |
| NC-029 | Static-search-only conclusion about `zod` usage | RF-030 | Deferred | Q track used manual guards and did not assume generated-code/package usage. A deeper dependency/codegen audit is future work if schema generation is introduced. |
| NC-030 | Actual unused export/type list via TS/ESLint/dependency analyzer | RF-058, RF-060, RF-TASK-083, RF-TASK-087 | Partial | ESLint scope and strict/unused measurements exist. Full unused export/dependency analyzer cleanup remains staged and tool-dependent. |

## Current Closure Summary

| Status | Count |
|---|---:|
| Closed | 6 |
| Partial | 15 |
| Gated | 7 |
| Deferred | 2 |
| Total | 30 |

## Carry-Forward Notes

- Gated CMS/board items must not be converted into code work without explicit approval.
- Partial backend/browser items should be verified with real sessions, backend job execution, WebSocket traffic, and screenshots rather than route status checks only.
- Deferred legacy/tooling items should remain outside sensitive Simulation2/Admin commits unless the product or tooling decision changes.
- This table complements `w12-disposition-snapshot.md`; it does not replace the final RF-TASK-089 cycle closure declaration.
