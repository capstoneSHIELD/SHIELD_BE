# Validation / Formatting / Parser Review

| ID | 심각도 | 파일 경로 | 라인 | 대상 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S6-VALIDATION-001 | High | `package.json`, `api/chat.js` | 103, 70 | request validation | `zod` 의존성은 있으나 legacy chat API route는 `req.body.message`를 schema 없이 사용한다. | 잘못된 body가 Gemini prompt/API 호출로 전달될 수 있다. | route request schema를 추가하거나 TS route handler로 전환한다. |
| S6-VALIDATION-002 | High | `components/pages/Simulation2Page.tsx` | 2378 | API request payload | PATCH body가 form state에서 `Record<string, any>`로 직접 만들어진다. | form validation과 API DTO 계약이 분리되지 않는다. | `buildUpdateSimulationBody(formState): UpdateSimulationBody`와 schema/guard를 둔다. |
| S6-PARSER-001 | Medium | `components/pages/Simulation2Page.tsx` | 313-325 | `formatAssistantContent` | assistant content에서 JSON 후보를 parse하고 표시 문자열을 만든다. | 표시 formatter가 parser 역할까지 수행한다. | parser와 formatter를 분리하고 parse 실패 정책을 명시한다. |
| S6-PARSER-002 | Medium | `components/pages/Simulation2Page.tsx` | 414-422 | `extractWarnings` | warning payload를 unknown에서 추출하지만 내부에 `as any`가 있다. | error/warning payload 변경 시 안전하게 narrowing되지 않는다. | `isWarningPayload` guard를 추가한다. |
| S6-PARSER-003 | Medium | `components/pages/PFMSimulationPage.tsx` | 196-219 | `parseLLMResponse` | LLM 응답에서 JSON block을 직접 추출/parse한다. | LLM 응답 format 변경 시 page 내부 로직이 깨진다. | legacy AI adapter/parser로 분리하고 parse result를 명시한다. |
| S6-MAPPER-001 | Medium | `lib/api/simulations.ts` | 136-142 | `normalizeSimulationCompositionDto` | API DTO normalization은 분리되어 있어 방향은 좋지만, admin API에도 유사 composition type이 있다. | admin/general mapper drift 가능성. | shared composition mapper로 통합 가능성 검토. |
| S6-FORMAT-001 | Medium | `components/pages/AdminPage3.tsx` | 262-339 | admin format/parse helpers | 날짜, byte, unknown, form numeric parser, download helper가 한 page 파일에 함께 있다. | admin 기능 확장 시 formatter 중복 가능성. | pure helper 파일로 분리하고 테스트한다. |
| S6-FORMAT-002 | Low | `lib/utils.ts` | 10 | `formatRelativeTime` | 공통 date formatter가 한국어 고정이다. | 다국어 UI에서 locale 정책과 어긋날 수 있다. | locale 인자 또는 i18n boundary와 연결한다. 확인 필요. |

## 확인 필요

- 실제 form validation 정책이 별도 백엔드 422에만 의존하는 설계인지 확인 필요하다.
- `zod`가 사용되지 않는다는 결론은 정적 검색 기준이다. 생성 코드나 외부 패키지 사용은 확인하지 않았다.
