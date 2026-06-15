# JS route type-check policy (2026-06-12)

> Scope: RF-TASK-083 partial implementation for legacy JavaScript API routes and lint CLI policy.

## Policy

- JavaScript API routes that remain `.js` must opt into `// @ts-check`.
- `tsconfig.json` includes `api/**/*.js` so opted-in JavaScript routes are checked by `npx tsc --noEmit`.
- Boundary helpers in JavaScript routes should use JSDoc for request/response and payload shapes until the route is migrated to TypeScript.
- Request validation must happen before upstream clients are called.
- Successful legacy response wire shapes must remain unchanged unless the corresponding API contract is explicitly changed.

## Current Application

- `api/chat.js` remains JavaScript.
- `api/chat.js` now uses `// @ts-check` and JSDoc for the handler and error/validation helpers.
- `getValidMessage` narrows `req.body` from `unknown` before reading `message`.
- `tsconfig.json` includes `api/**/*.js`, making this policy part of the standard `npx tsc --noEmit` check.
- New or touched UI type surfaces should reduce lint debt locally without enabling global unused/strict rules. In this pass, `AIChatAssistant` now accepts assistant params as `unknown`, `CommandDialogProps` / `TextareaProps` use type aliases instead of empty interfaces, `ScrollyText_UI` declares its item shape, and `VtkViewer` uses a typed VTK context plus a narrow VTK.js declaration supplement instead of `any` / `@ts-ignore`.
- Public introduction section helpers now type their local view props without changing the Supabase-backed `IntroductionPage` read shape. `InfiniteMenu` now types `description` as `ReactNode`, matching its existing runtime rendering behavior.
- `LogoLoop` now names its node/image item union and uses a type guard instead of render-time `as any` casts. A follow-up pass made its resize/image/animation hooks statically checkable by `react-hooks/exhaustive-deps`; the native `<img>` remains intentionally local-disabled because this generic logo scroller preserves caller-provided `srcSet` / `sizes`.
- Public/auth page lint cleanup removed an unused reset-password helper while preserving the active verification flow in `ResetPasswordPage`. Earlier `AlumniPage` / `ProfessorPage` cleanup was reverted because those pages are Supabase-backed public CMS surfaces and remain approval-gated.
- `npm run lint` now uses the ESLint CLI (`eslint .`) instead of deprecated `next lint`. The flat config ignores generated/build output (`.next`, `coverage`, `node_modules`, `out`, `next-env.d.ts`) and keeps the existing Next `core-web-vitals` / TypeScript rule set.
- CLI-scope setup cleanup removed lint-only errors from `eslint.config.mjs`, `tailwind.config.ts`, `vitest.setup.ts`, and `docs/labserver-trame-paraview/labserver-trame-client.ts` without changing app runtime behavior.
- Additional non-gated unused cleanup removed local lint warnings from `LanguageProvider`, `MobileNavigation`/`Header`, `MarkdownMessage`, `CipherImage`, `calendar`, `chart`, `utils/supabase/server`, `ScrollAnimation`, and leaf introduction section helpers without touching CMS/board mutation flows.
- The Labserver Trame docs integration example now satisfies lint locally by stabilizing `refreshViewState` with `useCallback`, adding explicit `tab` roles for viewer-mode buttons, and documenting the blob preview image as an intentional plain `<img>` in the docs sample.
- `apiRequest` now defaults its generic to `unknown` instead of `any`. Production call sites already pass explicit response types, so this only makes untyped test or future calls narrow the value before use.
- A follow-up non-gated image cleanup converted `ProjectCard`, `MemberDetailModal`, `ScrollingFocusSection`, and `ImageCarousel` image rendering to `next/image`. The attempted `ResearchPageTemplate` representative image/effect cleanup was reverted in W12 because it is a Supabase-backed public CMS surface.
- `components/ui/tiptap-editor.tsx` now uses TipTap's `Editor` type for the menu/editor image-upload boundary instead of `any`. CMS editor screens still own their upload/storage behavior; this pass only narrows the shared UI component contract.
- Reactbits follow-up cleanup removed `ColorBends` / `LogoLoop` hook dependency warnings while preserving WebGL runtime updates and LogoLoop's caller-provided image attributes. A later reactbits pass moved `InfiniteMenu` / `LogoLoop` arbitrary transition easing/duration values from Tailwind arbitrary classes to inline transition styles, removing the repeated build-time ambiguity warnings without changing visual timing.

