# Phase 4. Result Explorer

## 1. 작업 유형 판단

- 작업 유형: 컨트롤러/라우트/UI 경계 작업 + API 설계 + 리팩토링 계획
- 적용 우선 규칙:
  - `.codex/ai_rule_developer/GLOBAL_RULES.md`
  - `.codex/ai_rule_developer/API_DESIGN_RULES.md`
  - `.codex/ai_rule_developer/ARCHITECTURE_RULES.md`
  - `.codex/ai_rule_developer/SERVICE_LAYER_RULES.md`
  - `.codex/ai_rule_developer/EXTERNAL_INTEGRATION_RULES.md`
  - `.codex/ai_rule_developer/DOCUMENT_RULE.md`
- 주의:
  - 이 문서는 사용자 요청에 따라 `.codex/ref_docs` 아래에 작성한다.
  - 공식 프로젝트 명세로 승격해야 하는 내용은 실제 구현 단계에서 프로젝트 루트 `docs/`에 별도로 반영한다.

## 2. Phase 목표

백엔드 결과 API 명세에 맞춰 일반 사용자 화면에서도 시뮬레이션 결과를 탐색할 수 있게 한다.

이 Phase의 핵심은 다음 4가지다.

1. 결과 API 헬퍼를 `admin` 전용 모듈에서 공용 결과 API 계층으로 분리한다.
2. 일반 사용자 화면에서 결과 목록, 결과 상세, 필드 요약, 필드 파일 목록, 파일 다운로드를 사용할 수 있게 한다.
3. 결과 행 클릭과 시각화 생성 액션을 분리한다.
4. 결과 탐색 UI가 백엔드의 동기화 부작용과 라이브 카탈로그 비용을 과도하게 발생시키지 않도록 한다.

## 3. 현재 문서에 대한 비판적 검토

기존 Phase 4 문서는 방향은 맞지만 구현자가 바로 작업하기에는 다음 정보가 부족하다.

- `listSimulationResults`, `getResult`, `listResultFields`에 `sync` 파라미터가 있는 것처럼 적혀 있다. 백엔드 명세 기준으로 이 쿼리는 존재하지 않는다.
- `listResultFieldFiles`의 `refresh`는 명세에 남아 있지만 현재 백엔드는 DB 캐시 없이 매번 라이브 조회하므로 실질 차이가 없다는 점이 빠져 있다.
- 결과 목록과 결과 상세 API가 내부적으로 Lab Gateway 동기화를 수행한다는 부작용이 명시되어 있지 않다.
- `/results/{resultId}/fields`와 `/fields/{fieldName}/files`가 라이브 카탈로그 조회이며 비용이 큰 API라는 점이 충분히 드러나지 않는다.
- 현재 사용자 화면에서 결과 행 클릭이 곧바로 시각화 생성을 호출하는 구조를 다루지 않았다.
- 이미 `lib/api/admin.ts`에 결과 API 타입과 헬퍼가 존재한다는 점을 반영하지 않아 중복 구현 위험이 있다.
- `files[]`에 들어오는 입력/로그/메타데이터 파일 다운로드와 필드 파일 다운로드의 차이가 구분되어 있지 않다.
- 바이너리 다운로드 응답은 JSON이 아니므로 기존 `requestJson` 계열로 처리하면 안 된다는 경계가 부족하다.
- `result.status=completed`와 실제 잡 상태 `summary.jobStatus`가 다를 수 있는 부분, 특히 취소 후 부분 결과가 `completed`로 나타날 수 있는 부분이 빠져 있다.
- 테스트 범위와 수용 기준이 추상적이다.

따라서 이 문서는 단순히 "패널을 추가한다"가 아니라, 공용 API 계층 분리, 사용자 액션 분리, 라이브 카탈로그 호출 정책, 다운로드 경계, 테스트 기준까지 구현 가능한 수준으로 구체화한다.

## 4. 백엔드 명세 요약

### 4.1 결과 목록

- `GET /api/v1/simulations/{simulationId}/results`
- 요청 쿼리 없음.
- 응답은 `createdAt DESC` 기준 정렬.
- 응답 전 백엔드는 관련 Lab Job의 상태, 출력 요약, 출력 필드를 동기화할 수 있다.
- 전체 출력 파일 카탈로그는 동기화하지 않는다.
- 일시적인 Lab Gateway 실패는 캐시 또는 fallback으로 흡수될 수 있으나, 상황에 따라 `502`가 반환될 수 있다.

