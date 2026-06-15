# Phase 5. Visualization Contract

## 1. 작업 유형 판단

- 작업 유형: API 설계 + 컨트롤러/라우트/UI 경계 작업 + 외부 연동 경계 정리 + 리팩토링 계획
- 적용 우선 규칙:
  - `.codex/ai_rule_developer/GLOBAL_RULES.md`
  - `.codex/ai_rule_developer/API_DESIGN_RULES.md`
  - `.codex/ai_rule_developer/ARCHITECTURE_RULES.md`
  - `.codex/ai_rule_developer/SERVICE_LAYER_RULES.md`
  - `.codex/ai_rule_developer/EXTERNAL_INTEGRATION_RULES.md`
  - `.codex/ai_rule_developer/DOCUMENT_RULE.md`
- 주의:
  - 이 문서는 사용자 요청에 따라 `.codex/ref_docs` 아래에 작성한다.
  - 실제 구현 단계에서 공식 프로젝트 동작이 바뀌면 루트 `docs/`에 별도로 반영한다.

## 2. Phase 목표

백엔드 visualization API 명세를 기준으로 사용자 화면과 관리자 화면의 시각화 생성, 조회, 제어, 종료, 스크린샷, WebSocket 연결 흐름을 정렬한다.

이 Phase의 핵심 목표는 다음이다.

1. visualization API를 `lib/api/admin.ts` 또는 `Simulation2Page.tsx` 직접 호출에서 공용 API helper로 분리한다.
2. 시각화 생성 요청의 `field`, `colormap`, `viewAngle` 하드코딩을 제거하고 결과/필드 계약을 기준으로 초기값을 결정한다.
3. backend `PATCH /visualizations/{visualizationId}` 계약에 맞춰 field, colormap, timestep, camera 제어를 구현한다.
4. screenshot API가 JSON이 아닌 PNG 바이너리라는 경계를 명확히 한다.
5. 브라우저는 backend WebSocket proxy endpoint에 연결하고, Lab Gateway 직접 URL과 혼용하지 않는다.
6. Advanced Trame 직접 연동은 핵심 사용자 흐름과 분리된 고급 기능으로 유지한다.

## 3. 현재 문서에 대한 비판적 검토

기존 Phase 5 문서는 큰 방향은 맞지만 구현 계획으로는 다음 문제가 있다.

- `components/simulation/AdvancedTramePanel.tsx`라고 적었지만 실제 경로는 `components/simulation/trame/AdvancedTramePanel.tsx`다.
- 현재 사용자 화면은 이미 create, periodic get sync, WebSocket reconnect, delete cleanup 일부를 구현하고 있으므로 "새로 만든다"가 아니라 "공용 helper와 명세 기준으로 정리한다"가 더 정확하다.
- `viewAngle`은 PATCH body에 포함될 수 있지만, 백엔드 명세상 새 Lab Server 수정용 view endpoint가 없어 별도 upstream 호출을 만들지 않는다. 이 차이가 기존 문서에 충분히 드러나지 않았다.
- `websocketUrl` 응답 필드를 브라우저가 직접 연결해야 하는 URL처럼 오해할 수 있다. 사용자 화면의 기본 연결은 `/api/v1/visualizations/{visualizationId}/ws?accessToken=...` backend proxy여야 한다.
- `POST /results/{resultId}/visualizations` 응답에는 `fieldsAvailable`, `totalTimesteps`, `currentTimestep`이 없다. 생성 직후 메타데이터는 `GET /visualizations/{visualizationId}`로 동기화해야 한다.
- screenshot은 JSON 응답이 아니라 PNG 바이너리다. 공용 `downloadBinary` helper를 사용해야 한다.
- 현재 `Simulation2Page.tsx`의 unload cleanup raw `fetch`는 UI 컴포넌트에 외부 URL 조립과 auth header 조립을 남긴다. helper로 감싸야 한다.
- 생성 기본값을 result field 계약과 연결하지 않으면 `phase` 하드코딩이 계속 남는다.
- Advanced Trame 패널은 `NEXT_PUBLIC_LAB_SERVER_URL` 기반의 Lab Gateway 직접 연동이다. backend orchestration API 흐름과 같은 계층으로 섞으면 안 된다.
- 테스트 범위가 endpoint helper 수준에 머물러 있고, 사용자 화면에서 "시각화 생성은 명시 액션으로만 발생한다"는 회귀 테스트가 빠져 있다.

따라서 Phase 5는 단순히 camera field를 타입에 추가하는 작업이 아니라, 시각화 세션 생명주기와 외부 연동 경계를 재정렬하는 작업으로 다룬다.

## 4. 백엔드 명세 요약

### 4.1 생성

- `POST /api/v1/results/{resultId}/visualizations`
- 요청 쿼리 없음.
- 요청 body:
  - `field`: string, required, 1~100자
  - `colormap`: string, required, 1~50자
  - `viewAngle`: string, required, 1~20자
