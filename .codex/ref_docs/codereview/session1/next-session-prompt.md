# 다음 세션 시작용 프롬프트

아래 프롬프트를 다음 코드리뷰 또는 리팩토링 세션에 그대로 사용할 수 있다.

```text
이전 Session 1에서는 pfm-FE frontend 전체 구조와 아키텍처 맵을 분석했고, 결과 문서는 다음 경로에 저장되어 있다.

C:\pfm-FE\.codex\ref_docs\codereview\session1

먼저 아래 문서를 순서대로 읽고 Session 1의 구조 이해를 이어받아라.

1. C:\pfm-FE\.codex\ref_docs\codereview\session1\README.md
2. C:\pfm-FE\.codex\ref_docs\codereview\session1\refactoring-brief.md
3. C:\pfm-FE\.codex\ref_docs\codereview\session1\session1-findings.md
4. C:\pfm-FE\.codex\ref_docs\codereview\session1\dependency-flow.md
5. C:\pfm-FE\.codex\ref_docs\codereview\session1\oop-architecture-review.md
6. C:\pfm-FE\.codex\ref_docs\codereview\session1\frontend-structure.md
7. C:\pfm-FE\.codex\ref_docs\codereview\session1\domain-map.md

Session 1 요약:

- PFM API helper 계층은 `lib/apiClient.ts`와 `lib/api/*` 중심으로 분리되어 있으며, `scripts/check-pfm-api-boundaries.mjs`로 일부 경계를 검증한다.
- `components/pages/Simulation2Page.tsx`는 simulation2의 UI, workflow orchestration, API 호출, WebSocket lifecycle, 상태 관리를 많이 보유한 대형 컨테이너다.
- `components/pages/AdminPage3.tsx`는 admin query/mutation, 권한/상태 처리, URL state, UI 렌더링이 집중된 대형 컨테이너다.
- CMS/Supabase 영역은 여러 page component에서 Supabase client를 직접 호출한다.
- `lib/api/admin.ts`는 admin API helper와 shared resource DTO/wrapper가 섞여 있어 타입 drift 위험이 있다.
- TypeScript strict 설정이 꺼져 있고 `apiRequest<T = any>`, workflow `Record<string, any>` 등 타입 안정성 약점이 확인된다.
- legacy Gemini/EmailJS 외부 연동은 PFM API helper 흐름과 별도이므로 유지 여부와 계약 확인이 필요하다.

다음 세션에서 수행할 작업:

- 코드리뷰 세션이라면 Route/Page/Container 계층부터 탑다운으로 리뷰하고, 모든 주요 지적에는 파일 경로와 라인 번호를 포함해라.
- 리팩토링 세션이라면 frontend 소스를 바로 수정하기 전에 `refactoring-brief.md`의 민감 영역과 우선순위를 확인해라.
- 코드 수정이 허용된 세션에서만 소스를 수정해라. 코드리뷰 세션에서는 소스 수정 없이 리뷰만 수행해라.
- 확실하지 않은 내용은 단정하지 말고 “확인 필요”로 표시해라.
- `.codex/ref_docs`는 사용자 관리 참고자료 공간이며 프로젝트 공식 명세 위치가 아니다.

우선 확인할 코드 후보:

1. `app/simulation2/page.tsx`
2. `components/pages/Simulation2Page.tsx`
3. `app/cmsl20043/page.tsx`
4. `components/pages/AdminPage3.tsx`
5. `components/pages/HomePage.tsx`
6. `components/pages/NoticeBoardPage.tsx`
7. `components/pages/EditMemberPage.tsx`
8. `lib/apiClient.ts`
9. `lib/api/admin.ts`
```
