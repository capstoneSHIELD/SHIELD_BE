# W12 RF-FINDING Disposition Snapshot (2026-06-13)

> Scope: RF-TASK-089 partial output.
> This is not the final cycle-closure declaration. It records the current disposition of RF-FINDING-001 through RF-FINDING-061 from the current backlog, execution logs, and W12 convergence log.
> No application runtime code, runtime config, API contract, or database behavior changed in this pass.

## Status Legend

| Status | Meaning |
|---|---|
| Complete | Implemented or review-completed with relevant automated checks recorded. |
| Partial | Main implementation or measurement exists, but an explicit manual/backend/browser gate remains. |
| Gated | Not implemented because CMS/board/Supabase approval, product decision, external contract, or environment evidence is required. |
| Deferred | Intentionally carried forward because the plan requires staged cleanup or tool availability. |

## RF-FINDING Snapshot

| Finding | Status | Evidence | Remaining gate |
|---|---|---|---|
| RF-FINDING-001 | Partial | Simulation2 presenters/helpers/hooks extracted across S5-S9; automated tests/build/type/boundary checks recorded. | Backend-backed full workflow and visual regression. |
| RF-FINDING-002 | Partial | AdminPage3 query/mutation/url/tab presenter work completed in A4/A5. | Authenticated admin browser smoke for operations. |
| RF-FINDING-003 | Gated | W1 keeps CMS/Supabase service migration blocked. | CMS/board approval, RLS/storage policy, test data. |
| RF-FINDING-004 | Gated | PFM boundary check passes; CMS boundary inspection is RF-TASK-086 carry-forward. | CMS service boundary approval/implementation. |
| RF-FINDING-005 | Partial | PFM auth gate extracted; Supabase/CMS/legacy gates untouched. | CMS/legacy gate approval and route behavior validation. |
| RF-FINDING-006 | Gated | Board id parser/not-found work remains in CMS/board route scope. | CMS/board approval and route policy decision. |
| RF-FINDING-007 | Partial | Admin route/guard fallback presenters extracted. | Global error boundary/logging/recovery UX policy. |
| RF-FINDING-008 | Complete | Route layout reviewed; extraction deferred by product UX decision. | None for review-only disposition. |
| RF-FINDING-009 | Gated | Board session ownership remains in CMS/board scope. | CMS/board approval and RLS/session policy. |
| RF-FINDING-010 | Complete | WorkspaceTabsCard prop grouping completed and tested. | Browser tab smoke carried forward only. |
| RF-FINDING-011 | Complete | MemberDetailModal moved to Radix-backed dialog with tests. | Browser focus-cycle smoke carried forward only. |
| RF-FINDING-012 | Gated | NewsPage presentation/action cleanup remains board/CMS UI scope. | CMS/board approval and visual/product checks. |
| RF-FINDING-013 | Gated | CMS HTML sanitizer/trusted-input policy remains unconfirmed. | Sanitizer/trusted content policy. |
| RF-FINDING-014 | Partial | Stable keys were added for Simulation2 and ImageCarousel with tests. The attempted ResearchPageTemplate stable-section-key cleanup was reverted during W12 gate correction because it is a Supabase-backed public CMS surface. | ResearchPageTemplate cleanup remains behind the CMS/public-content approval gate; visual smoke carried forward. |
| RF-FINDING-015 | Complete | ResearchHighlightsSlider variants moved out of render path. | Visual animation smoke carried forward only. |
| RF-FINDING-016 | Complete | ResultExplorer data hook/stale guard work completed with tests. | Browser smoke carried forward only. |
| RF-FINDING-017 | Complete | Job result hook/stale guard work completed with tests. | Browser smoke carried forward only. |
| RF-FINDING-018 | Complete | Simulation list hook/stale guard work completed with tests. | Browser smoke carried forward only. |
| RF-FINDING-019 | Complete | Chat session hook/view split and stale guards completed with tests. | Backend smoke carried forward only. |
| RF-FINDING-020 | Gated | CMS notice/gallery stale guard work remains in Track C. | CMS/board approval. |
| RF-FINDING-021 | Gated | HomePage CMS fetch error/type cleanup remains CMS-backed public page scope. | CMS data shape and visual/product checks. |
| RF-FINDING-022 | Complete | PFM auth token storage adapter and policy doc completed. | Backend auth smoke carried forward only. |
| RF-FINDING-023 | Complete | LanguageProvider review-only disposition recorded. | Product/i18n decision before optional implementation. |
| RF-FINDING-024 | Complete | useToast listener subscription cleanup and tests completed. | Manual toast smoke carried forward only. |
| RF-FINDING-025 | Complete | useIsMobile tri-state behavior and tests completed. | Manual breakpoint smoke carried forward only. |
| RF-FINDING-026 | Complete | ResearchHighlights empty-list guard and tests completed. | Visual smoke carried forward only. |
| RF-FINDING-027 | Complete | Query policy documented and referenced by S6. | Product freshness changes would be future policy work. |
| RF-FINDING-028 | Partial | apiRequest timeout/signal and retry policy implemented. | Backend login/token refresh/401 retry browser validation. |
| RF-FINDING-029 | Partial | Admin query key, mutation, enabled-query, and formatter split completed. | Authenticated admin operation smoke. |
| RF-FINDING-030 | Partial | Legacy chat envelope/schema and adapter parser completed. | Live Gemini/manual UI smoke. |
| RF-FINDING-031 | Gated | Contact EmailJS adapter depends on env/contact usage decision. | Contact/env approval and submit success/failure smoke. |
| RF-FINDING-032 | Partial | Simulation2 polling single-flight guard and regression tests completed. | Backend job polling overlap observation. |
| RF-FINDING-033 | Partial | Job monitor and visualization session hooks extracted with tests. | Backend WS/polling fallback/leak validation. |
| RF-FINDING-034 | Deferred | Legacy PFMSimulationPage kept inactive/isolated by W1 decision. | Legacy product access path reconfirmation. |
| RF-FINDING-035 | Partial | getJob failure notice and regression tests completed. | Backend network failure/recovery smoke. |
| RF-FINDING-036 | Gated | EditNoticePage attachment rollback held by W1. | User approval, storage path, backup/restore, test data. |
| RF-FINDING-037 | Gated | Notice pin/delete failure feedback remains notice CMS mutation scope. | CMS/board approval. |
| RF-FINDING-038 | Partial | Strict/unused measurements completed; latest effective strict+unused is 38 diagnostics. Scoped strict/unused gate added via `tsconfig.strict-scope.json` and expanded through selected route wrappers, the checked JS chat route, focused strict test fixtures, helpers/scripts, AdminPage3 container/presenter/mutation helpers, Simulation2 container/workflow/lifecycle/presenter/list/session/result files, ResultExplorer display/data helpers, PFM trame helpers, labserver integration examples, PFM API adapter/client files and tests, legacy adapter boundary helpers/tests, PFM login/reset-password and AI assistant surfaces, common error/visual UI/hooks and tests, app chrome/root shell, Footer/language/scroll/idle helpers, introduction visual sections, config/type files, UI primitives, the PFM VTK viewer route/helper, the server Supabase SSR helper, the approval-gated diff script, the lint carry-forward script, the refactoring change group script, strict-scope coverage script, W12 aggregate guard script, and W12 full regression script; Supabase-backed research template helper was removed during gate correction; `npm.cmd run test:strict-scope` and `npm.cmd run test:strict-scope-coverage` passed. | Continue scoped expansion; do not enable global strict/unused in one step. |
| RF-FINDING-039 | Complete | Shared status/DTO aliases and admin contract boundary completed. | None beyond normal backend contract drift monitoring. |
| RF-FINDING-040 | Complete | apiRequest call-site typing and unknown default completed after explicit call sites. | None recorded beyond normal API tests. |
| RF-FINDING-041 | Complete | Workflow parameter DTO/state/editable split completed. | None recorded beyond workflow tests. |
| RF-FINDING-042 | Complete | buildUpdateSimulationBody mapper and tests completed. | Backend parameter PATCH/job flow smoke carried forward. |
| RF-FINDING-043 | Complete | extractWarnings guard narrowing completed with tests. | None recorded beyond warning display smoke. |
| RF-FINDING-044 | Gated | CMS typed model work remains Track C. | CMS data shape confirmation and approval. |
| RF-FINDING-045 | Complete | JobMonitorMessageDto parser added with tests. | Backend WS message smoke carried forward. |
| RF-FINDING-046 | Complete | ColorBends typed color-space cleanup completed. | WebGL visual smoke carried forward. |
| RF-FINDING-047 | Complete | Simulation2 pure helper/constant extraction completed with tests. | Browser smoke for warning/download surfaces. |
| RF-FINDING-048 | Complete | Admin formatter/file util extraction completed with tests. | Manual display/download smoke carried forward. |
| RF-FINDING-049 | Deferred | Legacy parser split deferred by W1 legacy inactive decision. | Legacy product access path reconfirmation. |
| RF-FINDING-050 | Partial | Simulation2/admin download-related helpers improved; CMS storage sanitizer side remains. | Storage filename policy and CMS/Supabase approval. |
| RF-FINDING-051 | Gated | Public env helper usage split; Supabase/Contact replacements held. | CMS/Contact approval and deployed env confirmation. |
| RF-FINDING-052 | Partial | PFM canonical env documented while legacy fallback preserved. | Vercel/local env confirmation before fallback removal. |
| RF-FINDING-053 | Gated | remotePatterns allowlist not changed. | Actual external CMS/CDN image domain list. |
| RF-FINDING-054 | Complete | next.config comments cleaned without config value changes. | None. |
| RF-FINDING-055 | Complete | Colormap/page-size/admin polling constants extracted. | Manual UI smoke carried forward. |
| RF-FINDING-056 | Complete | formatRelativeTime optional locale added; default remains `ko`. | Product choice for wiring UI locale. |
| RF-FINDING-057 | Complete | QueryParams type applied to HTTP helper signatures. | None beyond API helper tests. |
| RF-FINDING-058 | Partial | JS route policy, ESLint CLI, and many non-gated lint slices completed. | Full lint still fails with 72 errors / 40 warnings in gated or confirmation-needed areas. |
| RF-FINDING-059 | Complete | Admin re-export removed; helper imported from real owner. | None. |
| RF-FINDING-060 | Complete | W0 circular found, W2 madge remeasure showed 0 cycles, and W12 local equivalent `npm.cmd run test:circular` passed with 278 source files / 757 internal dependency edges / 0 cycles. | None. |
| RF-FINDING-061 | Partial | Admin NaN-safe parser and URL state hook completed; scripted local route smoke (`npm.cmd run test:route-smoke`) shows no `NaN`. | Authenticated admin deep-link/operation smoke. |

