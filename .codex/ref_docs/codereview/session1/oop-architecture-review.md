# 객체지향 관점 아키텍처 리뷰

프론트엔드에 class가 많지 않더라도, 모듈을 책임을 가진 객체처럼 보고 응집도/결합도/추상화 수준을 평가했다.

| 평가 항목 | 현재 상태 | 문제 여부 | 근거 파일/라인 | 리팩토링 방향 |
|---|---|---|---|---|
| 책임 분리 | PFM API helper는 리소스별로 분리되어 있으나, `Simulation2Page`가 workflow orchestration 대부분을 가진다 | 문제 있음 | `components/pages/Simulation2Page.tsx:589`, `components/pages/Simulation2Page.tsx:592`, `components/pages/Simulation2Page.tsx:1444`, `components/pages/Simulation2Page.tsx:2238`, `components/pages/Simulation2Page.tsx:2437` | chat/session/job/result/viz 흐름을 hook 또는 feature service로 분리 |
| 응집도 | `components/pages/simulation2/*`에 workflow 타입/매퍼/토큰 helper가 일부 분리되어 응집 단서가 있다 | 부분 양호 | `components/pages/simulation2/workflowTypes.ts:44`, `components/pages/simulation2/workflowMappers.ts:8`, `components/pages/simulation2/jobMonitorSession.ts:10` | 순수 helper 분리를 확대하고, side effect hook은 별도 파일로 분리 |
| 결합도 | CMS page components가 Supabase client와 DB/storage 구현에 직접 결합되어 있다 | 문제 있음 | `components/pages/HomePage.tsx:11`, `components/pages/NoticeBoardPage.tsx:58`, `components/pages/EditMemberPage.tsx:74` | CMS service/repository 또는 query hook 계층 도입 |
| 추상화 수준 | PFM은 API helper 계층이 있으나, admin helper에는 admin DTO와 shared resource wrapper가 섞여 있다 | 문제 있음 | `lib/api/admin.ts:130`, `lib/api/admin.ts:198`, `lib/api/admin.ts:246`, `lib/api/admin.ts:429`, `lib/api/admin.ts:459` | 공통 DTO와 admin 전용 view DTO를 분리하고 alias/mapper 경계 명확화 |
| 의존성 방향 | route/page에서 PFM API 세부 endpoint를 직접 다루지 않도록 guard script가 존재한다 | 양호 | `scripts/check-pfm-api-boundaries.mjs:6`, `scripts/check-pfm-api-boundaries.mjs:11`, `scripts/check-pfm-api-boundaries.mjs:19` | guard 대상을 CMS/Supabase boundary에도 확대할지 검토 |
| 변경 영향도 | `Simulation2Page`와 `AdminPage3`는 파일 규모가 크고 상태/side effect가 많아 작은 변경도 회귀 범위가 넓다 | 문제 있음 | `components/pages/Simulation2Page.tsx:589`, `components/pages/AdminPage3.tsx:483`, `components/pages/AdminPage3.tsx:666`, `components/pages/AdminPage3.tsx:1139` | 먼저 테스트로 현재 동작을 고정한 뒤, vertical slice 단위로 분리 |
| 테스트 용이성 | PFM API boundary test는 존재하지만 대형 컨테이너 내부 side effect는 독립 테스트가 어렵다 | 문제 있음 | `scripts/check-pfm-api-boundaries.mjs:63`, `vitest.config.ts:11`, `components/pages/Simulation2Page.tsx:611`, `components/pages/AdminPage3.tsx:597` | pure mapper/formatter부터 테스트하고 side effect hook 테스트를 추가 |
| 도메인 모델과 UI 모델 분리 | API DTO, workflow state, view state가 일부 분리되어 있으나 `Record<string, any>`와 `apiRequest<T = any>`가 남아 있다 | 문제 있음 | `components/pages/simulation2/workflowTypes.ts:72`, `lib/apiClient.ts:380`, `tsconfig.json:10`, `tsconfig.json:29` | API DTO, domain state, form/view state 타입을 명시적으로 분리 |
| 인증/권한 책임 | `app/simulation2/page.tsx`와 `AdminPage3` 내부에 인증/권한 처리가 존재한다 | 확인 필요 | `app/simulation2/page.tsx:18`, `app/simulation2/page.tsx:23`, `components/pages/AdminPage3.tsx:1157`, `components/pages/AdminPage3.tsx:1166` | route guard/hook/policy helper로 표준화 가능 여부를 Session 2에서 검토 |
| 외부 연동 경계 | EmailJS, Gemini legacy route가 PFM API client와 별도 흐름으로 존재한다 | 확인 필요 | `components/pages/ContactPage.tsx:25`, `api/chat.js:63`, `api/chat.js:80`, `lib/api/legacyAiChat.ts:11` | 유지 대상과 계약을 확인한 뒤 adapter/error handling 기준 통일 |

## 요약 평가

- 가장 큰 구조 리스크는 page container가 application service 역할까지 수행하는 점이다.
- PFM API helper 계층은 아키텍처 방향이 비교적 명확하고, boundary script로 일부 회귀를 막고 있다.
- CMS/Supabase 영역은 UI와 persistence가 강하게 결합되어 있어 PFM 계층 규칙과 일관성이 낮다.
- 타입 엄격성이 낮아 리팩토링 중 계약 위반을 컴파일 타임에 잡기 어렵다.