### 4.2 결과 상세

- `GET /api/v1/results/{resultId}`
- 요청 쿼리 없음.
- 가벼운 상세와 출력 요약을 반환한다.
- Lab 상태, 출력 요약, 출력 필드, 로그/BASIC 요약은 동기화할 수 있다.
- 특정 필드의 전체 출력 파일 목록은 조회하지 않는다.
- `summary.fileCatalogMode`는 현재 명세상 `live`다.
- `result.status`는 결과 리소스의 상태이고, 실제 실행 상태 판단에는 `summary.jobStatus`를 함께 사용해야 한다.
- 취소된 잡의 부분 결과는 `result.status=completed`, `summary.jobStatus=cancelled` 조합으로 나타날 수 있다.

### 4.3 필드 요약

- `GET /api/v1/results/{resultId}/fields`
- 요청 쿼리 없음.
- 호출 때마다 Lab Gateway에서 필드별 파일 목록을 라이브 조회하고 count, timestep, size를 집계한다.
- 결과 상세 화면 진입 시 무조건 반복 호출하지 않는다.
- 사용자가 명시적으로 필드 카탈로그를 열 때 1회 호출하는 흐름을 기본으로 한다.

### 4.4 필드 파일 목록

- `GET /api/v1/results/{resultId}/fields/{fieldName}/files`
- 요청 쿼리:
  - `page`: 기본 `1`, 최소 `1`
  - `size`: 기본 `100`, 최대 `100`
  - `timestep`: 특정 timestep만 조회
  - `from_timestep` 또는 `fromTimestep`
  - `to_timestep` 또는 `toTimestep`
  - `refresh`: 기본 `false`, 현재 구현에서는 캐시가 없어 실질 차이 없음
- 매 호출마다 Lab Gateway에서 해당 field의 파일 목록을 라이브 조회한 뒤 메모리에서 필터링/페이지네이션한다.
- `from > to` 조합은 `400 VALIDATION_ERROR`가 될 수 있으므로 프론트에서 사전 검증한다.

### 4.5 파일 다운로드

- `GET /api/v1/results/{resultId}/files/{fileId}/download`
- 응답은 바이너리 attachment이며 JSON이 아니다.
- `fileId`는 백엔드가 발급한 stateless encoded token이다.
- 프론트는 `fileId`를 파싱하거나 조합하지 않는다.
- 다운로드 대상은 입력 파일, 출력 파일, 로그, 메타데이터 파일일 수 있다.
- 파일명은 `Content-Disposition`을 우선 사용하고, 없으면 UI에서 전달한 fallback filename을 사용한다.

## 5. 현재 프론트엔드 상태

### 5.1 이미 구현된 영역

- `lib/api/admin.ts`
  - 결과 목록, 상세, 필드 요약, 필드 파일 목록, 다운로드 타입과 헬퍼가 존재한다.
  - 관리자 화면에서만 사용되도록 묶여 있어 일반 사용자 기능에서 재사용하기 어렵다.
- `components/pages/AdminPage3.tsx`
  - 결과 상세, 필드 조회, 필드 파일 조회, 파일 다운로드 UI가 이미 존재한다.
  - 다만 관리자 화면 중심의 밀도와 문맥이라 사용자 화면에 그대로 옮기면 안 된다.
- `lib/api/simulations.ts`
  - 일반 사용자 화면용 `listSimulationResults`가 있지만 결과 목록만 지원한다.
  - URL path segment encoding이 충분히 일관적이지 않다.
- `components/simulation/JobResultListCard.tsx`
  - 잡과 결과 목록을 보여준다.
  - 현재 결과 행 클릭은 `onSelectResult`를 호출하며, `Simulation2Page`에서는 이것이 시각화 생성으로 연결된다.
- `components/pages/Simulation2Page.tsx`
  - 최신 완료 결과를 찾아 시각화 가능 여부를 판단한다.
  - 결과 선택 상태와 시각화 대상 상태가 명확히 분리되어 있지 않다.

### 5.2 핵심 차이