## Current Carry-Forward Gates

| Gate | Blocks |
|---|---|
| CMS/board approval, RLS/storage path, backup/test data | RF-FINDING-003, 004, 006, 009, 012, 013, 020, 021, 036, 037, 044, 050, 051, and related lint debt. |
| Backend/session/browser environment | RF-FINDING-001, 002, 028, 029, 032, 033, 035, 042, 045, 061 full workflow verification. |
| Legacy product access decision | RF-FINDING-034, 049. |
| External/domain policy confirmation | RF-FINDING-031, 052, 053, 056. |
| Staged lint/strict cleanup | RF-FINDING-038 global option expansion, RF-FINDING-058 and `npm.cmd run lint` failure bucket. |

## Verification Linked To This Snapshot

- `npx.cmd tsc --noEmit`: passed in W12.
- `npm.cmd run test:run`: passed, 41 files / 232 tests.
- `npm.cmd run test:coverage`: passed, 41 files / 232 tests; statements 64.71%, branches 57.94%, functions 60.52%, lines 68.07%.
- `npm.cmd run test:boundaries`: passed.
- `npm.cmd run test:circular`: passed, 278 source files / 757 internal dependency edges / 0 circular dependencies.
- `npm.cmd run test:route-smoke`: passed, 9 routes against local `next start` including `/cmsl20043?page=abc&size=999` with no `NaN` body text, 0 uncaught page errors, expected sandbox resource classifier self-test, expected sandbox resource classification, and unexpected console/resource failure. Recent local runs observed 29-30 expected resource-load console messages, 29-30 expected sandbox resource issues, and 0 unexpected console/resource issues.
- `npm.cmd run test:strict-scope`: passed, scoped program includes 231 repo files / 41 test files.
- `npm.cmd run test:strict-scope-coverage`: passed, 141 changed non-gated code/config paths checked against 231 strict-scope repo files.
- `npm.cmd run test:diff-check`: passed; `git diff --check` now runs through a package script and the W12 guard bundle.
- `npm.cmd run test:w12-full`: passed, root typecheck + full vitest + coverage + PFM boundary check + production build with dummy public Supabase env fallbacks + W12 guards including the change-group checklist, git-add command manifest, JSON manifest, diff hygiene, and strict-scope coverage guards.
- `npm.cmd run test:lint-carry-forward`: passed, 112 lint problems (72 errors / 40 warnings) remain within approved carry-forward paths/rules and baseline counts.
- `npm.cmd run test:run -- components/pages/Simulation2Page.test.tsx components/simulation/ResultExplorerPanel.test.tsx`: passed, 2 files / 25 tests after strict-scope presenter narrowing.
- `npm.cmd run test:run -- components/simulation/JobResultListCard.test.tsx components/simulation/SessionListCard.test.tsx components/simulation/SimulationListCard.test.tsx components/simulation/ResultExplorerPanel.test.tsx components/pages/Simulation2Page.test.tsx`: passed, 5 files / 54 tests after adding PFM list/session/result cards and hooks to strict-scope.
- `npm.cmd run test:run -- components/pages/adminFormatters.test.ts components/pages/adminUrlState.test.tsx components/pages/adminGuardPresenters.test.tsx components/pages/adminPanels.test.tsx lib/api/admin.test.ts`: passed, 5 files / 36 tests after adding Admin presenter/mutation helpers to strict-scope.
- `npm.cmd run test:run -- lib/api/admin.test.ts lib/api/chatSessions.test.ts lib/api/jobs.test.ts lib/api/labserver.test.ts lib/api/labserverErrors.test.ts lib/api/labserverTrameClient.test.ts lib/api/results.test.ts lib/api/simulations.test.ts lib/api/visualizations.test.ts lib/auth.test.ts`: passed, 10 files / 66 tests after adding PFM API adapter/auth helpers to strict-scope.
- `npm.cmd run test:run -- lib/apiClient.test.ts lib/authTokenStorage.test.ts lib/auth.test.ts`: passed, 3 files / 18 tests after adding `lib/apiClient.ts` to strict-scope.
- `npm.cmd run test:run -- components/common/ApiErrorDetailsPanel.test.tsx hooks/use-mobile.test.tsx hooks/use-toast.test.tsx components/MemberDetailModal.test.tsx`: passed, 4 files / 8 tests after adding common error UI, mobile/toast hooks, and `MemberDetailModal` to strict-scope.
- `npm.cmd run test:run -- components/ImageCarousel.test.ts components/ResearchHighlightsSlider.test.tsx`: passed, 2 files / 3 tests after adding non-gated visual UI components to strict-scope.
- `npx.cmd eslint components/ProjectCard.tsx components/ScrollAnimation.tsx components/ScrollingFocusSection.tsx`: passed after adding additional non-gated visual UI components to strict-scope.
- `npx.cmd eslint components/Header.tsx components/Navigation.tsx components/MobileNavigation.tsx components/LanguageProvider.tsx components/ui/button.tsx components/ui/badge.tsx components/ui/card.tsx components/ui/alert.tsx components/ui/dialog.tsx`: passed after explicitly adding app chrome and UI primitives to strict-scope.
- `npx.cmd eslint components/ui/textarea.tsx components/ui/command.tsx components/ui/calendar.tsx components/ui/chart.tsx components/ui/CipherImage.tsx components/ui/ScrollyText_UI.tsx`: passed after adding additional UI primitives/display helpers to strict-scope.
- `npx.cmd eslint components/ui/input.tsx components/ui/label.tsx components/ui/separator.tsx components/ui/skeleton.tsx components/ui/progress.tsx components/ui/switch.tsx components/ui/tabs.tsx components/ui/tooltip.tsx components/ui/avatar.tsx`: passed after adding base Radix/shadcn UI primitives to strict-scope.
- `npx.cmd eslint components/ui/accordion.tsx components/ui/alert-dialog.tsx components/ui/aspect-ratio.tsx components/ui/breadcrumb.tsx components/ui/carousel.tsx components/ui/checkbox.tsx components/ui/collapsible.tsx components/ui/dropdown-menu.tsx components/ui/hover-card.tsx components/ui/popover.tsx components/ui/radio-group.tsx components/ui/scroll-area.tsx components/ui/select.tsx components/ui/sheet.tsx components/ui/slider.tsx components/ui/table.tsx`: passed after adding additional Radix/shadcn overlay/navigation/table primitives to strict-scope.
- `npx.cmd eslint components/ui/context-menu.tsx components/ui/drawer.tsx components/ui/form.tsx components/ui/input-otp.tsx components/ui/menubar.tsx components/ui/navigation-menu.tsx components/ui/pagination.tsx components/ui/resizable.tsx components/ui/sonner.tsx components/ui/toast.tsx components/ui/toaster.tsx components/ui/toggle.tsx components/ui/toggle-group.tsx components/ui/use-toast.ts components/ui/MagneticButton.tsx components/ui/SpotlightCard.tsx components/ui/TechText.tsx`: passed after adding remaining non-editor UI primitives/display helpers to strict-scope.
- `npx.cmd eslint components/ui/sidebar.tsx components/reactbits/ColorBends.tsx components/reactbits/InfiniteMenu.tsx components/reactbits/LogoLoop.tsx`: passed after adding sidebar and non-gated reactbits visual helpers to strict-scope.
- `npx.cmd eslint components/VtkViewer.tsx`: passed after adding the PFM VTK viewer helper to strict-scope.
- `npx.cmd eslint components/simulation/trame/CompositeDialog.tsx components/simulation/trame/TrameControlPanel.tsx`: passed after adding PFM trame control/composite helpers to strict-scope.
- `npx.cmd eslint app/simulation2/page.tsx app/pfm_chat/login/page.tsx app/cmsl20043/page.tsx`: passed after adding PFM/Admin route wrappers to strict-scope.
- `npm.cmd run test:run -- api/chat.test.ts` and `npx.cmd eslint api/chat.js`: passed after adding the checked JS chat route to strict-scope.
- `npm.cmd run test:run -- components/simulation/trame/trameReviewFixes.test.ts` and `npx.cmd eslint components/simulation/trame/AdvancedTramePanel.tsx components/simulation/trame/TrameExportCenter.tsx components/simulation/trame/TrameViewer.tsx docs/labserver-trame-paraview/ReactIntegrationExample.tsx docs/labserver-trame-paraview/labserver-trame-client.ts`: passed after adding the remaining PFM trame panels and labserver integration examples to strict-scope.
- `npx.cmd tsc --project tsconfig.strict-scope.json --listFilesOnly`: confirmed 231 repo files and 41 `.test.*` files are included in the scoped strict program; Supabase-backed research template paths remain out with CMS/public content shape follow-up.
- `npm.cmd run test:run -- hooks/use-mobile.test.tsx hooks/use-toast.test.tsx lib/utils.test.ts lib/authTokenStorage.test.ts lib/apiClient.test.ts components/common/ApiErrorDetailsPanel.test.tsx components/MemberDetailModal.test.tsx components/ImageCarousel.test.ts components/ResearchHighlightsSlider.test.tsx lib/auth.test.ts`: passed, 10 files / 33 tests after adding common hook/UI/auth/apiClient tests to strict-scope.
- `npm.cmd run test:run -- lib/api/admin.test.ts lib/api/chatSessions.test.ts lib/api/errors.test.ts lib/api/http.test.ts lib/api/jobs.test.ts lib/api/labserver.test.ts lib/api/labserverErrors.test.ts lib/api/labserverTrameClient.test.ts lib/api/results.test.ts lib/api/simulations.test.ts lib/api/visualizations.test.ts`: passed, 11 files / 73 tests after adding PFM API adapter tests to strict-scope.
- `npm.cmd run test:run -- components/pages/adminFormatters.test.ts components/pages/adminGuardPresenters.test.tsx components/pages/adminPanels.test.tsx components/pages/adminPolling.test.ts components/pages/adminUrlState.test.tsx components/pages/simulation2/jobMonitorSession.test.ts components/pages/simulation2/pfmAuthGate.test.tsx components/pages/simulation2/simulationParameterMappers.test.ts components/pages/simulation2/workflowHelpers.test.ts components/pages/simulation2/workflowMappers.test.ts components/simulation/JobResultListCard.test.tsx components/simulation/ResultExplorerPanel.test.tsx components/simulation/SessionListCard.test.tsx components/simulation/SimulationListCard.test.tsx components/simulation/VisualizationControlBar.test.tsx`: passed, 15 files / 86 tests after adding Admin/Simulation helper and presenter tests to strict-scope.
- `npm.cmd run test:run -- components/pages/Simulation2Page.test.tsx components/pages/LoginPage.test.tsx`: passed, 2 files / 25 tests after adding PFM top-level page tests to strict-scope.
- `npm.cmd run test:run -- lib/api/legacyAdapters.test.ts` and `npx.cmd eslint lib/api/legacyAdapters.test.ts lib/api/legacyAiChat.ts lib/api/legacySimulation.ts`: passed after adding legacy adapter boundary tests and helpers to strict-scope.
- `npm.cmd run test:run`: passed, 41 files / 232 tests after the scoped strict test expansion.
- `npm.cmd run build`: passed with temporary local public Supabase env values.
- Local Playwright route smoke is now scripted via `scripts/check-local-route-smoke.mjs`; it covers `/`, `/pfm_chat/login`, `/simulation2`, `/cmsl20043`, `/cmsl20043?page=abc&size=999`, `/introduction`, `/people/professor`, `/research/pfm`, and `/viewer`, with uncaught page-error detection, expected sandbox resource classifier self-test, expected sandbox resource classification, and unexpected console/resource failure.
- `scripts/check-w12-guards.mjs` / `npm.cmd run test:w12-guards` now runs the local W12 guard bundle in sequence, including the change-group checklist, git-add command manifest, JSON manifest, diff hygiene, and strict-scope coverage guards.
- `npm.cmd run lint`: still fails with 72 errors / 40 warnings.
