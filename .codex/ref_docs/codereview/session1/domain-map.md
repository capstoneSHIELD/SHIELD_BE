# Frontend 도메인/기능 맵

| 도메인/기능 | 관련 파일/디렉터리 | 핵심 책임 | 의존 대상 | 리팩토링 시 주의점 |
|---|---|---|---|---|
| PFM 사용자 simulation workbench | `app/simulation2/page.tsx`, `components/pages/Simulation2Page.tsx`, `components/simulation/*`, `components/pages/simulation2/*` | 인증된 사용자의 채팅 기반 simulation 생성/수정/실행, job monitor, result/viz 흐름 | `lib/auth.ts`, `lib/api/chatSessions.ts`, `lib/api/simulations.ts`, `lib/api/jobs.ts`, `lib/api/results.ts`, `lib/api/visualizations.ts`, `lib/api/http.ts` | `Simulation2Page.tsx`가 lifecycle/ref/WebSocket/상태를 많이 가진다. job/viz 순서와 cleanup 회귀 위험이 높으므로 테스트 선행 필요 |
| PFM admin console | `app/cmsl20043/page.tsx`, `components/pages/AdminPage3.tsx`, `lib/api/admin.ts` | 계정 요청/사용자/simulation/job/result/visualization 관리 | TanStack Query, `lib/api/admin.ts`, 일부 `lib/api/results.ts`, `lib/api/visualizations.ts` | admin 권한/계정 상태 처리와 query key, mutation invalidation을 보존해야 한다 |
| PFM API client/auth | `lib/apiClient.ts`, `lib/auth.ts`, `lib/api/*` | token 포함 request, refresh, error normalization, 리소스별 endpoint helper | backend API, browser storage, environment variables | `authFetch` refresh 흐름과 FastAPI 422 변환은 변경 영향이 크다: `lib/apiClient.ts:278`, `lib/apiClient.ts:304` |
| PFM job/result/visualization | `lib/api/jobs.ts`, `lib/api/results.ts`, `lib/api/visualizations.ts`, `components/simulation/ResultExplorerPanel.tsx`, `components/simulation/JobResultListCard.tsx` | job 상태/이벤트, 결과 탐색, visualization 생성/수정/삭제/스크린샷 | PFM API helper, WebSocket helper, result/viz backend | 일부 UI 컴포넌트가 API 호출을 직접 수행하므로 container/hook 경계 재설계 필요 |
| CMS public site | `components/pages/HomePage.tsx`, `components/pages/ResearchPageTemplate.tsx`, notice/publication/project/gallery 관련 pages | 공개 콘텐츠 조회 및 표시 | `lib/supabaseClient.ts`, Supabase DB/storage | UI 컴포넌트와 persistence query가 섞인 파일이 확인된다. RLS/권한 정책은 확인 필요 |
| CMS legacy admin | `app/cmsl2004`, `app/cmsl20042`, `components/pages/AdminPage.tsx`, `components/pages/AdminPage2.tsx`, `components/pages/EditMemberPage.tsx` | CMS 콘텐츠/멤버/팝업/게시글 관리 | Supabase auth/db/storage | 직접 삭제/업로드/수정 로직이 UI에 있어 service 추출 시 권한/스토리지 경로 보존 필요 |
| PFM login/auth page | `app/pfm_chat/login`, `components/pages/LoginPage.tsx`, `lib/auth.ts` | 로그인 UI와 token 저장/계정 확인 | PFM auth API | route guard와 token lifecycle 일관성은 Session 2에서 추가 검토 필요 |
| Legacy AI assistant | `components/AIChatAssistant.tsx`, `lib/api/legacyAiChat.ts`, `api/chat.js` | Gemini 기반 보조 응답 및 parameter 추출 | `/api/chat`, Google Gemini SDK, `GEMINI_API_KEY` | PFM API helper 계층과 별도 흐름이다. 현재 유지 대상인지 확인 필요 |
| Contact/email | `components/pages/ContactPage.tsx` | 문의 폼 제출 | EmailJS browser SDK, `NEXT_PUBLIC_EMAILJS_*` | 외부 연동 계약/실패 처리 기준 확인 필요 |
| 공통 UI/layout | `app/layout.tsx`, `app/providers.tsx`, `components/ui/*`, `components/common/*` | 전역 provider, header/footer, 재사용 UI | React Query, LanguageProvider, Toaster, Tailwind/shadcn | UI primitive는 business logic 유입 방지. provider 추가 시 전역 영향 확인 필요 |
| 테스트/아키텍처 guard | `vitest.config.ts`, `scripts/check-pfm-api-boundaries.mjs`, `*.test.tsx` | 단위/컴포넌트/경계 회귀 검증 | Vitest, boundary script | PFM 경계는 guard가 있으나 CMS/Supabase 경계 테스트는 확인 필요 |

## 확인된 핵심 도메인 경계

- PFM backend API 도메인은 `lib/api/*` helper 중심의 계층화 의도가 강하다.
- CMS 도메인은 Supabase persistence를 page component가 직접 사용하는 구조가 남아 있다.
- legacy AI와 Contact는 PFM API client와 별도 외부 연동 흐름이며, 계약/운영 지속 여부는 확인 필요다.
