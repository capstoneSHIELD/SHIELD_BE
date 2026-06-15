# Session 6 Findings

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 요약 | 영향 | 리팩토링 방향 |
|---|---|---|---|---:|---|---|---|
| S6-TYPE-001 | High | type safety | `tsconfig.json` | 8, 10, 29, 32 | TypeScript strict 계열 옵션이 꺼져 있다. | any/null/API 계약 오류가 컴파일에서 걸러지지 않는다. | 리팩토링 대상부터 strict-friendly 타입을 만들고 옵션을 단계적으로 강화한다. |
| S6-DUPTYPE-001 | High | duplicate type | `lib/api/simulations.ts`, `lib/api/admin.ts`, `components/pages/simulation2/workflowTypes.ts` | 16, 36, 4 | `SimulationStatus` 중복 정의 | 상태값 drift 위험 | API status 단일화, workflow stage mapper 유지 |
| S6-DUPTYPE-002 | High | duplicate type | `lib/api/jobs.ts`, `lib/api/admin.ts` | 4, 37 | `JobStatus` 중복 정의 | admin/job UI 상태 불일치 가능 | shared job status 도입 |
| S6-ANY-001 | High | any/unknown | `components/pages/simulation2/workflowTypes.ts` | 72 | workflow parameters가 `Record<string, any>` | simulation parameter 계약 검증 약화 | DTO/form/workflow parameter 타입 분리 |
| S6-ANY-002 | High | API DTO | `components/pages/Simulation2Page.tsx` | 2378 | API PATCH body를 page에서 `Record<string, any>`로 조립 | UI와 API request 결합 | request DTO builder/mapper 분리 |
| S6-ENV-001 | High | env | `lib/supabaseClient.ts` | 5-6 | Supabase env non-null assertion | 배포 누락 시 불명확한 실패 | required env helper 도입 |
| S6-ENV-002 | High | env | `components/pages/ContactPage.tsx` | 26-29 | EmailJS env non-null assertion | submit 시점 runtime failure 가능 | integration config helper 도입 |
| S6-ASSERT-001 | Medium | type assertion | `lib/apiClient.ts` | 380, 395 | API 응답을 `T = any`, `as T`로 단정 | 응답 shape 오류가 런타임으로 밀림 | parser/guard/schema 도입 |
| S6-DTO-001 | Medium | API DTO | `lib/api/admin.ts` | 149-308 | admin API 파일에 simulation/job/result/viz DTO가 함께 있음 | DTO drift와 파일 책임 확대 | shared DTO와 admin view type 분리 |
| S6-CMS-001 | Medium | form type | `components/pages/HomePage.tsx`, `components/pages/EditPageContentForm.tsx` | 18, 21 | CMS content state가 `any`/`Record<string, any>` | CMS schema drift 감지 어려움 | CMS DTO/view model/form model 정의 |
| S6-VALIDATION-001 | Medium | validation | `api/chat.js`, `package.json` | 70, 103 | zod 의존성은 있으나 legacy chat request schema 적용 근거 없음 | 잘못된 입력이 external API로 전달 가능 | request schema 또는 manual guard 추가 |
| S6-UTIL-001 | Medium | util/helper | `components/pages/Simulation2Page.tsx` | 221-534 | parser/formatter/mapper/domain util이 page에 집중 | 테스트와 변경 영향도 악화 | pure helper 모듈로 분리 |
| S6-CONFIG-001 | Medium | config | `next.config.ts` | 4-8 | 모든 HTTPS 이미지 host 허용 | 이미지 출처 정책 관리 약화 | 실제 도메인 allowlist로 제한 |
| S6-DEAD-001 | Medium | dead code | `tsconfig.json` | 30-31 | unused parameter/local 검출 꺼짐 | 미사용 type/util 누적 가능 | lint/tsconfig 품질 게이트 강화 |
| S6-CONST-001 | Low | constant | `components/pages/Simulation2Page.tsx` | 545-547 | workflow polling/reconnect constant가 page에 위치 | hook 분리 시 설정 이동 필요 | workflow config로 분리 |
| S6-ENUM-001 | Low | constant | `components/simulation/VisualizationControlBar.tsx`, `components/simulation/trame/TrameControlPanel.tsx` | 29, 33 | colormap 옵션 중복 가능성 | 옵션 drift 가능 | shared constant 또는 도메인별 명시 |

## 확인 필요

- admin DTO와 일반 DTO가 의도적으로 다른 계약인지 백엔드 API 명세 확인 필요.
- 실제 dead code/circular import 여부는 별도 도구 실행 필요.
