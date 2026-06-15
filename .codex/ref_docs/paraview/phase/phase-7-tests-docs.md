# Phase 7. Tests And Docs

## 1. 작업 유형 판단

- 작업 유형: 문서화 + 리팩토링 검증 계획 + API 설계 검증 + UI 경계 회귀 테스트 계획
- 적용 우선 규칙:
  - `.codex/ai_rule_developer/GLOBAL_RULES.md`
  - `.codex/ai_rule_developer/API_DESIGN_RULES.md`
  - `.codex/ai_rule_developer/ARCHITECTURE_RULES.md`
  - `.codex/ai_rule_developer/SERVICE_LAYER_RULES.md`
  - `.codex/ai_rule_developer/EXTERNAL_INTEGRATION_RULES.md`
  - `.codex/ai_rule_developer/DOCUMENT_RULE.md`
- 주의:
  - 이 문서는 사용자 요청에 따라 `.codex/ref_docs` 아래에 작성한다.
  - Phase 7에서 생성/갱신하는 공식 프로젝트 명세는 반드시 프로젝트 루트 `docs/` 아래에 둔다.
  - `.codex/ref_docs`는 구현 후 실제 프로젝트 명세 위치로 사용하지 않는다.

## 2. Phase 목표

Phase 0~6에서 정리한 backend API 계약 정합성, API client 계층, job/result/visualization/error UX를 테스트와 공식 문서로 고정한다.

Phase 7은 새 사용자 기능을 크게 추가하는 단계가 아니다. 앞선 phase의 구현이 다시 흩어지거나, backend 명세와 다른 query/body/status/error 처리를 만들지 않도록 회귀 보호막을 만드는 단계다.

핵심 목표:

1. backend API 명세와 프론트 API helper의 path/query/body/binary/WS 계약을 테스트로 고정한다.
2. 사용자 화면의 핵심 상태 전이와 고비용 API 호출 정책을 테스트한다.
3. 관리자 화면과 사용자 화면이 같은 공용 helper를 쓰도록 회귀를 막는다.
4. error details, token refresh, WebSocket reconnect 같은 장애 흐름을 테스트한다.
5. 공식 프로젝트 문서를 루트 `docs/`에 갱신한다.
6. phase별 구현 완료 여부를 검증할 수 있는 체크리스트와 실행 명령을 남긴다.

## 3. 현재 문서에 대한 비판적 검토

기존 Phase 7 문서는 필요한 키워드는 담고 있지만 다음 문제가 있다.

- 현재 저장소의 실제 테스트 구조와 실행 명령을 반영하지 않았다.
- `npm run test:run`, `npx tsc --noEmit --pretty false`, coverage, build 같은 검증 단계를 구분하지 않았다.
- 어떤 테스트 파일을 새로 만들고 어떤 기존 테스트를 수정할지 구체적이지 않다.
- Phase 1~6 산출물을 어떤 테스트가 보호하는지 연결되어 있지 않다.
- `sync=false`/`sync=true` 테스트를 언급하지만 job/result/field/visualization별로 query 정책이 다르다는 점을 분리하지 않았다.
- WebSocket 테스트가 "URL 생성"과 "reconnect 상태 전이"로 나뉘어야 하는데 하나로 묶여 있다.
- `docs/` 갱신 대상이 제안 수준에 머물고, 기존 공식 문서 구조와 충돌 여부를 검토하지 않았다.
- `.codex/ref_docs`에 생성한 phase 문서와 루트 `docs/` 공식 문서의 역할 차이를 수용 기준으로 충분히 못 박지 않았다.
- E2E/Playwright가 dev dependency에는 있지만 기존 script/config가 없으므로 Phase 7의 필수 검증으로 둘지 선택 기준이 필요하다.

따라서 Phase 7은 "테스트를 추가한다"가 아니라, 계약 테스트, UI 회귀 테스트, 정적 경계 점검, 공식 문서 동기화를 분리해서 실행 가능한 수준으로 작성한다.

## 4. 현재 검증 인벤토리

### 4.1 package scripts

현재 `package.json` 기준:

```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "test": "vitest",
    "test:run": "vitest run",
    "test:coverage": "vitest run --coverage"
  }
}
```