- 응답 `201 Created`:
  - `visualizationId`
  - `resultId`
  - `status`
  - `viewerUrl`
  - `websocketUrl`
  - `createdAt`
- 백엔드는 결과 접근 권한을 검증한다.
- 백엔드는 Trame Service session 생성 시 결과 소유자 UUID를 `user_uuid`로 전달한다.
- 백엔드는 Trame Service session 생성 body의 `simulation_id`로 API 서버 simulation UUID가 아니라 실행 job의 `labJobId`를 전달한다.
- 프론트는 `labJobId`, `user_uuid`, Lab Gateway API key를 직접 알거나 전달하지 않는다.

### 4.2 조회

- `GET /api/v1/visualizations/{visualizationId}`
- 요청 쿼리 없음.
- 응답:
  - `visualizationId`
  - `resultId`
  - `status`
  - `viewerUrl`
  - `websocketUrl`
  - `fieldsAvailable`
  - `currentTimestep`
  - `totalTimesteps`
- 조회 시점에 백엔드는 가능하면 upstream Trame 세션 상태를 다시 읽고 로컬 메타데이터를 갱신한다.
- `closed`, `failed` 상태는 upstream 재동기화 없이 저장된 메타데이터를 반환한다.

### 4.3 제어

- `PATCH /api/v1/visualizations/{visualizationId}`
- 요청 쿼리 없음.
- 요청 body optional fields:
  - `field`
  - `colormap`
  - `viewAngle`
  - `timestep`
  - `deltaAzimuth`
  - `deltaElevation`
  - `zoom`
  - `panX`
  - `panY`
  - `reset`
- 응답:
  - `visualizationId`
  - `status`
  - `updated`
- 백엔드 구현 규칙:
  - 값이 존재하는 필드만 `updated`와 upstream payload에 포함한다.
  - `field`, `colormap`, `timestep`은 Trame 통합 control endpoint로 전달한다.
  - `deltaAzimuth`, `deltaElevation`, `zoom`, `panX`, `panY`, `reset`은 Trame camera endpoint로 전달한다.
  - `viewAngle`은 생성 시 config 호환 필드이며, 새 Lab Server 명세에는 수정용 view endpoint가 없어 별도 upstream 호출을 만들지 않는다.

프론트 구현 주의:

- 빈 body로 PATCH를 보내지 않는다.
- UI에서 연속 drag 이벤트마다 PATCH를 난사하지 않는다.
- field, colormap, timestep 변경과 camera 변경은 가능하면 별도 사용자 액션 단위로 보낸다.
- `viewAngle`은 생성 기본값/재생성 옵션 중심으로 다루고, live control처럼 강조하지 않는다.

### 4.4 종료

- `DELETE /api/v1/visualizations/{visualizationId}`
- 요청 쿼리/body 없음.
- 응답:
  - `visualizationId`
  - `status: "closed"`
  - `closedAt`
- 백엔드는 Trame Service 세션 종료 API를 호출하고 로컬 상태를 `closed`로 저장한다.

### 4.5 스크린샷

- `GET /api/v1/visualizations/{visualizationId}/screenshot`
- 요청 쿼리:
  - `width`
  - `height`
  - `format`
- `format`은 현재 `png`만 허용한다.
- 응답은 JSON이 아니라 PNG 바이너리다.
- `Content-Type`은 upstream 시각화 서버 응답을 그대로 사용한다.

### 4.6 WebSocket

- `WS /api/v1/visualizations/{visualizationId}/ws`
- 요청 쿼리:
  - `accessToken`: 브라우저 WebSocket에서 Authorization 헤더를 보낼 수 없을 때 사용하는 JWT access token
- WebSocket 응답에서는 `X-New-Access-Token` 같은 HTTP token refresh header를 받을 수 없다.
- JWT가 만료되면 프론트는 `/auth/refresh`로 새 access token을 받은 뒤 재연결해야 한다.
- upstream 연결 실패 시 close code `1013`으로 종료될 수 있다.
- 인증/권한 실패는 연결 거부 또는 policy close로 나타날 수 있다.

## 5. 현재 프론트엔드 상태

### 5.1 `lib/api/admin.ts`

이미 존재:

- `VisualizationStatus`
- `CreateVisualizationBody`
- `CreateVisualizationResponse`
- `VisualizationDetail`
- `UpdateVisualizationBody`
- `UpdateVisualizationResponse`
- `CloseVisualizationResponse`
- `VisualizationScreenshotParams`
- `createVisualization`
- `getVisualization`
- `updateVisualization`
- `closeVisualization`
- `getVisualizationScreenshot`

부족:

- 공용 `lib/api/visualizations.ts`가 아니라 admin 모듈에 묶여 있다.
- `UpdateVisualizationBody`에 camera fields가 없다.
- unload keepalive delete helper가 없다.
- WebSocket URL 생성 helper가 없다.
- 이름이 `closeVisualization`, `getVisualizationScreenshot`으로 되어 있어 Phase 1 계획의 `deleteVisualization`, `downloadVisualizationScreenshot`과 어긋난다.

### 5.2 `components/pages/Simulation2Page.tsx`

이미 존재:

- 결과 기반 시각화 생성 호출
- 기존 visualization 세션 close 후 새 visualization 생성
- viewer iframe 표시
- visualization WebSocket proxy 연결
- WebSocket reconnect 최대 3회
- close code `1008`에서 token refresh 후 재연결 시도
- 주기적 `GET /visualizations/{id}` 동기화
- unload cleanup에서 `DELETE /visualizations/{id}` best effort 호출
- 수동 "시각화 동기화" 버튼으로 close 후 recreate

부족:

- `POST /results/{id}/visualizations`가 직접 `apiRequest`로 구현되어 있다.
- 생성 body가 `field: "phase"`, `colormap: "coolwarm"`, `viewAngle: "xz"`로 고정되어 있다.
- `GET /visualizations/{id}`도 직접 `apiRequest`로 구현되어 있다.
- unload cleanup raw `fetch`가 UI 파일 안에서 URL과 auth header를 직접 조립한다.
- WebSocket URL도 UI 파일 안에서 직접 조립한다.
- `websocketUrl` 응답 필드와 backend proxy WS endpoint의 의미 차이가 코드에 잘 드러나지 않는다.
- screenshot 다운로드 사용자 액션이 없다.
- backend PATCH 기반 기본 컨트롤이 없다.
- Advanced Trame 직접 제어와 backend orchestration 제어가 시각적으로/구조적으로 충분히 분리되어 있지 않다.

### 5.3 `components/pages/AdminPage3.tsx`

이미 존재:

- 관리자 화면에서 visualization create/get/update/close/screenshot 기능 사용
- screenshot mutation이 binary download helper를 사용

부족:

- update form이 field, colormap, viewAngle, timestep 중심이며 camera fields가 없다.
- admin helper가 공용 helper로 분리되지 않았다.
- 사용자 화면과 같은 API helper를 사용한다는 보장이 없다.

### 5.4 `components/simulation/trame/AdvancedTramePanel.tsx`

현재 성격:

- Lab Server Gateway 직접 연동 고급 패널
- `NEXT_PUBLIC_LAB_SERVER_URL`와 `trameSessionId`가 있을 때 활성화
- control/export/interactive viewer/composite 기능을 제공

정책:

- Phase 5의 기본 사용자 흐름은 backend visualization API다.
- Advanced Trame 패널은 고급 실험 기능으로 남긴다.
- 직접 Lab 연동은 `lib/api/labserverTrameClient.ts` adapter 뒤에 유지한다.
- backend API 기반 control과 Lab Gateway 직접 control을 같은 버튼/상태처럼 섞지 않는다.

## 6. 구현 범위

### 6.1 포함

- `lib/api/visualizations.ts` 신설
- 기존 `lib/api/admin.ts` visualization 타입/함수 이동 또는 re-export
- `Simulation2Page.tsx` 직접 visualization API 호출을 helper로 교체
- `CreateVisualizationBody` 생성 기본값 결정 정책 구현
- `UpdateVisualizationBody` camera fields 추가
- backend PATCH 기반 기본 visualization control UI 추가
- screenshot download helper와 사용자 액션 추가
- backend WebSocket URL 생성 helper 추가
- unload keepalive delete helper 추가
- Advanced Trame 직접 연동의 위치와 라벨 정책 정리
- API/UI 테스트 추가 또는 갱신
- 실제 구현 시 루트 `docs/` 업데이트

### 6.2 제외

- Lab Gateway의 새 endpoint 추가
- backend API에 없는 animation/data/scene/export 기능을 backend visualization API처럼 포장하는 작업
- WebSocket message protocol을 새로 정의하는 작업
- iframe 내부 Trame UI를 직접 조작하는 작업
- access token을 localStorage로 옮기는 작업
- server-side secret을 브라우저 환경변수로 노출하는 작업
- `viewAngle` live update를 실제 camera preset update처럼 꾸미는 작업

## 7. 공용 API 계층 설계

### 7.1 신규 파일

```text
lib/api/visualizations.ts
```

### 7.2 타입

