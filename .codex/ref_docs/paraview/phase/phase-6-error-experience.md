# Phase 6. Error Experience

## 1. 작업 유형 판단

- 작업 유형: 문서화 + 컨트롤러/라우트/UI 경계 작업 + API 설계 + 리팩토링 계획
- 적용 우선 규칙:
  - `.codex/ai_rule_developer/GLOBAL_RULES.md`
  - `.codex/ai_rule_developer/API_DESIGN_RULES.md`
  - `.codex/ai_rule_developer/ARCHITECTURE_RULES.md`
  - `.codex/ai_rule_developer/SERVICE_LAYER_RULES.md`
  - `.codex/ai_rule_developer/EXTERNAL_INTEGRATION_RULES.md`
  - `.codex/ai_rule_developer/DOCUMENT_RULE.md`
- 주의:
  - 이 문서는 사용자 요청에 따라 `.codex/ref_docs` 아래에 작성한다.
  - 실제 구현 단계에서 공식 프로젝트 오류 UX, 상태 전이, 공통 컴포넌트 구조가 바뀌면 루트 `docs/`에 별도로 반영한다.

## 2. Phase 목표

백엔드 공통 error envelope과 endpoint별 `details`를 일반 사용자 화면에서도 잃지 않고, 사용자가 취할 수 있는 행동 중심으로 표시한다.

이 Phase의 핵심 목표는 다음이다.

1. `ApiError` 표시/정규화 로직을 관리자 화면 내부 함수에서 공통 모듈로 분리한다.
2. 일반 사용자 화면에서 `WorkflowErrorDetails`를 저장만 하지 않고 실제 오류 패널로 표시한다.
3. `VALIDATION_ERROR`는 사용자가 고칠 수 있는 필드 단위 안내로 연결한다.
4. `UPSTREAM_LAB_ERROR`, `UPSTREAM_REQUEST_ERROR`는 재시도 가능 여부와 운영 진단을 분리해 표시한다.
5. 인증 만료, 권한 부족, 리소스 없음, 상태 충돌을 같은 "워크플로우 실패"로 뭉개지 않는다.
6. WebSocket 오류와 일반 HTTP 오류의 refresh/redirect 정책을 분리한다.

## 3. 현재 문서에 대한 비판적 검토

기존 Phase 6 문서는 방향은 맞지만 구현 기준으로는 다음이 부족하다.

- 백엔드 공통 error envelope 구조와 code별 표시 정책이 구체적으로 연결되어 있지 않다.
- `Simulation2Page.tsx`가 `errorDetails`를 저장하지만 화면에 거의 표시하지 않는 현재 차이를 충분히 지적하지 않았다.
- 관리자 화면의 `ErrorDetailsPanel` 원형이 이미 `AdminPage3.tsx` 내부에 있다는 사실을 활용하지 않았다.
- 공통 컴포넌트 후보를 `components/common/ApiErrorDetailsPanel.tsx`로 적었지만 현재 `components/common` 디렉터리가 없으므로 생성/문서 갱신까지 포함해야 한다.
- 사용자 화면과 관리자 화면의 정보 노출 수준을 구분하지 않았다.
- 422 validation, 409 conflict, 502 upstream, 401 refresh 실패, WebSocket 인증 실패가 모두 다른 회복 동작을 가져야 하는데 mapping이 없다.
- `X-New-Access-Token`은 HTTP 응답 헤더에서만 처리되고, WebSocket에서는 받을 수 없다는 차이가 빠져 있다.
- raw JSON 전체 노출 제한은 언급했지만 어떤 상황에서 열어 보여줄지, 무엇을 redaction할지 기준이 없다.
- 테스트 계획이 없어 이후 구현에서 `details`가 다시 유실될 수 있다.

따라서 Phase 6은 "에러 메시지를 예쁘게 표시"하는 작업이 아니라, 백엔드 계약의 에러 의미를 사용자 행동과 운영 진단으로 변환하는 공통 UX 계층을 만드는 작업으로 본다.

## 4. 백엔드 에러 계약 요약

### 4.1 공통 envelope

