# 다음 세션 시작용 프롬프트

아래 프롬프트를 다음 채팅에 그대로 붙여넣으면 이어서 진행할 수 있습니다.

```text
이전 Session 1, Session 2, Session 3에서는 frontend 구조, route/page/container 계층, component 계층을 리뷰했다.

문서 경로:
- Session 1: C:\pfm-FE\.codex\ref_docs\codereview\session1
- Session 2: C:\pfm-FE\.codex\ref_docs\codereview\session2
- Session 3: C:\pfm-FE\.codex\ref_docs\codereview\session3

Session 3 요약:
- `components/pages/Simulation2Page.tsx`는 chat, workflow, job monitor, result explorer, visualization control, WebSocket/polling, 대형 JSX를 한 component에 포함한다.
- `components/pages/AdminPage3.tsx`는 URL query state, React Query, mutation, table, dialog, tab UI가 한 component에 집중되어 있다.
- `ResultExplorerPanel`, `SessionListCard`, `JobResultListCard`, `SimulationListCard`는 feature component로 분리되어 있지만 API 호출과 local/server state를 직접 가진다.
- CMS 계열 `NoticeBoardPage`, `GalleryBoardPage`, `EditNoticePage`, `ResearchPageTemplate`, `HomePage`는 Supabase query/storage/DB update가 component 내부에 남아 있다.
- `ApiErrorNotice`/`ApiErrorDetailsPanel`은 normalized error view model 기반으로 비교적 좋은 경계 사례다.

다음은 Session 4를 진행해라.

Session 4. Hook / State Management 리뷰

검토 대상:
- custom hook
- useState / useReducer
- Context
- Zustand / Redux
- React Query / TanStack Query
- SWR
- localStorage/sessionStorage 상태
- derived state
- server state와 client state 분리

검토 기준:
- server state와 client state가 명확히 분리되어 있는가?
- 전역 상태가 불필요하게 남용되고 있지 않은가?
- hook이 너무 많은 책임을 갖고 있지 않은가?
- stale closure 가능성이 있는가?
- useEffect dependency가 정확한가?
- 상태 업데이트가 race condition을 만들 가능성이 있는가?
- optimistic update, cache invalidation, refetch 전략이 일관적인가?
- loading/error 상태가 중복 또는 불일치하지 않는가?

참고 문서:
1. C:\pfm-FE\.codex\ref_docs\codereview\session3\session3-findings.md
2. C:\pfm-FE\.codex\ref_docs\codereview\session3\refactoring-brief.md
3. C:\pfm-FE\.codex\ref_docs\codereview\session3\props-and-state-flow.md
4. C:\pfm-FE\.codex\ref_docs\codereview\session2\page-state-and-async-flow.md
5. C:\pfm-FE\.codex\ref_docs\codereview\session1\oop-architecture-review.md

코드 수정은 하지 말고 코드리뷰만 수행해라.
모든 주요 지적에는 파일 경로와 라인 번호를 포함해라.
확실하지 않은 내용은 “확인 필요”라고 표시해라.
최종 답변은 한국어로 작성해라.
```
