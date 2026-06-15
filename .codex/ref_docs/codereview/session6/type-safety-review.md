# Type Safety Review

| ID | 심각도 | 파일 경로 | 라인 | 대상 type/code | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S6-TYPE-001 | High | `tsconfig.json` | 8, 10, 29, 32 | TypeScript compiler option | `allowJs: true`, `strict: false`, `noImplicitAny: false`, `strictNullChecks: false`로 핵심 타입 안전장치가 꺼져 있다. | `any`, null 누락, JS API route의 타입 누락이 빌드에서 걸러지지 않는다. | 신규/핵심 리팩토링 영역부터 `strictNullChecks`, `noImplicitAny`를 단계적으로 켠다. |
| S6-ANY-001 | High | `components/pages/simulation2/workflowTypes.ts` | 72 | `WorkflowState.parameters` | workflow 핵심 파라미터가 `Record<string, any>`다. | simulation parameter 계약 변경이 컴파일 단계에서 드러나지 않는다. | `SimulationParametersDto`, `WorkflowParameters`, `EditableSimulationParameters`를 분리한다. |
| S6-ANY-002 | High | `components/pages/Simulation2Page.tsx` | 2378 | `patchBody: Record<string, any>` | page component가 API update payload를 `any` record로 직접 조립한다. | form/view state와 API request DTO가 결합된다. | DTO builder/mapper를 분리하고 `UpdateSimulationBody`를 명시한다. |
| S6-ANY-003 | Medium | `lib/apiClient.ts` | 380 | `apiRequest<T = any>` | API 응답 기본 generic이 `any`다. | call site가 타입 명시를 빼먹어도 컴파일에서 드러나지 않는다. | 기본값을 `unknown`으로 바꾸거나 endpoint wrapper에서만 typed response를 반환하게 한다. |
| S6-ASSERT-001 | Medium | `lib/apiClient.ts` | 395 | `JSON.parse(text) as T` | JSON 응답을 런타임 검증 없이 `T`로 단정한다. | 응답 shape 불일치가 UI 렌더 시점까지 늦게 발견된다. | 핵심 endpoint부터 parser/guard/schema를 추가한다. |
| S6-ASSERT-002 | Medium | `lib/api/labserverTrameClient.ts` | 500-502 | `response.json() as Promise<T>` | Labserver 응답을 제네릭으로 단정한다. | 외부 gateway contract 변경 시 런타임 오류 가능성이 있다. | gateway 응답별 최소 guard 또는 schema 검증을 도입한다. |
| S6-ASSERT-003 | Medium | `components/pages/Simulation2Page.tsx` | 417 | `(obj.details as any)?.warnings` | warning 추출 중 `any` assertion으로 구조를 우회한다. | 에러 details shape 변경 시 warning 누락 또는 잘못된 표시 가능성이 있다. | `isRecord(details)` 기반 narrowing으로 대체한다. |
| S6-NULLABLE-001 | High | `lib/supabaseClient.ts` | 5-6 | env non-null assertion | `NEXT_PUBLIC_SUPABASE_URL!`, `NEXT_PUBLIC_SUPABASE_ANON_KEY!`를 직접 단정한다. | env 누락 시 초기화 시점에 불명확한 실패가 발생한다. | required env helper로 검증하고 에러 메시지를 표준화한다. |
| S6-NULLABLE-002 | High | `components/pages/ContactPage.tsx` | 26-29 | EmailJS env non-null assertion | EmailJS public env 세 개를 `!`로 직접 사용한다. | 배포 누락 시 사용자가 제출 버튼을 누른 뒤 실패할 수 있다. | contact integration config helper와 disabled/error fallback을 둔다. |
| S6-TYPE-002 | Medium | `components/pages/HomePage.tsx` | 18, 21, 22 | CMS state | `pageContent`, `achievements`, `latestNews`가 `any` 기반이다. | CMS schema drift가 컴파일 단계에서 감지되지 않는다. | CMS DTO와 Home view model을 분리한다. |
| S6-TYPE-003 | Medium | `components/pages/EditPageContentForm.tsx` | 21, 24, 62-64 | CMS edit form state | content와 publication list가 `Record<string, any>`/`any[]`로 열려 있다. | form field 오타와 nested content shape 오류가 누락된다. | pageKey별 edit form 타입 또는 discriminated union을 정의한다. |
| S6-ANY-004 | Low | `components/reactbits/ColorBends.tsx` | 180 | Three renderer assertion | Three 버전 호환을 위해 `as any`가 사용된다. | 라이브러리 API 변경을 타입이 보호하지 못한다. | wrapper type 또는 `THREE.WebGLRenderer`의 지원 버전 확인 후 좁은 assertion으로 제한한다. |

## 확인 필요

- `strict` 옵션을 켰을 때 실제 오류량과 우선순위는 별도 타입체크 실행이 필요하다.
- `apiRequest<T>` 기본값 변경은 전체 call site 영향이 크므로 단계적 적용 계획이 필요하다.
