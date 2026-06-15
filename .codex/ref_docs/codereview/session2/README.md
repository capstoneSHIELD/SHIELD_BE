# Frontend 코드리뷰 Session 2 문서

## 목적

이 문서는 pfm-FE frontend 코드리뷰 Session 2에서 파악한 routing, page, layout, container 계층의 리뷰 결과를 이후 리팩토링 세션에서 재사용하기 위해 정리한 참고자료다.

`.codex/ref_docs`는 사용자 관리 참고자료 공간이므로, 이 문서는 프로젝트 공식 명세가 아니라 코드리뷰/리팩토링 세션의 기준 자료로만 사용한다.

## 분석 범위

- Next.js App Router route/page: `app/**/page.tsx`
- 전역 layout/provider/not-found: `app/layout.tsx`, `app/providers.tsx`, `app/not-found.tsx`
- route guard 또는 page-level 인증 확인: `app/simulation2/page.tsx`, `app/pfm_chat/login/page.tsx`, `app/cmsl2004/page.tsx`, `app/cmsl20042/page.tsx`
- 주요 page container: `components/pages/Simulation2Page.tsx`, `components/pages/AdminPage3.tsx`
- CMS board/detail/edit container: `components/pages/NoticeBoardPage.tsx`, `components/pages/GalleryDetailPage.tsx`, `components/pages/NoticeDetailPage.tsx`, `components/pages/EditGalleryPage.tsx`, `components/pages/EditNoticePage.tsx`
- Session 1 연결 문서: `.codex/ref_docs/codereview/session1`

## Session 1과의 연결

Session 1은 frontend 전체 구조와 도메인/의존성 맵을 정리했다. Session 2는 그중 사용자 진입점과 상위 UI 계층에 집중한다.

- Session 1에서 확인한 핵심 구조 리스크인 `Simulation2Page` 대형 컨테이너 문제를 route/page 진입 흐름 관점에서 재확인했다.
- Session 1에서 확인한 CMS/Supabase 직접 의존 문제를 board/detail/edit route와 container 경계 관점에서 연결했다.
- Session 1에서 확인한 PFM API helper 경계는 유지하되, route guard와 container의 page-level async 흐름을 별도로 정리했다.

## 집중 계층

| 계층 | 검토 초점 |
|---|---|
| route | URL path와 page/container 연결, params/searchParams 처리 |
| layout | 전역 provider/header/footer/not-found/error/loading boundary |
| page | route guard, page-level auth/session/data fetching, Suspense fallback |
| container | page-level state, async orchestration, API/service/store 의존 |
| boundary | loading/error/not-found/fallback 일관성 |

## 문서 구성

| 파일 | 역할 |
|---|---|
| `README.md` | Session 2 문서 목적, 범위, 읽는 순서 |
| `route-page-map.md` | route/page/layout/container 연결 맵 |
| `entry-flow.md` | 사용자 진입 후 page/container 실행 흐름 |
| `page-container-review.md` | page/container 책임 분리 리뷰 |
| `routing-review.md` | routing, layout, guard, boundary 리뷰 |
| `page-state-and-async-flow.md` | page/container 상태 및 비동기 흐름 정리 |
| `session2-findings.md` | Session 2 주요 문제 후보 종합 |
| `refactoring-brief.md` | 리팩토링 세션용 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션용 프롬프트 |

## 리팩토링 세션 참고 순서

1. `README.md`
2. `session2-findings.md`
3. `refactoring-brief.md`
4. `route-page-map.md`
5. `page-container-review.md`
6. `page-state-and-async-flow.md`
7. `entry-flow.md`
8. `routing-review.md`
9. `../session1/dependency-flow.md`
10. `../session1/oop-architecture-review.md`

## 근거 표기 기준

- `파일:라인`은 실제 코드에서 확인한 근거다.
- `확인 필요`는 Session 2 범위에서 route/page/container만으로 결론을 내리기 어려운 항목이다.
- 이 문서 생성 작업에서는 frontend 소스 코드를 수정하지 않았다.
