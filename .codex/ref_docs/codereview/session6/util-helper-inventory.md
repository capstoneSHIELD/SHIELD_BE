# Util / Helper Inventory

Session 6 범위에서 확인한 주요 util/helper/formatter/parser/mapper 목록이다.

| 구분 | 파일 경로 | 라인 | 함수/모듈 이름 | 역할 | 사용 위치 | 비고 |
|---|---|---:|---|---|---|---|
| util | `lib/utils.ts` | 4 | `cn` | className merge | UI components | generic util |
| date util | `lib/utils.ts` | 10 | `formatRelativeTime` | 상대 시간 표시 | `JobResultListCard` 등 | 표시 formatter |
| error util | `lib/api/errors.ts` | 102 | `isApiErrorLike` | API error type guard | error handling | 실제 코드 근거 |
| error util | `lib/api/errors.ts` | 246 | `redactApiErrorDetails` | 민감정보 redaction | API error details | 보안 관점 긍정적 |
| error util | `lib/api/errors.ts` | 255 | `normalizeApiError` | API error view model 생성 | error UI | formatter/normalizer |
| error util | `lib/api/errors.ts` | 283 | `formatApiError` | error message formatting | AdminPage3 등 | 표시 formatter |
| helper | `lib/api/http.ts` | 12 | `withQuery` | query string 생성 | API service | endpoint helper |
| helper | `lib/api/http.ts` | 25 | `encodePathSegment` | path segment encoding | API service | endpoint helper |
| file util | `lib/api/http.ts` | 71 | `getFilenameFromContentDisposition` | download filename 추출 | result/viz/admin | admin에서 re-export |
| helper | `lib/api/http.ts` | 90 | `createBackendWebSocketUrl` | backend WS URL 생성 | job/viz | API boundary helper |
| mapper | `lib/api/simulations.ts` | 136 | `normalizeSimulationCompositionDto` | composition DTO normalize | simulations API | API DTO mapper |
| helper | `lib/api/jobs.ts` | 67 | `isTerminalJobStatus` | terminal status 판별 | job UI/workflow | status helper |
| helper | `lib/api/jobs.ts` | 71 | `isCancellableJobStatus` | cancellable status 판별 | job control | status helper |
| helper | `components/pages/adminPolling.ts` | 6 | `isActiveJobStatus` | admin active job 판별 | AdminPage3 query interval | admin page helper |
| helper | `components/pages/adminPolling.ts` | 10 | `getAdminActiveJobRefetchInterval` | admin polling interval 계산 | AdminPage3 | pure helper |
| mapper | `components/pages/simulation2/workflowMappers.ts` | 8 | `mapSimulationStageToWorkflowStage` | simulation status to workflow stage | Simulation2Page | workflow mapper |
| mapper | `components/pages/simulation2/workflowMappers.ts` | 26 | `mapJobStatusToWorkflowStage` | job status to workflow stage | Simulation2Page | workflow mapper |
| parser | `components/pages/simulation2/workflowMappers.ts` | 38 | `getJobStatusHint` | unknown status narrowing | websocket/event message | type guard 성격 |
| parser | `components/pages/simulation2/workflowMappers.ts` | 53 | `getJobMonitorStatusHint` | raw monitor payload parsing | job websocket | `Record<string, unknown>` 사용 |
| formatter | `components/pages/Simulation2Page.tsx` | 313 | `formatAssistantContent` | assistant content 표시 문자열 변환 | Simulation2Page | page 내부 |
| parser | `components/pages/Simulation2Page.tsx` | 414 | `extractWarnings` | warning payload 추출 | Simulation2Page | page 내부, `as any` 포함 |
| helper | `components/pages/Simulation2Page.tsx` | 438 | `computeExpectedProcessCount` | MPI process count 계산 | Simulation2Page | domain logic 성격 |
| error util | `components/pages/Simulation2Page.tsx` | 499 | `toWorkflowErrorDetails` | error를 workflow error view로 변환 | Simulation2Page | page 내부 |
| file util | `components/pages/Simulation2Page.tsx` | 534 | `saveBlobDownload` | blob download 실행 | Simulation2Page | AdminPage3에도 유사 함수 |
| formatter | `components/pages/AdminPage3.tsx` | 262 | `formatDate` | 날짜 표시 | AdminPage3 | page 내부 |
| formatter | `components/pages/AdminPage3.tsx` | 296 | `formatUnknown` | unknown 값 표시 | AdminPage3 | page 내부 |
| formatter | `components/pages/AdminPage3.tsx` | 303 | `formatBytes` | byte 표시 | AdminPage3 | page 내부 |
| parser | `components/pages/AdminPage3.tsx` | 312 | `parseOptionalInteger` | optional integer input parse | AdminPage3 forms | page 내부 |
| parser | `components/pages/AdminPage3.tsx` | 318 | `parseOptionalNumber` | optional number input parse | AdminPage3 forms | page 내부 |
| validator | `components/pages/PFMSimulationPage.tsx` | 158 | `validateParams` | legacy simulation param validation | PFMSimulationPage | page 내부 |
| parser | `components/pages/PFMSimulationPage.tsx` | 196 | `parseLLMResponse` | LLM response parser | PFMSimulationPage | page 내부, JSON parse |
| formatter | `components/pages/PFMSimulationPage.tsx` | 229 | `formatScientific` | scientific notation format | PFMSimulationPage | page 내부 |
| parser | `components/simulation/trame/CompositeDialog.tsx` | 39 | `parsePositiveIntegerInput` | integer input parse | CompositeDialog | exported pure helper |
| parser | `components/simulation/trame/TrameControlPanel.tsx` | 49 | `parseCustomScalarRange` | scalar range parse | TrameControlPanel | exported pure helper |
| formatter | `components/simulation/trame/TrameExportCenter.tsx` | 97 | `buildExportDownloadFilename` | export filename 생성 | TrameExportCenter | exported pure helper |

## 확인 필요

- page 내부 helper 중 테스트가 있는 함수와 없는 함수의 목록은 별도 test coverage 리뷰에서 확인 필요하다.
