# W12 strict option measurement (latest refresh: 2026-06-13)

> Scope: RF-TASK-087 / RF-TASK-088 / RF-FINDING-038.
> This records the global strict/unused measurement and the expanded scoped strict gate.
> Root `tsconfig.json` settings were not changed.

## Current TypeScript Settings

| Setting | Current value |
|---|---|
| `allowJs` | `true` |
| `strict` | `false` |
| `noImplicitAny` | `false` |
| `strictNullChecks` | `false` |
| `noUnusedLocals` | `false` |
| `noUnusedParameters` | `false` |

## Measurement Method

All checks were run from `C:\pfm-FE` with `npx.cmd tsc --noEmit --pretty false --incremental false` plus temporary CLI overrides.

The command line overrides were used only to measure diagnostics. The repository config remains unchanged.

## Results

| Scenario | Temporary override | Exit | Diagnostics | Top error codes |
|---|---|---:|---:|---|
| Baseline | none | 0 | 0 | none |
| Unused locals | `--noUnusedLocals true` | 2 | 17 | `TS6133:17` |
| Unused parameters | `--noUnusedParameters true` | 2 | 3 | `TS6133:3` |
| Unused pair | `--noUnusedLocals true --noUnusedParameters true` | 2 | 20 | `TS6133:20` |
| no implicit any | `--noImplicitAny true` | 2 | 22 | `TS7018:7`, `TS7011:5`, `TS7053:4`, `TS7010:2`, `TS7016:2`, `TS7005:1`, `TS2339:1` |
| strict null checks | `--strictNullChecks true` | 2 | 16 | `TS2322:7`, `TS2345:5`, `TS18047:2`, `TS2769:1`, `TS18048:1` |
| strict only | `--strict true` | 0 | 0 | none |
| effective strict family | `--strict true --noImplicitAny true --strictNullChecks true` | 2 | 18 | `TS2322:6`, `TS7053:4`, `TS7016:2`, `TS18047:2`, `TS18048:1`, `TS2769:1`, `TS2339:1`, `TS2345:1` |
| effective strict + unused | `--strict true --noImplicitAny true --strictNullChecks true --noUnusedLocals true --noUnusedParameters true` | 2 | 38 | `TS6133:20`, `TS2322:6`, `TS7053:4`, `TS7016:2`, `TS18047:2`, `TS2769:1`, `TS18048:1`, `TS2345:1`, `TS2339:1` |
| TypeScript only include check | `--allowJs false` | 0 | 0 | none |

## Delta From 2026-06-12 Measurement

| Scenario | 2026-06-12 | 2026-06-13 | Delta |
|---|---:|---:|---:|
| Baseline | 0 | 0 | 0 |
| Unused locals | 26 | 17 | -9 |
| Unused parameters | 9 | 3 | -6 |
| Unused pair | 35 | 20 | -15 |
| no implicit any | 21 | 22 | +1 |
| strict null checks | 15 | 16 | +1 |
| strict only | 0 | 0 | 0 |
| effective strict family | 16 | 18 | +2 |
| effective strict + unused | 51 | 38 | -13 |
| TypeScript only include check | 0 | 0 | 0 |

## Interpretation

- Baseline `npx tsc --noEmit` remains clean, matching `w0-execution-log.md`.
- `--strict true` alone reports 0 diagnostics because `tsconfig.json` explicitly sets `noImplicitAny: false` and `strictNullChecks: false`; the effective strict-risk measurement is the `strict + explicit suboptions` scenario.
- RF-TASK-083 cleanup reduced the unused-pair measurement from 35 to 20 diagnostics, but enabling `noUnusedLocals` and `noUnusedParameters` globally would still fail.
- The effective strict family increased from 16 to 18 diagnostics after `apiRequest` began defaulting to `unknown`; this is acceptable as a measurement result, not a regression in runtime behavior.
- `allowJs false` is not a safety improvement by itself; it merely excludes JavaScript from the TypeScript program and reports 0 diagnostics. RF-TASK-083 now applies a narrower JS route policy by including `api/**/*.js` and requiring `// @ts-check` on `api/chat.js`; broader JS/unused policy remains RF-TASK-083/RF-TASK-088 follow-up work.

