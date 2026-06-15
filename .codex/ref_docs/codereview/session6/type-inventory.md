# Type Inventory

Session 6에서 확인한 주요 TypeScript type/interface/DTO/schema 후보 목록이다. 전체 type의 완전한 목록이 아니라, 리팩토링 영향도가 있는 대표 항목 중심으로 정리했다.

| 구분 | 파일 경로 | 라인 | 이름 | 역할 | 사용 위치 | 비고 |
|---|---|---:|---|---|---|---|
| domain type | `types.ts` | 1 | `Member` | 구성원 데이터 모델 | members/professor 계열 화면 | 실제 코드 근거 |
| domain type | `types.ts` | 19 | `Alumni` | 동문 데이터 모델 | alumni 화면 | 실제 코드 근거 |
| API request type | `lib/auth.ts` | 16 | `LoginRequest` | 로그인 요청 DTO | auth API 호출 | 실제 코드 근거 |
| API response type | `lib/auth.ts` | 21 | `AuthUser` | 인증 사용자 응답 일부 | 로그인/세션 처리 | 실제 코드 근거 |
| API response type | `lib/auth.ts` | 30 | `LoginResponse` | 로그인 응답 DTO | token 저장 흐름 | 실제 코드 근거 |
| DTO | `lib/auth.ts` | 38 | `AccountRequest` | 계정 신청 payload | signup/account request | 실제 코드 근거 |
| DTO | `lib/auth.ts` | 56 | `AccountRequestStatus` | 계정 신청 상태 응답 | login page 상태 표시 | interface로 정의되어 union status와 이름이 혼동될 수 있음 |
| union type | `lib/api/simulations.ts` | 16 | `SimulationStatus` | simulation 상태값 | simulation API, list card | `workflowTypes.ts`에도 중복 |
| DTO | `lib/api/simulations.ts` | 24 | `Composition` | alloy composition 구조 | simulation detail/update | admin API에도 유사 정의 |
| DTO | `lib/api/simulations.ts` | 29 | `SimulationCompositionDto` | composition 응답 허용 형태 | composition normalizer | `Record<string, unknown>` 포함 |
| API response type | `lib/api/simulations.ts` | 31 | `SimulationWarning` | simulation warning 응답 | workflow warning 표시 | workflow local type과 유사 |
| API response type | `lib/api/simulations.ts` | 45 | `SimulationSummary` | simulation 목록 응답 | `SimulationListCard`, `Simulation2Page` | 실제 코드 근거 |
| API response type | `lib/api/simulations.ts` | 54 | `SimulationDetail` | simulation 상세 응답 | `getSimulation`, restore workflow | `parameters: Record<string, unknown>` |
| API request type | `lib/api/simulations.ts` | 83 | `CreateSimulationBody` | simulation 생성 요청 | `createSimulation` | 실제 코드 근거 |
| API request type | `lib/api/simulations.ts` | 88 | `UpdateSimulationBody` | simulation 수정 요청 | `updateSimulation` | `parameters?: Record<string, unknown>` |
| union type | `lib/api/jobs.ts` | 4 | `JobStatus` | job 상태값 | job API, workflow, cards | admin API에도 중복 |
| API response type | `lib/api/jobs.ts` | 16 | `JobSummary` | job 목록 응답 | job/result list | admin API에도 유사 정의 |
| API response type | `lib/api/jobs.ts` | 26 | `JobDetail` | job 상세 응답 | job polling/detail | 실제 코드 근거 |
| API response type | `lib/api/jobs.ts` | 35 | `JobEvent` | job event 응답 | event list, monitor | workflow local type과 유사 |
| API request type | `lib/api/jobs.ts` | 42 | `SubmitSimulationJobBody` | job 제출 요청 | submit job | 실제 코드 근거 |
| API response type | `lib/api/jobs.ts` | 46 | `SubmitSimulationJobResponse` | job 제출 응답 | workflow transition | `warnings: Array<Record<string, unknown>>` |
| union type | `lib/api/results.ts` | 11 | `ResultStatus` | result 상태값 | result API/UI | admin API에도 중복 |
| union type | `lib/api/results.ts` | 12 | `ResultFileType` | result file type | result file list | admin API에도 중복 |
| API response type | `lib/api/results.ts` | 14 | `ResultSummary` | result 목록 응답 | result cards | admin API에도 유사 정의 |
| API response type | `lib/api/results.ts` | 36 | `ResultDetail` | result 상세 응답 | result explorer | `summary: Record<string, unknown>` |
| API response type | `lib/api/results.ts` | 51 | `ResultFieldsResponse` | result field 목록 응답 | result explorer | 실제 코드 근거 |
| union type | `lib/api/visualizations.ts` | 11 | `VisualizationStatus` | visualization 상태값 | visualization API/UI | workflow local type에도 중복 |
| DTO | `lib/api/admin.ts` | 33-40 | `AccountRequestStatus`, `UserRole`, `SimulationStatus`, `JobStatus`, `ResultStatus`, `VisualizationStatus` | admin API 상태 타입 | AdminPage3 | simulation/job/result 상태 중복 |
| DTO | `lib/api/admin.ts` | 149 | `Composition` | admin simulation composition | admin simulation detail | `lib/api/simulations.ts`와 유사 |
| DTO | `lib/api/admin.ts` | 154 | `SimulationCompositionDto` | admin composition 응답 형태 | admin simulation detail | `Record<string, unknown>` 포함 |
| DTO | `lib/api/admin.ts` | 170 | `SimulationDetail` | admin simulation 상세 응답 | AdminPage3 | 일반 simulation DTO와 유사 |
| DTO | `lib/api/admin.ts` | 198 | `JobSummary` | admin job 목록 응답 | AdminPage3 | `lib/api/jobs.ts`와 유사 |
| DTO | `lib/api/admin.ts` | 224 | `ResultSummary` | admin result 목록 응답 | AdminPage3 | `lib/api/results.ts`와 유사 |
| DTO | `lib/api/admin.ts` | 246 | `ResultDetail` | admin result 상세 응답 | AdminPage3 | `lib/api/results.ts`와 유사 |
| store state type | `components/pages/simulation2/workflowTypes.ts` | 44 | `WorkflowState` | simulation2 workflow view state | `Simulation2Page` | `parameters: Record<string, any>` |
| DTO-like local type | `components/pages/simulation2/workflowTypes.ts` | 22 | `JobEvent` | workflow event 표시 타입 | `Simulation2Page` | API `JobEvent`와 유사 |
| DTO-like local type | `components/pages/simulation2/workflowTypes.ts` | 29 | `SimulationWarning` | workflow warning 표시 타입 | `Simulation2Page` | API `SimulationWarning`와 유사 |
| component props type | `components/pages/Simulation2Page.tsx` | 562 | `Simulation2PageProps` | page props | `app/simulation2/page.tsx` | 실제 코드 근거 |
| form type | `components/pages/Simulation2Page.tsx` | 131 | `EditableParams` | manual parameter form state | `Simulation2Page` | API payload와 mapper 경계 확인 필요 |
| component state type | `components/pages/Simulation2Page.tsx` | 123 | `ChatMessage` | chat message UI state | `Simulation2Page` | API message DTO와 분리 여부 확인 필요 |
| form type | `components/pages/AdminPage3.tsx` | 123-160 | `ReviewDialogState`, `UserUpdateDialogState`, `FieldFileFilterState`, `VisualizationCreateFormState`, `ScreenshotFormState` | admin dialog/form state | AdminPage3 | 파일 내부 집중 |
| form/domain type | `components/pages/PFMSimulationPage.tsx` | 60 | `SimulationParams` | legacy PFM simulation form/domain params | PFMSimulationPage | page 내부 정의 |
| parser result type | `components/pages/PFMSimulationPage.tsx` | 86 | `ParsedResponse` | LLM response parse result | PFMSimulationPage | `config: any` 포함 |
| validation schema | `package.json` | 103 | `zod` | schema 라이브러리 의존성 | 확인 범위에서 실제 schema 사용처 미발견 | 확인 필요 |

## 확인 필요

- 백엔드 OpenAPI 또는 명세와 frontend DTO의 nullable/optional 필드가 일치하는지 추가 검증이 필요하다.
- `zod` 의존성은 확인되지만 실제 source에서 schema-first validation이 적용되는지는 확인 범위에서 발견하지 못했다.