```ts
export type VisualizationStatus = 'created' | 'active' | 'closed' | 'failed';

export interface CreateVisualizationBody {
  field: string;
  colormap: string;
  viewAngle: string;
}

export interface CreateVisualizationResponse {
  visualizationId: string;
  resultId: string;
  status: VisualizationStatus;
  viewerUrl: string | null;
  websocketUrl: string | null;
  createdAt: string;
}

export interface VisualizationDetail {
  visualizationId: string;
  resultId: string;
  status: VisualizationStatus;
  viewerUrl: string | null;
  websocketUrl: string | null;
  fieldsAvailable: string[];
  currentTimestep: number | null;
  totalTimesteps: number | null;
}

export interface UpdateVisualizationBody {
  field?: string;
  colormap?: string;
  viewAngle?: string;
  timestep?: number;
  deltaAzimuth?: number;
  deltaElevation?: number;
  zoom?: number;
  panX?: number;
  panY?: number;
  reset?: boolean;
}

export interface UpdateVisualizationResponse {
  visualizationId: string;
  status: VisualizationStatus;
  updated: UpdateVisualizationBody;
}

export interface CloseVisualizationResponse {
  visualizationId: string;
  status: 'closed';
  closedAt: string;
}

export interface VisualizationScreenshotParams {
  width?: number;
  height?: number;
  format?: 'png';
}

export interface VisualizationWebSocketParams {
  accessToken?: string | null;
}
```

주의:

- DTO와 UI view model을 섞지 않는다.
- `VisualizationDetail.websocketUrl`은 backend가 저장한 upstream URL이다. 기본 브라우저 연결 helper는 backend proxy URL을 만든다.
- `UpdateVisualizationBody`는 optional field 집합이지만 empty object는 유효한 사용자 액션이 아니다.

### 7.3 함수

```ts
export function createVisualization(
  resultId: string,
  body: CreateVisualizationBody
): Promise<CreateVisualizationResponse>;

export function getVisualization(
  visualizationId: string
): Promise<VisualizationDetail>;

export function updateVisualization(
  visualizationId: string,
  body: UpdateVisualizationBody
): Promise<UpdateVisualizationResponse>;

export function deleteVisualization(
  visualizationId: string
): Promise<CloseVisualizationResponse>;

export function deleteVisualizationKeepalive(
  visualizationId: string
): void;

export function downloadVisualizationScreenshot(
  visualizationId: string,
  params?: VisualizationScreenshotParams
): Promise<VisualizationScreenshotDownload>;

export function createVisualizationWebSocketUrl(
  visualizationId: string,
  params?: VisualizationWebSocketParams
): string;
```

구현 규칙:

- 모든 path segment는 `encodePathSegment`를 사용한다.
- `createVisualization` body에는 명세상 required 3개 필드만 보낸다.
- `updateVisualization`은 `undefined`, `null`, 빈 문자열을 제거한 뒤 body를 만든다.
- 정리 후 body가 비어 있으면 API를 호출하지 않고 validation error를 반환하거나 호출자가 막는다.
- screenshot은 `downloadBinary`를 사용한다.
- screenshot 기본 `format`은 `png`로 둔다.
- `width`, `height`는 값이 있을 때만 query에 넣는다.
- `deleteVisualizationKeepalive`는 unload cleanup 전용 helper다.
- `deleteVisualizationKeepalive`는 UI 컴포넌트가 base URL과 auth header 조립을 직접 하지 않도록 캡슐화한다.
- WebSocket URL helper는 backend API base URL을 `ws`/`wss`로 변환해 `/api/v1/visualizations/{id}/ws`를 만든다.
- access token은 query에 넣을 수밖에 없지만 로그에 남기지 않는다.

### 7.4 기존 모듈 정리

`lib/api/admin.ts`:

- visualization 타입과 함수 구현을 `visualizations.ts`로 이동한다.
- 관리자 화면 호환을 위해 re-export를 둘 수 있다.
- 최종적으로 admin 화면도 공용 helper를 사용한다.

`Simulation2Page.tsx`:

- direct `apiRequest('/api/v1/results/.../visualizations')` 제거
- direct `apiRequest('/api/v1/visualizations/...')` 제거
- raw unload `fetch` 제거
- WebSocket URL string 직접 조립 제거

## 8. 시각화 생성 기본값 정책

### 8.1 현재 문제

현재 사용자 화면은 시각화 생성 body를 다음처럼 고정한다.

```ts
{
  field: 'phase',
  colormap: 'coolwarm',
  viewAngle: 'xz',
}
```

이 값은 특정 결과에 `phase` field가 존재한다는 가정에 의존한다. 백엔드 명세는 `field`를 required로 요구하지만, 어떤 field가 유효한지는 결과/필드 계약에서 확인해야 한다.

### 8.2 권장 결정 순서

시각화 생성 field는 아래 순서로 결정한다.

1. 사용자가 Result Explorer에서 선택한 field
2. `getResult(resultId).summary.fields`에 포함된 field 중 사용자 기본 선호 field
3. `getResult(resultId).summary.fieldSummaries`의 첫 field
4. `listResultFields(resultId)`로 명시 조회한 첫 field
5. field가 없으면 시각화 생성을 막고 결과 필드 없음 상태를 표시