## RF-TASK-088 Input

Do not enable global strict or unused settings in one step.

## RF-TASK-088 Scoped Gate Applied

`tsconfig.strict-scope.json` and `npm run test:strict-scope` were added as the first scoped strict/unused gate. The scope was then expanded to selected Simulation2 workflow, lifecycle, presenter/list/session/result files, ResultExplorer files, AdminPage3 container/presenter/helper files, PFM login/reset-password and AI assistant surfaces, PFM API adapter/client files, focused tests, common error/visual UI/hooks, app chrome/root shell, Footer/language/scroll/idle helpers, introduction visual sections, config/type files, the viewer route/page, UI primitives, the server Supabase SSR helper, the approval-gated diff script, the lint carry-forward script, the refactoring change group script, the strict-scope coverage script, the W12 aggregate guard script, and the W12 full regression script without changing root `tsconfig.json`.

The reset-password page inclusion is typecheck-only. It does not implement the RF-TASK-006 env helper usage replacement, and `lib/supabaseClient.ts` behavior remains unchanged behind the Contact/CMS env approval gate.

Latest scoped expansion note: unused React imports were removed from small non-gated wrapper, popup dialog, and visual components, `SmoothScroll` now removes the same GSAP ticker callback it registers, and `useIdleTimer` uses a strict-safe nullable timeout ref. Supabase-backed research template paths were removed from the scoped strict program after the W12 gate-correction audit confirmed they are CMS/public-content follow-up work.

Current scoped target set:

- `app/cmsl20043/page.tsx`
- `app/layout.tsx`
- `app/not-found.tsx`
- `app/pfm_chat/login/page.tsx`
- `app/providers.tsx`
- `app/simulation2/page.tsx`
- `app/viewer/page.tsx`
- `api/chat.js`
- `api/chat.test.ts`
- `scripts/check-circular-imports.mjs`
- `scripts/check-local-route-smoke.mjs`
- `scripts/check-pfm-api-boundaries.mjs`
- `lib/api/admin.ts`
- `lib/api/admin.test.ts`
- `lib/api/chatSessions.ts`
- `lib/api/chatSessions.test.ts`
- `lib/api/errors.ts`
- `lib/api/errors.test.ts`
- `lib/api/http.ts`
- `lib/api/http.test.ts`
- `lib/api/jobs.ts`
- `lib/api/jobs.test.ts`
- `lib/api/labserver.ts`
- `lib/api/labserver.test.ts`
- `lib/api/labserverErrors.ts`
- `lib/api/labserverErrors.test.ts`
- `lib/api/labserverTrameClient.ts`
- `lib/api/labserverTrameClient.test.ts`
- `lib/api/legacyAdapters.test.ts`
- `lib/api/legacyAiChat.ts`
- `lib/api/legacySimulation.ts`
- `lib/api/results.ts`
- `lib/api/results.test.ts`
- `lib/api/sharedTypes.ts`
- `lib/api/simulations.ts`
- `lib/api/simulations.test.ts`
- `lib/api/visualizations.ts`
- `lib/api/visualizations.test.ts`
- `lib/apiClient.ts`
- `lib/apiClient.test.ts`
- `lib/auth.ts`
- `lib/auth.test.ts`
- `lib/authTokenStorage.ts`
- `lib/authTokenStorage.test.ts`
- `lib/utils.ts`
- `lib/utils.test.ts`
- `utils/supabase/server.ts`
- `docs/labserver-trame-paraview/ReactIntegrationExample.tsx`
- `docs/labserver-trame-paraview/labserver-trame-client.ts`
- `components/pages/adminAccountRequestsPanel.tsx`
- `components/pages/adminConstants.ts`
- `components/pages/AdminPage3.tsx`
- `components/pages/adminFormatters.ts`
- `components/pages/adminFormatters.test.ts`
- `components/pages/adminGuardPresenters.tsx`
- `components/pages/adminGuardPresenters.test.tsx`
- `components/pages/adminJobMutations.ts`
- `components/pages/adminOverviewPanel.tsx`
- `components/pages/adminPanels.test.tsx`
- `components/pages/adminSharedPresenters.tsx`
- `components/pages/adminSimulationDetailPanel.tsx`
- `components/pages/adminSimulationJobsPanel.tsx`
- `components/pages/adminSimulationResultsPanel.tsx`
- `components/pages/adminSimulationsPanel.tsx`
- `components/pages/adminPolling.ts`
- `components/pages/adminPolling.test.ts`
- `components/pages/adminUrlState.ts`
- `components/pages/adminUrlState.test.tsx`
- `components/pages/adminUsersPanel.tsx`
- `components/pages/LoginPage.test.tsx`
- `components/pages/LoginPage.tsx`
- `components/pages/ResetPasswordPage.tsx`
- `components/pages/Simulation2Page.test.tsx`
- `components/pages/Simulation2Page.tsx`
- `components/pages/simulation2/ChatPanel.tsx`
- `components/pages/simulation2/GeneratedInputFileCard.tsx`
- `components/pages/simulation2/jobMonitorSession.ts`
- `components/pages/simulation2/jobMonitorSession.test.ts`
- `components/pages/simulation2/ParameterPanel.tsx`
- `components/pages/simulation2/pfmAuthGate.tsx`
- `components/pages/simulation2/pfmAuthGate.test.tsx`
- `components/pages/simulation2/ResultWorkspace.tsx`
- `components/pages/simulation2/simulationParameterMappers.ts`
- `components/pages/simulation2/simulationParameterMappers.test.ts`
- `components/pages/simulation2/useJobMonitorSession.ts`
- `components/pages/simulation2/useVisualizationSession.ts`
- `components/pages/simulation2/workflowHelpers.ts`
- `components/pages/simulation2/workflowHelpers.test.ts`
- `components/pages/simulation2/workflowMappers.ts`
- `components/pages/simulation2/workflowMappers.test.ts`
- `components/pages/simulation2/workflowTypes.ts`
- `components/pages/introduction/ScrollyEvents.tsx`
- `components/pages/introduction/Section1_Intro.tsx`
- `components/pages/introduction/Section2_CoreCapabilites.tsx`
- `components/pages/introduction/Section3_ResearchAreas.tsx`
- `components/pages/introduction/Section4_Demo.tsx`
- `components/pages/introduction/Section5_Impact.tsx`
- `components/simulation/ResultExplorerPanel.tsx`
- `components/simulation/ResultExplorerPanel.test.tsx`
- `components/simulation/JobResultListCard.tsx`
- `components/simulation/JobResultListCard.test.tsx`
- `components/simulation/BugReportButton.tsx`
- `components/simulation/MarkdownMessage.tsx`
- `components/simulation/SessionListCard.tsx`
- `components/simulation/SessionListCard.test.tsx`
- `components/simulation/SessionListView.tsx`
- `components/simulation/SimulationListCard.tsx`
- `components/simulation/SimulationListCard.test.tsx`
- `components/simulation/simulationListConstants.ts`
- `components/simulation/useChatSessions.ts`
- `components/simulation/useResultExplorerData.ts`
- `components/simulation/useSimulationJobResults.ts`
- `components/simulation/useSimulationList.ts`
- `components/simulation/VisualizationControlBar.tsx`
- `components/simulation/VisualizationControlBar.test.tsx`
- `components/simulation/visualizationConstants.ts`
- `components/simulation/WorkspaceTabsCard.tsx`
- `components/simulation/trame/AdvancedTramePanel.tsx`
- `components/simulation/trame/CompositeDialog.tsx`
- `components/simulation/trame/TrameExportCenter.tsx`
- `components/simulation/trame/TrameControlPanel.tsx`
- `components/simulation/trame/TrameViewer.tsx`
- `components/simulation/trame/trameReviewFixes.test.ts`
- `components/common/ApiErrorDetailsPanel.tsx`
- `components/common/ApiErrorDetailsPanel.test.tsx`
- `components/common/ApiErrorNotice.tsx`
- `components/AIChatAssistant.tsx`
- `components/Footer.tsx`
- `components/Header.tsx`
- `components/ImageCarousel.tsx`
- `components/ImageCarousel.test.ts`
- `components/LanguageProvider.tsx`
- `components/LanguageSwitcher.tsx`
- `components/LanguageToggle.tsx`
- `components/MemberDetailModal.tsx`
- `components/MemberDetailModal.test.tsx`
- `components/MobileNavigation.tsx`
- `components/Navigation.tsx`
- `components/pages/VtiViewerPage.tsx`
- `components/ProjectCard.tsx`
- `components/ResearchHighlightsSlider.tsx`
- `components/ResearchHighlightsSlider.test.tsx`
- `components/ScrollToTop.tsx`
- `components/ScrollToTopButton.tsx`
- `components/SinglePopupDialog.tsx`
- `components/ScrollAnimation.tsx`
- `components/ScrollingFocusSection.tsx`
- `components/SmoothScroll.tsx`
- `components/VtkViewer.tsx`
- `components/SvgImageMorph.tsx`
- `components/reactbits/BlurText.tsx`
- `components/reactbits/ColorBends.tsx`
- `components/reactbits/GradientText.tsx`
- `components/reactbits/InfiniteMenu.tsx`
- `components/reactbits/LogoLoop.tsx`
- `components/reactbits/SpotlightCard.tsx`
- `components/reactbits/TiltedCard.tsx`
- `components/ui/CipherImage.tsx`
- `components/ui/ScrollyText_UI.tsx`
- `components/ui/accordion.tsx`
- `components/ui/alert.tsx`
- `components/ui/alert-dialog.tsx`
- `components/ui/aspect-ratio.tsx`
- `components/ui/avatar.tsx`
- `components/ui/badge.tsx`
- `components/ui/breadcrumb.tsx`
- `components/ui/button.tsx`
- `components/ui/calendar.tsx`
- `components/ui/card.tsx`
- `components/ui/carousel.tsx`
- `components/ui/chart.tsx`
- `components/ui/checkbox.tsx`
- `components/ui/collapsible.tsx`
- `components/ui/command.tsx`
- `components/ui/context-menu.tsx`
- `components/ui/dialog.tsx`
- `components/ui/drawer.tsx`
- `components/ui/dropdown-menu.tsx`
- `components/ui/form.tsx`
- `components/ui/hover-card.tsx`
- `components/ui/input-otp.tsx`
- `components/ui/input.tsx`
- `components/ui/label.tsx`
- `components/ui/MagneticButton.tsx`
- `components/ui/menubar.tsx`
- `components/ui/navigation-menu.tsx`
- `components/ui/pagination.tsx`
- `components/ui/popover.tsx`
- `components/ui/progress.tsx`
- `components/ui/radio-group.tsx`
- `components/ui/resizable.tsx`
- `components/ui/scroll-area.tsx`
- `components/ui/select.tsx`
- `components/ui/separator.tsx`
- `components/ui/sheet.tsx`
- `components/ui/sidebar.tsx`
- `components/ui/skeleton.tsx`
- `components/ui/slider.tsx`
- `components/ui/sonner.tsx`
- `components/ui/SpotlightCard.tsx`
- `components/ui/switch.tsx`
- `components/ui/tabs.tsx`
- `components/ui/table.tsx`
- `components/ui/TechText.tsx`
- `components/ui/textarea.tsx`
- `components/ui/tiptap-editor.tsx`
- `components/ui/toast.tsx`
- `components/ui/toaster.tsx`
- `components/ui/toggle-group.tsx`
- `components/ui/toggle.tsx`
- `components/ui/tooltip.tsx`
- `components/ui/use-toast.ts`
- `hooks/use-mobile.ts`
- `hooks/use-mobile.test.tsx`
- `hooks/use-toast.ts`
- `hooks/use-toast.test.tsx`
- `hooks/useIdleTimer.ts`
- `components/mediaItemKeys.ts`
- `declarations.d.ts`
- `next.config.ts`
- `tailwind.config.ts`
- `types.ts`
- `vitest.config.ts`
- `vitest.setup.ts`