## Explicit Non-Changes

- `api/chat.js` was not converted to TypeScript in this pass.
- The Gemini success payload remains unchanged.
- Global `noUnusedLocals`/`noUnusedParameters` were not enabled. The 2026-06-13 RF-TASK-087 remeasurement reports 20 diagnostics for the unused pair; RF-TASK-088 now starts staged cleanup with `tsconfig.strict-scope.json` and `npm run test:strict-scope`.
- `npm run lint` still fails on pre-existing repo-wide lint debt in CMS/legacy/general UI files. This pass does not hide those errors with broad ESLint disables.
- The touched UI type-surface cleanup does not change runtime behavior, component public rendering, or VTK viewer calls.
- The Tailwind plugin set, Vitest mock behavior, and labserver docs client timing behavior remain unchanged; only import/type/lint shapes were adjusted for the ESLint CLI scope.
- The latest non-gated cleanup preserves mobile navigation behavior, markdown rendering, chart CSS variable generation, Supabase server client behavior, and scroll animation timing.
- The docs integration example still uses the same Labserver client calls, viewer mode toggles, export polling, and blob preview behavior.
- `apiRequest` parsing, error handling, 204 handling, token refresh, retry, and public wire shapes are unchanged.

## Verification

| Check | Result |
|---|---|
| `npx tsc --noEmit` | passed |
| `npx eslint api/chat.js api/chat.test.ts lib/api/legacyAiChat.ts lib/api/legacyAdapters.test.ts` | passed |
| `npm run test:run -- api/chat.test.ts lib/api/legacyAdapters.test.ts` | passed, 2 files / 10 tests |
| `npm run test:run` | passed, 42 files / 233 tests |
| `npm run test:boundaries` | passed |
| `npm run build` | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `npx eslint components/AIChatAssistant.tsx components/ui/command.tsx components/ui/textarea.tsx` | passed |
| `npx tsc --noEmit` after targeted UI type-surface cleanup | passed |
| `npx eslint components/ui/ScrollyText_UI.tsx components/VtkViewer.tsx` | passed |
| `npx tsc --noEmit` after Scrolly/VtkViewer cleanup | passed |
| `npx eslint components/pages/introduction/Section2_CoreCapabilites.tsx components/pages/introduction/Section3_ResearchAreas.tsx components/pages/introduction/Section4_Demo.tsx components/reactbits/InfiniteMenu.tsx` | passed |
| `npx tsc --noEmit` after introduction public UI cleanup | passed |
| `npx eslint components/reactbits/LogoLoop.tsx` | passed with existing hook dependency and `<img>` warnings only |
| `npx tsc --noEmit` after LogoLoop explicit-any cleanup | passed |
| `npx eslint components/pages/ResetPasswordPage.tsx` | passed |
| `npx tsc --noEmit` after auth small lint cleanup | passed |
| `npx eslint eslint.config.mjs tailwind.config.ts vitest.setup.ts docs/labserver-trame-paraview/labserver-trame-client.ts` | passed |
| `npm run lint` after ESLint CLI migration | fails with 68 errors / 69 warnings from existing repo-wide debt; no `.next` or `next-env.d.ts` generated-file errors |
| `npx tsc --noEmit` after ESLint CLI migration | passed |
| `npm run test:run` after ESLint CLI migration | passed, 42 files / 233 tests |
| `npm run test:boundaries` after ESLint CLI migration | passed |
| `npm run build` after ESLint CLI migration | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `npx eslint components/LanguageProvider.tsx components/MobileNavigation.tsx components/Header.tsx components/simulation/MarkdownMessage.tsx components/ui/CipherImage.tsx components/ui/calendar.tsx components/ui/chart.tsx utils/supabase/server.ts components/pages/introduction/ScrollyEvents.tsx components/pages/introduction/Section5_Impact.tsx components/ScrollAnimation.tsx` | passed |
| `npx tsc --noEmit` after non-gated unused cleanup | passed |
| `npm run test:run` after non-gated unused cleanup | passed, 42 files / 233 tests |
| `npm run test:boundaries` after non-gated unused cleanup | passed |
| `npm run build` after non-gated unused cleanup | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `npx eslint docs/labserver-trame-paraview/ReactIntegrationExample.tsx` | passed |
| `npx tsc --noEmit` after docs integration example cleanup | passed |
| `npm run test:boundaries` after docs integration example cleanup | passed |
| `git diff --check` after docs integration example cleanup | passed with line-ending warnings only |
| `npm run lint` after non-gated unused/docs cleanup | fails with 68 errors / 50 warnings from existing repo-wide debt |
| `npx eslint lib/apiClient.ts lib/apiClient.test.ts lib/auth.ts lib/api/jobs.ts lib/api/simulations.ts lib/api/admin.ts lib/api/chatSessions.ts lib/api/results.ts lib/api/visualizations.ts` | passed |
| `npx tsc --noEmit` after `apiRequest<T = unknown>` | passed |
| `npm run test:run -- lib/apiClient.test.ts lib/auth.test.ts lib/api/admin.test.ts lib/api/jobs.test.ts lib/api/simulations.test.ts lib/api/chatSessions.test.ts lib/api/results.test.ts lib/api/visualizations.test.ts` | passed, 8 files / 53 tests |
| `npm run test:run` after `apiRequest<T = unknown>` | passed, 42 files / 233 tests |
| `npm run test:boundaries` after `apiRequest<T = unknown>` | passed |
| `npm run build` after `apiRequest<T = unknown>` | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `npm run lint` after `apiRequest<T = unknown>` | fails with 67 errors / 50 warnings from existing repo-wide debt |
| `npx eslint components/ProjectCard.tsx components/MemberDetailModal.tsx` | passed |
| `npx eslint components/ScrollingFocusSection.tsx` | passed |
| `npx eslint components/reactbits/LogoLoop.tsx components/reactbits/ColorBends.tsx` | passed |
| `npm run test:run -- components/MemberDetailModal.test.tsx` | passed, 1 file / 3 tests |
| `npx eslint components/ImageCarousel.tsx` | passed |
| `npm run test:run -- components/ImageCarousel.test.ts` | passed, 1 file / 1 test |
| `npx eslint components/ui/tiptap-editor.tsx` | passed |
| `npx tsc --noEmit` after tiptap editor type cleanup | passed |
| `npx eslint components/reactbits/InfiniteMenu.tsx components/reactbits/LogoLoop.tsx` after Tailwind transition cleanup | passed |
| `npx tsc --noEmit` after Tailwind transition cleanup | passed on retry after the parallel build race regenerated `.next/types` |
| `npx tsc --noEmit` after non-gated image cleanup | passed |
| `npm run test:run` after non-gated image cleanup | passed, 42 files / 233 tests |
| `npm run test:boundaries` after non-gated image cleanup | passed |
| `npm run build` after non-gated image cleanup | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `npm run build` after reactbits Tailwind transition cleanup | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; Tailwind arbitrary class ambiguity warnings were no longer emitted |
| `npm run lint` after tiptap editor type cleanup | failed with 65 errors / 37 warnings from then-current existing repo-wide debt; W12 gate correction later re-exposed Supabase-backed public CMS lint and the current count is 72 errors / 40 warnings |
| `npm run test:run` after RF-TASK-083 targeted lint cleanup | passed, 42 files / 233 tests |
| `npm run test:boundaries` after RF-TASK-083 targeted lint cleanup | passed |
| `npm run build` after RF-TASK-083 targeted lint cleanup | passed with temporary local `NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co` and `NEXT_PUBLIC_SUPABASE_ANON_KEY=test-anon-key`; existing Tailwind arbitrary class ambiguity warnings remain |
| `git diff --check` after RF-TASK-083 targeted lint cleanup | passed with line-ending warnings only |
| `npm run lint` | still fails on existing repo-wide lint debt outside these targeted slices; see RF-TASK-083 remaining work |

## Remaining RF-TASK-083 Work

- Decide whether `api/chat.js` should eventually become a TypeScript route or remain JS with `@ts-check`.
- Do not enable global unused/strict rules until RF-TASK-088 expands the scoped strict gate enough to make the global diagnostic counts safe.
- Continue reducing touched-file lint debt in small, non-gated slices; CMS/board-sensitive files remain approval-gated.
- Current remaining error buckets include CMS/board/editor files, legacy simulation pages that require product-path reconfirmation, and `components/ui/tiptap-editor.tsx` used only by CMS edit flows.
