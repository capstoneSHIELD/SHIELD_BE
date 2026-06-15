# Phase 3. Job Monitoring

## 목표

Phase 3의 목표는 시뮬레이션 실행 작업의 조회, 이벤트, 취소, WebSocket 모니터링을 backend 명세와 맞추는 것이다. 이 phase는 실행 안정성과 운영 비용에 직접 영향을 주므로 `sync` 정책, terminal 상태, cancel 가능 상태, polling/WS fallback을 명확히 분리해야 한다.

핵심 결과는 다음과 같다.

- 사용자 자동 상태 조회는 기본적으로 `sync=false`를 사용한다.
- 명세의 `WS /api/v1/jobs/{jobId}/monitor/ws`를 사용자 실행 흐름에 연결한다.
- WebSocket이 실패하거나 닫히면 polling fallback을 사용한다.
- `submitted`, `pending`, `running` job은 취소 가능 상태로 일관되게 다룬다.
- job 상태와 visualization 상태의 ref/connection을 분리한다.

## 비판적 검토

기존 Phase 3 문서는 방향은 맞지만 실제 구현 기준으로는 부족했다.

- terminal 상태에 `succeeded`를 적었는데 현재 명세와 프론트 enum은 `completed`를 사용한다.
- `sync` 정책이 "사용자 자동 polling은 false" 수준에 머물고, restore/list/events/manual refresh/admin sync가 구분되어 있지 않았다.
- `WS 우선, polling fallback`이라고만 되어 있고 어떤 ref, 어떤 reconnect, 어떤 authoritative source를 쓸지 불명확했다.
- backend job monitor WS는 upstream 메시지를 가공하지 않고 릴레이한다. 따라서 프론트가 모든 메시지를 normalized job detail로 가정하면 위험하다.
- 현재 `Simulation2Page.tsx`의 `wsRef`는 visualization WS에 사용된다. job monitor WS를 같은 ref로 붙이면 연결 lifecycle이 충돌한다.
- `stopWorkflow`는 `running`일 때만 실제 진입하고, `JobResultListCard`도 `submitted/pending` cancel을 막고 있다. 이는 backend 명세의 non-terminal cancel 정책과 다르다.
- `GET /simulations/{simulationId}/jobs` 명세 표에는 query가 없지만 구현 규칙에는 `sync=true/false`가 있다. 이 불일치를 명시적으로 다뤄야 한다.
- job detail response의 `resultId`, `labJobId`, `mpiNodes`, `mpiProcesses`가 사용자 DTO/상태에 충분히 반영되지 않았다.
- 테스트 계획이 없어 기존 `pending job cannot cancel` 테스트를 어떻게 바꿀지 드러나지 않았다.

## 선행 조건

Phase 1이 완료되어 있으면 다음을 그대로 사용한다.

- `lib/api/jobs.ts`
- `lib/api/http.ts`의 query/path/WS URL helper
- active UI에서 job 관련 직접 `apiRequest` 제거
- `JobResultListCard`가 `jobs.ts` helper를 import하는 구조

Phase 1이 완료되지 않은 상태에서 Phase 3를 먼저 진행한다면 최소 선행 작업은 다음이다.

- `lib/api/jobs.ts` 생성
- `getJob`, `listJobEvents`, `cancelJob`, `listSimulationJobs`, `createJobMonitorWebSocketUrl` 추가
- `Simulation2Page.tsx`의 job 관련 직접 API 호출을 helper로 교체

## 명세 요약

대상 endpoint:

- `POST /api/v1/simulations/{simulationId}/jobs`
- `GET /api/v1/simulations/{simulationId}/jobs`
- `GET /api/v1/jobs/{jobId}`
- `POST /api/v1/jobs/{jobId}/cancel`
- `WS /api/v1/jobs/{jobId}/monitor/ws`
- `GET /api/v1/jobs/{jobId}/events`

중요 계약:

- submit body는 `autoVisualization` boolean required.
- job detail의 `sync` query 기본값은 `true`.
- job events는 구현 규칙상 `sync=true/false`를 지원한다.
- job monitor WS는 `Authorization` header 또는 `accessToken` query를 허용한다.
- browser WebSocket에서는 custom Authorization header를 안정적으로 보낼 수 없으므로 query token 방식을 사용한다.
- WS close `1008`: auth/access/job/labJobId 문제.
- WS close `1013`: upstream Lab monitor WS 연결 실패.
- WS는 `X-New-Access-Token` 같은 HTTP response header refresh를 제공하지 않는다.
- cancel은 `completed`, `failed`, `cancelled` 상태에서 불가하다.

Spec anomaly:

- `GET /api/v1/simulations/{simulationId}/jobs`의 Request Query는 "없음"으로 적혀 있지만 현재 구현 규칙은 `sync=true/false`를 설명한다. 프론트 helper는 backend 구현 규칙과 관리자 화면 선례에 맞춰 `sync` 옵션을 지원하되, 이 불일치는 Phase 0의 `spec-anomaly` 목록에도 남긴다.

## 범위

포함:

- job API helper 정합화
- 사용자 자동 조회의 `sync=false` 적용
- job monitor WS URL/helper 및 연결 orchestration
- WS 실패 시 polling fallback
- cancel 가능 상태 정리
- job summary/detail DTO 확장
- job/result sidebar 표시 정보 보강
- 테스트 수정/추가

제외:

- result explorer UI 추가
- visualization 생성 기본값 변경
- backend job 상태 enum 변경
- Lab Gateway 직접 호출 구현
- 관리자 화면의 전체 UX 재설계
- legacy `SimulationPage.tsx`의 `/api/ws/status/{taskId}` 교체

## API Helper 계획

파일:

- `lib/api/jobs.ts`

타입:

```ts
export type JobStatus =
  | 'submitted'
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled';

export interface SyncOption {
  sync?: boolean;
}

export interface JobSummary {
  jobId: string;
  status: JobStatus;
  mpiNodes: string[];
  mpiProcesses: number | null;
  submittedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface JobDetail extends JobSummary {
  simulationId: string;
  resultId: string | null;
  labJobId: string | null;
  progress: number | null;
  currentStep: string | null;
  errorMessage: string | null;
}

export interface JobEvent {
  eventId: string;
  type: string;
  message: string;
  createdAt: string;
}

export interface SubmitSimulationJobBody {
  autoVisualization: boolean;
}

export interface SubmitSimulationJobResponse {
  jobId: string;
  simulationId: string;
  status: JobStatus;
  submittedAt: string;
  labJobId: string | null;
  warnings: Array<Record<string, unknown>>;
  expectedProcessCount: number | null;
}

export interface CancelJobResponse {
  jobId: string;
  status: 'cancelled';
  cancelledAt: string;
}

export interface JobMonitorWebSocketParams {
  accessToken: string;
  sync?: boolean;
}
```

함수:

```ts
export function submitSimulationJob(
  simulationId: string,
  body: SubmitSimulationJobBody,
): Promise<SubmitSimulationJobResponse>;

export function listSimulationJobs(
  simulationId: string,
  options?: SyncOption,
): Promise<{ items: JobSummary[] }>;

export function getJob(
  jobId: string,
  options?: SyncOption,
): Promise<JobDetail>;

export function cancelJob(jobId: string): Promise<CancelJobResponse>;

export function listJobEvents(
  jobId: string,
  options?: SyncOption,
): Promise<{ items: JobEvent[] }>;

export function createJobMonitorWebSocketUrl(
  jobId: string,
  params: JobMonitorWebSocketParams,
): string;
```

기본값:

- helper 자체는 backend 기본값을 숨기지 않도록 `sync`를 optional로 둔다.
- 사용자 자동 조회 call site는 반드시 `{ sync: false }`를 명시한다.
- manual refresh call site는 `{ sync: true }`를 명시한다.
- 관리자 cache-only query는 기존처럼 `{ sync: false }`, Sync Lab action은 `{ sync: true }`를 유지한다.

## 상태 정책

| 분류 | JobStatus | UI 의미 |
| --- | --- | --- |
| queued-like | `submitted`, `pending` | 제출/대기 중, 취소 가능, running 애니메이션은 쓰지 않음 |
| active | `running` | 실행 중, 취소 가능, 진행률/currentStep 표시 |
| terminal success | `completed` | 완료, polling/WS 종료, result availability 확인 |
| terminal failure | `failed` | 실패, polling/WS 종료, errorMessage 표시 |
| terminal cancel | `cancelled` | 취소됨, polling/WS 종료, partial result 가능성 확인 |

유틸:

```ts
export function isTerminalJobStatus(status: JobStatus | null | undefined): boolean {
  return status === 'completed' || status === 'failed' || status === 'cancelled';
}

export function isCancellableJobStatus(status: JobStatus | null | undefined): boolean {
  return status === 'submitted' || status === 'pending' || status === 'running';
}

export function isActiveJobStatus(status: JobStatus | null | undefined): boolean {
  return status === 'submitted' || status === 'pending' || status === 'running';
}
```

주의:

- `succeeded`는 사용하지 않는다.
- `queued`는 simulation status에는 있지만 job status에는 없다.
- `pending`과 `submitted`은 cancel 가능하지만 "실행 중" 문구는 쓰지 않는다.

## Sync 정책

| Context | Endpoint | sync | 이유 |
| --- | --- | --- | --- |
| submit 후 최초 상태 반영 | submit response | 없음 | submit response 자체가 source of truth |
| 사용자 자동 job polling | `GET /jobs/{jobId}` | `false` | 반복 Lab sync 비용과 upstream 장애 전파를 줄인다. |
| 사용자 자동 event refresh | `GET /jobs/{jobId}/events` | `false` | 이벤트 캐시만 표시한다. |
| 사용자 restore active job list | `GET /simulations/{id}/jobs` | `false` | 페이지 복원 시 Lab sync 비용을 피한다. |
| 사용자 수동 새로고침 후보 | job detail/events/list | `true` | 사용자가 명시적으로 최신 Lab 상태를 요청한 상황이다. |
| job monitor WS 연결 | `WS /jobs/{id}/monitor/ws` | `false` 기본 후보 | 연결 전 비싼 Lab sync를 피하고, 필요 시 fallback query가 sync policy를 담당한다. |
| 관리자 기본 query | job list/detail/events | `false` | 현재 AdminPage3 정책 유지 |
| 관리자 Sync Lab 버튼 | job detail/events | `true` | 명시적 운영 동기화 |
| cancel action | `POST /jobs/{id}/cancel` | 없음 | backend가 cancel 후 1회 결과 메타데이터 동기화를 수행 |

결정 포인트:

- job monitor WS를 `sync=true`로 열지 여부는 운영 비용과 초기 정확성 사이의 선택이다. 기본은 `false`로 시작하고, 사용자가 수동 sync를 누를 때만 HTTP sync를 수행한다.
- `sync=true`에서 `502/503/504/530`이 발생할 수 있으므로 자동 루프에 넣지 않는다.

## WebSocket Orchestration

### Ref 분리

현재 `Simulation2Page.tsx`의 `wsRef`는 visualization WS에 사용된다. Phase 3에서 job monitor WS를 추가할 때는 반드시 ref를 분리한다.

권장 ref:

```ts
const jobMonitorWsRef = useRef<WebSocket | null>(null);
const jobMonitorReconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
const visualizationWsRef = useRef<WebSocket | null>(null); // 기존 wsRef rename 후보
```

최소 구현:

- 기존 `wsRef`를 visualization 전용으로 이름 변경하거나, job monitor용 ref를 새로 만든다.
- cleanup에서 job monitor WS와 visualization WS를 각각 닫는다.

### 연결 시작

연결 시작 조건:

- `jobId`가 있다.
- `jobStatus`가 `submitted`, `pending`, `running` 중 하나다.
- access token이 있다.
- 이미 같은 `jobId`로 열린 job monitor WS가 없다.

연결 시:

- `createJobMonitorWebSocketUrl(jobId, { accessToken, sync: false })` 사용
- URL에 access token을 넣되 console/log/toast에 URL 전체를 출력하지 않는다.
- 연결 성공 후 polling fallback interval은 멈추거나 느린 reconciliation interval로 전환한다.

### 메시지 처리

backend는 upstream 메시지를 가공하지 않고 릴레이한다. 따라서 프론트는 메시지 shape을 보수적으로 처리한다.

처리 기준:

- JSON parse 가능하면 `type`, `state`, `status`, `message`, `progress` 후보를 읽는다.
- `{ type: "status", state: "running" }` 같은 알려진 메시지는 UI currentStep 또는 jobStatus 힌트로 반영할 수 있다.
- authoritative job detail은 `getJob(jobId, { sync: false })` 응답으로 갱신한다.
- 알 수 없는 메시지는 무시하거나 transient event로만 표시하고 job state를 강제로 바꾸지 않는다.
- 메시지 수신 때마다 HTTP detail을 호출하면 과도하므로 throttle을 둔다. 예: 2-5초에 1회.

권장:

- WS 메시지 수신은 "상태 변경 힌트"로 보고, `getJob(sync=false)`로 캐시 상태를 확인한다.
- terminal 상태를 감지하면 WS와 polling을 모두 정리하고 result availability 확인을 시작한다.

### Close/Error 처리

| Close/Error | 처리 |
| --- | --- |
| normal close | terminal 상태이면 종료. active 상태이면 polling fallback 시작 |
| `1008` | access token refresh 후 1회 재연결. 실패하면 polling fallback |
| `1013` | upstream unavailable로 보고 polling fallback |
| JSON error message | workflow error/currentStep에 반영 후보, 연결은 close handler 정책 따름 |
| browser error | polling fallback |

재연결:

- exponential backoff 후보: 1s, 2s, 5s
- 최대 3회
- 새 access token이 필요한 경우 `refreshAccessToken()` 후 새 URL 생성
- refresh token을 WS query에 넣지 않는다.

## Polling Fallback

기존 interval:

- 3초

Phase 3 정책:

- WS 연결 전까지 polling을 유지한다.
- WS open 후에는 polling을 중지하거나 10-15초 reconciliation으로 낮춘다.
- WS close/error 후 active 상태이면 3초 polling fallback으로 복귀한다.
- polling은 항상 `getJob(jobId, { sync: false })`, `listJobEvents(jobId, { sync: false })`를 사용한다.

terminal 후 처리:

- `completed`: polling/WS 종료, result availability 확인
- `failed`: polling/WS 종료, `errorMessage` 표시
- `cancelled`: polling/WS 종료, partial result 가능성 때문에 result availability를 몇 차례 확인

기존 terminal result check 정책:

- 현재 구현은 terminal 후 result가 없으면 최대 5회 확인한다.
- Phase 3에서는 이 정책을 유지하되, WS 도입 후에도 같은 함수에서만 수행하도록 중복을 막는다.

## Cancel 정책

Backend 계약:

- 취소 불가: `completed`, `failed`, `cancelled`
- 취소 가능 후보: `submitted`, `pending`, `running`
- `labJobId`가 없으면 backend가 `409 CONFLICT`를 반환할 수 있다.

UI 변경:

- `JobResultListCard`의 `JOB_STATUS_META.submitted.cancellable`을 `true`로 변경한다.
- `JobResultListCard`의 `JOB_STATUS_META.pending.cancellable`을 `true`로 변경한다.
- `Simulation2Page`의 `canRequestStopJob`를 `workflow.jobStatus === 'running'`에서 `isCancellableJobStatus(workflow.jobStatus)`로 변경한다.
- stop button title은 상태별로 구분한다.
  - `submitted`: 제출된 작업 취소
  - `pending`: 대기 중 작업 취소
  - `running`: 시뮬레이션 중단

취소 성공 후:

- local `jobStatus`를 `cancelled`로 즉시 반영한다.
- polling/WS 정리
- job/result list refresh
- simulation list refresh
- partial result 가능성 확인

취소 실패 후:

- polling/WS는 유지한다.
- local 상태를 `cancelled`로 바꾸지 않는다.
- 409/502 details는 Phase 6의 error panel에서 더 자세히 표시하되, Phase 3에서는 toast/error message에 최소 반영한다.

## Simulation2Page 작업 계획

대상:

- `components/pages/Simulation2Page.tsx`

작업:

- job API 직접 호출을 `jobs.ts` helper로 교체한다.
- `pollJobStatus(jobId)`가 `getJob(jobId, { sync: false })`를 사용하도록 한다.
- `fetchJobEvents(jobId)`가 `listJobEvents(jobId, { sync: false })`를 사용하도록 한다.
- restore 중 active job 조회가 `listSimulationJobs(simulationId, { sync: false })`를 사용하도록 한다.
- `submitJob`은 `submitSimulationJob(simulationId, { autoVisualization: false })`를 사용한다.
- job monitor WS 연결 함수 `connectJobMonitorWS(jobId, simulationId)`를 추가한다.
- `startJobMonitoring(jobId, simulationId)`를 만들어 WS와 polling fallback을 한 곳에서 시작한다.
- 기존 `startJobPolling`은 fallback 함수로 축소한다.
- visualization WS ref와 job monitor WS ref를 분리한다.
- cleanup/reset/unmount에서 job monitor WS, reconnect timer, polling interval을 모두 정리한다.
- terminal 상태 처리 함수 `handleTerminalJobStatus` 후보를 만들어 polling과 WS 양쪽에서 같은 후속 처리를 쓰도록 한다.

금지:

- job monitor WS 메시지를 확정된 backend DTO처럼 가정하지 않는다.
- WS URL 전체를 로그에 남기지 않는다.
- visualization WS와 job monitor WS를 같은 ref로 관리하지 않는다.
- `sync=true`를 자동 interval에 넣지 않는다.

## JobResultListCard 작업 계획

대상:

- `components/simulation/JobResultListCard.tsx`

작업:

- imports를 `lib/api/jobs`와 `lib/api/results`로 분리한다.
- `JobSummary`에 `mpiNodes`, `mpiProcesses`를 반영한다.
- submitted/pending/running 모두 cancel button 표시 후보로 둔다.
- running만 spinner icon을 사용한다.
- submitted/pending은 Clock icon을 유지한다.
- MPI 정보가 있으면 compact하게 표시한다. 예: `MPI 5 · i003:5`
- `actionsDisabled`이면 cancel/result action을 모두 잠근다.
- 수동 refresh 버튼은 cache-only refresh와 lab sync refresh를 분리할지 결정한다.
  - Phase 3 최소 구현: 기존 refresh는 `sync=false`.
  - 별도 "Lab sync" 버튼은 사용자 화면에 바로 추가하지 않고 관리자 화면 정책을 유지한다.

테스트 변경:

- 기존 "pending job은 취소 버튼 없음" 테스트는 명세와 맞지 않으므로 "pending job도 취소 가능"으로 수정한다.
- submitted job도 취소 가능 케이스를 추가한다.

## AdminPage3 정합성

현재 관리자 화면은 이미 좋은 기준을 갖고 있다.