기본 선호 field:

- `phase`가 실제 field 목록에 있을 때만 `phase` 사용
- 없으면 첫 번째 field 사용

주의:

- field 목록이 확인되지 않았는데 `phase`를 무조건 보내지 않는다.
- field 확인을 위해 고비용 `/fields` API를 매번 자동 호출하지 않는다.
- Phase 4의 Result Explorer가 이미 field를 로드했다면 그 선택/캐시를 우선 사용한다.

### 8.3 colormap 정책

- 기본값은 기존 사용자 경험 보존을 위해 `coolwarm`으로 둔다.
- 사용자가 선택한 colormap이 있으면 그 값을 사용한다.
- colormap 목록은 프론트에서 지원하는 제한 목록으로 UI를 구성할 수 있다.
- 백엔드가 colormap enum을 제공하지 않으므로 임의로 서버 계약처럼 문서화하지 않는다.

권장 기본 목록:

```ts
const DEFAULT_COLORMAPS = ['coolwarm', 'viridis', 'jet', 'grayscale'];
```

### 8.4 viewAngle 정책

- 생성 기본값은 기존 흐름을 유지해 `xz`로 둔다.
- UI에서 선택지를 제공한다면 `xz`, `xy`, `yz`, `iso` 정도의 제한된 문자열을 사용한다.
- live update control로는 강조하지 않는다.
- 다른 view angle로 보고 싶으면 새 visualization 생성 또는 backend가 향후 명확한 view update 계약을 제공할 때 별도 Phase로 다룬다.

## 9. 사용자 화면 설계

### 9.1 상태 분리

`Simulation2Page`의 workflow 상태는 다음 개념을 구분해야 한다.

- `resultId`: 현재 visualization이 대상으로 삼은 result
- `visualizableResultId`: 시각화를 열 수 있다고 판단된 result
- `visualizationId`: backend visualization resource id
- `visualizationStatus`: backend visualization status
- `viewerUrl`: iframe source
- `websocketUrl`: backend detail response에 저장된 upstream websocket URL
- `trameSessionId`: Advanced Trame 직접 연동을 위해 viewerUrl에서 파생한 Lab session id
- `fieldsAvailable`: backend `GET /visualizations/{id}`에서 받은 fields
- `currentTimestep`
- `totalTimesteps`

주의:

- `visualizationStatus`가 `closed` 또는 `failed`이면 iframe/WS/control을 비활성화한다.
- job stage가 `completed`여도 visualization은 계속 active일 수 있다.
- visualization 생명주기를 job polling 생명주기에 종속시키지 않는다.

### 9.2 생성 액션

시각화 생성은 사용자 명시 액션으로만 발생한다.

허용 액션:

- Result Explorer의 "시각화 열기"
- 최신 결과 기준 "시각화 열기"
- 현재 result에 대한 "시각화 동기화" 또는 "재생성"

금지:

- 결과 목록 조회 성공만으로 자동 생성
- field catalog 조회 성공만으로 자동 생성
- 페이지 복원만으로 사용자 의도 없이 새 visualization 생성

### 9.3 생성 전 검증

생성 버튼 클릭 시:

1. resultId가 있는지 확인한다.
2. 기존 active visualization이 있으면 먼저 `deleteVisualization`을 호출한다.
3. field 후보가 없으면 `getResult(resultId)`를 호출해 lightweight field 목록을 확인한다.
4. field가 여전히 없으면 사용자에게 시각화 가능한 field가 없다는 상태를 표시한다.
5. `CreateVisualizationBody`를 만든다.
6. `createVisualization(resultId, body)`를 호출한다.
7. 응답의 `visualizationId`, `viewerUrl`, `websocketUrl`, `status`를 상태에 반영한다.
8. 즉시 `getVisualization(visualizationId)`를 1회 호출해 metadata를 동기화한다.
9. WebSocket 연결 effect가 backend proxy WS에 연결한다.

### 9.4 기본 컨트롤 UI

backend API 기반 기본 컨트롤을 iframe 주변에 배치한다.

최소 컨트롤:

- field select
- colormap select
- timestep input 또는 slider
- camera reset
- camera rotate left/right/up/down
- zoom in/out
- screenshot download
- close visualization
- resync visualization

제어 규칙:

- field select 변경: `updateVisualization(vizId, { field })`
- colormap 변경: `updateVisualization(vizId, { colormap })`
- timestep 변경: `updateVisualization(vizId, { timestep })`
- camera reset: `updateVisualization(vizId, { reset: true })`
- rotate: `deltaAzimuth` 또는 `deltaElevation` 단위 PATCH
- zoom: `zoom` 단위 PATCH
- pan: 필요할 때만 `panX`, `panY` 단위 PATCH

UX 주의:

- slider drag 중 매 프레임 PATCH하지 않는다. commit 시점에만 호출한다.
- timestep은 `0 <= timestep < totalTimesteps` 범위로 제한한다. `totalTimesteps`가 없으면 숫자 입력만 제공하거나 비활성화한다.
- field 목록은 `fieldsAvailable`이 있으면 그것을 우선 사용한다.
- `fieldsAvailable`이 비어 있으면 Result Explorer의 field 목록을 fallback으로 사용할 수 있다.
- PATCH 성공 후 `updated`만 믿고 전체 메타데이터를 확정하지 않는다. 필요한 경우 `getVisualization`으로 재동기화한다.

### 9.5 screenshot 액션

사용자 액션:

- PNG 다운로드 버튼

동작:

1. `visualizationId`가 active인지 확인한다.
2. `downloadVisualizationScreenshot(visualizationId, { format: 'png' })`를 호출한다.
3. `Content-Disposition` 파일명을 우선 사용한다.
4. 파일명이 없으면 `visualization-{visualizationId}.png` fallback을 사용한다.
5. 실패해도 visualization 세션 상태를 failed로 바꾸지 않는다.

선택 옵션:

- width/height를 UI에서 직접 받는 것은 후순위다.
- 초기 구현은 width/height 없이 backend/upstream 기본값을 사용할 수 있다.

### 9.6 WebSocket 연결 정책

기본 연결:

- 브라우저는 `createVisualizationWebSocketUrl(visualizationId, { accessToken })` 결과로 연결한다.
- 이 URL은 backend proxy endpoint다.
- `VisualizationDetail.websocketUrl` 응답 필드는 화면 표시/디버그용으로만 취급하고 기본 연결에는 사용하지 않는다.

reconnect:

- 수동 close이면 reconnect하지 않는다.
- 정상 close `1000`이면 reconnect하지 않는다.
- policy/auth close로 해석되는 `1008`이면 `refreshAccessToken()` 후 재연결한다.
- upstream unavailable `1013`이면 제한된 backoff 후 중단하고 사용자에게 연결 불안정 상태를 표시한다.
- 최대 재시도 횟수는 현재처럼 3회 기준을 유지한다.

token 주의:

- access token을 console log에 남기지 않는다.
- WebSocket URL을 에러 메시지에 그대로 출력하지 않는다.
- sessionStorage 접근은 helper 호출 직전 UI event/effect 경계에서만 수행한다.

### 9.7 주기적 metadata sync

현재 코드의 7초 주기 `GET /visualizations/{id}`는 명세와 맞는다. 다만 helper로 이동한 뒤 다음 정책을 명확히 한다.

- 생성 직후 즉시 1회 sync
- active/created 상태에서만 주기 sync
- closed/failed 상태에서는 중지
- iframe 표시 여부와 무관하게 active visualization이면 유지 가능
- 페이지 hidden 상태에서 polling 빈도를 줄이는 것은 후속 최적화로 둔다.

## 10. Advanced Trame 직접 연동 정책

### 10.1 기본 원칙

사용자 기본 흐름:

```text
Frontend -> Backend /api/v1/visualizations/* -> Lab Gateway / Trame
```

고급 직접 흐름:

```text
Frontend -> lib/api/labserverTrameClient.ts -> Lab Server Gateway
```

두 흐름은 계약과 인증 경계가 다르므로 섞지 않는다.

### 10.2 화면 정책

- 기본 iframe과 기본 컨트롤은 backend visualization API 기반이다.
- `AdvancedTramePanel`은 "Lab Server 직결" 성격이 드러나야 한다.
- `NEXT_PUBLIC_LAB_SERVER_URL`이 없으면 비활성 안내를 유지한다.
- `trameSessionId`가 없으면 활성화하지 않는다.
- Advanced panel의 실패가 backend visualization 세션 실패로 전파되지 않게 한다.

### 10.3 코드 정책

- Advanced panel은 `lib/api/labserverTrameClient.ts` adapter를 계속 사용한다.
- `Simulation2Page`가 Lab Gateway REST endpoint를 직접 호출하지 않는다.
- backend API 기반 control을 Advanced panel 내부로 넣지 않는다.
- Advanced panel의 export/composite 기능을 backend visualization API 기능처럼 문서화하지 않는다.

## 11. 구현 순서

### Step 1. 공용 visualization API helper 분리

대상:

- `lib/api/visualizations.ts`
- `lib/api/admin.ts`
- `lib/api/visualizations.test.ts`
- 필요 시 `lib/api/admin.test.ts`

작업:

1. visualization 타입과 함수 구현을 `visualizations.ts`로 이동한다.
2. `admin.ts`는 기존 import 호환을 위해 re-export한다.
3. `UpdateVisualizationBody`에 camera fields를 추가한다.
4. `downloadVisualizationScreenshot` 이름으로 screenshot helper를 제공한다.
5. 기존 `getVisualizationScreenshot`은 호환 wrapper로 유지하거나 호출부를 모두 교체한다.
6. `deleteVisualizationKeepalive` helper를 추가한다.
7. `createVisualizationWebSocketUrl` helper를 추가한다.
8. path encoding, JSON body, binary download, WS URL 생성 테스트를 추가한다.

### Step 2. `Simulation2Page` 직접 호출 제거

대상:

- `components/pages/Simulation2Page.tsx`
- 필요 시 `components/pages/Simulation2Page.test.tsx`

작업:

1. `createVisualization` direct `apiRequest`를 helper로 교체한다.
2. `syncVisualizationFromServer`의 direct `apiRequest`를 `getVisualization`으로 교체한다.
3. `closeVisualizationSession`의 normal delete를 `deleteVisualization`으로 교체한다.
4. unload cleanup raw fetch를 `deleteVisualizationKeepalive`로 교체한다.
5. WebSocket URL string 조립을 `createVisualizationWebSocketUrl`로 교체한다.
6. access token refresh reconnect 흐름은 유지하되 token logging 금지를 지킨다.

### Step 3. 생성 기본값 정렬

대상:

- `components/pages/Simulation2Page.tsx`
- Phase 4에서 추가될 `ResultExplorerPanel`
- 필요 시 `lib/visualization/defaults.ts`

작업:

1. `phase/coolwarm/xz` 하드코딩을 제거한다.
2. selected field가 있으면 우선 사용한다.
3. `getResult(resultId)`에서 field 후보를 확보한다.
4. field 후보가 없으면 생성을 막는다.
5. colormap 기본값은 `coolwarm` 유지.
6. viewAngle 기본값은 `xz` 유지.
7. 생성 body를 만드는 순수 helper를 둘 수 있다.

예시 helper:

```ts
interface BuildCreateVisualizationInput {
  selectedField?: string | null;
  resultFields?: string[];
  colormap?: string | null;
  viewAngle?: string | null;
}

export function buildCreateVisualizationBody(
  input: BuildCreateVisualizationInput
): CreateVisualizationBody | null;
```

### Step 4. 기본 backend control UI 추가

대상:

- 신규 후보 `components/simulation/VisualizationControlBar.tsx`
- 또는 기존 visualization 카드 내부
- `components/pages/Simulation2Page.tsx`

작업:

1. field select를 추가한다.
2. colormap select를 추가한다.
3. timestep control을 추가한다.
4. camera reset/rotate/zoom 버튼을 추가한다.
5. 각 control은 `updateVisualization`을 호출한다.
6. PATCH 성공 후 필요한 경우 `getVisualization`으로 metadata를 재동기화한다.
7. 실패 시 Phase 6 에러 경험 기준에 맞춰 toast 또는 inline error로 표시한다.

### Step 5. screenshot 다운로드 추가

대상:

- visualization 카드 또는 `VisualizationControlBar`
- `lib/api/visualizations.ts`
- 테스트 파일

작업:

1. screenshot 버튼을 추가한다.
2. `downloadVisualizationScreenshot(vizId, { format: 'png' })`를 호출한다.
3. Blob 다운로드 처리는 기존 다운로드 유틸과 일관되게 처리한다.
4. screenshot 실패가 active visualization 상태를 닫지 않게 한다.

### Step 6. Advanced Trame 경계 정리

대상:

- `components/simulation/trame/AdvancedTramePanel.tsx`
- `components/pages/Simulation2Page.tsx`
- 필요 시 루트 `docs/`

작업:

1. 기본 backend control과 Advanced Trame 직접 control의 위치를 분리한다.
2. Advanced panel의 직접 연동 조건을 유지한다.
3. `trameSessionId` 파생 로직을 helper로 옮길지 검토한다.
4. 직접 Lab 연동이 backend API 기능처럼 보이지 않게 문서/라벨을 정리한다.

### Step 7. 테스트와 문서 반영

대상:

- `lib/api/visualizations.test.ts`
- `components/pages/Simulation2Page.test.tsx`
- 필요 시 control component test
- 루트 `docs/`

작업:

1. API helper 테스트를 추가한다.
2. 사용자 화면에서 시각화 생성 body가 field 후보 기반으로 만들어지는지 테스트한다.
3. WebSocket URL 생성이 backend proxy endpoint를 향하는지 테스트한다.
4. close cleanup이 delete helper를 호출하는지 테스트한다.
5. screenshot download binary 테스트를 추가한다.
6. 실제 구현 후 공식 문서를 갱신한다.

## 12. 테스트 계획

### 12.1 API helper 테스트

`lib/api/visualizations.test.ts`:

- `createVisualization('result/1', body)`:
  - `/api/v1/results/result%2F1/visualizations`
  - method `POST`
  - body에 `field`, `colormap`, `viewAngle`만 포함