기본 검증 명령:

```bash
npm run test:run
npx tsc --noEmit --pretty false
npm run build
```

보조 검증:

```bash
npm run test:coverage
npm run lint
```

주의:

- `lint`는 현재 `next lint`다. Next.js 버전 변화로 script가 실패할 수 있으므로 Phase 7 구현 시 실제 동작을 확인하고, 실패하면 별도 문서에 남긴다.
- Playwright dependency는 있지만 현재 확인된 script/config는 없다. E2E는 필수 완료 조건이 아니라 별도 선택 작업으로 둔다.

### 4.2 현재 테스트 파일

현재 확인된 주요 테스트:

- `lib/apiClient.test.ts`
- `lib/auth.test.ts`
- `lib/api/admin.test.ts`
- `lib/api/chatSessions.test.ts`
- `lib/api/labserver*.test.ts`
- `components/pages/Simulation2Page.test.tsx`
- `components/pages/LoginPage.test.tsx`
- `components/simulation/JobResultListCard.test.tsx`
- `components/simulation/SessionListCard.test.tsx`
- `components/simulation/trame/trameReviewFixes.test.ts`

현재 강점:

- `apiClient` token refresh와 `X-New-Access-Token` 처리가 일부 테스트된다.
- admin helper의 path encoding, query, binary download, screenshot이 일부 테스트된다.
- Simulation2Page의 채팅 입력 기본 흐름이 테스트된다.
- JobResultListCard의 job cancel/result visualization 액션이 테스트된다.

현재 부족:

- 공용 `lib/api/http.ts`, `jobs.ts`, `results.ts`, `visualizations.ts`, `errors.ts` 분리 후 테스트 파일 계획이 없다.
- `Simulation2Page` 테스트는 chat input 중심이라 job/result/visualization/error 상태 전이를 거의 보호하지 않는다.
- Phase 4 이후 결과 행 클릭과 시각화 액션 분리 테스트가 필요하다.
- Phase 5 이후 visualization 생성 기본 field 선택, PATCH camera body, WS URL helper, keepalive delete 테스트가 필요하다.
- Phase 6 이후 error normalizer와 user/admin error panel 테스트가 필요하다.
- 문서와 구현의 endpoint/helper mapping을 검증하는 절차가 없다.

## 5. 테스트 분류 원칙

### 5.1 Contract Unit Tests

대상:

- API helper
- query builder
- path segment encoding
- binary download
- WebSocket URL 생성
- error normalizer

목표:

- backend 명세와 다른 path/query/body가 생기면 즉시 실패한다.
- UI 없이 빠르게 실행된다.
- 네트워크는 `global.fetch` mock만 사용한다.

### 5.2 State And Component Tests

대상:

- `Simulation2Page`
- `JobResultListCard`
- `ResultExplorerPanel`
- `VisualizationControlBar`
- `ApiErrorNotice`

목표:

- 사용자 액션과 상태 전이를 검증한다.
- 고비용 API가 자동 반복 호출되지 않는지 검증한다.
- viewer/WS/timer는 mock으로 제한한다.

### 5.3 Static Boundary Checks

대상:

- UI 파일의 직접 `apiRequest` 호출 제거 여부
- 공용 helper 사용 여부
- `.codex/ref_docs`에 공식 프로젝트 명세를 새로 쓰지 않는지 확인

목표:

- 계층 경계가 다시 흐려지는 회귀를 잡는다.

선택 구현:

- `scripts/check-pfm-api-boundaries.mjs` 같은 작은 검증 스크립트를 둘 수 있다.
- 단, Phase 7에서 스크립트 추가가 과하다고 판단되면 수동 `rg` 체크를 문서화한다.

### 5.4 Official Docs Checks

대상:

- `docs/api`
- `docs/architecture`
- `docs/database` 필요 여부

목표:

- 구현된 API client 계층, 상태 전이, error/retry 정책이 공식 문서에 남는다.
- backend API 자체를 프론트에서 새로 정의하지 않는다.

## 6. Phase별 테스트 매트릭스