| 영역 | 현재 프론트엔드 | 백엔드 명세 기준 필요 상태 |
|---|---|---|
| 결과 API 모듈 | `admin.ts`와 `simulations.ts`에 분산 | `lib/api/results.ts` 공용 모듈 |
| 결과 상세 | 관리자 화면에만 있음 | 사용자 화면에서도 접근 |
| 필드 요약 | 관리자 화면에만 있음 | 사용자 화면에서 명시 액션으로 조회 |
| 필드 파일 목록 | 관리자 화면에만 있음 | 사용자 화면에서 필드 선택 후 조회 |
| 다운로드 | 관리자 화면에만 있음 | 사용자 화면에서 `fileId` 기반 바이너리 다운로드 |
| 결과 행 클릭 | 시각화 생성 | 결과 상세 선택 |
| 시각화 생성 | 결과 선택과 결합됨 | 별도 버튼/아이콘 액션 |
| API 파라미터 | 일부 문서에 `sync` 언급 | 명세상 `sync` 없음 |

## 6. 구현 범위

### 6.1 포함

- `lib/api/results.ts` 신설 또는 결과 API 공용 모듈화
- 기존 `lib/api/admin.ts` 결과 API 타입/헬퍼를 공용 모듈로 이동 또는 re-export
- `lib/api/simulations.ts`의 결과 목록 helper를 공용 helper로 통합
- 사용자 화면용 `ResultExplorerPanel` 추가
- `JobResultListCard`의 결과 선택 액션과 시각화 열기 액션 분리
- `Simulation2Page`에서 선택된 결과 ID와 시각화 대상 결과 ID 분리
- 결과 상세 요약 표시
- `files[]` 다운로드 표시
- 로그/BASIC excerpt 표시
- 필드 요약 로드
- 필드 파일 목록 필터/페이지네이션/다운로드
- API 및 UI 테스트 추가/수정
- 실제 코드 변경 시 루트 `docs/`의 관련 문서 업데이트

### 6.2 제외

- 시각화 세션 생성/연결 방식 변경
- Trame iframe 또는 원격 렌더링 UI 개선
- Lab Gateway API 직접 호출
- 백엔드에 없는 결과 삭제/이름변경/재처리 기능
- 클라이언트에서 `fileId` 생성 또는 해석
- 전체 필드 파일 목록을 한 번에 모두 불러와 클라이언트 캐싱하는 기능
- 관리자 화면의 정보 구조를 사용자 화면에 1:1 복제하는 작업

## 7. 공용 API 계층 설계

### 7.1 신규 파일

```text
lib/api/results.ts
```

### 7.2 타입

공용 모듈에 아래 타입을 둔다. 기존 `admin.ts`의 결과 타입과 이름이 같다면 이동 후 re-export한다.

```ts
export type ResultStatus = 'completed' | 'failed';

export interface ResultSummary {
  resultId: string;
  simulationId: string;
  jobId: string;
  status: ResultStatus;
  createdAt: string;
  completedAt: string | null;
  fileCount: number;
  totalSizeBytes: number;
  fields: string[];
}

export interface ResultFileSummary {
  fileId: string;
  name: string;
  type: string;
  sizeBytes: number;
}

export interface ResultFieldSummary {
  fieldName: string;
  cachedFileCount: number;
  firstTimestep: number | null;
  lastTimestep: number | null;
  totalSizeBytes: number;
  catalogStatus: string;
}

export interface ResultDetailSummary {
  upstreamState?: string | null;
  appDirectory?: string | null;
  trameSessionId?: string | null;
  fields: string[];
  totalFiles: number;
  totalSizeBytes: number;
  currentTimestep?: number | null;
  currentTemperature?: number | null;
  solidFraction?: number | null;
  walltime?: number | null;
  logExcerpt?: string | null;
  logTotalLines?: number | null;
  basicExcerpt?: string | null;
  basicTotalLines?: number | null;
  fileCatalogMode?: string | null;
  jobStatus?: string | null;
  simulationStatus?: string | null;
  fieldSummaries?: ResultFieldSummary[];
}

export interface ResultDetail {
  resultId: string;
  simulationId: string;
  jobId: string;
  status: ResultStatus;
  completedAt: string | null;
  files: ResultFileSummary[];
  summary: ResultDetailSummary;
}

export interface ResultFieldsResponse {
  resultId: string;
  fields: ResultFieldSummary[];
}

export interface ListResultFieldFilesParams {
  page?: number;
  size?: number;
  timestep?: number;
  fromTimestep?: number;
  toTimestep?: number;
  refresh?: boolean;
}

export interface ResultFieldFile {
  fileId: string;
  name: string;
  fieldName: string;
  timestep: number | null;
  sizeBytes: number;
}

export interface ResultFieldFilesResponse {
  resultId: string;
  fieldName: string;
  page: number;
  size: number;
  total: number;
  files: ResultFieldFile[];
}
```