- `getVisualization('viz/1')`:
  - `/api/v1/visualizations/viz%2F1`
  - query 없음
- `updateVisualization('viz/1', { timestep: 120 })`:
  - method `PATCH`
  - body `{ timestep: 120 }`
- `updateVisualization('viz/1', { deltaAzimuth: 15, zoom: 1.1 })`:
  - camera fields가 body에 포함
- `updateVisualization('viz/1', {})`:
  - API 호출이 발생하지 않거나 호출자가 막는 정책 검증
- `deleteVisualization('viz/1')`:
  - method `DELETE`
- `downloadVisualizationScreenshot('viz/1', { width: 640, height: 480 })`:
  - `/api/v1/visualizations/viz%2F1/screenshot?format=png&width=640&height=480`
  - binary blob 반환
- `createVisualizationWebSocketUrl('viz/1', { accessToken: 'token' })`:
  - backend proxy WS path 사용
  - visualizationId encode
  - accessToken query encode

### 12.2 사용자 화면 테스트

`Simulation2Page.test.tsx`:

- 시각화 열기 클릭 시 `createVisualization` helper가 호출된다.
- 생성 field는 실제 result field 후보에서 선택된다.
- field 후보가 없으면 `createVisualization`이 호출되지 않는다.
- 기존 active visualization이 있으면 새 생성 전에 delete helper가 호출된다.
- 생성 직후 `getVisualization` sync가 호출된다.
- WebSocket 연결 URL은 backend proxy endpoint다.
- close 버튼 클릭 시 `deleteVisualization` helper가 호출된다.
- screenshot 버튼 클릭 시 `downloadVisualizationScreenshot` helper가 호출된다.
- PATCH control 클릭 시 `updateVisualization` helper가 예상 body로 호출된다.

### 12.3 관리자 화면 테스트

`AdminPage3` 관련 테스트 또는 helper 테스트:

- 기존 create/get/update/close/screenshot 동작이 공용 helper 이동 후에도 유지된다.
- camera fields가 update body에 포함될 수 있다.
- screenshot helper 이름 변경 후에도 기존 기능이 깨지지 않는다.

## 13. 수용 기준

Phase 5는 아래 조건을 만족하면 완료로 본다.

- visualization API helper가 `lib/api/visualizations.ts`로 분리되어 있다.
- 관리자 화면과 사용자 화면이 같은 visualization helper를 사용한다.
- 사용자 화면에 visualization 관련 direct `apiRequest`와 raw WebSocket URL 조립이 남지 않는다.
- unload cleanup의 raw `fetch`가 helper 뒤로 이동했다.
- 생성 body의 field가 결과 field 후보에 기반해 결정된다.
- field 후보가 없을 때 시각화 생성을 시도하지 않는다.
- POST 생성 직후 GET 상세 동기화가 수행된다.
- PATCH body에 명세상 camera fields를 보낼 수 있다.
- viewAngle은 live upstream update로 오해되지 않게 처리된다.
- screenshot은 binary download helper로 처리된다.
- WebSocket 연결은 backend proxy endpoint를 사용한다.
- Advanced Trame 직접 연동은 고급 기능으로 분리되어 있다.
- 관련 테스트가 갱신된다.
- 실제 코드 변경이 있다면 루트 `docs/` 문서도 함께 갱신된다.

## 14. 주요 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `phase` field 하드코딩 유지 | 특정 결과에서 시각화 생성 실패 | result field 후보 기반 생성 |
| `websocketUrl`에 직접 연결 | 인증/권한/proxy 정책 우회 | backend WS URL helper만 기본 사용 |
| screenshot을 JSON으로 처리 | 다운로드 실패 | `downloadBinary` 사용 |
| PATCH body 과도한 조합 | upstream control/camera 라우팅 혼선 | 사용자 액션 단위 payload |
| viewAngle live update 오해 | UI는 바뀌는 듯 보이나 upstream 미반영 | 생성/재생성 옵션으로 제한 |
| WS token 만료 | 시각화 연결 끊김 | 1008 close 후 refresh/reconnect |
| unload cleanup 실패 | Trame 세션 누수 | keepalive helper + best effort 명시 |
| Advanced direct Lab 기능 혼용 | 인증/계약 경계 흐림 | 기본 backend control과 고급 직접 panel 분리 |
| GET metadata polling 과다 | backend/upstream 부하 | active 상태에서만 7초 기준 유지 |

## 15. 후속 Phase 연결

- Phase 4의 Result Explorer는 field 후보와 selected field를 Phase 5 생성 body 결정에 제공한다.
- Phase 6은 visualization 생성/제어/WS/screenshot 실패 메시지와 사용자 회복 동작을 정리한다.
- Phase 7은 공용 helper 이동 후 관리자/사용자 화면의 회귀 테스트와 문서 동기화를 묶는다.
