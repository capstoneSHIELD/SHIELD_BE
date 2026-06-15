# Session 1 주요 문제 후보

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S1-ARCH-001 | High | architecture/container | `components/pages/Simulation2Page.tsx` | 589 | simulation2 page container가 UI, workflow orchestration, API 호출, WebSocket lifecycle, 상태 관리를 과도하게 보유 | 변경 영향도와 회귀 위험 증가, 단위 테스트 어려움 | `useSimulationWorkflow`, `useJobMonitor`, `useVisualizationSession`, `useSimulationDraft` 등으로 책임 분리 |
| S1-ARCH-002 | High | architecture/persistence | `components/pages/HomePage.tsx` | 11 | CMS page component가 Supabase client와 DB query를 직접 사용 | UI와 persistence 결합, 테스트/권한 정책 변경에 취약 | CMS service/repository 또는 query hook 계층 도입 |
| S1-ARCH-003 | High | architecture/persistence | `components/pages/NoticeBoardPage.tsx` | 58 | 게시판 page component에서 Supabase query/mutation을 직접 수행 | route/component와 데이터 접근 정책 결합, mutation 회귀 위험 | notice domain API/hook로 조회/수정/삭제 분리 |
| S1-ARCH-004 | Medium | architecture/container | `components/pages/AdminPage3.tsx` | 483 | admin container 하나가 query params, 권한, query/mutation, UI 렌더링을 모두 관리 | admin 기능 추가 시 파일 복잡도와 충돌 가능성 증가 | tab/feature별 container와 hook으로 점진 분리 |
| S1-DEPENDENCY-001 | Medium | dependency/api | `lib/api/admin.ts` | 198 | admin API 파일에 job/result/visualization 계열 DTO와 wrapper가 공존하며 일부 중복 가능성 존재 | backend contract 변경 시 generic API 타입과 admin 타입 drift 위험 | shared DTO 재사용/mapper 분리, admin view type 명확화 |
| S1-STRUCT-001 | Medium | structure/state | `components/pages/Simulation2Page.tsx` | 592 | 전용 store/state 계층 없이 page-local state가 대형 컨테이너에 집중 | 상태 전파/초기화/cleanup 흐름 파악 어려움 | workflow 단위 hook과 reducer 도입 검토 |
| S1-TYPE-001 | Medium | type-safety | `tsconfig.json` | 10 | TypeScript strict 모드가 꺼져 있음 | null/any/계약 위반을 컴파일 타임에 놓칠 가능성 증가 | 신규/리팩토링 영역부터 strict-friendly 타입 작성, 단계적 strict 옵션 강화 검토 |
| S1-TYPE-002 | Medium | type-safety | `lib/apiClient.ts` | 380 | `apiRequest<T = any>` 기본 타입이 `any` | API 응답 타입 누락이 조용히 확산될 수 있음 | 기본 generic을 `unknown`으로 바꾸는 것은 영향 큼. 우선 call site 타입 명시 강화 |
| S1-TYPE-003 | Medium | type-safety/workflow | `components/pages/simulation2/workflowTypes.ts` | 72 | workflow parameters가 `Record<string, any>` | simulation parameter contract 변경 시 타입 검증 어려움 | parameter schema/type을 domain DTO와 view/form state로 분리 |
| S1-EXTERNAL-001 | Low | external-integration | `api/chat.js` | 80 | legacy Gemini API route가 PFM API client/error normalization과 별도 흐름 | 장애/보안/로깅 정책이 일관되지 않을 수 있음 | 유지 여부 확인 후 adapter/error response 표준화 |
| S1-EXTERNAL-002 | Low | external-integration | `components/pages/ContactPage.tsx` | 25 | EmailJS browser SDK 직접 호출 | 외부 연동 실패 처리/계약 관리 기준 확인 필요 | 외부 연동 정책 문서화 및 wrapper 도입 검토 |
| S1-TEST-001 | Suggestion | test/architecture | `scripts/check-pfm-api-boundaries.mjs` | 6 | PFM simulation boundary guard는 있으나 CMS/Supabase boundary guard는 확인되지 않음 | CMS 리팩토링 중 UI-persistence 결합 재발 가능 | CMS service boundary 도입 후 정적 검사 또는 테스트 추가 |

## 확인 필요 항목

- Supabase RLS/권한 정책과 현재 UI 직접 호출 구조가 의도된 설계인지 확인 필요.
- legacy AI assistant와 `api/chat.js`가 현재 제품 범위에 포함되는지 확인 필요.
- `store` 디렉터리 부재가 의도된 상태 관리 전략인지 확인 필요.
- route guard 정책은 Session 2에서 page/layout 계층 중심으로 추가 검토 필요.
