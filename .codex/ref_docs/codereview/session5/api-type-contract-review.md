# API Type / Contract Review

| ID | 심각도 | 파일 경로 | 라인 | 타입/계약 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S5-TYPE-001 | Medium | `lib/apiClient.ts` | 380 | `apiRequest<T = any>` | 기본 응답 타입이 `any` | 타입 명시 누락이 UI까지 전파될 수 있음 | 신규 호출부 타입 명시, 장기적으로 `unknown` 검토 |
| S5-DTO-001 | Medium | `lib/api/admin.ts` | 391 | admin simulation/job/result wrapper | admin wrapper가 공용 result/visualization helper와 admin DTO를 함께 노출 | API contract drift 위험 | shared DTO와 admin view model 경계 명확화 |
| S5-CONTRACT-001 | Medium | `components/pages/Simulation2Page.tsx` | 2378 | PATCH body | component에서 `Record<string, any>` patch body를 조립 | request DTO와 form/view state 혼합 | parameter mapper/DTO builder 분리 |
| S5-VALIDATION-001 | Medium | `api/chat.js` | 70 | request body | `req.body.message` runtime validation 확인되지 않음 | 잘못된 input이 SDK prompt로 직접 전달될 수 있음 | zod/manual schema validation |
| S5-TYPE-002 | Medium | `components/pages/HomePage.tsx` | 18 | CMS data state | `pageContent`, achievements/news가 `any` 중심 | CMS schema drift를 컴파일 타임에 잡기 어려움 | CMS view model 타입화 |
| S5-CONTRACT-002 | Low | `lib/api/legacyAiChat.ts` | 21 | legacy response parsing | response JSON을 type assertion으로 변환 | runtime response shape 불일치 가능 | 최소 parser/validator 추가 |
| S5-CONTRACT-003 | Low | `lib/api/results.ts` | 120 | field files query | helper가 query building을 담당 | 긍정적 경계 | 유지. request safety는 caller에서 보강 |

## 확인 필요

- backend OpenAPI/계약에서 nullable/optional field와 frontend DTO가 모두 일치하는지는 Session 6에서 추가 확인 필요.