| Phase | 보호할 계약 | 테스트 위치 |
|---|---|---|
| Phase 1 API client layer | `http.ts`, feature API helper, path/query/body/binary/WS | `lib/api/*.test.ts`, `lib/apiClient.test.ts` |
| Phase 2 chat/session | session list/detail/messages, restore flow, refresh | `lib/api/chatSessions.test.ts`, `SessionListCard.test.tsx`, `Simulation2Page.test.tsx` |
| Phase 3 job monitoring | `sync=false` polling, `sync=true` manual, cancel, WS fallback | `lib/api/jobs.test.ts`, `Simulation2Page.test.tsx` |
| Phase 4 result explorer | result detail/fields/files/download, row select vs visualization action | `lib/api/results.test.ts`, `ResultExplorerPanel.test.tsx`, `JobResultListCard.test.tsx` |
| Phase 5 visualization | create/get/patch/delete/screenshot/ws, field default, cleanup | `lib/api/visualizations.test.ts`, `VisualizationControlBar.test.tsx`, `Simulation2Page.test.tsx` |
| Phase 6 error experience | normalizer, user/admin panel, endpoint별 error state | `lib/api/errors.test.ts`, `ApiErrorDetailsPanel.test.tsx`, `Simulation2Page.test.tsx`, `AdminPage3` 관련 테스트 |

## 7. API Helper Test Plan

### 7.1 `lib/api/http.test.ts`

검증:

- `encodePathSegment('a/b c')`가 path segment를 안전하게 encode한다.
- `withQuery`가 `undefined`, `null`, 빈 문자열을 생략한다.
- boolean query가 `true`/`false` 문자열로 직렬화된다.
- `getFilenameFromContentDisposition`가 `filename`, quoted filename, `filename*`을 처리한다.
- `downloadBinary`가 blob, filename, contentType을 보존한다.
- download error envelope도 `ApiError`로 보존한다.

### 7.2 `lib/api/chatSessions.test.ts`

검증:

- `createChatSession`
- `listChatSessions`
- `getChatSession`
- `updateChatSession`
- `deleteChatSession`
- `listChatSessionMessages`
- `sendChatSessionMessage`
- path segment encoding
- query `page`, `size`, `title`, `status`
- delete response의 cascade count 필드 보존

### 7.3 `lib/api/simulations.test.ts`

검증:

- `createSimulation`
- `getSimulation`
- `updateSimulation`
- `listSimulations`
- `getSimulationInputPreview`
- `submitSimulationJob`
- `autoVisualization=false` body
- PATCH validation body가 UI view model과 섞이지 않는지 최소 계약 테스트

주의:

- `listSimulationResults`는 Phase 4 이후 `results.ts`로 이동하거나 re-export한다. 중복 helper가 생기면 테스트에서 드러나야 한다.

### 7.4 `lib/api/jobs.test.ts`

검증:

- `listSimulationJobs(simulationId, { sync: false })`
- `listSimulationJobs(simulationId, { sync: true })`
- `getJob(jobId, { sync })`
- `listJobEvents(jobId, { sync })`
- `cancelJob(jobId)`
- job monitor WS URL helper가 생겼다면 path/accessToken encode

중요:

- polling 기본값은 `sync=false`여야 한다.
- 수동 refresh/sync 액션만 `sync=true`를 사용한다.
- `sync` query가 job 계열에만 붙고 result/visualization 계열에는 붙지 않아야 한다.

### 7.5 `lib/api/results.test.ts`

검증:

- `listSimulationResults(simulationId)`:
  - `/api/v1/simulations/{simulationId}/results`
  - query 없음
- `getResult(resultId)`:
  - query 없음
- `listResultFields(resultId)`:
  - query 없음
- `listResultFieldFiles(resultId, fieldName, params)`:
  - fieldName path encode
  - `page`, `size`, `timestep`, `fromTimestep`, `toTimestep`, `refresh`
  - `timestep` 사용 시 range query 생략 정책
- `downloadResultFile(resultId, fileId, fallbackFilename)`:
  - binary helper 사용
  - filename/contentType fallback

### 7.6 `lib/api/visualizations.test.ts`

검증:

- `createVisualization(resultId, body)`:
  - required body `field`, `colormap`, `viewAngle`
