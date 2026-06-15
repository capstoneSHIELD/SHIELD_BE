# 다음 세션 시작용 프롬프트

```text
이전 Session 1과 Session 2에서는 pfm-FE frontend의 전체 구조와 route/page/container 계층을 분석했다.

Session 1 문서 경로:
C:\pfm-FE\.codex\ref_docs\codereview\session1

Session 2 문서 경로:
C:\pfm-FE\.codex\ref_docs\codereview\session2

먼저 아래 문서를 순서대로 읽고 이어서 진행해라.

1. C:\pfm-FE\.codex\ref_docs\codereview\session2\README.md
2. C:\pfm-FE\.codex\ref_docs\codereview\session2\session2-findings.md
3. C:\pfm-FE\.codex\ref_docs\codereview\session2\refactoring-brief.md
4. C:\pfm-FE\.codex\ref_docs\codereview\session2\route-page-map.md
5. C:\pfm-FE\.codex\ref_docs\codereview\session2\page-container-review.md
6. C:\pfm-FE\.codex\ref_docs\codereview\session2\page-state-and-async-flow.md
7. C:\pfm-FE\.codex\ref_docs\codereview\session1\dependency-flow.md
8. C:\pfm-FE\.codex\ref_docs\codereview\session1\oop-architecture-review.md

Session 2 요약:

- 대부분의 public route는 `app/**/page.tsx`에서 `components/pages/*Page`를 단순 연결하는 얇은 route다.
- `app/simulation2/page.tsx`는 PFM token/getMe 기반 auth gate와 `session` query 처리를 직접 수행한 뒤 `Simulation2Page`를 렌더링한다.
- `app/pfm_chat/login/page.tsx`도 token 확인 후 `/simulation2` redirect를 직접 수행하므로 auth redirect 정책이 route에 분산되어 있다.
- `app/cmsl2004/page.tsx`, `app/cmsl20042/page.tsx`는 Supabase session 확인과 auth subscription을 page에서 직접 구현하며 중복이 있다.
- `app/cmsl20043/page.tsx`는 얇은 Suspense wrapper지만, `components/pages/AdminPage3.tsx`가 URL query state, 권한, query/mutation, loading/error/denied UI를 모두 관리한다.
- `Simulation2Page`는 job polling, WebSocket, visualization, chat, simulation start/update 흐름을 하나의 container에서 관리한다.
- board detail/edit route는 `params.id`를 container에 넘기며, id validation과 edit 권한 정책은 확인 필요다.
- global `error.tsx`와 route별 `loading.tsx`는 확인되지 않았고, loading/error UI가 route/container 로컬 구현에 분산되어 있다.

이제 Session 3를 진행해라.

Session 3에서는 component 계층을 탑다운 방식으로 코드리뷰해라.

검토 방향:

- page/container에서 호출되는 component를 추적해라.
- 공통 component와 feature component를 분류해라.
- component 책임 분리, props 계약, props drilling, conditional rendering, re-render 가능성을 검토해라.
- `Simulation2Page` 하위 simulation components와 `AdminPage3` 하위 section/UI 구조를 우선 확인해라.
- CMS board/detail/edit component의 form state와 Supabase side effect가 component 안에 섞여 있는지도 확인해라.
- 모든 주요 지적에는 파일 경로와 라인 번호를 포함해라.
- 확실하지 않은 내용은 “확인 필요”라고 표시해라.

코드 수정 여부:

- 코드리뷰 세션이면 frontend 소스 코드를 수정하지 마라.
- 리팩토링 세션으로 명시된 경우에만 코드를 수정하고, 수정 전 Session 2 `refactoring-brief.md`의 주의사항을 확인해라.
```
