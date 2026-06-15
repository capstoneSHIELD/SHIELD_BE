# Util Responsibility Review

| ID | 심각도 | 파일 경로 | 라인 | util/helper | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S6-UTIL-001 | High | `components/pages/Simulation2Page.tsx` | 221, 313, 414, 438, 499, 534 | `normalizeComposition`, `formatAssistantContent`, `extractWarnings`, `computeExpectedProcessCount`, `toWorkflowErrorDetails`, `saveBlobDownload` | formatter, parser, DTO mapper, domain 계산, file util이 page component 파일에 집중되어 있다. | page 변경과 domain/API 변환 변경이 결합되고 단위 테스트가 어려워진다. | `simulation2` 하위의 `workflowMapper`, `parameterMapper`, `errorMapper`, `downloadUtil`로 분리한다. |
| S6-FORMATTER-001 | Medium | `components/pages/AdminPage3.tsx` | 262-339 | `formatDate`, `formatUnknown`, `formatBytes`, `saveBlobDownload` 등 | admin page 내부에 표시 formatter와 file util이 집중되어 있다. | admin tab 분리 시 중복 복사 또는 큰 파일 유지가 발생한다. | admin view util 또는 common formatter로 이동한다. |
| S6-VALIDATOR-001 | Medium | `components/pages/PFMSimulationPage.tsx` | 158, 196 | `validateParams`, `parseLLMResponse` | legacy simulation validation/parser가 page 내부에 있다. | form UI와 LLM/domain parser 변경이 결합된다. | legacy simulation parser/validator 모듈로 분리한다. |
| S6-MAPPER-001 | Medium | `components/pages/simulation2/workflowMappers.ts` | 38, 53, 63 | `getJobStatusHint`, `getJobMonitorStatusHint` | raw websocket payload를 `Record<string, unknown>`로 다루지만 schema 또는 명시적 DTO가 없다. | websocket message contract 변경 시 일부 필드 누락이 조용히 fallback될 수 있다. | `JobMonitorMessageDto` union과 parser result를 정의한다. |
| S6-UTIL-002 | Low | `lib/api/http.ts` | 12, 90, 103 | `withQuery`, `createBackendWebSocketUrl`, `sendKeepaliveRequest` | API boundary helper로 역할은 적절하지만 `params: object`가 넓다. | query param 직렬화 대상이 컴파일에서 제한되지 않는다. | `QueryParams` 타입을 함수 인자에 일관 적용한다. |
| S6-FORMATTER-002 | Low | `lib/api/errors.ts` | 62, 75, 255, 283 | `normalizeApiError`, `formatApiError` | error normalization과 표시용 title/label constant가 한 파일에 함께 있다. | 현재는 응집도가 높지만 audience별 메시지가 늘면 파일 책임이 커질 수 있다. | user-facing message catalog와 raw error normalization 경계를 유지한다. |

## 실제 코드 근거와 해석

- 실제 코드 근거: 위 표의 파일/라인에서 helper 함수가 확인된다.
- 해석: page 내부 helper가 항상 문제는 아니지만, Session 1~5에서 같은 파일들의 책임 집중이 반복 지적되었기 때문에 리팩토링 후보로 분류했다.