- `CACHE_ONLY_SYNC = { sync: false }`
- `LAB_SYNC = { sync: true }`
- 기본 query는 cache-only
- `Sync Lab` 버튼에서만 `sync=true`
- active job status는 `submitted`, `pending`, `running`

Phase 3에서는 가능하면 이 정책을 사용자 화면에도 맞춘다. `admin.ts`의 일반 job helper는 Phase 1에서 `jobs.ts`로 이동 또는 wrapper 처리하되, admin query key와 Sync Lab mutation 정책은 유지한다.

## 구현 순서

### Step 1. Job helper/DTO 정리

- `lib/api/jobs.ts` 확정
- `JobSummary`, `JobDetail`, `JobEvent`, `SubmitSimulationJobResponse` 정의
- `sync` query 생성 테스트 추가
- monitor WS URL 생성 테스트 추가

검증:

- `lib/api/jobs.test.ts`
- `lib/api/admin.test.ts`

### Step 2. 사용자 자동 조회를 cache-only로 변경

- `Simulation2Page` polling/restore/events에 `{ sync: false }` 적용
- `JobResultListCard` 목록 조회에 `{ sync: false }` 적용
- 기존 동작은 유지하되 upstream 502가 자동 UI loop에 섞이지 않도록 한다.

검증:

- `components/pages/Simulation2Page.test.tsx`
- `components/simulation/JobResultListCard.test.tsx`

### Step 3. Cancel 상태 정책 수정

- shared `isCancellableJobStatus` 유틸 추가 후보
- stop button 조건 수정
- JobResultListCard status meta 수정
- pending/submitted cancel 테스트 추가

검증:

- `components/simulation/JobResultListCard.test.tsx`
- cancel 관련 Simulation2Page 테스트 추가

### Step 4. Job monitor WS URL 및 연결 추가

- `createJobMonitorWebSocketUrl` 사용
- `jobMonitorWsRef` 추가
- `connectJobMonitorWS` 추가
- close/error/reconnect/fallback 처리
- token refresh 후 reconnect 처리

검증:

- URL helper unit test
- WebSocket mock 기반 component test 후보

### Step 5. Polling fallback과 terminal 처리 통합

- `startJobMonitoring` 도입
- `startJobPollingFallback` 분리
- terminal 후 result availability 확인 중복 방지
- cleanup/reset/unmount 정리

검증:

- fake timers 기반 polling cleanup test 후보
- terminal status에서 interval clear 확인

### Step 6. UI 정보 보강

- MPI nodes/processes 표시
- currentStep/progress 반영 위치 확인
- job event list가 WS/polling 상태와 충돌하지 않도록 유지

검증:

- JobResultListCard rendering test

## 테스트 계획

### `lib/api/jobs.test.ts`

필수 케이스:

- `listSimulationJobs('sim/1', { sync: false })`가 encoded path와 `sync=false` query를 만든다.
- `getJob('job/1', { sync: true })`가 encoded path와 `sync=true` query를 만든다.
- `listJobEvents('job/1', { sync: false })`가 encoded path와 query를 만든다.
- `cancelJob('job/1')`가 encoded path와 POST method를 사용한다.
- `submitSimulationJob('sim/1', { autoVisualization: false })` body가 정확하다.
- `createJobMonitorWebSocketUrl`이 `ws/wss`, encoded jobId, encoded accessToken, sync query를 만든다.
- WS URL helper가 token을 반환값 외부 로그로 출력하지 않는다. 이건 코드 리뷰 기준으로 확인한다.

### `components/simulation/JobResultListCard.test.tsx`

필수 케이스:

- running job cancel button 표시
- pending job cancel button 표시
- submitted job cancel button 표시
- completed/failed/cancelled는 cancel button 미표시
- `actionsDisabled`일 때 cancel/result action 비활성
- MPI process/node 정보 표시
- list helper가 `{ sync: false }`로 호출됨

### `components/pages/Simulation2Page.test.tsx`

필수 케이스:

