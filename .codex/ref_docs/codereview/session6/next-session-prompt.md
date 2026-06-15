# Next Session Prompt

아래 프롬프트를 다음 세션에 그대로 붙여넣으면 이어서 진행할 수 있다.

```text
이전 Session 1~6 코드리뷰 문서는 다음 경로에 저장되어 있다.

- Session 1: C:\pfm-FE\.codex\ref_docs\codereview\session1
- Session 2: C:\pfm-FE\.codex\ref_docs\codereview\session2
- Session 3: C:\pfm-FE\.codex\ref_docs\codereview\session3
- Session 4: C:\pfm-FE\.codex\ref_docs\codereview\session4
- Session 5: C:\pfm-FE\.codex\ref_docs\codereview\session5
- Session 6: C:\pfm-FE\.codex\ref_docs\codereview\session6

Session 6 요약:
- TypeScript strict 계열 옵션이 꺼져 있어 type safety 품질 게이트가 약하다.
- SimulationStatus, JobStatus, VisualizationStatus와 simulation/job/result DTO가 여러 파일에 중복되어 있다.
- Simulation2Page의 workflow parameters와 PATCH body가 Record<string, any>로 열려 있고, form state와 API DTO 경계가 흐리다.
- Supabase/EmailJS env 접근에 non-null assertion이 사용된다.
- API response는 apiRequest<T = any>, JSON.parse() as T 구조로 런타임 검증이 약하다.
- util/helper/formatter/parser/mapper가 page 파일 내부에 집중된 지점이 있다.
- zod 의존성은 있으나 주요 request/form schema와 연결된 사용처는 확인 범위에서 발견하지 못했다.

이제 Session 7을 진행해라.
Session 7에서는 test / performance / accessibility / quality gate 계층을 리뷰해라.

검토 대상:
- unit test, integration test, e2e test 구조
- 테스트 커버리지와 핵심 workflow 테스트 누락
- rendering performance, memoization, lazy loading, bundle size
- accessibility, keyboard interaction, aria 속성, semantic HTML
- lint, formatter, build, typecheck, CI 설정
- architecture boundary를 검증하는 테스트/스크립트

리뷰 규칙:
- 코드 수정은 하지 말고 코드리뷰만 수행해라.
- 실제 코드 근거 없이 추측하지 마라.
- 주요 지적에는 파일 경로와 라인 번호를 포함해라.
- 확인하지 못한 내용은 "확인 필요"라고 표시해라.
- Session 1~6 문서를 참고하되 그대로 복사하지 말고 Session 7 관점에 맞게 연결해라.
- 최종 답변은 한국어로 작성해라.
```
