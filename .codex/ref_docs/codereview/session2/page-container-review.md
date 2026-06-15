# Page/Container 계층 리뷰

| ID | 심각도 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|
| S2-PAGE-001 | Medium | `app/simulation2/page.tsx` | 16 | route page가 token 조회, `getMe()`, token clear, redirect, loading state를 직접 처리한다 | 인증 흐름이 route에 묶여 재사용/테스트가 어렵고, 다른 PFM 보호 route와 정책을 공유하기 어렵다 | `usePfmAuthGate` 또는 route guard component로 추출하고 redirect 정책을 표준화 |
| S2-PAGE-002 | Medium | `app/simulation2/page.tsx` | 20 | redirect에 `window.location.href`를 직접 사용한다 | Next navigation 흐름과 일관성이 낮고 테스트/SSR 경계가 좁아진다 | client route에서는 `useRouter().replace()` 또는 공통 auth redirect helper 검토 |
| S2-PAGE-003 | Low | `app/simulation2/page.tsx` | 43 | inner loading과 Suspense fallback이 동일한 route에서 중복 정의된다 | loading UX/문구 변경 시 중복 수정 필요 | route-level fallback component를 공통화하거나 auth gate 내부 loading UI로 단일화 |
| S2-PAGE-004 | Medium | `app/cmsl2004/page.tsx` | 13 | legacy admin route가 Supabase session check와 auth subscription을 page에서 직접 처리한다 | `cmsl2004`, `cmsl20042`에 중복 guard 패턴이 생기고 권한 정책 확장 시 중복 변경 필요 | `useSupabaseSessionGate` 또는 `LegacyAdminGate`로 분리 |
| S2-PAGE-005 | Medium | `app/cmsl20042/page.tsx` | 13 | `cmsl2004`와 동일한 client auth guard가 복제되어 있다 | 인증 상태 처리, loading UI, cleanup 변경이 두 route에 반복된다 | 두 legacy admin page가 공유하는 guard component를 도입 |
| S2-PAGE-006 | Medium | `app/board/news/[id]/edit/page.tsx` | 7 | edit route가 `params.id`만 container에 넘기며 route-level session/권한 확인이 보이지 않는다 | 비로그인 접근 시 container 또는 Supabase 정책에 의존할 수 있다. 실제 보호 여부 확인 필요 | edit route guard 또는 edit container의 명시적 권한 check 확인/표준화 |
| S2-PAGE-007 | Medium | `app/board/gallery/[id]/edit/page.tsx` | 7 | gallery edit route도 `params.id`만 전달하고 route-level guard가 확인되지 않는다 | 게시글 수정 권한 UX가 route마다 달라질 수 있다 | news edit와 동일한 guard 기준 적용 |
| S2-CONTAINER-001 | High | `components/pages/Simulation2Page.tsx` | 589 | container가 chat, simulation draft, job polling, WebSocket, result/viz orchestration, UI state를 함께 가진다 | page/container 변경 영향도가 매우 크고, Session 1의 책임 집중 문제가 사용자 진입 흐름에서도 반복된다 | workflow hook/service 단위로 분리하고 container는 조립/props 전달 중심으로 축소 |
| S2-CONTAINER-002 | High | `components/pages/AdminPage3.tsx` | 483 | admin container가 URL query state, 권한, query/mutation, cleanup성 query normalization, UI rendering을 모두 처리한다 | admin 기능 추가 시 결합도와 회귀 위험 증가 | tab별 container/hook으로 분리하고 URL state helper를 별도 모듈화 |
| S2-CONTAINER-003 | Medium | `components/pages/NoticeBoardPage.tsx` | 23 | route에서 session을 받았지만 container에서 다시 `supabase.auth.getSession()`을 호출한다 | session source가 중복되어 상태 불일치와 불필요한 client auth 호출 가능 | session ownership을 route/server 또는 client gate 중 하나로 정리 |
| S2-CONTAINER-004 | Medium | `components/pages/GalleryDetailPage.tsx` | 35 | detail container가 session prop이 없으면 client session을 재조회한다 | detail/list 간 인증 처리 패턴이 중복되고 불명확하다 | board 공통 session hook 또는 server session 전달 정책 통일 |
| S2-CONTAINER-005 | Medium | `components/pages/NoticeDetailPage.tsx` | 58 | detail container가 Supabase 조회와 삭제 mutation을 직접 수행한다 | UI와 persistence가 결합되어 테스트/권한/error 처리 변경에 취약 | notice service/query hook으로 조회/삭제 분리 |
| S2-CONTAINER-006 | Medium | `components/pages/EditNoticePage.tsx` | 122 | edit container가 storage upload/remove와 DB update, router 이동까지 직접 수행한다 | 파일 저장 정책과 form UI가 결합되어 변경 영향도가 커진다 | attachment service와 edit form state/hook 분리 |
| S2-CONTAINER-007 | Medium | `components/pages/EditGalleryPage.tsx` | 112 | gallery edit container가 storage thumbnail 처리와 DB update를 직접 수행한다 | gallery storage path/cleanup 정책 변경이 UI 파일에 직접 반영되어야 한다 | gallery repository/adapter와 form hook으로 분리 |
| S2-CONTAINER-008 | Low | `components/pages/AdminPage3.tsx` | 1139 | admin loading/error/inactive/forbidden 상태가 container early return으로 각각 구현된다 | 상태 UI는 명확하지만 route/layout boundary와 재사용 기준이 약하다 | admin guard 상태 presenter를 별도 component로 분리 |

## Session 1 연결

- `S2-CONTAINER-001`은 Session 1 `S1-ARCH-001`의 route/page 관점 확장이다.
- `S2-CONTAINER-002`는 Session 1 `S1-ARCH-004`의 page-level query/state 관점 확장이다.
- CMS board/edit 문제는 Session 1의 Supabase 직접 의존 문제를 route guard와 page/container 경계 관점에서 다시 확인한 것이다.