Latest result:

| Check | Result |
|---|---|
| `npm.cmd run test:strict-scope` | passed, scoped program includes 231 repo files / 41 test files |
| `npm.cmd run test:strict-scope-coverage` | passed, 141 changed non-gated code/config paths are present in the scoped strict TypeScript program; approval-gated paths stay excluded |
| targeted eslint for latest strict-scope expansion | passed for `LanguageToggle`, `ScrollToTopButton`, `SmoothScroll`, `useIdleTimer`, `VtiViewerPage`, Footer/language/scroll helpers, `BugReportButton`, and config files |
| targeted eslint for visual/top-level strict-scope expansion | passed for remaining reactbits visual helpers, introduction section components, `SvgImageMorph`, `AIChatAssistant`, `LoginPage`, `AdminPage3`, and `Simulation2Page` |
| targeted eslint for popup dialog strict-scope expansion | passed for `SinglePopupDialog`; HTML render behavior and sanitizer policy were not changed |
| `npm.cmd run test:run -- components/pages/Simulation2Page.test.tsx components/simulation/ResultExplorerPanel.test.tsx` | passed, 2 files / 25 tests after strict-scope presenter narrowing |
| `npm.cmd run test:run -- components/simulation/JobResultListCard.test.tsx components/simulation/SessionListCard.test.tsx components/simulation/SimulationListCard.test.tsx components/simulation/ResultExplorerPanel.test.tsx components/pages/Simulation2Page.test.tsx` | passed, 5 files / 54 tests after adding PFM list/session/result cards and hooks to strict-scope |
| `npm.cmd run test:run -- components/pages/adminFormatters.test.ts components/pages/adminUrlState.test.tsx components/pages/adminGuardPresenters.test.tsx components/pages/adminPanels.test.tsx lib/api/admin.test.ts` | passed, 5 files / 36 tests after adding Admin presenter/mutation helpers to strict-scope |
| `npm.cmd run test:run -- lib/api/admin.test.ts lib/api/chatSessions.test.ts lib/api/jobs.test.ts lib/api/labserver.test.ts lib/api/labserverErrors.test.ts lib/api/labserverTrameClient.test.ts lib/api/results.test.ts lib/api/simulations.test.ts lib/api/visualizations.test.ts lib/auth.test.ts` | passed, 10 files / 66 tests after adding PFM API adapter/auth helpers to strict-scope |
| `npm.cmd run test:run -- lib/apiClient.test.ts lib/authTokenStorage.test.ts lib/auth.test.ts` | passed, 3 files / 18 tests after adding `lib/apiClient.ts` to strict-scope |
| `npm.cmd run test:run -- components/common/ApiErrorDetailsPanel.test.tsx hooks/use-mobile.test.tsx hooks/use-toast.test.tsx components/MemberDetailModal.test.tsx` | passed, 4 files / 8 tests after adding common error UI, mobile/toast hooks, and `MemberDetailModal` to strict-scope |
| `npm.cmd run test:run -- components/ImageCarousel.test.ts components/ResearchHighlightsSlider.test.tsx` | passed, 2 files / 3 tests after adding non-gated visual UI components to strict-scope |
| `npx.cmd eslint components/ProjectCard.tsx components/ScrollAnimation.tsx components/ScrollingFocusSection.tsx` | passed after adding additional non-gated visual UI components to strict-scope |
| `npx.cmd eslint components/Header.tsx components/Navigation.tsx components/MobileNavigation.tsx components/LanguageProvider.tsx components/ui/button.tsx components/ui/badge.tsx components/ui/card.tsx components/ui/alert.tsx components/ui/dialog.tsx` | passed after explicitly adding app chrome and UI primitives to strict-scope |
| `npx.cmd eslint components/ui/textarea.tsx components/ui/command.tsx components/ui/calendar.tsx components/ui/chart.tsx components/ui/CipherImage.tsx components/ui/ScrollyText_UI.tsx` | passed after adding additional UI primitives/display helpers to strict-scope |
| `npx.cmd eslint components/ui/input.tsx components/ui/label.tsx components/ui/separator.tsx components/ui/skeleton.tsx components/ui/progress.tsx components/ui/switch.tsx components/ui/tabs.tsx components/ui/tooltip.tsx components/ui/avatar.tsx` | passed after adding base Radix/shadcn UI primitives to strict-scope |
| `npx.cmd eslint components/ui/accordion.tsx components/ui/alert-dialog.tsx components/ui/aspect-ratio.tsx components/ui/breadcrumb.tsx components/ui/carousel.tsx components/ui/checkbox.tsx components/ui/collapsible.tsx components/ui/dropdown-menu.tsx components/ui/hover-card.tsx components/ui/popover.tsx components/ui/radio-group.tsx components/ui/scroll-area.tsx components/ui/select.tsx components/ui/sheet.tsx components/ui/slider.tsx components/ui/table.tsx` | passed after adding additional Radix/shadcn overlay/navigation/table primitives to strict-scope |
| `npx.cmd eslint components/ui/context-menu.tsx components/ui/drawer.tsx components/ui/form.tsx components/ui/input-otp.tsx components/ui/menubar.tsx components/ui/navigation-menu.tsx components/ui/pagination.tsx components/ui/resizable.tsx components/ui/sonner.tsx components/ui/toast.tsx components/ui/toaster.tsx components/ui/toggle.tsx components/ui/toggle-group.tsx components/ui/use-toast.ts components/ui/MagneticButton.tsx components/ui/SpotlightCard.tsx components/ui/TechText.tsx` | passed after adding remaining non-editor UI primitives/display helpers to strict-scope |
| `npx.cmd eslint components/ui/sidebar.tsx components/reactbits/ColorBends.tsx components/reactbits/InfiniteMenu.tsx components/reactbits/LogoLoop.tsx` | passed after adding sidebar and non-gated reactbits visual helpers to strict-scope |
| `npx.cmd eslint components/VtkViewer.tsx` | passed after adding the PFM VTK viewer helper to strict-scope |
| `npx.cmd eslint components/simulation/trame/CompositeDialog.tsx components/simulation/trame/TrameControlPanel.tsx` | passed after adding PFM trame control/composite helpers to strict-scope |
| `npx.cmd eslint app/simulation2/page.tsx app/pfm_chat/login/page.tsx app/cmsl20043/page.tsx` | passed after adding PFM/Admin route wrappers to strict-scope |
| `npm.cmd run test:run -- api/chat.test.ts` and `npx.cmd eslint api/chat.js` | passed after adding the checked JS chat route to strict-scope |
| `npm.cmd run test:run -- components/simulation/trame/trameReviewFixes.test.ts` and `npx.cmd eslint components/simulation/trame/AdvancedTramePanel.tsx components/simulation/trame/TrameExportCenter.tsx components/simulation/trame/TrameViewer.tsx docs/labserver-trame-paraview/ReactIntegrationExample.tsx docs/labserver-trame-paraview/labserver-trame-client.ts` | passed after adding the remaining PFM trame panels and labserver integration examples to strict-scope |
| `npx.cmd tsc --project tsconfig.strict-scope.json --listFilesOnly` | confirmed 231 repo files and 41 `.test.*` files are included in the scoped strict program; Supabase-backed research template paths remain out with CMS/public content shape follow-up |
| `npm.cmd run test:run -- hooks/use-mobile.test.tsx hooks/use-toast.test.tsx lib/utils.test.ts lib/authTokenStorage.test.ts lib/apiClient.test.ts components/common/ApiErrorDetailsPanel.test.tsx components/MemberDetailModal.test.tsx components/ImageCarousel.test.ts components/ResearchHighlightsSlider.test.tsx lib/auth.test.ts` | passed, 10 files / 33 tests after adding common hook/UI/auth/apiClient tests to strict-scope |
| `npm.cmd run test:run -- lib/api/admin.test.ts lib/api/chatSessions.test.ts lib/api/errors.test.ts lib/api/http.test.ts lib/api/jobs.test.ts lib/api/labserver.test.ts lib/api/labserverErrors.test.ts lib/api/labserverTrameClient.test.ts lib/api/results.test.ts lib/api/simulations.test.ts lib/api/visualizations.test.ts` | passed, 11 files / 73 tests after adding PFM API adapter tests to strict-scope |
| `npm.cmd run test:run -- components/pages/adminFormatters.test.ts components/pages/adminGuardPresenters.test.tsx components/pages/adminPanels.test.tsx components/pages/adminPolling.test.ts components/pages/adminUrlState.test.tsx components/pages/simulation2/jobMonitorSession.test.ts components/pages/simulation2/pfmAuthGate.test.tsx components/pages/simulation2/simulationParameterMappers.test.ts components/pages/simulation2/workflowHelpers.test.ts components/pages/simulation2/workflowMappers.test.ts components/simulation/JobResultListCard.test.tsx components/simulation/ResultExplorerPanel.test.tsx components/simulation/SessionListCard.test.tsx components/simulation/SimulationListCard.test.tsx components/simulation/VisualizationControlBar.test.tsx` | passed, 15 files / 86 tests after adding Admin/Simulation helper and presenter tests to strict-scope |
| `npm.cmd run test:run -- components/pages/Simulation2Page.test.tsx components/pages/LoginPage.test.tsx` | passed, 2 files / 25 tests after adding PFM top-level page tests to strict-scope |
| `npm.cmd run test:run -- lib/api/legacyAdapters.test.ts` and `npx.cmd eslint lib/api/legacyAdapters.test.ts lib/api/legacyAiChat.ts lib/api/legacySimulation.ts` | passed after adding legacy adapter boundary tests and helpers to strict-scope |
| `npm.cmd run test:run` | passed, 41 files / 232 tests after the scoped strict test expansion |

This is a staged application of RF-TASK-088. Global `strict`, `noImplicitAny`, `strictNullChecks`, `noUnusedLocals`, and `noUnusedParameters` remain unchanged because the global diagnostic counts above are still too high.

Suggested follow-up order:

1. Continue fixing or scoping `noUnusedLocals`/`noUnusedParameters` diagnostics in new/refactored areas first. The remaining unused-pair count is 20.
2. Keep JavaScript routes either migrated to TypeScript or explicitly checked with `// @ts-check` plus JSDoc; `api/chat.js` is the first applied example.
3. Add missing declarations or narrow wrappers for implicit external modules and unknown response boundaries before `noImplicitAny`.
4. Address strict null paths around legacy admin/CMS pages, Simulation2 restored data, and unknown-to-rendering paths before `strictNullChecks`.
5. Re-run this measurement after each batch and only then consider a small tsconfig change.