타입 설계 주의:

- 서버 응답에 추가 필드가 있어도 UI는 필요한 필드만 사용한다.
- DTO를 UI view model로 바로 간주하지 않는다.
- 표시용 문자열, badge tone, 크기 포맷 등은 UI 또는 view helper에서 별도로 만든다.
- `fileId`는 opaque token으로 취급한다.

### 7.3 함수

```ts
export async function listSimulationResults(
  simulationId: string
): Promise<ResultSummary[]>;

export async function getResult(
  resultId: string
): Promise<ResultDetail>;

export async function listResultFields(
  resultId: string
): Promise<ResultFieldsResponse>;

export async function listResultFieldFiles(
  resultId: string,
  fieldName: string,
  params?: ListResultFieldFilesParams
): Promise<ResultFieldFilesResponse>;

export async function downloadResultFile(
  resultId: string,
  fileId: string,
  fallbackFilename?: string
): Promise<ResultFileDownload>;
```

구현 규칙:

- `simulationId`, `resultId`, `fieldName`, `fileId`는 모두 path segment로 encode한다.
- `listSimulationResults`, `getResult`, `listResultFields`에는 `sync` 같은 명세 외 쿼리를 붙이지 않는다.
- `listResultFieldFiles`는 명세가 허용한 쿼리만 붙인다.
- `fromTimestep`, `toTimestep`은 백엔드가 camelCase와 snake_case를 모두 허용하지만 프론트 내부에서는 camelCase로 통일한다.
- 실제 query key는 현재 관리자 테스트와 호환되는 `fromTimestep`, `toTimestep`을 우선 사용한다.
- 다운로드는 `downloadBinary` 계열을 사용하고 `requestJson`을 사용하지 않는다.
- `Content-Disposition` 파일명이 없으면 `fallbackFilename ?? fileId`를 사용한다.

### 7.4 기존 모듈 정리

`lib/api/admin.ts`:

- 결과 API 타입과 함수 구현을 제거하고 `lib/api/results.ts`에서 import/re-export한다.
- 관리자 화면의 import를 한 번에 바꾸기 어렵다면 단기적으로 re-export를 유지한다.
- 동일 URL helper가 중복되지 않게 한다.

`lib/api/simulations.ts`:

- 결과 목록 타입/함수를 제거하거나 `results.ts`에서 import/re-export한다.
- 사용자 화면은 최종적으로 `lib/api/results.ts`를 직접 import하게 한다.

## 8. 사용자 화면 설계

### 8.1 컴포넌트 구조

신규 컴포넌트:

```text
components/simulation/ResultExplorerPanel.tsx
```

역할:

- 선택된 `resultId` 기준으로 결과 상세를 조회한다.
- 결과 상세, 다운로드 가능한 파일, 로그/BASIC excerpt, 필드 요약, 필드 파일 목록을 표시한다.
- 필드 파일 필터 상태와 페이지네이션 상태를 내부에서 관리한다.
- 시각화 생성은 직접 수행하지 않고 callback으로만 요청한다.

권장 props:

```ts
interface ResultExplorerPanelProps {
  resultId: string | null;
  simulationId: string;
  canOpenVisualization?: boolean;
  onOpenVisualization?: (resultId: string) => void;
}
```

선택 사항:

- `initialResult?: ResultSummary`를 받아 목록에서 이미 가진 정보를 skeleton/summary에 먼저 표시할 수 있다.
- 단, 서버 상세 응답과 UI view model은 분리한다.

### 8.2 결과 목록 카드 변경

`components/simulation/JobResultListCard.tsx`는 결과 행 선택과 시각화 열기를 분리한다.

권장 props:

```ts
onSelectResult?: (result: ResultSummary) => void;
onOpenResultVisualization?: (result: ResultSummary) => void;
selectedResultId?: string | null;
```

동작:

- 결과 행 또는 제목 클릭: 결과 상세 패널 선택
- 별도 아이콘 버튼: 시각화 열기
- 시각화 버튼은 `result.status === 'completed'`일 때만 활성화한다.
- `summary.jobStatus`가 아직 목록 응답에 없으므로, 부분 결과 여부의 정밀 표시는 상세 패널에서 처리한다.

중요:

- 기존 `onSelectResult` 의미를 "시각화 열기"로 계속 사용하면 이후 유지보수자가 같은 실수를 반복할 가능성이 크다.
- 기존 테스트는 "결과 클릭 시 시각화 열기" 기대를 "결과 클릭 시 상세 선택"으로 바꾼다.

### 8.3 `Simulation2Page` 상태 분리

현재 흐름에서 `workflow.resultId`, `visualizableResultId`, 결과 선택 동작이 섞일 수 있다.

권장 상태:

```ts
const [selectedResultId, setSelectedResultId] = useState<string | null>(null);
const [selectedResultSummary, setSelectedResultSummary] = useState<ResultSummary | null>(null);
const [visualizableResultId, setVisualizableResultId] = useState<string | null>(null);
```

역할:

- `selectedResultId`: 결과 탐색 패널의 현재 대상
- `selectedResultSummary`: 목록에서 가져온 표시 보조 정보
- `visualizableResultId`: 최신 완료 결과 또는 사용자가 시각화를 요청할 수 있는 결과
- `workflow.resultId`: 기존 시각화/워크플로우 문맥에서 필요한 경우에만 유지

동작:

- 결과 목록 행 선택:
  - `selectedResultId` 갱신
  - 시각화 생성 호출 없음
- 시각화 버튼 클릭:
  - 기존 `openResultVisualization(result)` 호출
- 최신 결과 시각화 버튼:
  - 기존 `openLatestResultVisualization()` 유지
- 잡 완료 후 결과 자동 확인:
  - `listSimulationResults`를 호출하되, 결과 패널의 필드 카탈로그 조회를 자동으로 동반하지 않는다.

## 9. ResultExplorerPanel 상세 동작

### 9.1 초기 상태

- `resultId`가 없으면 비선택 상태를 표시한다.
- `resultId`가 생기면 `getResult(resultId)`를 호출한다.
- `getResult` 성공 후 아래 정보를 표시한다.
  - 결과 상태
  - 실제 잡 상태 `summary.jobStatus`
  - 시뮬레이션 상태 `summary.simulationStatus`
  - 완료 시각
  - 총 파일 수
  - 총 크기
  - 현재 timestep
  - 현재 온도
  - 고상분율
  - walltime
  - 필드 이름 목록

표시 원칙:

- `result.status`만으로 "실행 성공"을 단정하지 않는다.
- `summary.jobStatus`가 `cancelled`이면 "부분 결과" 성격을 드러낼 수 있게 한다.
- `summary.appDirectory`, `summary.trameSessionId` 같은 운영/디버그 성격 정보는 기본 요약보다 낮은 위계에 둔다.

### 9.2 기본 파일 목록

`ResultDetail.files`는 전체 output field 파일 목록이 아니다.

표시 대상:

- input
- log
- metadata
- 기타 백엔드가 상세 응답에 포함한 lightweight 파일

동작:

- 각 파일 행에 이름, 타입, 크기, 다운로드 액션을 표시한다.
- 다운로드 클릭 시 `downloadResultFile(resultId, file.fileId, file.name)`을 호출한다.
- `fileId`가 비어 있거나 파일명이 없을 때도 UI가 깨지지 않게 fallback을 둔다.

### 9.3 로그/BASIC excerpt

상세 응답의 `summary.logExcerpt`, `summary.basicExcerpt`를 표시한다.

표시 원칙:

- excerpt는 전체 로그가 아니다.
- `logTotalLines`, `basicTotalLines`를 함께 표시한다.
- 긴 텍스트는 고정 높이 영역 또는 접힘 영역으로 제한한다.
- 전체 로그 다운로드는 `files[]`에 로그 파일이 있을 때만 다운로드 액션으로 제공한다.
- 별도 "전체 로그 조회 API"를 임의로 만들지 않는다.

### 9.4 필드 요약 로드

필드 요약은 비용이 큰 라이브 조회이므로 다음 중 하나로 처리한다.

기본 권장안:

- 상세 패널 진입 시 `getResult`만 호출한다.
- `summary.fields`와 `summary.fieldSummaries`가 있으면 먼저 표시한다.
- 사용자가 필드 카탈로그 영역을 열거나 새로고침 버튼을 누를 때 `listResultFields(resultId)`를 호출한다.
- 결과는 resultId 단위로 캐시하고, 같은 resultId에서는 자동 반복 호출하지 않는다.

대안:

- `summary.fieldSummaries`가 비어 있고 `summary.fields.length <= 3`일 때만 자동 1회 호출할 수 있다.
- 이 대안은 UX가 더 부드럽지만 Lab Gateway 호출 비용이 늘 수 있으므로 기본안보다 우선하지 않는다.

### 9.5 필드 선택

- 필드 요약 목록에서 field를 선택한다.
- 선택된 field의 기본 파일 목록 쿼리는 다음 값으로 시작한다.
  - `page=1`
  - `size=100`
  - `refresh=false`
- fieldName은 URL path segment로 encode한다.
- fieldName을 UI에서 변형하거나 slug로 바꾸지 않는다.

### 9.6 필터 정책

필드 파일 목록 필터:

- `timestep`: 정확히 해당 timestep만 조회
- `fromTimestep`, `toTimestep`: 범위 조회
- `size`: 1 이상 100 이하
- `page`: 1 이상

프론트 검증:

- `fromTimestep`과 `toTimestep`이 모두 있고 `fromTimestep > toTimestep`이면 API 호출 전에 에러 상태를 표시한다.
- `timestep`이 입력되어 있으면 `fromTimestep`, `toTimestep`은 요청에 포함하지 않는다.
- 숫자 입력이 빈 문자열이면 해당 query를 생략한다.
- 소수점, 음수, NaN은 유효하지 않은 값으로 처리한다.

### 9.7 필드 파일 목록

표시 정보:

- 파일명
- fieldName
- timestep
- 크기
- 다운로드 액션

페이지네이션:

- `page`, `size`, `total`을 응답 기준으로 표시한다.
- `page * size < total`일 때 다음 페이지를 활성화한다.
- 이전 페이지는 `page > 1`일 때 활성화한다.

다운로드:

- `downloadResultFile(resultId, file.fileId, file.name)` 사용
- 다운로드 중인 파일 ID를 기준으로 버튼 로딩 상태를 표시한다.
- 실패 시 사용자 화면에 간단한 에러 상태를 표시한다.

## 10. 데이터 호출 정책

### 10.1 자동 호출

자동 호출 허용:

- 선택된 결과가 바뀔 때 `getResult(resultId)`
- 사용자가 선택한 field와 필터가 확정되어 있을 때 필드 파일 목록 조회

자동 호출 제한:

- `listResultFields(resultId)`는 패널 진입마다 자동 반복하지 않는다.
- 결과 목록 polling과 필드 카탈로그 조회를 연결하지 않는다.
- 시각화 availability 확인을 위해 필드 파일 목록을 호출하지 않는다.

### 10.2 새로고침

- 결과 상세 새로고침: `getResult(resultId)` 재호출
- 필드 요약 새로고침: `listResultFields(resultId)` 재호출
- 필드 파일 목록 새로고침: 같은 필터로 `listResultFieldFiles` 재호출
- `refresh=true`는 사용자가 명시적으로 새로고침을 눌렀을 때만 보낼 수 있으나, 현재 백엔드에서는 실질 차이가 없음을 구현자가 인지해야 한다.

## 11. 에러/빈 상태

결과 상세:

- `404`: 결과가 없거나 접근 불가
- `502`: Lab Gateway 동기화 실패 가능성
- 기타: 공통 API 에러 메시지 사용

필드 요약:

- 빈 배열: 필드 출력 없음
- `502`: 라이브 카탈로그 조회 실패 가능성

필드 파일:

- 빈 배열: 해당 필터에 맞는 파일 없음
- `400 VALIDATION_ERROR`: 필터 검증 누락 가능성
- `502`: Lab Gateway 파일 목록 조회 실패 가능성

다운로드:

- 브라우저 저장 실패와 API 실패를 구분하기 어렵다면 API 실패 메시지만 표시한다.
- 다운로드 실패 후에도 패널 전체를 실패 상태로 만들지 않는다.

## 12. 구현 순서