- `getVisualization(visualizationId)`:
  - query 없음
- `updateVisualization(visualizationId, body)`:
  - field/colormap/timestep
  - camera fields `deltaAzimuth`, `deltaElevation`, `zoom`, `panX`, `panY`, `reset`
  - empty body 방지 정책
- `deleteVisualization(visualizationId)`
- `deleteVisualizationKeepalive(visualizationId)`:
  - `keepalive: true`
  - auth header helper 사용
- `downloadVisualizationScreenshot(visualizationId, params)`:
  - `format=png`
  - binary helper 사용
- `createVisualizationWebSocketUrl(visualizationId, { accessToken })`:
  - backend proxy path
  - token query encode
  - token을 console/log에 노출하지 않는 구조

### 7.7 `lib/api/errors.test.ts`

검증:

- `VALIDATION_ERROR` invalidFields/validationErrors 추출
- `UPSTREAM_LAB_ERROR` diagnostics 추출
- `UPSTREAM_REQUEST_ERROR` category
- `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT` category
- malformed response fallback
- secret/token redaction
- boolean config flags 보존

### 7.8 `lib/apiClient.test.ts`

기존 테스트 유지 + 추가 검증:

- `X-New-Access-Token` 저장
- protected 401에서 refresh single-flight
- refresh 실패 시 token cleanup
- `auth/refresh`, `auth/login`은 refresh loop 대상이 아님
- envelope 없는 422/500 응답이 `UNKNOWN_ERROR`로 보존됨
- API base URL 누락 시 frontend origin으로 fallback하지 않음

## 8. Component And UI Test Plan

### 8.1 `components/simulation/JobResultListCard.test.tsx`

Phase 4 이후 수정:

- 결과 행 클릭은 result detail selection만 호출한다.
- 별도 visualization 버튼만 visualization callback을 호출한다.
- `actionsDisabled`일 때 job cancel과 visualization action 모두 비활성화된다.
- `selectedResultId` 표시 상태를 검증한다.
- failed result는 visualization action이 비활성화된다.

### 8.2 `components/simulation/ResultExplorerPanel.test.tsx`

검증:

- resultId 없음 상태
- `getResult` 성공 후 summary/files/log/basic 표시
- files[] download action
- field catalog는 사용자 액션 전 호출되지 않음
- field catalog load 후 field summaries 표시
- field 선택 후 file list 조회
- filter validation `fromTimestep > toTimestep`
- timestep 단독 필터 사용 시 range query 생략
- download 실패가 panel 전체 실패로 번지지 않음
- `UPSTREAM_REQUEST_ERROR`는 해당 영역 error로 표시

### 8.3 `components/simulation/VisualizationControlBar.test.tsx`

검증:

- field change PATCH body
- colormap change PATCH body
- timestep commit PATCH body
- camera reset/rotate/zoom PATCH body
- screenshot download action
- update 실패 시 viewer를 닫지 않음
- disabled 상태: visualizationId 없음, closed/failed 상태

### 8.4 `components/common/ApiErrorDetailsPanel.test.tsx`

검증:

- user audience는 수정 가능한 항목과 action guide 중심으로 표시
- admin audience는 diagnostics와 redacted raw details 표시
- validationErrors가 없으면 validation section 숨김
- raw details redaction
- 긴 값 overflow 방지 기본 class 유지

### 8.5 `components/pages/Simulation2Page.test.tsx`

기존 chat input 테스트 유지 + 추가:

- session restore 성공/404/403
- chat message 실패 시 error notice 표시
- simulation patch 422는 draft/editing context 유지
- job submit body에 `autoVisualization=false`
- job polling은 `sync=false`
- terminal job에서 polling 중단
- terminal cancelled job에서 partial result availability 확인
- job submit 422는 invalid field 표시
- job submit 502 retryable/actionGuide 표시
- cancel 502는 local cancelled로 거짓 전이하지 않음
- result 선택은 visualization 생성하지 않음
- visualization 버튼은 createVisualization 호출
- existing visualization이 있으면 새 create 전에 delete 호출
- createVisualization field는 result fields 기반
- visualization GET metadata sync 실행
- WS close 1008은 refresh 후 reconnect
- WS close 1013은 제한된 retry 후 notice 표시

