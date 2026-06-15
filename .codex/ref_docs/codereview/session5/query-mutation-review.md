# Query / Mutation Review

| ID | 심각도 | 파일 경로 | 라인 | query/mutation | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S5-QUERY-001 | High | `components/pages/AdminPage3.tsx` | 551 | admin `useQuery` group | auth/system/account/users/simulation/job/result/viz query가 한 container에 집중 | query dependency와 enabled 조건 변경 영향이 admin 전체로 확산 | tab별 query hook으로 분리 |
| S5-QUERY-002 | Medium | `components/pages/AdminPage3.tsx` | 597 | `jobsQuery` | `refetchInterval` 정책이 component 내부에 직접 존재 | polling 정책 재사용/테스트가 어려움 | `useAdminJobsQuery`로 polling policy 캡슐화 |
| S5-QUERY-003 | Medium | `components/pages/AdminPage3.tsx` | 617 | `jobEventsQuery` | selected job detail status에 refetch interval이 의존 | job detail/events 흐름이 component local relation에 묶임 | job detail/events query hook에서 함께 관리 |
| S5-MUTATION-001 | High | `components/pages/AdminPage3.tsx` | 666 | `syncJobMutation` | mutation 내부에서 API orchestration과 `setQueryData`/invalidate가 직접 실행됨 | cache 정책 변경 시 UI container 수정 필요 | `useSyncAdminJobMutation`으로 이동 |
| S5-MUTATION-002 | Medium | `components/pages/AdminPage3.tsx` | 697 | `cancelJobMutation` | cancel success에서 여러 query invalidation을 직접 fan-out | query key 추가/변경 시 누락 가능 | domain mutation hook 또는 cache policy helper |
| S5-CACHE-001 | Medium | `components/pages/AdminPage3.tsx` | 729 | `loadFieldFilesMutation` | `fetchQuery` 결과를 `fieldFilesData` local state에 복사 | cache와 local state 불일치 가능 | selected params 기반 query로 표현 |
| S5-INVALIDATE-001 | Low | `components/pages/AdminPage3.tsx` | 1080 | account request invalidation | literal key `['pfmAdmin','accountRequests']`와 builder key가 혼재 | key rename 시 누락 위험 | query key root helper 도입 |
| S5-QUERY-004 | Suggestion | `app/providers.tsx` | 15 | `QueryClient` | 전역 기본값은 `staleTime: 30_000`, `refetchOnWindowFocus: false`만 확인됨 | 도메인별 retry/gcTime 정책 의도 확인 어려움 | domain별 freshness/retry 정책 문서화 |

## 확인 필요

- React Query retry/gcTime 기본값이 제품 요구사항과 맞는지 확인 필요.
- SWR 사용은 확인되지 않았다.
