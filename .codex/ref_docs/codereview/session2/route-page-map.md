# Route/Page/Layout/Container 맵

| 구분 | 파일 경로 | 라인 | 역할 | 연결된 하위 컴포넌트/기능 | 비고 |
|---|---|---:|---|---|---|
| layout | `app/layout.tsx` | 87 | 전역 provider와 공통 layout 적용 | `Providers`, `Header`, `Footer`, `ScrollToTopButton`, `Analytics` | `main`에 모든 route children 배치: `app/layout.tsx:89` |
| layout | `app/providers.tsx` | 26 | 전역 client providers 구성 | `QueryClientProvider`, `LanguageProvider`, `Toaster` | React Query 기본 stale/refetch 옵션: `app/providers.tsx:16` |
| error boundary | `app/not-found.tsx` | 3 | 전역 not-found 화면 | `/` 링크 | `error.tsx`, route별 `loading.tsx`는 Session 2에서 확인되지 않음 |
| route | `app/page.tsx` | 4 | home route | `HomePage` | 얇은 route |
| route | `app/introduction/page.tsx` | 4 | introduction route | `IntroductionPage` | 얇은 route |
| route | `app/contact/page.tsx` | 4 | contact route | `ContactPage` | 얇은 route, 외부 EmailJS는 container에서 처리 |
| route | `app/publications/page.tsx` | 4 | publications route | `PublicationsPage` | 얇은 route |
| route | `app/research/pfm/page.tsx` | 4 | PFM research route | `RealScalePfmResearchPage` | 얇은 route |
| route | `app/research/films/page.tsx` | 4 | thin films route | `ThinFilmsPage` | 얇은 route |
| route | `app/research/biodegradable/page.tsx` | 4 | biodegradable route | `BiodegradableAlloysPage` | 얇은 route |
| route | `app/people/professor/page.tsx` | 4 | professor route | `ProfessorPage` | 얇은 route |
| route | `app/people/members/page.tsx` | 4 | members route | `MembersPage` | 얇은 route |
| route | `app/people/alumni/page.tsx` | 4 | alumni route | `AlumniPage` | 얇은 route |
| page | `app/board/news/page.tsx` | 4 | notice list route | `NoticeBoardPage` | server Supabase session 조회 후 prop 전달: `app/board/news/page.tsx:7` |
| page | `app/board/gallery/page.tsx` | 4 | gallery list route | `GalleryBoardPage` | server Supabase session 조회 후 prop 전달: `app/board/gallery/page.tsx:7` |
| page | `app/board/news/[id]/page.tsx` | 8 | notice detail route | `NoticeDetailPage` | `params.id`와 server session 전달: `app/board/news/[id]/page.tsx:9`, `app/board/news/[id]/page.tsx:13` |
| page | `app/board/gallery/[id]/page.tsx` | 8 | gallery detail route | `GalleryDetailPage` | `params.id`와 server session 전달: `app/board/gallery/[id]/page.tsx:9`, `app/board/gallery/[id]/page.tsx:13` |
| page | `app/board/news/[id]/edit/page.tsx` | 7 | notice edit route | `EditNoticePage` | `params.id`만 전달, route guard 확인 필요 |
| page | `app/board/gallery/[id]/edit/page.tsx` | 7 | gallery edit route | `EditGalleryPage` | `params.id`만 전달, route guard 확인 필요 |
| route guard | `app/cmsl2004/page.tsx` | 13 | legacy admin route guard | `AdminPage`, `LegacyLoginPage` | Supabase session client check와 loading 처리: `app/cmsl2004/page.tsx:14`, `app/cmsl2004/page.tsx:27` |
| route guard | `app/cmsl20042/page.tsx` | 13 | legacy admin2 route guard | `AdminPage2`, `LegacyLoginPage` | `cmsl2004`와 거의 동일한 인증 패턴 |
| route guard | `app/pfm_chat/login/page.tsx` | 8 | PFM login route에서 기존 token 확인 | `LoginPage` | token valid 시 `/simulation2`로 이동: `app/pfm_chat/login/page.tsx:15` |
| route guard | `app/simulation2/page.tsx` | 8 | PFM simulation2 auth gate | `Simulation2Page` | `useSearchParams`, token/getMe, redirect, Suspense fallback 포함 |
| loading boundary | `app/simulation2/page.tsx` | 52 | `useSearchParams` client route fallback | `Simulation2Inner` | fallback과 inner loading이 같은 텍스트 기반 UI |
| loading boundary | `app/cmsl20043/page.tsx` | 14 | admin3 Suspense fallback | `AdminPage3` | admin 권한 loading/error는 container 내부 처리 |
| loading boundary | `app/reset-password/page.tsx` | 10 | reset password Suspense fallback | `ResetPasswordPage` | 단순 fallback |
| route | `app/forgot-password/page.tsx` | 4 | forgot password route | `ForgotPasswordPage` | 얇은 route |
| route | `app/reset-password/page.tsx` | 8 | reset password route | `ResetPasswordPage` | Suspense 사용 |
| route | `app/viewer/page.tsx` | 6 | VTI viewer route | dynamic `VtiViewerPage` | SSR 불가로 `next/dynamic` 사용: `app/viewer/page.tsx:5` |
| container | `components/pages/Simulation2Page.tsx` | 589 | simulation2 핵심 page container | chat, simulation, job monitor, result, visualization | page-level state/WS/API orchestration 집중 |
| container | `components/pages/AdminPage3.tsx` | 483 | PFM admin console container | account, user, simulation, job, result, visualization 관리 | React Query와 URL query state 집중 |
| container | `components/pages/NoticeBoardPage.tsx` | 23 | notice board container | Supabase notice list/search/pin/delete | route session prop 수신 후 client session 재조회 |
| container | `components/pages/GalleryDetailPage.tsx` | 26 | gallery detail container | Supabase gallery detail/delete | session prop 수신 후 client session 재조회 |
| container | `components/pages/NoticeDetailPage.tsx` | 27 | notice detail container | Supabase notice detail/delete | session prop 수신 후 client session 재조회 |
| container | `components/pages/EditGalleryPage.tsx` | 26 | gallery edit container | Supabase select/storage/update | edit route guard 확인 필요 |
| container | `components/pages/EditNoticePage.tsx` | 29 | notice edit container | Supabase select/storage/update | edit route guard 확인 필요 |

## 구조 관찰

- 많은 public route는 얇은 route/page 역할만 수행한다.
- 보호가 필요한 route는 PFM과 legacy CMS가 서로 다른 guard 방식을 사용한다.
- global `error.tsx`와 route별 `loading.tsx`는 확인되지 않았고, Suspense fallback과 component-local loading이 혼재한다.
- dynamic `[id]` route는 raw `id` string을 container로 전달하며, id validation/not-found 처리 표준은 확인 필요다.