### 8.6 `components/pages/AdminPage3` 관련 테스트

선택지:

- `AdminPage3` 전체 렌더링 테스트가 무거우면 공통 error panel과 API helper 테스트로 대부분 대체한다.
- 그래도 아래 회귀는 가능한 범위에서 검증한다.

검증:

- admin error description이 공통 `ApiErrorDetailsPanel`을 사용한다.
- 404 selected id cleanup 정책이 유지된다.
- result field/file API는 수동 액션 전 자동 호출되지 않는다.
- visualization screenshot binary download mutation이 유지된다.

## 9. Static Boundary Check Plan

### 9.1 직접 API 호출 점검

Phase 1 이후 사용자 화면에서 아래 직접 호출이 남지 않아야 한다.

- `POST /api/v1/results/{id}/visualizations`
- `GET /api/v1/visualizations/{id}`
- `DELETE /api/v1/visualizations/{id}`
- job/result helper로 이동한 endpoint의 raw `apiRequest`
- WebSocket URL 직접 string 조립
- unload cleanup raw `fetch` 직접 조립

수동 점검 예:

```bash
rg -n "apiRequest\\(`/api/v1/(results|visualizations|jobs|simulations)" components/pages/Simulation2Page.tsx
rg -n "new WebSocket|accessToken=" components/pages/Simulation2Page.tsx
rg -n "fetch\\(`\\$\\{PFM_LLM_URL\\}/api/v1/visualizations" components/pages/Simulation2Page.tsx
```

판단:

- 모든 raw 호출을 금지하는 것은 아니다. Phase 1에서 helper로 옮기기로 한 endpoint만 금지한다.
- chat/simulation helper 마이그레이션이 완료된 뒤에는 그 범위도 같이 확장한다.

### 9.2 `.codex/ref_docs` 공식 문서 사용 금지 점검

점검:

- 구현 후 공식 프로젝트 명세가 `.codex/ref_docs`에만 남아 있지 않은지 확인한다.
- phase 문서는 참고자료로 유지 가능하지만, 실제 architecture/API/state/flow 변경은 `docs/`에 있어야 한다.

수동 점검:

```bash
rg -n "lib/api/(jobs|results|visualizations|errors)|ResultExplorerPanel|VisualizationControlBar|ApiErrorDetailsPanel" docs
```

## 10. 공식 문서 갱신 계획

### 10.1 반드시 검토할 기존 문서

- `docs/architecture/directory.md`
- `docs/architecture/architecture.md`
- `docs/architecture/component.md`
- `docs/architecture/state.md`
- `docs/architecture/flow.md`
- `docs/api/endpoints.md`
- `docs/api/specification.md`

주의:

- 프론트가 backend public endpoint를 새로 추가하는 것이 아니면 `docs/api/endpoints.md`와 `docs/api/specification.md`를 backend endpoint 정의처럼 수정하지 않는다.
- 다만 프론트 내부 API client mapping 문서를 `docs/api` 또는 `docs/architecture`에 추가할 수 있다.

### 10.2 신규/갱신 후보 문서

#### `docs/architecture/api-client-layer.md`

내용:

- `lib/api/http.ts`
- `lib/api/chatSessions.ts`
- `lib/api/simulations.ts`
- `lib/api/jobs.ts`
- `lib/api/results.ts`
- `lib/api/visualizations.ts`
- `lib/api/errors.ts`
- DTO와 UI view model 분리 원칙
- binary download helper
- WS URL helper
- auth refresh 경계

#### `docs/architecture/paraview-visualization-flow.md`

내용:

- result explorer에서 field 선택
- visualization 생성 body 결정
- iframe viewer
- backend proxy WebSocket
- metadata sync
- PATCH control
- screenshot
- close/cleanup
- Advanced Trame 직접 연동과 backend orchestration API 분리

#### `docs/api/frontend-backend-api-map.md`

내용:

- frontend helper별 backend endpoint mapping
- query/body 주의사항
- 고비용 API 호출 정책
- error code 표시 정책 링크

주의:

- 이 문서는 backend API specification이 아니라 frontend 사용 map이다.
- endpoint 상세 계약을 중복 작성하지 않고, 상세 계약은 backend spec 또는 `docs/api/specification.md`를 참조한다.

### 10.3 기존 문서별 업데이트 기준

| 문서 | 업데이트 조건 |
|---|---|
| `docs/architecture/directory.md` | 새 `lib/api/*`, `components/common`, result/visualization component 추가 |
| `docs/architecture/component.md` | `ResultExplorerPanel`, `VisualizationControlBar`, `ApiErrorNotice` 책임 추가 |
| `docs/architecture/state.md` | `selectedResultId`, `visualizableResultId`, visualization/error 상태 전이 추가 |
| `docs/architecture/flow.md` | job polling, result explorer, visualization, error/retry 흐름 추가 |
| `docs/architecture/architecture.md` | API client layer와 external boundary 변경이 크면 갱신 |
| `docs/api/endpoints.md` | 프론트 public route/API handler가 추가된 경우만 갱신 |
| `docs/api/specification.md` | 프론트 public API 계약이 바뀐 경우만 갱신 |
| `docs/database/schema.md` | DB 변경이 없으면 갱신하지 않음 |

## 11. 구현 순서

### Step 1. 테스트 기반 정리

1. 기존 테스트를 모두 실행해 baseline을 확인한다.
2. Phase 1~6에서 실제 구현된 파일 목록을 정리한다.
3. 구현된 helper와 backend endpoint mapping 표를 만든다.
4. 중복 helper 또는 raw 호출 잔존 지점을 `rg`로 찾는다.

### Step 2. API contract test 추가

1. `lib/api/http.test.ts`
2. `lib/api/jobs.test.ts`
3. `lib/api/results.test.ts`
4. `lib/api/visualizations.test.ts`
5. `lib/api/errors.test.ts`
6. 기존 `admin.test.ts`는 공용 helper re-export 호환 테스트로 축소 또는 유지한다.

### Step 3. UI/state regression test 추가

1. `JobResultListCard.test.tsx` 갱신
2. `ResultExplorerPanel.test.tsx` 추가
3. `VisualizationControlBar.test.tsx` 추가
4. `ApiErrorDetailsPanel.test.tsx` 추가
5. `Simulation2Page.test.tsx` 핵심 흐름 추가

### Step 4. 정적 경계 점검

1. 직접 API 호출 잔존 여부 확인
2. WebSocket URL 조립 helper 사용 여부 확인
3. unload keepalive helper 사용 여부 확인
4. `.codex/ref_docs`에 공식 명세가 남지 않았는지 확인

### Step 5. 공식 문서 갱신

1. `docs/architecture/directory.md`
2. `docs/architecture/component.md`
3. `docs/architecture/state.md`
4. `docs/architecture/flow.md`
5. 필요 시 `docs/architecture/api-client-layer.md`
6. 필요 시 `docs/architecture/paraview-visualization-flow.md`
7. 필요 시 `docs/api/frontend-backend-api-map.md`

### Step 6. 최종 검증

필수:

```bash
npm run test:run
npx tsc --noEmit --pretty false
```

권장:

```bash
npm run test:coverage
npm run build
```

선택:

```bash
npm run lint
```

실행 결과 기록:

- 실패한 명령
- 실패 원인
- 수정 여부
- 남은 known risk

## 12. Completion Checklist

### 12.1 API contract

- [ ] 모든 API helper path segment encode 테스트가 있다.
- [ ] job 계열 `sync` query 정책이 테스트된다.
- [ ] result 계열에는 명세 외 `sync` query가 붙지 않는 것이 테스트된다.
- [ ] field file filter validation/query 정책이 테스트된다.
- [ ] binary download filename/contentType/fallback이 테스트된다.
- [ ] visualization PATCH camera fields가 테스트된다.
- [ ] WebSocket URL helper가 backend proxy endpoint를 만든다.
- [ ] keepalive delete helper가 unload cleanup을 캡슐화한다.
- [ ] `ApiError` details가 normalizer까지 보존된다.

### 12.2 UI behavior

- [ ] 결과 행 선택과 시각화 생성 액션이 분리되어 있다.
- [ ] result field catalog는 사용자 액션 전 자동 호출되지 않는다.
- [ ] job polling은 `sync=false`로 동작한다.
- [ ] 수동 sync만 `sync=true`를 사용한다.
- [ ] cancel 실패 시 로컬 상태를 거짓 cancelled로 만들지 않는다.
- [ ] visualization 생성은 사용자 명시 액션으로만 발생한다.
- [ ] visualization field 기본값은 실제 result field 후보 기반이다.
- [ ] screenshot/control 실패가 viewer를 닫지 않는다.
- [ ] 422 validation details가 사용자에게 표시된다.
- [ ] 502 upstream diagnostics가 보존된다.

### 12.3 Docs

- [ ] 새 공식 프로젝트 명세는 `docs/`에 있다.
- [ ] `.codex/ref_docs`는 phase/reference 문서로만 남아 있다.
- [ ] `docs/architecture/directory.md`가 새 파일/디렉터리와 일치한다.
- [ ] `docs/architecture/component.md`가 새 component 책임을 설명한다.
- [ ] `docs/architecture/state.md`가 workflow/result/visualization/error 상태를 설명한다.
- [ ] `docs/architecture/flow.md`가 주요 사용자 흐름과 error/retry를 설명한다.
- [ ] frontend-backend API mapping 문서가 필요하면 `docs/` 아래에 있다.
- [ ] 존재하지 않는 endpoint나 컴포넌트를 문서화하지 않았다.

## 13. Acceptance Criteria

Phase 7은 아래 조건을 만족하면 완료로 본다.

- `npm run test:run`이 통과한다.
- `npx tsc --noEmit --pretty false`가 통과한다.
- API helper contract 테스트가 backend 명세의 path/query/body/binary/WS 규칙을 보호한다.
- 사용자 화면의 job/result/visualization/error 핵심 흐름이 컴포넌트 테스트로 보호된다.
- 관리자 화면과 사용자 화면의 공용 helper/error panel 사용이 테스트 또는 정적 점검으로 확인된다.
- 고비용 API 호출 정책이 테스트된다.
- 공식 문서가 루트 `docs/`에 갱신되어 있다.
- `.codex/ref_docs`에만 남은 프로젝트 명세가 없다.
- 실행하지 못한 검증이 있다면 이유와 잔여 위험이 문서화되어 있다.

## 14. 주요 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| UI 테스트가 timer/WS 때문에 불안정 | 회귀 테스트 신뢰도 하락 | fake timer, WebSocket mock, 핵심 상태만 검증 |
| contract 테스트가 admin helper에만 남음 | 사용자 helper 회귀 미검출 | 공용 `lib/api/*` 테스트로 이동 |
| 문서가 backend spec을 중복 작성 | 명세 drift 증가 | frontend mapping은 요약/참조 중심 |
| `.codex/ref_docs`가 공식 문서처럼 사용됨 | 문서 위치 규칙 위반 | official docs checklist |
| coverage 목표만 강조 | 중요한 계약 누락 | phase별 risk 기반 테스트 우선 |
| `next lint` script 실패 | 검증 파이프라인 혼란 | lint는 선택 검증으로 두고 실패 사유 기록 |
| Playwright를 무리하게 필수화 | 환경 부담 증가 | 현 단계 필수는 Vitest/TS check, E2E는 별도 |
| mock이 실제 API 계약과 달라짐 | 거짓 안정감 | backend spec의 실제 sample/error shape 사용 |

## 15. 후속 운영 규칙

Phase 7 이후 backend API 명세가 바뀌면 다음 순서로 처리한다.

1. `.codex/ref_docs/backend_api.md` 또는 공식 backend spec 변경 내용을 확인한다.
2. frontend-backend API mapping 문서를 갱신한다.
3. 해당 `lib/api/*` contract test를 먼저 수정한다.
4. API helper 구현을 수정한다.
5. 영향받는 component/state/error 테스트를 수정한다.
6. 루트 `docs/architecture` 문서를 갱신한다.
7. `npm run test:run`과 `npx tsc --noEmit --pretty false`를 실행한다.