### Step 1. 공용 결과 API 모듈 분리

파일:

- `lib/api/results.ts`
- `lib/api/admin.ts`
- `lib/api/simulations.ts`
- `lib/api/results.test.ts`
- 필요 시 `lib/api/admin.test.ts`

작업:

1. `admin.ts`에 있는 결과 타입과 helper를 `results.ts`로 이동한다.
2. `admin.ts`는 기존 관리자 import를 깨지 않도록 re-export한다.
3. `simulations.ts`의 결과 목록 helper를 `results.ts` 기준으로 정리한다.
4. `sync` 쿼리 없이 명세 URL만 호출하는지 테스트한다.
5. `fieldName`, `fileId` path encoding 테스트를 유지한다.
6. 다운로드가 `downloadBinary`를 사용하는지 테스트한다.

### Step 2. 결과 선택과 시각화 액션 분리

파일:

- `components/simulation/JobResultListCard.tsx`
- `components/simulation/WorkspaceTabsCard.tsx`
- `components/pages/Simulation2Page.tsx`
- `components/simulation/JobResultListCard.test.tsx`
- 필요 시 `components/pages/Simulation2Page.test.tsx`

작업:

1. 결과 행 선택 callback과 시각화 열기 callback을 분리한다.
2. 결과 행 클릭은 `selectedResultId` 갱신만 수행한다.
3. 시각화 열기 버튼은 별도 액션으로 제공한다.
4. 기존 최신 결과 시각화 흐름은 유지한다.
5. 테스트에서 결과 행 클릭과 시각화 버튼 클릭을 분리 검증한다.

### Step 3. 사용자 ResultExplorerPanel 추가

파일:

- `components/simulation/ResultExplorerPanel.tsx`
- 필요 시 `components/simulation/ResultExplorerPanel.test.tsx`
- `components/pages/Simulation2Page.tsx`

작업:

1. `resultId` 기준 `getResult` 호출을 구현한다.
2. 상태/요약/기본 파일/로그/BASIC excerpt 표시를 구현한다.
3. 파일 다운로드 액션을 연결한다.
4. `summary.fieldSummaries` 또는 `summary.fields` 기반의 초기 필드 영역을 표시한다.
5. 사용자 액션으로 `listResultFields`를 호출한다.
6. 선택 field의 파일 목록 조회와 필터/페이지네이션을 구현한다.
7. 필드 파일 다운로드 액션을 연결한다.

### Step 4. 화면 통합

파일:

- `components/pages/Simulation2Page.tsx`
- 필요 시 `components/simulation/WorkspaceTabsCard.tsx`

작업:

1. 사용자 화면에 `ResultExplorerPanel`을 배치한다.
2. 선택된 결과가 없을 때와 있을 때의 화면 상태를 연결한다.
3. 기존 시뮬레이션 실행/잡 모니터링/시각화 영역과 상태가 충돌하지 않는지 확인한다.
4. 결과 목록이 갱신되어 선택된 결과가 사라진 경우 선택 상태를 안전하게 초기화한다.

### Step 5. 문서와 회귀 확인

파일:

- `docs/` 아래 관련 공식 문서
- 테스트 파일

작업:

1. 실제 코드 변경 사항을 루트 `docs/` 문서에 반영한다.
2. API helper 테스트를 실행한다.
3. 결과 목록/상세 UI 테스트를 실행한다.
4. 가능하면 사용자 화면 smoke test를 수행한다.

## 13. 테스트 계획

### 13.1 API 테스트

`lib/api/results.test.ts`:

- `listSimulationResults('sim/1')`:
  - `/api/v1/simulations/sim%2F1/results`
  - query 없음
- `getResult('result/1')`:
  - `/api/v1/results/result%2F1`
  - query 없음
- `listResultFields('result/1')`:
  - `/api/v1/results/result%2F1/fields`
  - query 없음
- `listResultFieldFiles('result/1', 'FE/NI', { page: 2, size: 25, timestep: 150 })`:
  - fieldName encode 확인
  - `timestep` 포함
  - 범위 query 미포함
- `listResultFieldFiles('result/1', 'FE/NI', { fromTimestep: 100, toTimestep: 200, refresh: false })`:
  - `fromTimestep`, `toTimestep`, `refresh=false` query 확인
