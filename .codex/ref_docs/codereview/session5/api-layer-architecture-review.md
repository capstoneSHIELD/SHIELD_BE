# API Layer Architecture Review

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---|---:|---|---|---|
| S5-APIARCH-001 | High | component-service coupling | `components/pages/Simulation2Page.tsx` | 2255 | chat/session/simulation/job/result/viz API orchestration이 component에 집중 | use-case 변경 영향이 UI와 state 전체에 전파 | workflow/job/viz service hook으로 분리 |
| S5-APIARCH-002 | High | component-service coupling | `components/pages/AdminPage3.tsx` | 551 | admin query/mutation/cache policy가 component 내부에 집중 | admin 기능 추가 시 회귀 위험 증가 | tab별 API hook/container 분리 |
| S5-SERVICE-001 | High | service layer | `components/pages/NoticeBoardPage.tsx` | 58 | Supabase query/mutation이 page component에 직접 존재 | persistence 정책과 UI 결합 | CMS service/repository 또는 query hook 도입 |
| S5-SERVICE-002 | High | service layer | `components/pages/EditNoticePage.tsx` | 85 | Supabase storage upload/remove와 DB update가 form에 직접 존재 | storage rollback/권한/error 정책 분산 | attachment adapter와 edit use-case hook |
| S5-ENDPOINT-001 | Medium | endpoint management | `lib/apiClient.ts` | 192 | PFM base URL/path 조합은 공통화되어 있음 | 긍정적 구조 | 유지 |
| S5-INTERCEPTOR-001 | Medium | interceptor | `lib/apiClient.ts` | 278 | 401 refresh retry는 있지만 timeout/network retry 정책은 없음 | 장애 UX가 caller별로 달라짐 | retry/timeout policy를 공통 옵션화 |
| S5-AUTH-001 | Medium | auth header | `lib/auth.ts` | 66 | token storage helper가 `lib/auth.ts`와 `lib/apiClient.ts`에 중복 | token persistence 정책 drift 가능 | token storage adapter 단일화 |
| S5-ERRORNORM-001 | Low | error normalization | `lib/api/errors.ts` | 255 | normalized error model과 redaction이 존재 | 긍정적 구조 | legacy/CMS error에도 확장 검토 |
| S5-APIARCH-003 | Suggestion | domain API module | `scripts/check-pfm-api-boundaries.mjs` | 6 | PFM simulation boundary guard만 확인됨 | CMS boundary 회귀 보호 부재 | CMS boundary 도입 후 정적 검사 확장 |

## 추론

- 지금은 API helper가 없는 것이 아니라, helper를 조합하는 use-case/service hook 경계가 부족한 상태다.