백엔드는 애플리케이션 예외를 아래 JSON 구조로 반환한다.

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "사용자 메시지",
    "details": {}
  }
}
```

필드:

- `error.code`: 애플리케이션 에러 코드
- `error.message`: 사용자에게 전달할 메시지
- `error.details`: 부가 정보 객체, 없으면 빈 객체

주의:

- FastAPI/Pydantic 기본 타입 검증은 `422 Unprocessable Entity`를 반환할 수 있다.
- API 서버는 업스트림 HTML 오류 페이지를 그대로 전달하지 않고 JSON 오류 응답으로 변환한다.
- 보호된 HTTP endpoint 응답에는 `X-New-Access-Token`이 포함될 수 있고, 클라이언트는 이를 저장해야 한다.
- WebSocket은 HTTP 응답 헤더로 token refresh를 받을 수 없다.

### 4.2 공통 에러 코드

| code | 대표 의미 | 사용자 화면 기본 처리 |
|---|---|---|
| `UNAUTHORIZED` | access/refresh token 누락, 만료, 검증 실패 | 세션 만료 안내, 로그인 이동 |
| `FORBIDDEN` | 비활성 계정 또는 권한 부족 | 접근 권한 없음 안내 |
| `NOT_FOUND` | 리소스 없음 | 선택 상태 정리 또는 목록으로 복귀 |
| `VALIDATION_ERROR` | 비즈니스 검증 실패 | 수정 가능한 필드/값 안내 |
| `CONFLICT` | 현재 상태에서 허용되지 않음 | 현재 상태 설명, 최신 상태 재조회 |
| `UPSTREAM_LAB_ERROR` | Lab Server 또는 visualization upstream 실패 | 재시도 가능 여부와 진단 표시 |
| `UPSTREAM_REQUEST_ERROR` | 처리되지 않은 upstream 예외 공통 JSON | 결과/파일/동기화 실패로 표시 |
| `AUTHENTICATED_ACCOUNT_NOT_IN_REQUEST_CONTEXT` | 인증 context 누락 | 인증 오류로 취급 |

### 4.3 422 validation details

시뮬레이션 submit에서 발생 가능한 `422 VALIDATION_ERROR` 예시는 다음 details를 포함한다.

- `source`
- `upstreamStatus`
- `upstreamErrorType`
- `invalidFields`
- `validationErrors[]`
- `validationErrors[].field`
- `validationErrors[].sourceField`
- `validationErrors[].reason`
- `validationErrors[].message`
- `validationErrors[].actual`
- `validationErrors[].minimum`
- `validationErrors[].maximum`

사용자 화면에서는:

- `invalidFields`를 파라미터 패널의 필드 강조에 사용한다.
- `validationErrors`를 "수정 필요 항목" 목록으로 표시한다.
- `sourceField`는 보조 정보로만 둔다.
- 사용자가 직접 고칠 수 없는 schema/계약 오류처럼 보이면 일반 사용자에게는 간단히 안내하고 관리자 진단에 raw details를 보존한다.

### 4.4 502 upstream details

submit/cancel/visualization 관련 `502 UPSTREAM_LAB_ERROR`는 다음 details를 포함할 수 있다.

- `operation`
- `upstreamStatus`
- `upstreamErrorType`
- `retryable`
- `configuredLabServerMode`
- `labApiKeyConfigured`
- `cloudflareAccessJwtConfigured`
- `cloudflareServiceTokenConfigured`
- `browserHeaderBundleConfigured`
- `failureCategory`
- `actionGuide`

사용자 화면에서는:

- `operation`, `retryable`, `actionGuide`를 우선 표시한다.
- `retryable=true`이면 재시도 버튼 또는 재시도 가능한 상태를 제공한다.
- `retryable=false`이면 반복 클릭을 유도하지 않고 관리자 확인/설정 문제 가능성을 안내한다.
- config boolean들은 관리자/진단 영역에 둔다.
- secret 값은 표시하지 않는다. 현재 명세의 config 필드는 boolean이라 표시 가능하지만, 향후 문자열 secret이 들어오면 redaction한다.

## 5. 현재 프론트엔드 상태

### 5.1 `lib/apiClient.ts`

현재 장점:

- `ApiError`가 `code`, `status`, `details`를 보존한다.
- `readErrorBody`가 envelope이 깨진 응답도 `UNKNOWN_ERROR`로 감싼다.
- `authFetch`가 401에서 refresh를 시도한다.
- `X-New-Access-Token` 응답 헤더를 읽어 `sessionStorage` access token을 갱신한다.
- proactive refresh와 single-flight refresh가 구현되어 있다.

부족:

- `ApiError`를 화면용 view model로 변환하는 공통 helper가 없다.
- refresh 실패 후 401을 받은 UI가 login redirect인지 workflow error인지 일관되게 판단하지 않는다.
- error details redaction/formatting 정책이 공통화되어 있지 않다.
- WebSocket 인증 실패는 `apiClient` 경로가 아니므로 별도 표시 정책이 필요하다.

### 5.2 `components/pages/AdminPage3.tsx`

현재 장점:

- `formatError`, `getErrorDetails`, `ErrorDetailsPanel`, `AdminErrorDescription`이 있다.
- `invalidFields`, `validationErrors`, upstream diagnostic keys를 표시한다.
- raw details를 접을 수 있는 영역으로 제공한다.

부족:

- 관리자 화면 내부 함수라 일반 사용자 화면에서 재사용할 수 없다.
- 표시 label이 `invalidFields`, `validationErrors` 같은 backend field명 그대로라 일반 사용자 화면에는 딱딱하다.
- raw details redaction 정책이 명시되어 있지 않다.

### 5.3 `components/pages/Simulation2Page.tsx`

현재 장점:

- `WorkflowErrorDetails` 타입이 있다.
- submit/update/sendMessage 실패 시 `ApiError`의 code/details/status를 저장한다.
- cancel 실패는 toast로 HTTP status/code를 일부 표시한다.

부족:

- `workflow.errorDetails`를 실제 화면에 상세 표시하지 않는다.
- `error && !workflow.error` global alert는 문자열 한 줄만 보여준다.
- submit 실패의 422/502가 같은 `stage='failed'` 흐름으로 보인다.
- update validation 실패 시 draft/editing 상태를 유지하기보다 workflow failed로 밀릴 수 있다.
- `invalidFields`가 파라미터 입력 UI 강조로 연결되어 있지 않다.
- `actionGuide`, `retryable`, `failureCategory`가 사용자에게 전달되지 않는다.
- 인증 만료와 일반 워크플로우 실패가 구분되지 않는다.
- WebSocket reconnect 실패는 console warning 중심이고 사용자 안내가 약하다.

## 6. 구현 범위

### 6.1 포함

- 공통 API error formatter/normalizer 추가
- 공통 error details panel 추가
- 관리자 화면 내부 error panel을 공통 컴포넌트로 이동
- 사용자 화면에 `WorkflowErrorPanel` 또는 `ApiErrorDetailsPanel` 표시
- validation details를 파라미터 패널과 연결
- submit/update/cancel/result/visualization 오류 mapping 정리
- 인증 만료와 권한 오류 처리 정책 정리
- WebSocket 오류 안내 정책 정리
- 테스트 추가/수정
- 실제 코드 변경 시 루트 `docs/` 업데이트

### 6.2 제외

- 백엔드 error code 변경
- 백엔드 details schema 변경
- 새로운 retry API 추가
- 모든 화면의 copy를 전면 재작성
- Lab Gateway 직접 에러를 backend error처럼 변환하는 작업
- secret 또는 token 값을 화면에 표시하는 기능

## 7. 공통 에러 모듈 설계

### 7.1 신규 후보 파일

```text
lib/api/errors.ts
components/common/ApiErrorDetailsPanel.tsx
components/common/ApiErrorNotice.tsx
```

`components/common` 디렉터리는 현재 없으므로, 실제 구현 시 생성하고 `docs/architecture/directory.md`를 갱신한다.

### 7.2 `lib/api/errors.ts`

역할:

- `ApiError` 또는 unknown error를 안전하게 판별한다.
- backend details를 화면 표시용 view model로 변환한다.
- raw details redaction을 담당한다.
- UI 컴포넌트에 React 의존성을 넣지 않는다.

타입 후보:

```ts
export type ApiErrorCode =
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'VALIDATION_ERROR'
  | 'CONFLICT'
  | 'UPSTREAM_LAB_ERROR'
  | 'UPSTREAM_REQUEST_ERROR'
  | 'AUTHENTICATED_ACCOUNT_NOT_IN_REQUEST_CONTEXT'
  | 'UNKNOWN_ERROR'
  | 'CONFIGURATION_ERROR';

