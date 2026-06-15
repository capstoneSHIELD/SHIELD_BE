# 다음 세션 시작용 프롬프트

아래 프롬프트를 다음 채팅에 그대로 붙여넣으면 이어서 진행할 수 있습니다.

```text
이전 세션들에서 frontend 구조와 코드리뷰 결과를 문서화했다.

참고 문서 경로:
- Session 1: C:\pfm-FE\.codex\ref_docs\codereview\session1
- Session 2: C:\pfm-FE\.codex\ref_docs\codereview\session2
- Session 3: C:\pfm-FE\.codex\ref_docs\codereview\session3
- Session 4: C:\pfm-FE\.codex\ref_docs\codereview\session4

Session 4 요약:
- `Simulation2Page`는 workflow, chat, job monitor, visualization, form state를 한 component에서 관리한다.
- `AdminPage3`는 React Query를 사용하지만 query/mutation/cache invalidation/form/dialog state가 한 container에 집중되어 있다.
- `JobResultListCard`, `SimulationListCard`, `SessionListCard`, `ResultExplorerPanel`은 server state를 local state로 관리하며 일부 요청에는 stale response guard가 없다.
- `Simulation2Page`의 job polling fallback은 async interval에 in-flight guard가 없어 중복 실행 가능성이 있다.
- `lib/auth.ts`와 `lib/apiClient.ts`에 token sessionStorage helper가 중복되어 있다.
- Zustand/Redux/SWR 직접 사용은 확인되지 않았다. React Query는 admin 영역 중심으로 사용된다.

이제 Session 5를 진행해라.

Session 5에서는 API/service/async flow 계층을 탑다운 방식으로 코드리뷰해라.

검토 대상:
- hook과 component에서 호출되는 API/service
- fetch, axios, API client, Supabase client, external SDK 호출
- service layer와 API client 경계
- async/await, Promise, mutation, polling, retry, error handling
- request cancellation, timeout, token refresh, response type 안정성
- DTO/API response type과 UI view model 분리

리뷰 기준:
- UI component가 API/client를 직접 과도하게 호출하지 않는가?
- API client와 service 계층의 책임이 분리되어 있는가?
- external integration은 adapter/client 경계 뒤에 있는가?
- timeout/retry/cancellation/error normalization이 일관적인가?
- token refresh와 auth failure 처리가 안전한가?
- response type이 `any`로 새지 않는가?
- server state cache와 API 호출 흐름이 일관적인가?

출력 형식:

심각도 | 파일 경로 | 라인 | 계층 | 문제 | 영향 | 개선 방향

주의사항:
- 코드 수정은 하지 마라.
- 실제 코드 근거 없이 추측하지 마라.
- 모든 주요 지적에는 파일 경로와 라인 번호를 포함해라.
- 확실하지 않은 내용은 “확인 필요”라고 표시해라.
- 최종 답변은 한국어로 작성해라.
```
