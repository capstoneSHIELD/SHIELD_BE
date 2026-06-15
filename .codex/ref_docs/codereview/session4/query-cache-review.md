# Query Cache Review

| ID | 심각도 | 파일 경로 | 라인 | query/mutation | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S4-QUERY-001 | Medium | `app/providers.tsx` | 15 | `QueryClient` | 전역 기본값은 `staleTime: 30_000`, `refetchOnWindowFocus: false`만 확인된다 | 도메인별 freshness/retry 정책이 query 단위에 흩어질 수 있음 | admin/simulation/CMS별 query policy 문서화 |
| S4-QUERY-002 | Medium | `components/pages/AdminPage3.tsx` | 551 | admin `useQuery` group | me/health/ready/account/users/simulation/jobs/results/viz query가 한 container에 집중되어 있다 | query 간 dependency와 enabled 조건 파악이 어렵다 | tab별 query hook으로 분리 |
| S4-QUERY-003 | Medium | `components/pages/AdminPage3.tsx` | 597 | `jobsQuery` polling | `refetchInterval`이 query data status에 의존한다 | active job polling 정책은 좋지만 admin container에 묶여 있음 | `useAdminJobsQuery`로 polling policy 캡슐화 |
| S4-QUERY-004 | Medium | `components/pages/AdminPage3.tsx` | 617 | `jobEventsQuery` polling | `selectedJobQuery.data?.status` 기반 polling | selected job detail과 events query의 refetch timing이 결합 | job detail/events query hook에서 함께 관리 |
| S4-CACHE-001 | Medium | `components/pages/AdminPage3.tsx` | 676 | `syncJobMutation` | mutation success에서 `setQueryData`와 여러 invalidate가 직접 수행된다 | cache update 정책이 UI container에 노출 | mutation hook으로 cache side effect 이동 |
| S4-INVALIDATE-001 | Low | `components/pages/AdminPage3.tsx` | 1080 | `reviewMutation` invalidation | `['pfmAdmin', 'accountRequests']` literal prefix invalidation과 builder key가 혼재한다 | key rename 시 일부 invalidation 누락 위험 | `buildAdminQueryKeys.accountRequestsRoot()` 같은 helper 추가 검토 |
| S4-CACHE-002 | Medium | `components/pages/AdminPage3.tsx` | 744 | `loadFieldFilesMutation` | `queryClient.fetchQuery` 결과를 `fieldFilesData` local state로 복사한다 | cache와 UI local state가 불일치할 수 있음 | selected field/files query를 enabled state로 표현 |
| S4-MUTATION-001 | Medium | `components/pages/AdminPage3.tsx` | 697 | `cancelJobMutation` | cancel success가 selected simulation/job/result/viz cache invalidation을 직접 수행한다 | mutation이 admin page 구조를 강하게 안다 | `useCancelAdminJobMutation`에서 관련 cache 정책 캡슐화 |
| S4-QUERY-005 | Suggestion | 전체 | 0 | SWR | SWR 사용은 확인되지 않는다 | 해당 없음 | React Query 기준으로 리뷰 지속 |

## 실제 코드 근거

- `lib/api/admin.ts:310`에 `buildAdminQueryKeys`가 존재하고 `accountRequests`는 `['pfmAdmin', 'accountRequests', 'list', params]`를 반환한다.
- `components/pages/AdminPage3.tsx:1080`에서는 literal prefix `['pfmAdmin', 'accountRequests']` invalidation을 사용한다. React Query의 prefix matching 의도일 수 있으나 helper와 혼재한다.

## 확인 필요

- React Query retry/gcTime 정책은 기본값 사용 여부만 확인했으며, product freshness 요구사항과 맞는지는 확인 필요.
