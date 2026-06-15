# Component Inventory

## 기준

이 목록은 리팩토링 우선순위 판단을 위한 주요 component inventory이다. `components/ui/*`의 shadcn primitive 전체를 개별 행으로 모두 나열하지 않고, 실제 리뷰와 연결되는 대표 component와 디렉터리 단위로 정리했다.

| 구분 | 파일 경로 | component 이름 | 역할 | 사용 위치 | 비고 |
|---|---|---|---|---|---|
| layout component | `components/Header.tsx` | `Header` | 전역 header, 로그인 상태/로그아웃 UI | `app/layout.tsx` | 모든 route에 적용 |
| layout component | `components/Footer.tsx` | `Footer` | 전역 footer | `app/layout.tsx` | 모든 route에 적용 |
| layout component | `components/Navigation.tsx` | `Navigation` | desktop navigation | `Header` | dropdown hover state 보유 |
| layout component | `components/MobileNavigation.tsx` | `MobileNavigation` | mobile navigation sheet | `Header` | open/expanded state 보유 |
| common component | `components/ui/*` | shadcn primitives | Button, Dialog, Table, Tabs 등 UI primitive | 전역 | 대부분 presentation primitive |
| common component | `components/common/ApiErrorNotice.tsx` | `ApiErrorNotice` | normalized API error 표시 | `Simulation2Page`, `AdminPage3` 등 | `normalizeApiError` 사용 |
| common component | `components/common/ApiErrorDetailsPanel.tsx` | `ApiErrorDetailsPanel` | user/admin error details 표시 | `ApiErrorNotice` | raw details 노출 정책 분리 |
| common component | `components/ScrollAnimation.tsx` | `ScrollAnimation` | intersection 기반 reveal animation | public pages | presentation/helper 성격 |
| common component | `components/ImageCarousel.tsx` | `ImageCarousel` | image/video carousel | `ResearchPageTemplate` | `key={index}` 확인 |
| common component | `components/LanguageProvider.tsx` | `LanguageProvider`, `useLanguage` | language context/state | `app/providers.tsx` | Session 4 hook/state에서 추가 확인 |
| page-specific component | `components/pages/HomePage.tsx` | `HomePage` | homepage CMS data fetch + rendering | `app/page.tsx` | Supabase 직접 호출 |
| page-specific component | `components/ResearchPageTemplate.tsx` | `ResearchPageTemplate` | research page CMS template | research pages | Supabase 직접 호출, HTML render |
| page-specific component | `components/pages/ContactPage.tsx` | `ContactPage` | contact form + EmailJS send | `app/contact/page.tsx` | 외부 SDK 호출 포함 |
| page-specific component | `components/pages/NoticeBoardPage.tsx` | `NoticeBoardPage` | notice list container | `app/board/news/page.tsx` | Supabase query/mutation 직접 수행 |
| table/list | `components/pages/NewsPage.tsx` | `NewsPage` | notice list presentation | `NoticeBoardPage` | 많은 props/handler 수신 |
| page-specific component | `components/pages/GalleryBoardPage.tsx` | `GalleryBoardPage` | gallery list container | `app/board/gallery/page.tsx` | Supabase query 직접 수행 |
| table/list | `components/pages/GalleryListPage.tsx` | `GalleryListPage` | gallery list presentation | `GalleryBoardPage` | search/page props 수신 |
| form component | `components/pages/EditNoticePage.tsx` | `EditNoticePage` | notice edit form/storage/update | edit route | form, storage, DB update 혼재 |
| form component | `components/pages/EditGalleryPage.tsx` | `EditGalleryPage` | gallery edit form/storage/update | edit route | `EditNoticePage`와 패턴 유사 |
| form component | `components/ui/tiptap-editor.tsx` | `TiptapEditor` | rich text editor wrapper | edit forms | editor toolbar 포함 |
| feature component | `components/pages/Simulation2Page.tsx` | `Simulation2Page` | PFM simulation2 핵심 container | `app/simulation2/page.tsx` | 3347 lines, 주요 리팩토링 대상 |
| feature component | `components/simulation/WorkspaceTabsCard.tsx` | `WorkspaceTabsCard` | simulation/result sidebar tabs | `Simulation2Page` | props pass-through 성격 |
| table/list | `components/simulation/SimulationListCard.tsx` | `SimulationListCard` | simulation list fetch/render | `WorkspaceTabsCard` | API 호출과 list UI 혼재 |
| table/list | `components/simulation/JobResultListCard.tsx` | `JobResultListCard` | job/result list fetch/render | `WorkspaceTabsCard` | API 호출과 row actions 혼재 |
| table/list | `components/simulation/SessionListCard.tsx` | `SessionListCard` | chat session list/search/delete/rename | `Simulation2Page` | API 호출, form, dialog 혼재 |
| chart/viewer | `components/simulation/ResultExplorerPanel.tsx` | `ResultExplorerPanel` | result detail/files/field explorer | `Simulation2Page` | API 호출, filters, download 혼재 |
| chart/viewer | `components/simulation/VisualizationControlBar.tsx` | `VisualizationControlBar` | visualization control intent UI | `Simulation2Page` | 비교적 presentation 경계 양호 |
| chart/viewer | `components/simulation/trame/AdvancedTramePanel.tsx` | `AdvancedTramePanel` | Lab/Trame advanced panel | `Simulation2Page` | 직접 Lab 기능, 별도 adapter 확인 필요 |
| chart/viewer | `components/simulation/trame/TrameViewer.tsx` | `TrameViewer` | Trame iframe/viewer | `AdvancedTramePanel` | viewer feature |
| chart/viewer | `components/VtkViewer.tsx` | `VtkViewer` | VTK viewer | `VtiViewerPage` 등 | `memo` 적용 |
| modal/dialog | `components/MemberDetailModal.tsx` | `MemberDetailModal` | member detail modal | `MembersPage` | custom modal, 접근성 확인 필요 |
| modal/dialog | `components/SinglePopupDialog.tsx` | `SinglePopupDialog` | site popup dialog | `SitePopup` | HTML content render 확인 필요 |
| modal/dialog | `components/pages/AdminPage3.tsx` | inline dialogs | review/user/cancel/close dialogs | `AdminPage3` 내부 | 별도 component 추출 후보 |
| fallback/loading/error component | `app/not-found.tsx` | `NotFound` | global not-found | Next app router | error boundary는 Session 2에서 확인 필요 |
| fallback/loading/error component | `app/cmsl20043/page.tsx` | `Admin3Loading` | admin Suspense fallback | admin3 route | route fallback |

## 확인 필요

- `components/reactbits/*`는 visual interaction component로 보이며, 이번 문서에서는 주요 product workflow 리팩토링 대상에서 제외했다.
- `components/pages/AdminPage.tsx`, `AdminPage2.tsx`, `PFMSimulationPage.tsx`는 legacy 성격이 섞여 있어 유지 범위 확인이 필요하다.
