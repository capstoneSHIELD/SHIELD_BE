# 다음 세션 시작용 프롬프트

```text
이전 세션들에서 frontend 코드리뷰 결과를 문서화했다.

참고 문서 경로:
- Session 1: C:\pfm-FE\.codex\ref_docs\codereview\session1
- Session 2: C:\pfm-FE\.codex\ref_docs\codereview\session2
- Session 3: C:\pfm-FE\.codex\ref_docs\codereview\session3
- Session 4: C:\pfm-FE\.codex\ref_docs\codereview\session4
- Session 5: C:\pfm-FE\.codex\ref_docs\codereview\session5

Session 5 요약:
- PFM API는 `lib/api/*`, `lib/apiClient.ts`, `lib/api/http.ts`, `lib/api/errors.ts` 경계가 있으나 use-case/service hook 계층은 얇다.
- `Simulation2Page`는 chat/simulation/job/result/visualization API orchestration을 과도하게 가진다.
- `AdminPage3`는 React Query를 사용하지만 query/mutation/cache invalidation이 container에 집중되어 있다.
- `Simulation2Page` job polling과 legacy `PFMSimulationPage` polling은 async interval in-flight guard가 없다.
- CMS Supabase list/edit page는 service/repository 경계 없이 UI에서 직접 DB/storage를 호출한다.
- `apiRequest<T = any>`, request timeout/signal 부재, legacy `/api/chat` error envelope 불일치가 주요 리스크다.

이제 Session 6을 진행해라.

Session 6에서는 type/util/config 계층을 리뷰해라.

검토 대상:
- TypeScript type/interface
- DTO, API response type, request type
- form state type, view model, domain model
- util/helper, formatter/parser, validation schema
- constant/config/env 사용 구조
- `any`, `unknown`, type assertion, 중복 type 정의
- API 계약과 frontend type 불일치 가능성

출력 형식:

심각도 | 파일 경로 | 라인 | 타입/유틸/설정 | 문제 | 영향 | 개선 방향

주의사항:
- 코드 수정은 하지 마라.
- 실제 코드 근거 없이 추측하지 마라.
- 파일 경로와 라인 번호를 포함해라.
- 확인하지 못한 내용은 “확인 필요”라고 표시해라.
- 최종 답변은 한국어로 작성해라.
```