- `downloadResultFile('result/1', 'file/1')`:
  - `/api/v1/results/result%2F1/files/file%2F1/download`
  - binary helper 사용 확인

### 13.2 UI 테스트

`JobResultListCard.test.tsx`:

- 결과 행 클릭 시 `onSelectResult`만 호출한다.
- 시각화 버튼 클릭 시 `onOpenResultVisualization`만 호출한다.
- failed result는 시각화 버튼이 비활성화된다.
- selected result id가 표시 상태에 반영된다.

`ResultExplorerPanel.test.tsx`:

- resultId 없음 상태를 렌더링한다.
- resultId가 있으면 `getResult`를 호출하고 요약을 표시한다.
- `summary.jobStatus=cancelled`, `result.status=completed` 조합을 부분 결과로 표시할 수 있다.
- `files[]` 다운로드 버튼이 `downloadResultFile`을 호출한다.
- 필드 카탈로그 로드 버튼이 `listResultFields`를 호출한다.
- field 선택 후 파일 목록 조회가 `listResultFieldFiles`를 호출한다.
- `timestep` 입력 시 range query를 보내지 않는다.
- `fromTimestep > toTimestep`이면 API 호출 없이 validation error를 표시한다.
- 파일 다운로드 실패가 패널 전체 실패로 전파되지 않는다.

`Simulation2Page.test.tsx`:

- 결과 선택이 시각화 생성을 호출하지 않는다.
- 시각화 버튼은 기존 visualization flow를 호출한다.
- 최신 결과 시각화 버튼의 기존 동작이 유지된다.

## 14. 수용 기준

Phase 4는 아래 조건을 만족하면 완료로 본다.

- 일반 사용자 화면에서 결과 목록의 특정 결과를 선택하면 결과 상세 패널이 열린다.
- 결과 선택만으로 시각화 세션이 생성되지 않는다.
- 별도 시각화 액션을 눌렀을 때만 기존 시각화 생성 흐름이 실행된다.
- 결과 상세 API, 필드 요약 API, 필드 파일 목록 API, 다운로드 API가 백엔드 명세 URL과 query에 맞게 호출된다.
- `sync` 같은 명세 외 query가 결과 API에 붙지 않는다.
- 필드 카탈로그 API는 사용자 액션 또는 명확한 1회 로드 정책으로만 호출된다.
- field file range validation이 프론트에서 먼저 수행된다.
- binary download는 JSON request helper를 사용하지 않는다.
- 관리자 화면과 사용자 화면이 같은 결과 API helper를 사용한다.
- 관련 테스트가 갱신된다.
- 실제 코드 변경이 있다면 루트 `docs/`의 관련 문서가 함께 갱신된다.

## 15. 주요 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 결과 행 클릭이 계속 시각화 생성에 묶임 | 사용자 탐색과 고비용 세션 생성이 결합됨 | 결과 선택 callback과 시각화 callback 분리 |
| 필드 API 자동 반복 호출 | Lab Gateway 부하 증가 | 명시 액션 또는 resultId 단위 1회 호출 |
| 결과 API helper 중복 | admin/user 동작 불일치 | `lib/api/results.ts` 공용화 |
| `sync` 같은 명세 외 query 사용 | 백엔드 계약 이탈 | API 테스트에서 query 부재 검증 |
| 바이너리 다운로드를 JSON으로 처리 | 다운로드 실패 | `downloadBinary` 전용 helper 사용 |
| `fileId`를 프론트에서 해석 | 백엔드 stateless token 계약 위반 | opaque string으로만 전달 |
| 취소 부분 결과를 실패로 오해 | 결과 확인/다운로드 기회 누락 | `result.status`와 `summary.jobStatus` 동시 표시 |
| 거대한 파일 목록 | UI 지연/과도한 호출 | 서버 pagination query 사용, size max 100 |

## 16. 후속 Phase 연결

- Phase 5에서 시각화 생성/연결 흐름을 다룰 때, Phase 4에서 분리한 `onOpenResultVisualization` 액션을 재사용한다.
- Phase 6에서 사용자 화면 상태를 정리할 때, `selectedResultId`와 `visualizableResultId`를 별도 상태로 유지한 결정을 반영한다.
- Phase 7에서 관리자/사용자 공통 API 회귀 테스트를 묶어 결과 API helper 중복이 다시 생기지 않게 한다.