export interface NormalizedApiError {
  code: string;
  status?: number;
  message: string;
  details: Record<string, unknown> | null;
  category: 'auth' | 'permission' | 'notFound' | 'validation' | 'conflict' | 'upstream' | 'unknown';
  userTitle: string;
  userMessage: string;
  retryable: boolean | null;
  invalidFields: string[];
  validationErrors: ValidationErrorItem[];
  diagnostics: DiagnosticItem[];
  rawDetails: Record<string, unknown> | null;
}

export interface ValidationErrorItem {
  field: string;
  sourceField?: string;
  reason?: string;
  message: string;
  actual?: unknown;
  minimum?: unknown;
  maximum?: unknown;
}

export interface DiagnosticItem {
  key: string;
  label: string;
  value: string;
}
```

함수 후보:

```ts
export function isApiErrorLike(error: unknown): error is ApiErrorLike;

export function normalizeApiError(error: unknown): NormalizedApiError;

export function getInvalidFields(error: unknown): string[];

export function redactApiErrorDetails(
  details: Record<string, unknown> | null
): Record<string, unknown> | null;
```

redaction 규칙:

- key에 `token`, `secret`, `password`, `authorization`, `apiKey`가 포함되고 value가 boolean이 아니면 `"[redacted]"`로 표시한다.
- `labApiKeyConfigured`처럼 boolean 설정 여부 필드는 표시 가능하다.
- access token 또는 refresh token은 raw details에 있어도 표시하지 않는다.

### 7.3 `ApiErrorDetailsPanel`

역할:

- 관리자/사용자 모두 쓸 수 있는 error details 표시 컴포넌트
- technical raw details는 prop으로 제어

권장 props:

```ts
interface ApiErrorDetailsPanelProps {
  error: unknown;
  audience?: 'user' | 'admin';
  showRawDetails?: boolean;
  compact?: boolean;
}
```

표시 정책:

- `audience='user'`
  - title/message
  - invalid field chips
  - validation error list
  - retryable/action guide
  - raw details는 기본 숨김
- `audience='admin'`
  - upstream diagnostics 전체
  - validation details 전체
  - redacted raw details
- `compact=true`
  - toast/inline 짧은 표시용

### 7.4 `ApiErrorNotice`

역할:

- `Alert` 기반의 한 줄 또는 짧은 블록 표시
- `ApiErrorDetailsPanel`을 접이식으로 포함 가능

사용처:

- `Simulation2Page` global/workflow error
- `ResultExplorerPanel` API 실패
- `VisualizationControlBar` PATCH/screenshot 실패
- `AdminPage3` mutation/query error

## 8. 사용자 화면 error mapping

### 8.1 채팅/세션 복원

대상:

- `createChatSession`
- `sendChatSessionMessage`
- `getChatSession`
- `getChatSessionMessages`

정책:

- `401 UNAUTHORIZED`: 세션 만료 안내, 로그인 이동 버튼
- `403 FORBIDDEN`: 해당 세션 접근 권한 없음
- `404 NOT_FOUND`: URL의 session query가 유효하지 않음, 새 채팅 시작 안내
- 기타: 채팅 요청 실패 안내

주의:

- 세션 복원 실패는 진행 중 simulation/job/visualization 상태를 무조건 삭제하지 않는다.
- 새 채팅 시작은 사용자 명시 액션으로 둔다.

### 8.2 시뮬레이션 생성/수정

대상:

- `POST /api/v1/simulations`
- `PATCH /api/v1/simulations/{simulationId}`

정책:

- `VALIDATION_ERROR`
  - `invalidFields`를 editable parameter UI에 연결한다.
  - `validationErrors`를 파라미터 패널 하단에 표시한다.
  - workflow stage는 가능한 한 `draft` 또는 editing context를 유지한다.
- `CONFLICT`
  - queued/running 상태 수정 시도라면 최신 simulation/job 상태를 다시 조회한다.
- `404`
  - 선택 simulation이 사라진 것으로 안내하고 세션/목록 상태를 재조회한다.

### 8.3 잡 제출

대상:

- `POST /api/v1/simulations/{simulationId}/jobs`

정책:

- `422 VALIDATION_ERROR`
  - `stage='draft'`로 되돌리고 수정 가능한 필드 목록을 표시한다.
  - `invalidFields`와 `validationErrors`를 파라미터 패널에 연결한다.
  - "실행 실패"보다 "제출 전 검증 실패"로 표시한다.
- `409 CONFLICT`
  - 이미 진행 중인 job이 있음을 안내하고 job list/detail을 새로고침한다.
- `502 UPSTREAM_LAB_ERROR`
  - `stage='failed'` 또는 submit error 상태로 표시한다.
  - `retryable=true`이면 "다시 제출" 액션을 활성화할 수 있다.
  - `retryable=false`이면 반복 제출 버튼을 강조하지 않는다.
  - `actionGuide`를 접이식 진단 영역에 표시한다.
- `401/403/404`
  - auth/permission/not found 정책에 따른다.

### 8.4 잡 취소

대상:

- `POST /api/v1/jobs/{jobId}/cancel`

정책:

- cancel 요청 실패 시 로컬 상태를 즉시 cancelled로 바꾸지 않는다.
- `502 UPSTREAM_LAB_ERROR`
  - 현재처럼 polling/WS는 유지한다.
  - toast만으로 끝내지 말고 inline 진단도 남긴다.
  - `retryable`이면 중단 버튼을 다시 시도 가능하게 둔다.
- `409 CONFLICT`
  - 이미 terminal 상태일 수 있으므로 job detail을 다시 조회한다.
- `404`
  - job이 사라진 것으로 안내하고 polling을 정리한다.

### 8.5 결과 탐색

대상:

- `GET /simulations/{simulationId}/results`
- `GET /results/{resultId}`
- `GET /results/{resultId}/fields`
- `GET /results/{resultId}/fields/{fieldName}/files`
- `GET /results/{resultId}/files/{fileId}/download`

정책:

- `UPSTREAM_REQUEST_ERROR`
  - Lab 결과 동기화/파일 목록/다운로드 실패로 표시한다.
  - field catalog와 file list 오류는 panel 전체 실패가 아니라 해당 영역 실패로 제한한다.
- `VALIDATION_ERROR`
  - field file range 오류는 필터 입력 옆에 표시한다.
- `404`
  - 선택 result/file이 사라진 것으로 안내하고 목록 재조회 액션을 제공한다.
- 다운로드 실패
  - panel 전체 상태를 failed로 바꾸지 않는다.

### 8.6 시각화

대상:

- `POST /results/{resultId}/visualizations`
- `GET /visualizations/{visualizationId}`
- `PATCH /visualizations/{visualizationId}`
- `DELETE /visualizations/{visualizationId}`
- `GET /visualizations/{visualizationId}/screenshot`
- `WS /visualizations/{visualizationId}/ws`

정책:

- create 실패
  - viewer 영역에 error notice 표시
  - result explorer selection은 유지
- `PATCH` 실패
  - 해당 control만 실패 상태 표시
  - visualization 세션을 자동 close하지 않는다.
- screenshot 실패
  - 다운로드 실패 안내만 표시
  - visualization 세션 상태는 유지
- delete 실패
  - unload cleanup은 best effort라 사용자에게 크게 방해하지 않는다.
  - 명시적 close 실패는 inline/toast로 표시한다.
- WebSocket 실패
  - 1008은 refresh 후 reconnect
  - 1013은 upstream temporary unavailable로 안내
  - 최대 retry 후 "실시간 연결이 불안정하지만 viewer는 계속 사용할 수 있음"으로 표시

## 9. 상태 전이 정책

오류 때문에 항상 `workflow.stage='failed'`로 가지 않는다.

| 오류 위치 | 권장 상태 |
|---|---|
| chat session restore 실패 | `idle` 또는 복원 실패 notice |
| simulation patch validation 실패 | `draft` 유지 |
| job submit validation 실패 | `draft` 유지 |
| job submit upstream 실패 | `failed` 가능 |
| job cancel 실패 | 현재 job 상태 유지 |
| result field list 실패 | Result Explorer field 영역만 error |
| file download 실패 | 해당 다운로드 액션만 error |
| visualization create 실패 | viewer 영역 error, result 선택 유지 |
| visualization control 실패 | control 영역 error |
| screenshot 실패 | screenshot action error |
| auth 만료 | workflow보다 auth/session notice 우선 |

## 10. 구현 순서

### Step 1. 공통 error normalizer 작성

대상:

- `lib/api/errors.ts`
- 테스트: `lib/api/errors.test.ts`

작업:

1. `isApiErrorLike`를 만든다.
2. `normalizeApiError`를 만든다.
3. error code별 `category`, `userTitle`, `userMessage`, `retryable`을 만든다.
4. `invalidFields`, `validationErrors`, upstream diagnostics를 추출한다.
5. raw details redaction을 구현한다.
6. unknown error와 envelope 깨진 error fallback을 테스트한다.

### Step 2. 공통 error panel 추가

대상:

- `components/common/ApiErrorDetailsPanel.tsx`
- `components/common/ApiErrorNotice.tsx`
- 문서: `docs/architecture/directory.md`, 필요 시 `docs/architecture/component.md`

작업:

1. `components/common` 디렉터리를 만든다.
2. user/admin audience별 표시 수준을 구현한다.
3. validation list, invalid field chips, upstream diagnostics, raw details를 각각 섹션으로 분리한다.
4. raw details는 redacted 값만 표시한다.
5. 모바일에서 긴 JSON과 URL이 overflow되지 않게 한다.

### Step 3. 관리자 화면 내부 panel 공용화

대상:

- `components/pages/AdminPage3.tsx`

작업:

1. 내부 `formatError`, `getErrorDetails`, `ErrorDetailsPanel`을 공통 모듈로 대체한다.
2. 기존 admin error 표시 범위를 줄이지 않는다.
3. 기존 mutation/query error toast 문구는 깨지지 않게 한다.

### Step 4. 사용자 workflow error 표시

대상:

- `components/pages/Simulation2Page.tsx`

작업:

1. `workflow.errorDetails`를 `ApiErrorNotice`로 표시한다.
2. 문자열 `error`만 있는 경우도 같은 컴포넌트의 unknown error 경로로 표시한다.
3. submit/update/sendMessage/createVisualization/cancel 에러 저장 형식을 통일한다.
4. `workflow.stage='failed'`가 아닌 validation/cancel/control 실패는 해당 영역 notice로 표시한다.

### Step 5. validation field 연결

대상:

- `Simulation2Page` 파라미터 편집 영역
- 필요 시 field helper

작업:

1. `getInvalidFields(workflow.errorDetails)`로 invalid field set을 만든다.
2. editable parameter input 옆에 field-level error를 표시한다.
3. unknown extra parameter field도 validation list에는 표시한다.
4. 사용자가 해당 field를 수정하면 관련 field error를 지우거나 다음 저장 시 갱신한다.

### Step 6. endpoint별 error mapping 적용

대상:

- chat/session restore
- simulation create/patch
- job submit/cancel
- result explorer
- visualization create/control/screenshot/ws

작업:

1. code/status별 상태 전이를 구현한다.
2. retryable 액션을 필요한 위치에만 노출한다.
3. 401/403은 auth/session notice로 우선 처리한다.
4. 404는 선택 id 정리 또는 목록 재조회로 연결한다.
5. 409는 최신 상태 재조회로 연결한다.

### Step 7. 테스트와 문서 반영

대상:

- `lib/api/errors.test.ts`
- `components/common/ApiErrorDetailsPanel.test.tsx`
- `components/pages/Simulation2Page.test.tsx`
- `components/pages/AdminPage3` 관련 테스트 또는 기존 admin helper test
- 루트 `docs/`

작업:

1. normalizer unit test를 추가한다.
2. user/admin audience 렌더링 차이를 테스트한다.
3. submit 422/502 사용자 화면 표시를 테스트한다.
4. cancel 502에서 로컬 cancelled로 바뀌지 않는지 테스트한다.
5. auth 401에서 session notice/redirect 정책을 테스트한다.
6. 실제 구현 후 공식 문서를 갱신한다.

## 11. 테스트 계획

### 11.1 normalizer unit test

`lib/api/errors.test.ts`:

- `VALIDATION_ERROR`:
  - invalidFields 추출
  - validationErrors field/sourceField/message/min/max 추출
  - category `validation`
- `UPSTREAM_LAB_ERROR`:
  - operation/upstreamStatus/retryable/failureCategory/actionGuide diagnostics 추출
  - category `upstream`
- `UNAUTHORIZED`:
  - category `auth`
  - userTitle이 세션 만료 안내
- `FORBIDDEN`:
  - category `permission`
- `CONFLICT`:
  - category `conflict`
- malformed response:
  - `UNKNOWN_ERROR` fallback
- redaction:
  - token/secret/password/apiKey 문자열 value redaction
  - boolean `labApiKeyConfigured`는 보존

### 11.2 component test

`ApiErrorDetailsPanel.test.tsx`:

- user audience는 validation/action guide를 표시하고 raw details를 기본 표시하지 않는다.
- admin audience는 diagnostics와 redacted raw details를 표시한다.
- 긴 actionGuide/JSON이 렌더링되어도 crash하지 않는다.
- validationErrors가 비어 있으면 validation section을 숨긴다.

`Simulation2Page.test.tsx`:

- job submit 422 응답 시:
  - workflow가 draft context로 남는다.
  - invalid field 안내가 표시된다.
  - create visualization이 호출되지 않는다.
- job submit 502 retryable true:
  - actionGuide 또는 retry 안내가 표시된다.
- cancel 502:
  - job 상태를 즉시 cancelled로 바꾸지 않는다.
  - 오류 notice가 남는다.
- 401:
  - 일반 workflow failed가 아니라 세션 만료 안내를 표시한다.
- visualization screenshot/PATCH 실패:
  - viewer는 유지된다.

`AdminPage3` 관련 테스트:

- 기존 details panel 정보가 공통 panel 이동 후에도 유지된다.
- raw details가 redaction된다.

## 12. 표시 copy 기준

사용자용 문구는 짧게 유지한다.

권장 title:

- `UNAUTHORIZED`: `세션이 만료되었습니다`
- `FORBIDDEN`: `접근 권한이 없습니다`
- `NOT_FOUND`: `대상을 찾을 수 없습니다`
- `VALIDATION_ERROR`: `수정이 필요한 파라미터가 있습니다`
- `CONFLICT`: `현재 상태에서는 수행할 수 없습니다`
- `UPSTREAM_LAB_ERROR`: `랩서버 요청에 실패했습니다`
- `UPSTREAM_REQUEST_ERROR`: `결과 정보를 불러오지 못했습니다`
- `UNKNOWN_ERROR`: `요청을 처리하지 못했습니다`

기술 정보:

- 일반 사용자에게는 기본적으로 접이식 진단 영역에 둔다.
- 관리자는 기본적으로 더 많은 diagnostics를 볼 수 있게 한다.
- raw JSON은 redacted 상태로만 보여준다.

## 13. 수용 기준

Phase 6은 아래 조건을 만족하면 완료로 본다.

- `ApiError` 표시/정규화 로직이 공통 모듈로 분리되어 있다.
- 관리자 화면 내부 error details panel 중복이 줄어든다.
- 일반 사용자 화면에서 `workflow.errorDetails`가 실제로 표시된다.
- 422 validation 실패 시 수정 대상 필드와 상세 이유가 보인다.
- job submit 502 실패 시 `retryable`, `operation`, `failureCategory`, `actionGuide`가 보존된다.
- cancel 실패 시 로컬 상태를 거짓 cancelled로 바꾸지 않는다.
- result/field/file/download 오류는 해당 영역에만 표시된다.
- visualization create/control/screenshot/ws 오류가 viewer 전체를 불필요하게 닫지 않는다.
- 401 refresh 실패와 일반 workflow 실패가 구분된다.
- raw details는 redaction된 형태로만 표시된다.
- 관련 테스트가 추가/갱신된다.
- 실제 코드 변경이 있다면 루트 `docs/` 문서가 함께 갱신된다.

## 14. 주요 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| details를 문자열 메시지로만 소모 | 사용자가 수정할 필드를 알 수 없음 | normalizer + details panel |
| 모든 오류를 workflow failed로 처리 | recover 가능한 상태가 사라짐 | endpoint별 상태 전이 |
| raw details 과다 노출 | 사용자 부담, 정보 노출 위험 | audience 분리 + redaction |
| 502 retryable 무시 | 불필요한 반복 클릭 또는 재시도 기회 누락 | retryable 기반 action |
| cancel 실패 후 로컬 cancelled 처리 | UI와 실제 job 상태 불일치 | cancel 실패 시 polling 유지 |
| auth 만료를 workflow 오류로 표시 | 로그인 복구 흐름 혼란 | auth category 우선 처리 |
| WebSocket 오류를 HTTP와 동일 처리 | token refresh/reconnect 실패 | WS 전용 정책 |
| 관리자 화면 기능 축소 | 운영 진단 정보 손실 | admin audience 보존 테스트 |

## 15. 후속 Phase 연결

- Phase 3의 job monitoring 오류와 cancel 오류는 이 Phase의 endpoint별 mapping을 따른다.
- Phase 4의 Result Explorer 오류 표시는 `ApiErrorNotice`를 영역 단위로 재사용한다.
- Phase 5의 visualization create/control/screenshot/ws 오류 표시는 이 Phase의 visualization mapping을 따른다.
- Phase 7은 error normalizer, common panel, 사용자/관리자 회귀 테스트를 묶어 검증한다.