- submit 후 `startJobMonitoring` 경로로 진입
- polling fallback은 `getJob(..., { sync: false })`를 사용
- events refresh는 `listJobEvents(..., { sync: false })`를 사용
- submitted/pending/running에서 stop button이 활성 후보
- cancel 성공 후 local status가 cancelled로 바뀌고 interval/WS가 정리됨
- cancel 실패 후 local status를 cancelled로 바꾸지 않음
- WS close `1008`에서 refresh 후 reconnect 시도
- WS close `1013`에서 polling fallback 시작
- terminal status에서 result availability check가 중복 실행되지 않음

### `components/pages/AdminPage3` 또는 admin helper tests

필수 케이스:

- 기존 `CACHE_ONLY_SYNC`와 `LAB_SYNC` 정책 유지
- Sync Lab 버튼만 `sync=true`
- cancel 후 cache query invalidation 유지

## Acceptance Criteria

- 사용자 자동 job detail/events/list 조회가 `sync=false`를 명시한다.
- 관리자 기본 query는 `sync=false`, Sync Lab action은 `sync=true`를 유지한다.
- job monitor WS가 active job 흐름에 연결된다.
- job monitor WS와 visualization WS가 별도 ref/lifecycle로 관리된다.
- WS 실패 또는 close 시 polling fallback이 동작한다.
- submitted/pending/running job cancel이 UI와 API 호출 조건에서 일관된다.
- terminal 상태는 `completed`, `failed`, `cancelled`만 사용한다.
- `succeeded` 문자열이 job status 정책에 남아 있지 않다.
- job summary/detail DTO가 MPI와 resultId/labJobId/progress/currentStep/errorMessage를 반영한다.
- 테스트가 sync query, cancel status, WS URL, fallback cleanup을 커버한다.

## 허용 잔여 항목

Phase 3 완료 후에도 다음은 남아 있을 수 있다.

- 사용자 화면의 별도 Lab Sync 버튼
- result explorer 상세 UI
- job WS raw message의 모든 upstream variant 파싱
- 관리자 화면의 WS monitor UI
- legacy `/api/ws/status/{taskId}` 제거

## 문서 업데이트 기준

Phase 3에서 실제 코드 변경이 발생하면 다음 문서를 검토한다.

- `docs/architecture/flow.md`: job submit, monitor, cancel, fallback 흐름 변경 시 갱신
- `docs/architecture/state.md`: job state, terminal/cancellable 상태 정책 공식화 시 갱신
- `docs/architecture/architecture.md`: WS/polling orchestration 책임 경계가 바뀌면 갱신
- `docs/architecture/directory.md`: `lib/api/jobs.ts` 또는 shared status utility 추가 시 갱신

backend public API 자체를 바꾸는 작업은 아니므로 `docs/api/endpoints.md`와 `docs/api/specification.md`는 원칙적으로 수정하지 않는다. 다만 frontend-backend 매핑 문서를 `docs/api`에 공식화했다면 job endpoint row를 갱신한다.

## 리스크

- job monitor WS는 upstream raw message relay라서 메시지 shape을 과신하면 상태가 잘못 갱신될 수 있다.
- WS와 polling이 동시에 상태를 갱신하면 terminal 처리, result availability check, visualization trigger가 중복 실행될 수 있다.
- `sync=true`를 자동 루프에 넣으면 Lab Gateway 장애가 사용자 화면의 반복 오류로 번질 수 있다.
- job monitor WS와 visualization WS가 같은 ref를 쓰면 하나를 닫을 때 다른 연결도 끊길 수 있다.
- access token을 query에 넣은 WS URL을 console에 출력하면 민감정보 노출 위험이 있다.
- cancel 성공 후 backend는 partial result를 동기화할 수 있으므로 프론트가 "취소 = 결과 없음"으로 단정하면 안 된다.
- 기존 테스트는 pending cancel 비활성화를 기대하므로 명세에 맞춰 테스트를 수정해야 한다.

