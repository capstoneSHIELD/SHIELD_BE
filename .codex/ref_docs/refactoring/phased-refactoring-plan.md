# 단계별 리팩토링 실행 계획 (Phased Refactoring Plan)

> 기반 문서:
> - 통합 이슈 목록: `C:\pfm-FE\.codex\ref_docs\refactoring\consolidated-findings.md` (RF-FINDING-001 ~ RF-FINDING-061, 총 61건)
> - 우선순위 로드맵: `C:\pfm-FE\.codex\ref_docs\refactoring\refactoring-priority-roadmap.md` (P0~P3 배정을 그대로 따름)
> - 코드리뷰 원본: `C:\pfm-FE\.codex\ref_docs\codereview\session1` ~ `session6` (각 세션 `refactoring-brief.md`의 우선순위/민감 영역)

**서두 고지 (반드시 읽을 것)**

- 본 문서의 파일 경로·라인·문제 요약은 코드리뷰 원본과 `consolidated-findings.md`에 적힌 **근거**를 그대로 옮긴 것이다. 계획 수립 과정에서 새로 내린 판단(Phase 배정, 작업 순서, 난이도/위험도 해석 등)은 **(추론)** 으로 표기한다. 원본에 없는 사항은 단정하지 않고 **"확인 필요"** 로 표시한다.
- 우선순위(P0~P3)는 `refactoring-priority-roadmap.md`의 배정을 그대로 따른다. 로드맵 4.4절의 "우선순위 → 주 수행 Phase 매핑"은 잠정(추론)이었으며, **본 문서에서 Phase 배정을 확정한다**. 로드맵 잠정 매핑과 달라진 항목은 9장(배정 요약)에 사유를 기록했다 (추론).
- RF-FINDING 61건 전부가 정확히 하나의 **주관 Phase**에 배정되며, 여러 Phase에 걸치는 항목은 **연관 Phase**를 구분 표기한다. 문서 말미 9장에 Phase별 배정 요약 표가 있다.
- 좋은 패턴 14건(consolidated-findings 6장)은 이슈가 아니므로 배정하지 않고, 각 Phase에서 기준 패턴으로 인용한다.
- **게시판 앱(cmsl*, Supabase 기반) 수정 지양 규칙**: 프로젝트 규칙상 별도 요청이 없으면 게시판 앱 수정은 지양한다. 본 계획에서 게시판 앱 영역을 포함하는 작업(Phase 0 ③④, Phase 2 RF-003, Phase 3 RF-020/021/037, Phase 5 RF-044, Phase 6 RF-005/006/007/009 일부)은 **결함 수정/리팩토링 근거와 변경 범위를 커밋·PR에 명시하고 최소 수정으로 진행**하며, 착수 전 사용자 확인을 받는다. (추론)

**검증 명령어 공통 세트 (package.json에서 확인된 실제 스크립트)**

```bash
npm run lint              # next lint
npm run build             # next build
npm run test:run          # vitest run
npm run test:coverage     # vitest run --coverage
npm run test:boundaries   # PFM API boundary 정적 검사 (scripts/check-pfm-api-boundaries.mjs)
npx tsc --noEmit          # 후보 — 전용 typecheck 스크립트 없음 (확인 필요). tsconfig strict 계열 off라 검출력 약함 (RF-FINDING-038 참조)
```

수동 검증 (Playwright MCP):

- local: `http://localhost:3000`
- production (시뮬레이션 앱): `https://pfm.cmsl-kookmin.com/simulation2`

---

## Phase 0. 리팩토링 준비 및 안전장치 확인 (+ P0 핫픽스 선행 트랙)

### 목적

- 리팩토링 전체 기간의 비교 기준이 될 **baseline 검증 상태**(lint/build/test 결과)를 기록한다.
- 세션 brief가 지정한 **민감 영역 목록**을 확정하고, 이후 Phase에서 해당 영역 변경 시 적용할 보호 규칙을 합의한다.
- 구조 리팩토링과 독립적으로 핫픽스 가능한 **P0 4건**을 국소 수정(guard/parser/보상 처리/helper)으로 선행 처리한다 (로드맵 3장 P0 선행 트랙과 동일).

### 관련 코드리뷰 근거

- **RF-FINDING-032** (S4: S4-RACE-001=S4-EFFECT-001(상세), S5: S5-POLLING-001=S5-RACE-001(상세)) — job polling fallback in-flight guard 부재, `Simulation2Page.tsx:1605` (1599, 1611-1615)
- **RF-FINDING-061** (S4: S4-URL-001, S2: S2-STATE-001, S4-EFFECT-005(연관)) — AdminPage3 URL query `Number(...)` NaN 가능, `AdminPage3.tsx:498` (489, 918)
- **RF-FINDING-036** (S5: S5-ROLLBACK-001) — attachment storage remove/upload 후 DB update 실패 시 보상(rollback) 없음, `EditNoticePage.tsx:107`
- **RF-FINDING-051** (S6: S6-ENV-001=S6-NULLABLE-001, S6-ENV-002=S6-NULLABLE-002) — Supabase·EmailJS public env non-null assertion(`!`) 직접 사용, `lib/supabaseClient.ts:5-6`, `ContactPage.tsx:26-29`
- 민감 영역 목록: 각 세션 `refactoring-brief.md` (아래 작업 범위 표 참조)

### 작업 범위

1. **baseline 검증**: `npm run lint` / `npm run build` / `npm run test:run` / `npm run test:coverage` / `npm run test:boundaries` 실행 결과(통과/실패/경고, 커버리지 수치)를 기록한다. 현재 통과 여부는 실행 전 단정하지 않는다 (확인 필요). `npx tsc --noEmit`도 시도하되 strict off로 검출력이 약함을 함께 기록한다.
2. **민감 영역 목록 확인** — 세션 brief 원문 기준:

   | 민감 영역 | 이유 | 확인 근거 (원본 brief) |
   |---|---|---|
   | `lib/apiClient.ts` token refresh / error normalization | 모든 PFM API 호출의 공통 기반 | S1 brief — `lib/apiClient.ts:265, 278, 304` |
   | `lib/api/http.ts` WebSocket/binary/keepalive helper | job/viz/download/unload cleanup과 연결 | S1 brief — `lib/api/http.ts:56, 90, 103` |
   | `Simulation2Page` WebSocket refs/lifecycle, polling fallback, visualization sync | 연결 중복/cleanup/상태 전파 회귀 위험. "상태 전이가 복잡하므로 guard 테스트 없이 대규모 이동 금지" | S1 brief — `Simulation2Page.tsx:611, 1674, 1949` / S3·S4 brief |
   | `AdminPage3` 권한/early return, React Query invalidation, URL query correction | admin 접근 제어 UX와 직접 연결. "query key와 invalidation은 admin UI 전반에 영향이 커서 helper 정리 후 이동" | S1 brief — `AdminPage3.tsx:1157, 1166` / S3·S4 brief |
   | Supabase delete/upload/update 흐름 (CMS edit form의 storage delete/upload 순서 포함) | 운영 데이터 손실 및 권한 정책과 연결 | S1 brief — `EditMemberPage.tsx:71, 74`, `AdminPage2.tsx:66` / S3 brief |
   | `apiRequest<T>` 기본값 전역 변경 | 전체 API call site에 영향 | S6 brief |
   | admin DTO 통합 | 백엔드 admin response가 일반 response와 같은지 확인 후 진행 | S6 brief |
   | `Simulation2Page` parameter mapper 변경 | job submit/update/restore 흐름과 함께 테스트 필요 | S6 brief |

3. **P0 핫픽스 선행 트랙 4건** (로드맵 3장과 동일): ① job polling in-flight guard, ② AdminPage3 URL NaN-safe parser, ③ EditNoticePage attachment rollback, ④ required public env helper.
4. P0 착수 전 해소할 "확인 필요" 항목 수집: ③ 실제 storage path/URL parsing(S5 원문 "확인 필요"), ④ env 이름/배포 설정(S6 원문 "확인 필요").
5. 기준 패턴 목록 공유: consolidated-findings 6장 좋은 패턴 14건(특히 S4-ABORT-001 visualization sync guard `Simulation2Page.tsx:2057, 2100`, S5-TIMEOUT-002 `labserverTrameClient.ts:466-484`).

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `components/pages/Simulation2Page.tsx:1605` (1599, 1611-1615) | ① single-flight polling loop 또는 `pollingInFlightRef` 도입 — 같은 파일의 visualization sync guard(S4-ABORT-001, :2057/:2100)가 기준 패턴 | RF-FINDING-032 |
| `components/pages/AdminPage3.tsx:498` (489, 918) | ② safe integer parser 우선 도입. `useAdminUrlState` correction hook 전체 도입은 Phase 6으로 이연 (추론). 기존 deep link/query 호환성 보존 | RF-FINDING-061 |
| `components/pages/EditNoticePage.tsx:107` | ③ storage remove/upload 후 DB update 실패 시 보상(rollback) 정책 추가. 실제 storage path/URL parsing 확인 필요 | RF-FINDING-036 |
| `lib/supabaseClient.ts:5-6`, `components/pages/ContactPage.tsx:26-29` (+ 신규 `getRequiredPublicEnv` helper, `lib/config/emailjs.ts` 등 integration별 config module) | ④ non-null assertion 제거, 명확한 초기화 오류/disabled fallback 전환. env 이름/배포 설정 확인 후 적용 | RF-FINDING-051 |
| (문서) baseline 기록, 민감 영역 목록 | 이후 Phase 검증·보호 기준 | 전체 |

### 작업 순서

1. baseline 실행: `npm run lint` → `npm run build` → `npm run test:run` → `npm run test:coverage` → `npm run test:boundaries` 결과를 그대로 기록한다 (소스 무변경).
2. `npx tsc --noEmit` 시도 후 오류량 기록 (확인 필요 — 전용 스크립트 없음, strict off라 검출력 약함). RF-FINDING-038(Phase 8)의 사전 측정 자료로 보관.
3. 민감 영역 목록(위 표)을 팀 합의 문서로 확정한다.
4. ③④ 관련 "확인 필요" 해소: storage path/URL parsing 실물 확인, env 이름/배포 설정 확인.
5. **핫픽스 ①** polling guard — 단일 commit. WS fallback/terminal status/result availability 경로와 함께 테스트 (원본 주의사항).
6. **핫픽스 ②** NaN-safe parser — 단일 commit. correction 로직은 최소 정리만, deep link 호환 유지.
7. **핫픽스 ③** attachment rollback — 단일 commit. 게시판 앱 영역이므로 최소 수정 + 근거 명시 (서두 고지 참조).
8. **핫픽스 ④** env helper — 단일 commit. helper 추가 후 기존 env 접근을 한두 파일부터 교체(S6 brief "안전하게 먼저 개선 가능한 영역" 원문 준수).
9. 핫픽스 4건 후 baseline 명령어 재실행, 차이 기록.

### 완료 조건

- baseline 결과(명령어 5종 + tsc 후보)와 민감 영역 목록이 문서로 기록됨.
- P0 4건이 각각 독립 commit으로 반영되고, 수정 지점이 이후 Phase 문서(Phase 3/6 후속)와 연결 표기됨 — 핫픽스가 이후 구조 리팩토링에서 이동될 코드 위에 얹히므로 이중 작업 방지 (로드맵 3.1 원문).
- 핫픽스 후 검증 명령어가 baseline 대비 악화 없이 통과.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요 — strict off라 검출력 약함)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run`, `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP, local `http://localhost:3000` / production `https://pfm.cmsl-kookmin.com/simulation2`):
  - ① job 실행 → polling 네트워크 요청 겹침 없음 (`browser_network_requests` 확인)
  - ② `/cmsl20043?page=abc` 류 invalid query에서 admin 목록 정상 동작
  - ③ 공지 첨부 수정 실패 시나리오에서 파일-DB 정합 유지
  - ④ env 미설정 시 명확한 실패 메시지/disabled fallback

### 위험 요소

- ① **Simulation2Page WebSocket/polling은 민감 영역** (S1/S3/S4 brief): guard 도입이 WS fallback 전환·terminal status 처리·result availability 흐름을 깨지 않는지 함께 테스트해야 함 (원본 주의사항).
- ② URL behavior 변경이 admin list query key에 영향 (S4 brief 원문). **AdminPage3 invalidation/URL correction은 민감 영역** — parser는 pure function으로 먼저 분리(S2 brief "안전하게 먼저" 원문).
- ③ **Supabase delete/upload 순서는 민감 영역** (S1/S3 brief) — 운영 데이터 손실 위험. 게시판 앱 수정 지양 규칙 적용 대상.
- ④ env 이름/배포 설정이 원본 문서에서 확정되지 않음 (확인 필요) — Vercel 배포 환경 확인 전 fallback 동작을 보수적으로 설계.

### 롤백 기준

- 핫픽스 commit 단위 revert. 4건은 서로 독립 commit이므로 문제 항목만 개별 revert.
- ①에서 job polling이 멈추거나 terminal status 미반영이 재현되면 즉시 revert.
- ②에서 기존 deep link(query param 조합)가 깨지면 즉시 revert.
- ③에서 첨부 저장 성공 경로가 회귀하면 즉시 revert (운영 데이터 영역).
- 검증 명령어가 baseline 대비 악화되면 해당 commit revert 후 원인 분석.

---

## Phase 1. type / DTO / API contract 정리

### 목적

- 모든 후속 Phase의 전제가 되는 **status union/DTO 단일화**와 **API 응답 타입 명시 강화**를 수행한다. 하위 계층(type/DTO)을 먼저 정리해야 API client(Phase 2), hook(Phase 4), component(Phase 5) 리팩토링 시 계약 drift를 컴파일 타임에 잡을 수 있다 (추론).

### 관련 코드리뷰 근거

- **RF-FINDING-039** (S1: S1-DEPENDENCY-001, S5: S5-DTO-001, S6: S6-DTO-001, S6-DUPTYPE-001~005, S6-ENUM-001(상세 type-duplication-review), S6-MAPPER-001(상세 validation-formatting-review)) — `SimulationStatus`/`JobStatus`/`VisualizationStatus` status union과 `Composition`/`JobSummary·Detail·Event`/`ResultSummary·Detail·FieldsResponse` 계열 DTO가 일반 API·admin API·workflow에 중복 정의
- **RF-FINDING-040** (S1: S1-TYPE-002, S5: S5-TYPE-001, S6: S6-ANY-003, S6-ASSERT-001, S6-ASSERT-002) — `apiRequest<T = any>` 기본 generic, `JSON.parse(text) as T`/`response.json() as Promise<T>` 단정
- **RF-FINDING-041** (S1: S1-TYPE-003, S6: S6-ANY-001) — `WorkflowState.parameters`가 `Record<string, any>`
- **RF-FINDING-042** (S5: S5-CONTRACT-001, S6: S6-ANY-002, S6-VALIDATION-002) — PATCH body를 page component에서 `Record<string, any>`로 직접 조립

### 작업 범위

- shared status union/DTO 모듈 신설 및 admin 확장 type 구조 도입 (RF-039).
- 핵심 endpoint부터 call site 타입 명시 강화 + parser/guard/schema 도입 (RF-040). `apiRequest<T>` 기본값 `unknown` 전환은 **장기 단계 적용** — 전체 call site 영향이 커서 이번 Phase에서 수행하지 않음 (원본 주의사항).
- `SimulationParametersDto`/`WorkflowParameters`/`EditableSimulationParameters` 분리 (RF-041) — 연관: Phase 5 workflow 리팩토링과 함께 마무리.
- `buildUpdateSimulationBody(formState): UpdateSimulationBody` DTO builder/mapper + schema/guard 분리 (RF-042) — 연관: Phase 5에서 page 측 호출 교체 완성.

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `lib/api/admin.ts` (36/37/40/149-308/391) | admin DTO를 shared DTO + admin 확장 type 구조로 정리. simulation/job/result/viz DTO·wrapper 공존 해소 | RF-FINDING-039 |
| `lib/api/simulations.ts` (16/24-42/136-142), `lib/api/jobs.ts` (4/16-35), `lib/api/results.ts` (14-51), `lib/api/visualizations.ts` (11) | status union/DTO를 shared module로 단일화 | RF-FINDING-039 |
| `components/pages/simulation2/workflowTypes.ts` (4/5/22-36) | workflow stage는 shared status에서 mapper로 파생 | RF-FINDING-039 |
| `components/pages/simulation2/workflowTypes.ts:72` | `WorkflowState.parameters` 타입 분리 | RF-FINDING-041 |
| `lib/apiClient.ts:380, 395`, `lib/api/labserverTrameClient.ts:500-502` | call site 타입 명시 강화, 핵심 endpoint parser/guard 도입 | RF-FINDING-040 |
| `components/pages/Simulation2Page.tsx:2378` (+ 신규 mapper 모듈) | PATCH body DTO builder 분리 — page에서는 builder 호출로 교체 | RF-FINDING-042 |

### 작업 순서

1. **백엔드 명세 확인** (확인 필요 선행): admin DTO와 일반 DTO가 의도적으로 다른 계약인지 백엔드 API 명세 확인 (S6 원문 "확인 필요"). 다르면 admin 확장 type, 같으면 단일 DTO.
2. shared status union/DTO 모듈 신설 후, 기존 각 파일의 타입을 **alias/re-export로 연결**해 행동 무변경 상태에서 컴파일만 통과시킨다 (추론: 한 번에 정의 삭제 금지).
3. 파일별로 작게 (simulations → jobs → results → visualizations → admin 순) 중복 정의를 shared 참조로 교체하고 매 단계 build/test 실행 (추론: 의존 적은 순).
4. workflowTypes의 status를 mapper 파생으로 전환 (RF-039), `workflowMappers.ts` 기존 테스트(S1 brief "안전하게 먼저 개선 가능한 영역": mapper 테스트 보강) 활용.
5. RF-041: parameters 타입 3분리 — 타입 정의와 기존 사용처 최소 수정만. 본격 적용은 Phase 5(연관).
6. RF-042: `buildUpdateSimulationBody` builder/guard 신설 + 단위 테스트. `Simulation2Page.tsx:2378`은 호출 교체만 수행.
7. RF-040: 핵심 endpoint(job/result 계열부터 — 추론)에 parser/guard를 도입하고 call site generic 명시를 보강.

### 완료 조건

- status union/DTO 중복 정의가 shared module 단일 출처로 정리되고, admin 확장 type 구조가 백엔드 명세 확인 결과와 일치.
- `buildUpdateSimulationBody`가 단위 테스트와 함께 도입되고 `Simulation2Page.tsx:2378`의 `Record<string, any>` 직접 조립이 제거됨.
- `apiRequest` 핵심 call site의 generic 명시가 강화됨 (기본값 변경은 미수행 — 명시적 비범위).
- 검증 명령어 전체가 baseline 대비 악화 없이 통과.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요 — strict off라 타입 단일화 회귀 검출력 약함. 신설 모듈은 strict-friendly로 작성)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (workflowMappers/신규 builder·parser 테스트 포함), `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP, `https://pfm.cmsl-kookmin.com/simulation2` 또는 local): simulation 생성 → parameter 수정(PATCH) → job submit → 상태 표시 정상. RF-042는 job submit/update/restore 흐름과 함께 테스트 (S6 brief 원문).

### 위험 요소

- **admin DTO 통합은 민감 영역** (S6 brief): 백엔드 admin response가 일반 response와 같은지 확인 전 통합 금지.
- **`apiRequest<T>` 기본값 전역 즉시 변경 금지** (S6 brief 민감 영역) — call site 명시 강화만 수행.
- **parameter mapper 변경은 job submit/update/restore 흐름과 함께 테스트** (S6 brief 민감 영역).
- status union 단일화 시 admin/job/result 화면 상태 표기가 달라질 수 있음 — 화면 단위 수동 확인 필요 (추론).
- strict off(RF-038)라 타입 교체 누락이 컴파일에서 안 잡힐 수 있음 — 단계별 build + 수동 확인 병행 (추론).

### 롤백 기준

- shared module 도입 commit 이후 build 실패 또는 admin/simulation 화면 상태 표기 오류 발견 시 해당 단계 commit revert (alias/re-export 단계로 복귀).
- job submit/update/restore 수동 플로우 중 하나라도 회귀하면 RF-041/042 관련 commit revert.
- 테스트 커버리지/통과 수가 baseline 대비 감소하면 원인 commit revert.

---

## Phase 2. API client / service layer 정리

### 목적

- PFM API 공통 클라이언트(`lib/apiClient.ts`)의 request safety(timeout/signal/retry 정책)와 token storage 단일화를 정리하고, CMS/게시판의 Supabase 직접 호출을 domain service/query hook + storage adapter로 이관하기 시작한다. 외부 연동(legacy Gemini chat, EmailJS)의 adapter/error mapping을 표준화한다.

### 관련 코드리뷰 근거

- **RF-FINDING-003** (S1: S1-ARCH-002, S1-ARCH-003 / S2: S2-DEPENDENCY-001, S2-DEPENDENCY-002(=S2-CONTAINER-006), S2-DEPENDENCY-003(=S2-CONTAINER-007), S2-CONTAINER-005 / S3: S3-CMS-001(=S3-RESP-007), S3-CMS-002(=S3-RESP-006), S3-RESP-005 / S4: S4-SERVER-005, S4-SERVER-006 / S5: S5-SERVICE-003(요약)(=S5-SERVICE-001(상세)), S5-SERVICE-002(상세)) — CMS/게시판 page·form component가 Supabase query/mutation/storage upload·remove를 직접 수행 (S5-FLOW-015 참고)
- **RF-FINDING-022** (S4: S4-PERSIST-001, S4-PERSIST-002, S4-PERSIST-003(연관) / S5: S5-PERSIST-001(=S5-AUTH-001)) — PFM token storage helper가 `lib/auth.ts`와 `lib/apiClient.ts`에 중복
- **RF-FINDING-028** (S5: S5-TIMEOUT-001, S5-CANCEL-001, S5-INTERCEPTOR-001, S5-RETRY-001) — 공통 `doFetch`/refresh fetch에 AbortSignal/timeout 없음, 일반 network/5xx retry 정책 없음
- **RF-FINDING-030** (S1: S1-EXTERNAL-001 / S5: S5-ERROR-001, S5-ERROR-002, S5-VALIDATION-001, S5-CONTRACT-002 / S6: S6-VALIDATION-001) — legacy Gemini API route의 비표준 error envelope/schema validation 부재
- **RF-FINDING-031** (S1: S1-EXTERNAL-002 / S3: S3-RESP-008) — EmailJS browser SDK를 form submit에서 직접 호출

### 작업 범위

- RF-028: `apiRequest` 옵션에 signal/timeout 도입, refresh timeout 적용, retryable error 정책을 query/hook에 명시할 수 있는 형태로 노출. 기준 패턴: S5-TIMEOUT-002 (`lib/api/labserverTrameClient.ts:466-484`의 timeout+AbortSignal polling loop).
- RF-022: `authTokenStorage` adapter로 단일화, PFM token과 Supabase session storage 경계 문서화.
- RF-003: CMS domain service/repository 또는 query hook(`useNoticeBoard`, `useGalleryBoard`, `useNoticeEditor`) + storage adapter 도입. **한 번에 전체 이관하지 말고 도메인별 진행** (S1 brief 원문). 이번 Phase에서는 service/adapter 계층 신설 + 1~2개 도메인 이관까지 (추론: 잔여 도메인은 Phase 3 error 처리, Phase 5/6 component·page 작업과 함께).
- RF-030: legacy 유지 여부 확인(확인 필요) 후 adapter/error mapping 표준화, request schema(zod/manual guard) + 최소 response parser, TS route handler 전환 검토.
- RF-031: `sendContactEmail` adapter/wrapper 분리와 error mapping 통일 (env 문제는 Phase 0 ④에서 선행 처리됨).
- 연관 처리: RF-013(Phase 5 주관)의 sanitize 정책 확인 결과에 따라 sanitizer를 service 경계에 둘지 이번 Phase에서 결정만 한다 (추론).

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `lib/apiClient.ts:151, 236, 278` | `doFetch`/refresh에 timeout/AbortSignal 옵션, retryable error 정책 노출 | RF-FINDING-028 |
| `lib/auth.ts:66-67`, `lib/apiClient.ts:38`, `lib/supabaseClient.ts:21` (+ 신규 `authTokenStorage` adapter) | token storage helper 단일화, storage 경계 문서화 | RF-FINDING-022 |
| `components/pages/HomePage.tsx:11`, `NoticeBoardPage.tsx:58`, `NoticeDetailPage.tsx:58`, `EditNoticePage.tsx:85/122`, `EditGalleryPage.tsx:112`, `GalleryBoardPage.tsx:22`, `components/ResearchPageTemplate.tsx:72` (+ 신규 CMS service/hook/storage adapter) | Supabase 직접 호출의 service 계층 이관 (도메인별 점진) | RF-FINDING-003 |
| `api/chat.js:70/80/88/103`, `lib/api/legacyAiChat.ts:17/21` | error envelope 표준화, request schema, response parser | RF-FINDING-030 |
| `components/pages/ContactPage.tsx:13, 25` (+ 신규 `sendContactEmail` adapter) | EmailJS 호출 wrapper 분리 + error mapping | RF-FINDING-031 |

### 작업 순서

1. "확인 필요" 선행 해소: legacy AI assistant/`api/chat.js` 제품 범위 포함 여부(S1/S5 원문), Supabase RLS/권한 정책(S1/S2 원문 — RF-003 이관 범위 판단에 필요).
2. RF-028: `apiRequest` 옵션 확장(기본 동작 무변경, opt-in 형태) → refresh timeout 추가 → 호출부 단계 적용. **token refresh/error normalization은 민감 영역이므로 신중히 진행** (원본 주의사항). `lib/api/errors.ts`의 normalized error model(S5-ERRORNORM-001, :255)은 보존.
3. RF-022: `authTokenStorage` adapter 신설 → `lib/auth.ts`/`lib/apiClient.ts`가 adapter를 사용하도록 교체 → 로그인/refresh/401 retry 수동 검증. (S4 brief 원문: "token storage는 로그인/refresh/401 retry와 연결되므로 Session 5 API client 리뷰와 함께 변경 계획 수립" — 본 Phase에서 RF-028과 함께 수행)
4. RF-003 1단계: CMS service/storage adapter 골격 신설 + notice 도메인(`useNoticeBoard`/`useNoticeEditor`)부터 이관 (추론: Phase 0 ③에서 손댄 EditNoticePage 흐름과 연결되는 도메인 우선). 게시판 앱 최소 수정 원칙 적용.
5. RF-003 2단계: gallery 도메인 이관 여부를 진행 상황에 따라 결정 (잔여는 Phase 3~6 연관 작업으로 이월, 9장 표기).
6. RF-030: legacy 유지 확정 시 — error envelope 표준화 + zod schema(의존성 존재, 미적용 상태가 원본 근거) + 최소 response parser. 제거 확정 시 — 격리/제거 계획 별도 수립 (확인 필요).
7. RF-031: `sendContactEmail` adapter 분리, ContactPage는 호출 교체만.
8. RF-004(Phase 8 주관) 대비: 이관된 service 경계를 boundary 검사로 확대할 수 있도록 경계 규칙 메모 (연관).

### 완료 조건

- `apiRequest`에 timeout/signal opt-in 옵션과 refresh timeout이 도입되고 기존 호출 동작이 회귀 없이 유지됨.
- token storage 접근 경로가 `authTokenStorage` 단일 adapter로 수렴.
- notice 도메인(최소 1개 도메인)의 Supabase 직접 호출이 service/hook 경유로 이관됨.
- legacy `/api/chat` 유지 여부가 확정 기록되고, 유지 시 schema/error mapping 적용됨.
- 검증 명령어 전체 통과 + 로그인/refresh 수동 플로우 정상.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (adapter/service 단위 테스트 신규 추가), `npm run test:coverage`
- boundary: `npm run test:boundaries` (PFM 경계 회귀 확인)
- 주요 사용자 플로우 수동 검증 (Playwright MCP):
  - PFM 로그인 → token refresh → 401 retry 경로 (`https://pfm.cmsl-kookmin.com/simulation2`)
  - 장시간 요청 시 timeout 동작, refresh hang 시 보호 요청이 무한 대기하지 않는지
  - notice 목록/상세/수정 플로우 (local `http://localhost:3000/board/news`) — 게시판 앱 회귀 확인
  - Contact form 제출 성공/실패 피드백

### 위험 요소

- **`lib/apiClient.ts` token refresh/error normalization은 민감 영역** (S1 brief — :265/:278/:304): 모든 PFM API 호출의 공통 기반. timeout/retry 도입이 refresh 흐름을 바꾸지 않도록 opt-in 설계 (추론).
- **`lib/api/http.ts` WebSocket/binary/keepalive helper는 민감 영역** (S1 brief — :56/:90/:103): 이번 Phase에서 직접 수정하지 않음. RF-057(Phase 7)에서 타입만 좁힘.
- **Supabase delete/upload/update 흐름은 민감 영역** (S1/S3 brief): RF-003 이관 시 storage delete/upload 순서를 바꾸지 말 것. 운영 데이터 손실 위험.
- RLS/권한 정책 미확인 상태에서 service 이관 시 권한 동작이 달라질 수 있음 (확인 필요 선행).
- 게시판 앱 수정 지양 규칙 — RF-003/030/031은 게시판·외부 연동 영역 포함, 최소 수정 + 근거 명시.

### 롤백 기준

- 로그인/refresh/401 retry 수동 플로우 중 하나라도 회귀하면 RF-028/022 관련 commit 즉시 revert.
- notice CRUD 플로우(작성/수정/삭제/첨부)에서 데이터 정합 문제 발견 시 RF-003 이관 commit revert.
- `npm run test:boundaries` 실패(PFM 경계 위반 재발) 시 해당 commit revert.
- legacy chat 동작이 회귀하면 RF-030 commit revert 후 유지/제거 결정 재검토.

---

## Phase 3. async flow / polling / error handling 안정화

### 목적

- Phase 0의 P0 guard 위에서, 남은 polling/stale response/error 피드백 문제를 구조 이동 없이 **국소 안정화**한다. Phase 4의 hook 추출 전에 async 흐름이 안전해야 이동 시 회귀를 줄일 수 있다 (S4 brief 원문: "guard 테스트 없이 대규모 이동 금지" — 추론: 그 전제 조건을 이 Phase에서 충족).

### 관련 코드리뷰 근거

- **RF-FINDING-020** (S4: S4-ASYNCSTATE-002, S4-ASYNCSTATE-003) — CMS list `Promise.all`/fetch에 page/search 변경 중 stale response guard 없음, `NoticeBoardPage.tsx:75`, `GalleryBoardPage.tsx:42`
- **RF-FINDING-021** (S3: S3-QUALITY-002(=S3-QUAL-006) / S4: S4-EFFECT-001(요약)(=S4-EFFECT-004(상세)), S4-ASYNCSTATE-001, S4-STATE-002(상세) / S5: S5-LOADING-001) — HomePage CMS fetch에 try/catch/finally/error state 없음, `HomePage.tsx:18, 36, 66` (S5-FLOW-014 참고)
- **RF-FINDING-034** (S5: S5-POLLING-002) — legacy simulation job polling in-flight guard 없음, `PFMSimulationPage.tsx:468`
- **RF-FINDING-035** (S5: S5-ERROR-003) — job polling `getJob` 실패를 삼키고 null 반환, `Simulation2Page.tsx:1469`
- **RF-FINDING-037** (S5: S5-ERROR-004) — pin/delete mutation 실패 처리/사용자 피드백 제한적, `NoticeBoardPage.tsx:98`
- 연관(주관 Phase 0): RF-FINDING-032 후속 회귀 검증, RF-FINDING-036 보상 정책 정식화

### 작업 범위

- RF-021: `useHomeContent` hook에서 try/catch/finally + typed error state + typed view model 도입 (typed view model의 완전한 타입화는 Phase 5 RF-044 연관).
- RF-020: notice/gallery 공통 list query hook으로 통일하고 request sequence 적용 — RF-003 service 분리(Phase 2)와 함께 진행 (consolidated-findings 원문 (추론) 표기 유지).
- RF-034: legacy 유지 대상이면 Phase 0 ①과 동일한 guard 패턴 이식, 아니면 제거/격리 (legacy 유지 여부 확인 필요 — 원본 원문).
- RF-035: 연속 실패 카운트와 inline notice 정책 도입. error presenter 기준 패턴: S3-BOUNDARY-001 (`components/common/ApiErrorNotice.tsx:19`).
- RF-037: mutation hook + toast/error state (RF-003 service 분리와 함께 — 원본 원문).
- Phase 0 핫픽스(032/036)의 후속 회귀 검증과, Phase 4 hook 이동 시 guard가 보존되도록 테스트 보강.

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `components/pages/HomePage.tsx:18, 36, 66` (+ 신규 `useHomeContent`) | fetch try/catch/finally + typed error state, loading 고착 제거 | RF-FINDING-021 |
| `components/pages/NoticeBoardPage.tsx:75`, `components/pages/GalleryBoardPage.tsx:42` | 공통 list query hook + request sequence guard | RF-FINDING-020 |
| `components/pages/PFMSimulationPage.tsx:468` | legacy polling guard 이식 또는 제거/격리 (확인 필요 선행) | RF-FINDING-034 |
| `components/pages/Simulation2Page.tsx:1469` | `getJob` 실패 연속 카운트 + inline notice | RF-FINDING-035 |
| `components/pages/NoticeBoardPage.tsx:98` | pin/delete mutation 실패 피드백 (toast/error state) | RF-FINDING-037 |
| `components/pages/Simulation2Page.tsx:1605` 일대, `EditNoticePage.tsx:107` 일대 | Phase 0 ①③ 후속 회귀 테스트 보강 (연관) | RF-FINDING-032, RF-FINDING-036 (주관 Phase 0) |

### 작업 순서

1. legacy 화면(`PFMSimulationPage`) 유지 여부 확인 (확인 필요 — S1/S5 원문). 결과에 따라 RF-034 방식 확정.
2. RF-021: `useHomeContent` 도입 — error/loading 경로 단위 테스트 작성 후 hook 적용 (게시판 앱 첫 화면이므로 최소 수정).
3. RF-020: Phase 2에서 이관한 notice service 위에 list query hook + request sequence 적용 → gallery 동일 패턴 적용.
4. RF-037: notice pin/delete mutation hook + 실패 toast. RF-020/037은 같은 파일이므로 한 흐름으로 묶되 commit은 분리 (추론).
5. RF-035: `getJob` 실패 카운트/notice — Phase 0 ① guard 코드와 같은 polling 루프이므로 guard 동작 회귀 테스트와 함께 수행.
6. RF-034: 확정된 방식(guard 이식 또는 격리) 적용.
7. polling/stale guard 동작에 대한 단위/통합 테스트를 보강해 Phase 4 이동의 안전망 마련 (S3 brief 원문: "테스트가 있는 경계부터 작게 이동"의 전제).

### 완료 조건

- HomePage loading 고착 경로 제거(실패 시 error UI 표시)가 테스트로 보장됨.
- notice/gallery list fetch에 stale response guard 적용, page/search 빠른 전환 시 count/목록 불일치 재현 불가.
- job polling 실패가 사용자에게 가시화되고(연속 실패 notice), Phase 0 ① guard가 테스트로 고정됨.
- legacy polling(RF-034) 처리 방식이 확정·적용됨.
- 검증 명령어 전체 통과.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (hook error/loading/stale guard 테스트), `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP):
  - home 첫 화면 로딩/에러 fallback (local `http://localhost:3000`) — `browser_console_messages`로 에러 확인
  - 게시판 목록에서 검색어/페이지 빠른 전환 시 목록·count 정합
  - `https://pfm.cmsl-kookmin.com/simulation2` job 실행 중 네트워크 차단/복구 시 실패 notice 표시, polling 요청 겹침 없음 (`browser_network_requests`)

### 위험 요소

- **Simulation2Page polling/WS는 민감 영역** (S1/S3/S4 brief): RF-035 수정이 Phase 0 ① guard나 WS fallback 전환 로직과 충돌하지 않도록 같은 루프 내 변경은 한 commit에서 함께 검증.
- RF-020/021/037은 게시판 앱 영역 — 수정 지양 규칙에 따라 최소 수정 + 근거 명시.
- RF-034는 legacy 유지 여부 미확정(확인 필요) — 확인 전 기능 제거 금지.
- 이 Phase는 의도적으로 구조 이동을 하지 않는다 — hook 추출까지 당기면 Phase 4와 중복 작업 발생 (추론).

### 롤백 기준

- home 화면이 어떤 경로로든 빈 화면/무한 로딩이 되면 RF-021 commit revert.
- 게시판 목록 동작(검색/페이지네이션/고정글) 회귀 시 RF-020/037 commit revert.
- job polling 흐름에서 상태 갱신 누락이 재현되면 RF-035 commit revert (Phase 0 ① guard는 유지).

---

## Phase 4. hook / state management 구조 개선

### 목적

- local state + refreshKey로 관리되던 server state를 stale guard를 갖춘 도메인 hook(또는 React Query)으로 추출하고, AdminPage3의 query/mutation/cache 정책과 Simulation2Page의 WebSocket lifecycle을 hook으로 격리한다. Phase 5~6 컨테이너 분해의 직접적인 전제 작업이다 (추론).

### 관련 코드리뷰 근거

- **RF-FINDING-016** (S3: S3-STATE-001(=S3-RESP-001) / S4: S4-SERVER-004, S4-STALE-001(=S4-EFFECT-002), S4-STALE-002(=S4-EFFECT-003) / S5: S5-STALE-002) — ResultExplorerPanel catalog/files stale guard 없음, `ResultExplorerPanel.tsx:286, 392, 411` (S5-FLOW-008/009 참고)
- **RF-FINDING-017** (S3: S3-STATE-003(=S3-RESP-003) / S4: S4-SERVER-001, S4-RACE-002, S4-CLEANUP-001 / S5: S5-STALE-001) — JobResultListCard local state + refreshKey, stale guard·cleanup 없음, `JobResultListCard.tsx:93, 98, 121` (S5-FLOW-010 참고)
- **RF-FINDING-018** (S3: S3-RESP-004, S3-PERF-004(연관) / S4: S4-SERVER-002, S4-RACE-003, S4-CLEANUP-002) — SimulationListCard list state·`FETCH_SIZE` 일괄 조회, `SimulationListCard.tsx:50, 56, 61, 65, 77`
- **RF-FINDING-019** (S3: S3-STATE-002(=S3-RESP-002) / S4: S4-SERVER-003, S4-RACE-004, S4-CLEANUP-003) — SessionListCard list/search/delete/rename/dialog 집중, `SessionListCard.tsx:63, 73, 90, 122, 164, 212` (S5-FLOW-011 참고)
- **RF-FINDING-023** (S4: S4-CONTEXT-001) — LanguageProvider context+persistence 동거, `LanguageProvider.tsx:230` (P3 — 후순위)
- **RF-FINDING-024** (S4: S4-HOOK-001(=S4-DEPENDENCY-002(상세)), S4-GLOBAL-001(연관)) — use-toast `[state]` dependency 재구독, `hooks/use-toast.ts:131, 176`
- **RF-FINDING-025** (S4: S4-HOOK-002) — use-mobile 초기 `undefined`→`false`, `hooks/use-mobile.ts:20`
- **RF-FINDING-026** (S4: S4-DEPENDENCY-001) — ResearchHighlightsSlider empty guard 부재, `ResearchHighlightsSlider.tsx:32`
- **RF-FINDING-027** (S2: S2-LAYOUT-002 / S4: S4-QUERY-001 / S5: S5-QUERY-004) — QueryClient 전역 기본값만 존재, 도메인별 정책 불명, `app/providers.tsx:15-16` (S4-QUERY-005: SWR 미사용 확인)
- **RF-FINDING-029** (S4/S5: S4-CACHE-001(요약)(=S4-CACHE-002(상세)=S5-CACHE-001(상세)), S4-MUTATION-001(요약)(=S4-CACHE-001(상세)=S5-MUTATION-001), S4-MUTATION-001(상세)(=S5-MUTATION-002), S4-INVALIDATE-001(=S5-INVALIDATE-001), S4-QUERY-003(=S5-QUERY-002=S5-REFETCH-001), S4-QUERY-004(=S5-QUERY-003), S5-CACHE-001(요약), S5-REFETCH-002(=S5-DUPREQ-001), S5-LOADING-002) — AdminPage3 mutation/cache/polling 정책 산재, `AdminPage3.tsx:510/744/729, 666/676, 697, 1080, 597, 617, 1187, 648` (S5-FLOW-013 참고)
- **RF-FINDING-033** (S2: S2-ASYNC-001, S2-ASYNC-002) — job monitor WS(polling fallback/reconnect)·visualization WS/sync interval이 container 내부, `Simulation2Page.tsx:1674, 1949` — RF-FINDING-001 분해의 핵심 축 (추론, consolidated-findings 원문 표기)

### 작업 범위

- 소형 hook 정리: RF-024(mount-only subscription 검토 — shadcn 패턴/기존 toast 테스트 확인), RF-025(tri-state/mounted guard), RF-026(empty length guard), RF-023(`useMemo` 확인/persistence hook 분리 검토 — P3 후순위, 검토 결과만 기록해도 됨).
- RF-027: 도메인별(admin/simulation/CMS) query 정책 문서화, 민감 server state는 query별 정책 명시. retry/gcTime이 제품 요구와 맞는지 확인 필요 (원문).
- RF-016~019: list/catalog server state를 `useResultDetail`/`useResultFieldCatalog`/`useResultFieldFiles`/`useResultDownload`, `useSimulationJobResults`, `useSimulationList`, `useChatSessions`로 추출 + request token/AbortController/sequence guard. 기준 패턴: S4-ABORT-001(visualization sync guard), S5-CANCEL-002(`TrameExportCenter.tsx:147-149` AbortController cleanup).
- RF-029: `buildAdminQueryKeys` root helper → mutation hook(`useSyncAdminJobMutation`/`useCancelAdminJobMutation` 등)으로 cache side effect 캡슐화 → `fieldFilesData` local 복사를 enabled query로 전환 → tab별 refresh hook. **query key helper 먼저 안정화** (S4 brief 원문).
- RF-033: `useJobMonitorSession`/`useVisualizationSession`으로 WS lifecycle 격리. Phase 0 ① guard와 Phase 3 RF-035 처리를 hook 내부로 보존 이동.

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `hooks/use-toast.ts:131, 176` | listener effect dependency `[]` 고정 검토 | RF-FINDING-024 |
| `hooks/use-mobile.ts:20` | tri-state 반환 또는 mounted guard | RF-FINDING-025 |
| `components/ResearchHighlightsSlider.tsx:32` | effect 초기 `highlights.length === 0` guard | RF-FINDING-026 |
| `components/LanguageProvider.tsx:230` | value `useMemo` 여부 확인, persistence hook 분리 검토 (P3) | RF-FINDING-023 |
| `app/providers.tsx:15-16` | 도메인별 query 정책 문서화/명시 | RF-FINDING-027 |
| `components/simulation/ResultExplorerPanel.tsx:286, 392, 411` | detail/catalog/files/download hook 분리 + `resultId+field+filters` request token 또는 AbortController | RF-FINDING-016 |
| `components/simulation/JobResultListCard.tsx:93, 98, 121` | `useSimulationJobResults` (React Query 또는 sequence guard). `sync:false` 정책 유지 | RF-FINDING-017 |
| `components/simulation/SimulationListCard.tsx:50, 56, 61, 65, 77` | `useSimulationList` + request token. server pagination 전환 여부 확인 | RF-FINDING-018 |
| `components/simulation/SessionListCard.tsx:63, 73, 90, 122, 164, 212` | `useChatSessions` + action별 mutation state 분리 (view 분리는 Phase 5 연관) | RF-FINDING-019 |
| `components/pages/AdminPage3.tsx:510/744/729, 666/676, 697, 1080, 597, 617, 1187, 648` (+ 신규 admin query/mutation hook 모듈) | `buildAdminQueryKeys` + mutation hook + enabled query + tab별 refresh hook | RF-FINDING-029 |
| `components/pages/Simulation2Page.tsx:1674, 1949` (+ 신규 `useJobMonitorSession`/`useVisualizationSession`) | WS lifecycle hook 격리 | RF-FINDING-033 |

### 작업 순서

1. 소형·저위험부터: RF-026 → RF-025 → RF-024(기존 toast 테스트 확인 선행) → RF-023(검토/기록). 각각 독립 commit.
2. RF-027: query 정책 문서화 — 코드 변경 최소(주석/정책 명시), product freshness 요구 확인 필요 항목은 기록만.
3. RF-018 → RF-017 → RF-016 순으로 list/catalog hook 추출 (추론: 의존 단순한 카드부터). 각 hook은 request sequence guard 단위 테스트 동반. refreshKey contract와 기존 테스트 영향 확인 (S3 brief 원문).
4. RF-019: `useChatSessions` 추출 + action별 mutation state 분리. parent callbacks(`onDeleted`, `onRenamed`) contract 유지 (S3 brief 원문). view component 분리는 Phase 5로 이월.
5. RF-029 1단계: `buildAdminQueryKeys` helper 도입, literal key 교체 — **helper 정리 후 이동** (S4 brief 원문).
6. RF-029 2단계: mutation hook 추출(sync/cancel 등), invalidation fan-out을 hook 내부로 캡슐화. 3단계: `fieldFilesData` enabled query 전환, Refresh fan-out 정리.
7. RF-033: 마지막 수행 (추론: 최고 위험). `useJobMonitorSession` 먼저, `useVisualizationSession` 다음. Phase 0 ① guard·reconnect timer·`beforeunload`·stale token guard 테스트 필요 (S2 brief 원문). 행동 무변경 이동 원칙.

### 완료 조건

- 4개 list/catalog component의 server state가 stale guard를 갖춘 hook으로 추출되고, 빠른 전환 시 이전 응답 반영이 재현 불가.
- AdminPage3의 query key가 `buildAdminQueryKeys` 단일 출처로 통일되고, mutation cache side effect가 hook으로 캡슐화됨.
- job monitor/visualization WS lifecycle이 hook으로 격리되고 Phase 0 ① guard가 보존됨 (테스트로 고정).
- 검증 명령어 전체 통과 + admin/simulation 수동 플로우 정상.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (신규 hook 테스트 — stale guard/sequence/cleanup), `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP, `https://pfm.cmsl-kookmin.com/simulation2` + local):
  - simulation/session/job/result 목록 빠른 전환·검색·rename/delete 시 목록 정합
  - result 상세에서 field 빠른 전환 시 이전 응답 미반영
  - job 실행 전체 플로우: WS 연결 → 이벤트 수신 → polling fallback 전환 → terminal status → result 표시
  - visualization 세션 시작/종료 반복 시 연결 누수 없음 (`browser_network_requests`)
  - admin 화면(job sync/cancel, 목록 refresh) 동작 — invalidation 누락 없음

### 위험 요소

- **Simulation2Page WebSocket lifecycle은 민감 영역** (S1/S3/S4 brief): "guard 테스트 없이 대규모 이동 금지", "한 번에 전체 분리하지 말고 lifecycle 단위로 분할" (원문). RF-033은 이 Phase에서 가장 마지막, 가장 작게.
- **AdminPage3 React Query invalidation은 민감 영역** (S3/S4 brief): key rename 시 invalidation 누락 위험 — helper 단일화 선행, 기존 refetch interval(`adminPolling`) 정책 유지.
- RF-017: **Lab sync 비용 때문에 `sync:false` 정책 유지** (S4 brief 원문).
- RF-016: field selection callback과 visualization field preference 유지 (S4 brief 원문).
- RF-018: server pagination 전환 여부는 확인 필요 (원문) — 이번 Phase에서는 클라이언트 동작 유지가 기본.
- React Query 전환 vs custom hook 선택은 도메인별 결정 필요 (S4 원문 "확인 필요" — Supabase CMS 영역 통합 여부 포함).

### 롤백 기준

- job 실행 플로우(WS→polling fallback→terminal)에서 상태 누락/중복이 재현되면 RF-033 commit 즉시 revert.
- admin 화면에서 mutation 후 목록 미갱신(invalidation 누락) 발견 시 RF-029 해당 단계 commit revert.
- 목록 카드에서 데이터 섞임/빈 목록 회귀 시 해당 hook commit revert (카드별 독립 commit 전제).
- 테스트/빌드 악화 시 원인 commit revert.

---

## Phase 5. component 책임 분리 및 props/state flow 개선

### 목적

- Simulation2Page 거대 컨테이너를 presenter 단위로 분해(Phase 4에서 추출한 hook 활용)하고, component 계층의 props contract·접근성·렌더링 key·CMS 타입 문제를 정리한다.

### 관련 코드리뷰 근거

- **RF-FINDING-001** (S1: S1-ARCH-001, S1-STRUCT-001(연관) / S2: S2-CONTAINER-001 / S3: S3-COMP-001(=S3-QUAL-001) / S4: S4-STATE-001 / S5: S5-SERVICE-001(요약)(=S5-APIARCH-001) / S6: S6-IMPORT-001) — 3347 line 거대 컨테이너, `Simulation2Page.tsx:589, 592, 2255, 4-102`
- **RF-FINDING-010** (S3: S3-PROPS-001) — WorkspaceTabsCard 15개 이상 props pass-through, `WorkspaceTabsCard.tsx:14`
- **RF-FINDING-011** (S3: S3-ACCESS-001(=S3-QUAL-004)) — MemberDetailModal 접근성 미확인, `MemberDetailModal.tsx:18`
- **RF-FINDING-012** (S3: S3-QUAL-010, S3-RENDER-004(상세)) — NewsPage list action props/pagination handler 재생성, `NewsPage.tsx:29, 65`
- **RF-FINDING-013** (S3: S3-QUAL-008) — ResearchPageTemplate `dangerouslySetInnerHTML` sanitize 미확인, `ResearchPageTemplate.tsx:65`
- **RF-FINDING-014** (S3: S3-RENDER-001(요약)(=S3-PERF-001(상세)), S3-PERF-002(상세), S3-RENDER-002(요약)(=S3-RENDER-001(상세)), S3-RENDER-002(상세)) — index key 사용, `Simulation2Page.tsx:3355/3360/3368/3419`, `ImageCarousel.tsx:32-33`, `ResearchPageTemplate.tsx:142`
- **RF-FINDING-015** (S3: S3-RENDER-003(상세)) — slider variants 매 render 재생성, `ResearchHighlightsSlider.tsx:80` (P3 — 후순위)
- **RF-FINDING-043** (S6: S6-ASSERT-003(=S6-PARSER-002)) — `extractWarnings` `(obj.details as any)?.warnings` 구조 우회, `Simulation2Page.tsx:414-422, 417`
- **RF-FINDING-044** (S3: S3-CMS-003(=S3-QUAL-003) / S5: S5-TYPE-002 / S6: S6-CMS-001(=S6-TYPE-002+S6-TYPE-003), S6-PROPS-001) — CMS content state/접근이 `any`·`Record<string, any>`·field string 기반, `HomePage.tsx:18/21/22`, `EditPageContentForm.tsx:21/24/62-64`, `ResearchPageTemplate.tsx:53`, `EditHomePageForm.tsx:181`, `introduction/Section2_CoreCapabilites.tsx:8`, `Section3_ResearchAreas.tsx:10`
- **RF-FINDING-045** (S6: S6-MAPPER-001(상세 util-responsibility-review)) — WS payload를 `Record<string, unknown>`로 처리, `workflowMappers.ts:38, 53, 63`
- **RF-FINDING-047** (S6: S6-UTIL-001, S6-PARSER-001, S6-CONST-001) — pure helper/constant가 page 파일에 집중, `Simulation2Page.tsx:221-534 (221, 313-325, 414, 438, 499, 534), 545-547` — (추론) 로드맵 잠정안은 Phase 7이었으나, RF-001 분해의 선행 정지작업이므로 본 Phase 1단계로 확정 (9장 참조)

### 작업 범위

- RF-047: `normalizeComposition`/`formatAssistantContent`/`extractWarnings`/`computeExpectedProcessCount`/`toWorkflowErrorDetails`/`saveBlobDownload`와 polling/reconnect constant를 `simulation2` 하위 `workflowMapper`/`parameterMapper`/`errorMapper`/`downloadUtil`/workflow config constant로 분리. **행동 변경 없이 pure function부터 이동 후 테스트 추가** (원문).
- RF-043: `isRecord(details)`/`isWarningPayload` guard 기반 narrowing — RF-047 이동과 함께 수행 (추론: 같은 helper).
- RF-045: `JobMonitorMessageDto` union과 parser result 정의 — RF-039(Phase 1) shared 타입과 연계 (로드맵 원문 (추론) 표기 유지).
- RF-001: `ChatPanel`/`ParameterPanel`/`ResultWorkspace` presenter 분리. hook 축(`useSimulationWorkflow`/`useJobMonitorSession`/`useVisualizationSession`/`useSimulationDraft`)은 Phase 4(RF-033)에서 선행. **"한 번에 전체 분리 금지. 테스트가 있는 경계부터 작게 이동"** (S3 brief 원문).
- RF-010: workspace domain hook/presenter 경계 재정의. context 도입 전 상위 컨테이너 책임 분리 선행 (원문) — RF-001 진행 후 수행.
- RF-011/012/014/015: 접근성, list presenter, stable key, variants 정리.
- RF-013: sanitize 정책 확인(저장 시점 sanitize 여부, trusted content 정책 — 원문 "확인 필요") 후 sanitizer/service 경계로 이동.
- RF-044: CMS DTO/view model/form model(pageKey별 또는 discriminated union) + localized getter 타입화. CMS 데이터 shape 확인 필요 (원문). Phase 3 RF-021의 typed view model과 연결.

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `components/pages/Simulation2Page.tsx:221-534, 545-547` (+ 신규 simulation2 mapper/util/config 모듈) | pure helper/constant 분리 (행동 무변경) + 테스트 | RF-FINDING-047 |
| `components/pages/Simulation2Page.tsx:414-422, 417` | guard 기반 narrowing으로 교체 | RF-FINDING-043 |
| `components/pages/simulation2/workflowMappers.ts:38, 53, 63` | `JobMonitorMessageDto` union + parser result | RF-FINDING-045 |
| `components/pages/Simulation2Page.tsx` (589, 592, 2255, 4-102) | `ChatPanel`/`ParameterPanel`/`ResultWorkspace` presenter 단계 분리 | RF-FINDING-001 |
| `components/simulation/WorkspaceTabsCard.tsx:14` | props pass-through 정리 (RF-001 후) | RF-FINDING-010 |
| `components/pages/Simulation2Page.tsx:3355/3360/3368/3419`, `components/ImageCarousel.tsx:32-33`, `components/ResearchPageTemplate.tsx:142` | stable id 기반 key (message id, event timestamp/type, item.url, CMS section id) | RF-FINDING-014 |
| `components/MemberDetailModal.tsx:18` | Radix Dialog 전환 또는 `role="dialog"`/`aria-modal`/focus trap 보강 | RF-FINDING-011 |
| `components/pages/NewsPage.tsx:29, 65` | row action model/action slot + 공통 pagination presenter | RF-FINDING-012 |
| `components/ResearchHighlightsSlider.tsx:80` | variants static 이동 또는 `useMemo` (P3 후순위) | RF-FINDING-015 |
| `components/pages/ResearchPageTemplate.tsx:65` | sanitize 정책 확인 후 sanitizer/service 경계 이동 | RF-FINDING-013 |
| `components/pages/HomePage.tsx:18/21/22`, `EditPageContentForm.tsx:21/24/62-64`, `ResearchPageTemplate.tsx:53`, `EditHomePageForm.tsx:181`, `introduction/Section2_CoreCapabilites.tsx:8`, `Section3_ResearchAreas.tsx:10` | CMS DTO/view model/form model 타입화 | RF-FINDING-044 |

### 작업 순서

1. RF-047: pure helper를 행동 무변경으로 모듈 분리 + 단위 테스트 추가 (S6 brief "안전하게 먼저 개선 가능한 영역" 원문). RF-043 guard 교체를 같은 흐름에서 수행.
2. RF-045: WS payload DTO/parser 정의 — Phase 4의 `useJobMonitorSession`이 이 parser를 소비하도록 연결 (추론).
3. RF-014 중 Simulation2Page 외 부분(ImageCarousel, ResearchPageTemplate) 선행 적용 — 소형 독립 commit.
4. RF-001: presenter 분리를 **테스트가 있는 경계부터 작게** — (추론 제안 순서) `ResultWorkspace`(Phase 4 hook과 경계 명확) → `ChatPanel`(RF-014 message key 정리 동반) → `ParameterPanel`(RF-041/042 타입·builder 소비). 각 분리는 독립 commit + 수동 플로우 검증.
5. RF-010: RF-001로 상위 책임이 정리된 뒤 WorkspaceTabsCard props contract 축소.
6. RF-011, RF-012, RF-015: 독립 소형 commit (기존 스타일/모바일 레이아웃 회귀 확인 — S3 brief 원문).
7. RF-013: sanitize 정책 확인(확인 필요) → trusted 확정 시 정책 문서화, 아니면 sanitizer 도입 후 service 경계 이동.
8. RF-044: CMS 데이터 shape 확인(확인 필요) → pageKey별 view model/form model 정의 → public page와 edit form에 단계 적용 (게시판 앱 최소 수정).

### 완료 조건

- Simulation2Page에서 pure helper/constant가 모듈로 분리되고 단위 테스트가 추가됨.
- presenter 3종(또는 합의된 분리 단위)이 분리되어 Simulation2Page 라인 수와 책임이 실질 감소 (전량 분해를 완료 조건으로 삼지 않음 — "한 번에 전체 분리 금지" 원문) (추론).
- index key 사용 지점이 stable key로 교체됨.
- CMS content 접근이 typed view model 경유로 전환됨(최소 HomePage + edit form 1종).
- sanitize 정책이 확인·기록되고 필요한 경우 sanitizer 적용.
- 검증 명령어 전체 통과.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (분리된 mapper/presenter 테스트), `npm run test:coverage`
- boundary: `npm run test:boundaries` (Simulation2Page 분해 후 PFM 경계 유지 확인)
- 주요 사용자 플로우 수동 검증 (Playwright MCP):
  - `https://pfm.cmsl-kookmin.com/simulation2` 전체 워크플로우: chat → parameter 편집 → job submit → monitor → result/visualization (presenter 분리 단계마다)
  - chat 메시지 다수 추가/job event 누적 시 렌더링 안정 (key 교체 검증)
  - home/research/news/gallery 페이지 렌더링 및 edit form 동작 (local `http://localhost:3000`)
  - MemberDetailModal 키보드 조작(Escape/focus trap)

### 위험 요소

- **Simulation2Page는 코드베이스 최대 회귀 위험 지점** (RF-001 원문: "변경 영향도와 회귀 위험이 코드베이스에서 가장 큼"). presenter 분리 중 stale closure/race 방어 코드가 내부 누적되어 있음 — 이동 시 보존 확인.
- **WebSocket lifecycle 민감 영역**: presenter 분리가 Phase 4 hook 경계를 침범하지 않도록 props/contract만 이동.
- RF-013: HTML content가 모두 관리자 trusted input인지, sanitizer가 저장 시점에 적용되는지 확인 필요 (S3 원문) — 확인 전 sanitize 동작 추가로 기존 콘텐츠 렌더링이 깨질 수 있음 (추론).
- RF-044: CMS content는 pageKey별 자유 schema 의도 가능성 (S6 원문 "확인 필요") — 완전 공통화 전에 데이터 구조 확인.
- RF-044/013/012는 게시판 앱 영역 포함 — 수정 지양 규칙, 최소 수정.

### 롤백 기준

- simulation 전체 워크플로우(chat→job→result→viz) 중 어느 단계든 회귀하면 마지막 presenter 분리 commit revert.
- helper 이동 후 동작 차이(다운로드 파일명, warning 표시 등) 발견 시 RF-047/043 commit revert.
- CMS 페이지 렌더링이 기존 콘텐츠에서 깨지면 RF-044/013 commit revert.

---

## Phase 6. route / page / container 계층 정리

### 목적

- route 인증/redirect guard를 표준화하고, AdminPage3 거대 컨테이너를 tab별 container로 분해하며, board route의 id parsing/세션 소유권/error fallback 정책을 정리한다.

### 관련 코드리뷰 근거

- **RF-FINDING-002** (S1: S1-ARCH-004 / S2: S2-CONTAINER-002 / S3: S3-COMP-002(=S3-QUAL-002), S3-PERF-003, S3-QUAL-009 / S4: S4-STATE-002(요약), S4-QUERY-002(=S5-QUERY-001), S4-CLIENT-001 / S5: S5-SERVICE-002(요약)(=S5-APIARCH-002) / S6: S6-IMPORT-002) — 2942 line admin 컨테이너, `AdminPage3.tsx:483, 551, 501, 1327, 2906, 21-117`
- **RF-FINDING-005** (S2: S2-GUARD-001(=S2-PAGE-001(상세)), S2-GUARD-002, S2-GUARD-003(=S2-PAGE-004(상세)), S2-GUARD-004(=S2-PAGE-006(상세)), S2-PAGE-002(상세), S2-PAGE-005(상세), S2-PAGE-007(상세), S2-PAGE-001(요약)(=S2-PAGE-003(상세)), S2-ROUTE-001) — route별 인증/redirect guard 분산, `app/simulation2/page.tsx:16/18/20/43`, `app/pfm_chat/login/page.tsx:8/10`, `app/cmsl2004/page.tsx:13/14`, `app/cmsl20042/page.tsx:13`, `app/board/news/[id]/edit/page.tsx:7`, `app/board/gallery/[id]/edit/page.tsx:7`
- **RF-FINDING-006** (S2: S2-PAGE-002(요약)(=S2-ROUTE-002), S2-ROUTE-003 / S3: S3-QUALITY-001(=S3-QUAL-005)) — dynamic route id validation/not-found 불명확, `app/board/news/[id]/page.tsx:9`, `app/board/gallery/[id]/page.tsx:9`, `EditNoticePage.tsx:46`
- **RF-FINDING-007** (S2: S2-BOUNDARY-001, S2-BOUNDARY-002, S2-CONTAINER-008) — global `error.tsx`/route별 `loading.tsx` 미확인, `app`, `app/cmsl20043/page.tsx:14`, `AdminPage3.tsx:1139`
- **RF-FINDING-008** (S2: S2-LAYOUT-001) — 모든 route가 동일 Header/Footer layout 공유, `app/layout.tsx:87` (P3 — 검토만)
- **RF-FINDING-009** (S2: S2-CONTAINER-003, S2-CONTAINER-004) — session 재조회 중복, `NoticeBoardPage.tsx:23`, `GalleryDetailPage.tsx:35`
- **RF-FINDING-048** (S6: S6-FORMATTER-001(=S6-FORMAT-001)) — AdminPage3 formatter/file util 집중, `AdminPage3.tsx:262-339` — (추론) 로드맵 잠정안은 Phase 7이었으나 RF-002 tab 분해 시 중복 복사 방지를 위해 분해 직전 수행으로 확정 (원문: "admin tab 분리 시 중복 복사 또는 대형 파일 유지 발생")
- 연관(주관 Phase 0): **RF-FINDING-061** 후속 — `useAdminUrlState` correction hook 전체 도입 (`AdminPage3.tsx:489, 918`)

### 작업 범위

- RF-005: `usePfmAuthGate`/`ProtectedPfmRoute`/`RedirectIfAuthenticated`, `LegacyAdminGate`/`useSupabaseSessionGate` 도입. edit route 권한 기준 명시화 — **RLS 확인 전 과도한 차단 금지** (원문). Suspense fallback presenter 단일화. 기존 `LegacyLoginPage` 분기와 subscription cleanup 보존 (S2 brief 원문).
- RF-006: board 공통 `parseBoardId` parser와 notFound/redirect 정책 표준화. invalid state를 error/not-found UI로 전환. Next `notFound()` 사용 여부는 route/server boundary 확인 (S2 brief 원문).
- RF-007: global error boundary와 보호 route fallback 전략 검토, admin guard 상태 presenter 분리. 로깅/복구 UX 정책 필요 (확인 필요 — 원문).
- RF-009: session ownership을 route/server 또는 client gate 중 하나로 통일, board 공통 session hook 검토.
- RF-048: AdminPage3 formatter/file util을 admin view util 또는 common formatter로 이동 + 테스트.
- RF-002: tab별 container(`AdminOverviewPanel`/`AccountRequestsPanel`/`UsersPanel`/`SimulationAdminPanel` 등)·dialog/table component 분리. query/mutation hook은 Phase 4(RF-029) 선행 완료분 사용. query key helper 안정화 선행 (원문 — Phase 4에서 완료).
- RF-061 후속: `useAdminUrlState` correction hook 전체 도입 (Phase 0 ②의 safe parser 위에서).
- RF-008: route group layout 필요성 검토 — 제품 UX 결정 필요(확인 필요)이므로 **검토/기록만** 수행, 구현은 결정 이후 (추론).

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `app/simulation2/page.tsx:16/18/20/43`, `app/pfm_chat/login/page.tsx:8/10` | PFM auth gate 표준화 (`usePfmAuthGate` 등), fallback 단일화 | RF-FINDING-005 |
| `app/cmsl2004/page.tsx:13/14`, `app/cmsl20042/page.tsx:13` | `LegacyAdminGate`/`useSupabaseSessionGate`로 중복 제거 | RF-FINDING-005 |
| `app/board/news/[id]/edit/page.tsx:7`, `app/board/gallery/[id]/edit/page.tsx:7` | edit route 권한 기준 명시 (RLS 확인 후) | RF-FINDING-005 |
| `app/board/news/[id]/page.tsx:9`, `app/board/gallery/[id]/page.tsx:9`, `components/pages/EditNoticePage.tsx:46` | `parseBoardId` + invalid id error/not-found UI | RF-FINDING-006 |
| `app` (error.tsx 부재), `app/cmsl20043/page.tsx:14`, `components/pages/AdminPage3.tsx:1139` | global error boundary/fallback 전략, admin guard presenter 분리 | RF-FINDING-007 |
| `components/pages/NoticeBoardPage.tsx:23`, `components/pages/GalleryDetailPage.tsx:35` | session ownership 단일화 | RF-FINDING-009 |
| `components/pages/AdminPage3.tsx:262-339` | formatter/file util 분리 (tab 분해 선행) | RF-FINDING-048 |
| `components/pages/AdminPage3.tsx:483, 551, 501, 1327, 2906, 21-117` (+ 신규 tab별 container/dialog/table) | tab별 container 분해 | RF-FINDING-002 |
| `components/pages/AdminPage3.tsx:489, 918` | `useAdminUrlState` correction hook 도입 (Phase 0 ② 후속) | RF-FINDING-061 (주관 Phase 0) |
| `app/layout.tsx:87` | route group layout 필요성 검토 (기록만, 확인 필요) | RF-FINDING-008 |

### 작업 순서

1. RF-005 중 저위험부터: PFM auth gate(파일 범위 작음 — S2 brief "먼저 개선하면 좋은 구조" 원문) → legacy admin gate 중복 제거(cmsl2004/20042 거의 동일 구조 — 원문) → board edit 권한 기준은 RLS 확인(확인 필요) 후.
2. RF-006: `parseBoardId` 정책 확정 → board detail/edit route 적용 (S2 brief 원문: "invalid id 처리 기준을 먼저 정하면 detail/edit container 리팩토링의 안전성이 올라간다").
3. RF-009: session ownership 통일 (RF-005 gate 구조 확정 후 — 추론).
4. RF-007: admin guard 상태 presenter 분리 → global error.tsx 도입 여부는 로깅/복구 UX 정책 확인(확인 필요) 후 결정.
5. RF-048: AdminPage3 formatter/util 분리 (행동 무변경 + 테스트).
6. RF-061 후속: `useAdminUrlState` correction hook 도입 — deep link/query 호환성 보존 (원문).
7. RF-002: tab별 container를 **한 tab씩** 분리 (추론: overview → account/users → simulation/job/result/viz 순), 각 tab 분리마다 admin 수동 플로우 검증. inline dialog/table component 분리 동반.
8. RF-008: route group layout 검토 결과 기록 (제품 UX 결정 대기).

### 완료 조건

- 인증/redirect guard가 재사용 가능한 gate hook/component로 표준화되고 cmsl2004/20042 중복이 제거됨.
- board dynamic route의 invalid id가 일관된 error/not-found UI로 처리됨 (EditNoticePage 무한 loading 제거 — RF-006).
- AdminPage3가 tab별 container로 분해되어 단일 파일 책임이 실질 감소 (전량 분해를 완료 조건으로 강제하지 않음 — 추론, RF-001과 동일 원칙).
- admin URL state가 `useAdminUrlState`로 정리되고 기존 deep link가 호환됨.
- 검증 명령어 전체 통과.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run` (gate/parser/url state hook 테스트), `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP):
  - 미인증 상태 `https://pfm.cmsl-kookmin.com/simulation2` 접근 → login redirect → 로그인 → 복귀
  - legacy admin(cmsl2004/20042/20043) 로그인 gate 동작 (local)
  - `/board/news/abc` 류 invalid id → not-found/error UI
  - admin deep link(`?page=...&size=...` 등 기존 query 조합) 동작 보존
  - admin tab 전환·dialog·table 동작 (tab 분리 단계마다)

### 위험 요소

- **AdminPage3 권한/early return은 민감 영역** (S1 brief — :1157/:1166): admin 접근 제어 UX와 직접 연결. guard presenter 분리 시 차단 동작 보존.
- **AdminPage3 invalidation/URL query correction은 민감 영역** (S3/S4 brief): tab 분해는 Phase 4의 key helper/mutation hook 완료를 전제로 함. URL behavior 변경이 admin list query key에 영향 (S4 원문).
- **edit route 권한: RLS 정책 확인 전 과도한 UI 차단 금지** (S2 brief 원문). middleware 부재가 의도인지 확인 필요 (S2 원문).
- redirect 방식 변경 시 login/simulation UX 회귀 확인 (S2 brief 원문).
- 게시판 앱 영역(board/cmsl*) 다수 포함 — 수정 지양 규칙, 최소 수정 + 근거 명시.
- global error.tsx 도입은 로깅/복구 UX 정책 미정(확인 필요) 상태에서 강행하지 않음.

### 롤백 기준

- 인증 redirect 루프/잘못된 차단(권한 있는 사용자 차단) 발견 시 해당 gate commit 즉시 revert.
- admin deep link 호환성 깨짐 발견 시 RF-061 후속/RF-002 commit revert.
- admin tab 기능(목록/mutation/dialog) 회귀 시 마지막 tab 분리 commit revert.
- board 상세/수정 접근이 기존보다 막히면 RF-005/006 commit revert (RLS 확인 재선행).

---

## Phase 7. util / config / constant / validation 정리

### 목적

- 앞 Phase들의 구조 작업에서 남은 util/helper 중복, config/env/constant 산재, validation/formatter 정리를 일괄 수행해 코드베이스의 단일 출처(single source) 원칙을 마무리한다.

### 관련 코드리뷰 근거

- **RF-FINDING-046** (S6: S6-ANY-004) — Three 버전 호환 `as any`, `components/reactbits/ColorBends.tsx:180` (P3 — 후순위)
- **RF-FINDING-049** (S6: S6-VALIDATOR-001(=S6-PARSER-003)) — legacy `validateParams`/`parseLLMResponse`가 page 내부, `PFMSimulationPage.tsx:158, 196-219`
- **RF-FINDING-050** (S3: S3-DUP-001(=S3-QUAL-007), S3-QUAL-011) — `sanitizeForStorage`/blob download helper 중복, `AdminPage.tsx:43`, `EditNoticePage.tsx:14`, `EditGalleryPage.tsx:13`, `ResultExplorerPanel.tsx:197` (+ `Simulation2Page`/`AdminPage3` 유사 helper)
- **RF-FINDING-052** (S6: S6-CONFIG-001) — `NEXT_PUBLIC_PFM_API_URL`/`NEXT_PUBLIC_PFM_LLM_URL` fallback과 required error 안내 불일치, `lib/apiClient.ts:217-231`
- **RF-FINDING-053** (S6: S6-CONFIG-002) — `images.remotePatterns` 모든 HTTPS host 허용, `next.config.ts:4-8`
- **RF-FINDING-054** (S6: S6-CONFIG-003) — `experimental` 주석/`CDN_IMG_PREFIX` 설명 인코딩 깨짐, `next.config.ts:14-19`
- **RF-FINDING-055** (S6: S6-ENUM-001(요약)(=S6-CONST-002(상세)), S6-MAGIC-001, S6-MAGIC-002) — colormap/page size/fetch size/admin polling interval 하드코딩, `VisualizationControlBar.tsx:29`, `trame/TrameControlPanel.tsx:33`, `trame/CompositeDialog.tsx:37`, `SessionListCard.tsx:44`, `SimulationListCard.tsx:28-30`, `components/pages/adminPolling.ts:4`
- **RF-FINDING-056** (S6: S6-FORMAT-002) — `formatRelativeTime` 한국어 고정, `lib/utils.ts:10` (P3 — 후순위, 확인 필요)
- **RF-FINDING-057** (S6: S6-UTIL-002) — `withQuery` 등 `params: object` 인자 넓음, `lib/api/http.ts:12, 90, 103`
- **RF-FINDING-058** (S6: S6-DEAD-001, S6-DEAD-002) — `noUnusedParameters`/`noUnusedLocals` 꺼짐, `allowJs`로 JS 검출 약함, `tsconfig.json:30-31, 8` (P3)
- **RF-FINDING-059** (S6: S6-EXPORT-001) — admin API 파일의 `getFilenameFromContentDisposition` 재-export, `lib/api/admin.ts:31`

### 작업 범위

- RF-050: `lib/storage/filename` 또는 공통 storage/download util로 통합 (Phase 5 RF-047의 `downloadUtil`, Phase 6 RF-048의 admin util과 합류 — 추론). 파일명 정책이 backend/storage 정책과 충돌하지 않는지 확인 (원문).
- RF-049: legacy simulation parser/validator 모듈 분리 + parse result 명시 (legacy 유지 여부 확인은 Phase 3 RF-034에서 확정된 결과 재사용 — 추론).
- RF-052: canonical env 단일화, legacy fallback 주석/문서화 (Phase 0 ④ `getRequiredPublicEnv`와 일관 구조 — 추론).
- RF-053: 실제 CMS/CDN 도메인 allowlist 제한. 전체 host 허용이 CMS 요구사항인지 확인 필요 (원문).
- RF-054: next.config 주석 UTF-8 정리.
- RF-055: 공통 옵션이면 shared constant, 도메인별이면 이름 구분. 실제로 도메인별 옵션이 다른지 확인 (원문).
- RF-056: locale 인자 또는 i18n boundary 연결 (확인 필요 — 원문).
- RF-057: `QueryParams` 타입 일관 적용 — **`lib/api/http.ts`는 민감 영역(WS/binary/keepalive)이므로 타입 시그니처만 좁히고 런타임 동작 무변경** (추론).
- RF-058: 신규 리팩토링 영역부터 unused check/lint rule 보완, JS API route(`api/chat.js`) TS 전환 또는 별도 lint/typecheck 정책 (Phase 2 RF-030 결정과 연계).
- RF-059: 호출부가 `lib/api/http`에서 직접 import하도록 정리.
- RF-046: wrapper type 또는 지원 버전 확인 후 좁은 assertion으로 제한 (P3 후순위).

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `components/pages/AdminPage.tsx:43`, `EditNoticePage.tsx:14`, `EditGalleryPage.tsx:13`, `components/simulation/ResultExplorerPanel.tsx:197` (+ Simulation2Page/AdminPage3 유사 helper, 신규 공통 util) | sanitizer/download helper 단일화 | RF-FINDING-050 |
| `components/pages/PFMSimulationPage.tsx:158, 196-219` | legacy validator/parser 모듈 분리 | RF-FINDING-049 |
| `lib/apiClient.ts:217-231` | canonical env 정리, fallback 문서화 | RF-FINDING-052 |
| `next.config.ts:4-8` | remotePatterns allowlist 제한 (확인 필요 선행) | RF-FINDING-053 |
| `next.config.ts:14-19` | 주석 인코딩 정리 | RF-FINDING-054 |
| `components/simulation/VisualizationControlBar.tsx:29`, `trame/TrameControlPanel.tsx:33`, `trame/CompositeDialog.tsx:37`, `SessionListCard.tsx:44`, `SimulationListCard.tsx:28-30`, `components/pages/adminPolling.ts:4` | colormap/page size/polling constant 정리 | RF-FINDING-055 |
| `lib/utils.ts:10` | `formatRelativeTime` locale 처리 (확인 필요) | RF-FINDING-056 |
| `lib/api/http.ts:12, 90, 103` | `QueryParams` 타입 좁히기 (타입만) | RF-FINDING-057 |
| `tsconfig.json:30-31, 8` (+ eslint 설정) | unused check/lint 정책 보완, JS route 정책 | RF-FINDING-058 |
| `lib/api/admin.ts:31` (+ 호출부) | helper 재-export 제거 | RF-FINDING-059 |
| `components/reactbits/ColorBends.tsx:180` | 좁은 assertion/wrapper type | RF-FINDING-046 |

### 작업 순서

1. 무위험 정리부터: RF-054(주석) → RF-059(재-export) → RF-057(타입만) → RF-046.
2. RF-050: 공통 util 신설 → 파일별 교체(독립 commit) → 파일명 정책 충돌 확인(확인 필요) 후 backend/storage 정책과 대조.
3. RF-055: 도메인별 옵션 차이 확인(확인 필요) → shared constant/feature config 분리.
4. RF-052: env 사용 실태 확인 → canonical 단일화. `NEXT_PUBLIC_LAB_SERVER_API_KEY`/`NEXT_PUBLIC_PFM_AUTH_TOKEN`의 runtime 사용 여부도 이때 확인 (S6 원문 "확인 필요").
5. RF-049: legacy parser/validator 분리 (RF-034 결정 결과에 따름).
6. RF-053: CMS/CDN 도메인 목록 확인(확인 필요) 후 allowlist 적용 — 이미지 렌더링 회귀 주의.
7. RF-056: 다국어 정책 확인 후 locale 인자 도입 또는 보류 기록.
8. RF-058: lint rule/unused check를 신규 리팩토링 영역부터 적용, `api/chat.js` 처리 방식은 RF-030 결정과 일치시킴.

### 완료 조건

- sanitizer/download/constant의 중복 정의가 단일 출처로 수렴.
- env/config 안내가 실제 사용과 일치하고 인코딩 깨짐이 제거됨.
- remotePatterns allowlist가 확인된 도메인 목록으로 제한됨 (또는 전체 허용이 요구사항으로 확인되어 기록됨).
- 검증 명령어 전체 통과 + 이미지/다운로드/legacy 화면 수동 확인.

### 검증 방법

- type check: `npx tsc --noEmit` (후보, 확인 필요)
- lint: `npm run lint`
- build: `npm run build` (next.config 변경 검증에 필수)
- unit test: `npm run test:run` (공통 util/parser 테스트), `npm run test:coverage`
- boundary: `npm run test:boundaries`
- 주요 사용자 플로우 수동 검증 (Playwright MCP):
  - 갤러리/뉴스 이미지 렌더링 (remotePatterns 변경 후, local + production)
  - 파일 업로드(파일명 sanitize)·결과 다운로드(blob download) 동작
  - colormap 선택/page size 동작 (`https://pfm.cmsl-kookmin.com/simulation2`)
  - legacy PFMSimulationPage 동작 (유지 시)

### 위험 요소

- RF-053: allowlist 누락 도메인이 있으면 운영 이미지가 깨짐 — 도메인 목록 확인(확인 필요) 전 적용 금지.
- RF-050: 파일명 정책 변경이 기존 업로드 파일과의 호환/backend storage 정책과 충돌 가능 (원문 확인 필요). Supabase storage 흐름은 민감 영역 (S1 brief).
- RF-057: `lib/api/http.ts`는 S1 brief 민감 영역(WS/binary/keepalive helper) — 런타임 동작 변경 금지, 타입만.
- RF-052: env 변경은 Vercel 배포 설정과 동기화 필요 — 배포 설정 확인 전 기존 fallback 제거 금지 (추론).
- RF-055 중 adminPolling interval은 운영 정책 — 값 변경 없이 위치만 이동 (원문: pure helper라 영향 낮음).

### 롤백 기준

- 운영/스테이징에서 이미지 미표시 발견 시 RF-053 commit 즉시 revert.
- 업로드 파일명/다운로드 동작 변화 발견 시 RF-050 commit revert.
- env 관련 초기화 실패 발생 시 RF-052 commit revert.
- build 실패(next.config) 시 해당 commit revert.

---

## Phase 8. 테스트, 빌드, 회귀 검증, 최종 정리

### 목적

- 전체 Phase의 누적 결과를 회귀 검증하고, boundary 검사 확대·strict 옵션 단계 강화·import 구조 도구 검증 등 **검출력 자체를 높이는 장기 안전장치**를 도입한 뒤 리팩토링 사이클을 종결한다.

### 관련 코드리뷰 근거

- **RF-FINDING-004** (S1: S1-TEST-001 / S5: S5-BOUNDARY-001, S5-APIARCH-003) — PFM boundary guard만 존재(일부 PFM page 한정), CMS/Supabase boundary guard 부재, `scripts/check-pfm-api-boundaries.mjs:6`
- **RF-FINDING-038** (S1: S1-TYPE-001 / S6: S6-TYPE-001) — `allowJs: true`, `strict: false`, `noImplicitAny: false`, `strictNullChecks: false`, `tsconfig.json:8, 10, 29, 32`
- **RF-FINDING-060** (S6: S6-BARREL-001, S6-CYCLE-001) — barrel export 과다·circular import 여부 미확정 (정적 `rg`만으로 확정하지 않음 — 원문), 전체, 라인 확인 필요

### 작업 범위

- RF-004: Phase 2에서 확정된 CMS service 경계를 기준으로 정적 검사/테스트 확대 — **service 경계를 먼저 정한 뒤 검사 추가** (S1 brief 원문, Phase 2 완료가 전제).
- RF-038: strict 계열 옵션 단계적 강화 — **단번에 전체 strict 전환 금지** (원문). Phase 0에서 측정한 `npx tsc --noEmit` 오류량과 Phase 1~7 누적 타입 개선을 바탕으로 옵션별(예: `noUnusedLocals`/`noUnusedParameters`(RF-058 연계) → `noImplicitAny` → `strictNullChecks` — 추론) 적용 시도. strict 시 오류량 측정 필요 (확인 필요 — 원문).
- RF-060: madge/dependency-cruiser 등 도구로 barrel export/circular import 검증 (확인 필요 — 원문). 발견 시 별도 이슈로 채번하지 않고 후속 백로그로 기록 (추론).
- 전체 회귀 검증: baseline(Phase 0 기록) 대비 명령어 결과/커버리지 비교, 주요 사용자 플로우 전체 재검증, 잔여 "확인 필요" 항목(consolidated-findings 5장 30건)의 해소/이월 상태 정리.

### 수정 대상 후보

| 파일/영역 | 변경 목적 | 관련 이슈 |
|---|---|---|
| `scripts/check-pfm-api-boundaries.mjs:6` (+ 신규 CMS boundary 검사) | CMS/Supabase service 경계 정적 검사 확대 | RF-FINDING-004 |
| `tsconfig.json:8, 10, 29, 32` | strict 계열 옵션 단계 강화 (오류량 측정 후) | RF-FINDING-038 |
| 전체 (도구 실행) | madge/dependency-cruiser로 circular import 검증 | RF-FINDING-060 |
| (문서) 회귀 검증 리포트, 잔여 확인 필요 목록 | 리팩토링 사이클 종결 기록 | 전체 |

### 작업 순서

1. 전체 검증 명령어 실행, Phase 0 baseline과 비교 리포트 작성.
2. RF-060: madge/dependency-cruiser 실행 → 순환 import 발견 시 백로그 기록 (즉시 수정은 영향 분석 후 — 추론).
3. RF-004: CMS boundary 검사 스크립트 추가 → `test:boundaries`에 통합 또는 별도 스크립트 (추론: 기존 스크립트 패턴 준수).
4. RF-038 1단계: `npx tsc --noEmit`에 옵션별 플래그를 임시 적용해 오류량 측정 (코드 무변경).
5. RF-038 2단계: 오류량이 수용 가능한 옵션부터 tsconfig 반영 + 오류 해소. 디렉터리/feature 단위로 오류 해소 후 옵션 강화 (S6 brief 원문). 오류량 과다 옵션은 후속 백로그로 이월.
6. 주요 사용자 플로우 전체 수동 회귀 (아래 검증 방법).
7. 잔여 "확인 필요" 30건의 최종 상태(해소/이월) 표 작성, 리팩토링 사이클 종료 선언.

### 완료 조건

- 모든 검증 명령어가 통과하고 baseline 대비 테스트 수/커버리지가 동등 이상.
- CMS boundary 검사가 CI 가능한 스크립트로 추가됨.
- strict 계열 옵션 강화의 적용/이월 내역이 측정치와 함께 기록됨.
- circular import 검증 결과가 기록됨.
- 61건 RF-FINDING의 처리 상태(완료/부분/이월)가 최종 표로 정리됨.

### 검증 방법

- type check: `npx tsc --noEmit` (옵션 강화 후에는 검출력 상승 — 확인 필요 단서 해제 여부 기록)
- lint: `npm run lint`
- build: `npm run build`
- unit test: `npm run test:run`, `npm run test:coverage` (baseline 대비 비교)
- boundary: `npm run test:boundaries` (+ 신규 CMS boundary 검사)
- 주요 사용자 플로우 수동 검증 (Playwright MCP) — 전체 회귀:
  - 시뮬레이션 앱: 로그인 → chat → simulation 생성 → parameter 수정 → job submit → monitor(WS/polling) → result → visualization → 다운로드 (`https://pfm.cmsl-kookmin.com/simulation2`)
  - admin: 로그인 gate → tab 전환 → 목록/mutation → deep link
  - 게시판 앱: home → news/gallery 목록·상세 → (관리자) 작성/수정/첨부 (local `http://localhost:3000`)
  - error/not-found fallback, invalid URL 처리

### 위험 요소

- RF-038: strict 옵션 강화는 난이도/위험도 모두 High (로드맵 추론 평가) — 오류량 측정 없이 옵션을 켜면 빌드 불능. 옵션별·디렉터리별 단계 적용 필수.
- RF-004: 검사 규칙이 과도하면 정상 코드가 차단됨 — Phase 2 경계 정의와 정확히 일치시킬 것.
- RF-060: 도구가 보고하는 순환이 실제 런타임 문제인지 별도 분석 필요 (원문: 정적 rg만으로 확정하지 않음).
- 회귀 검증에서 발견되는 결함이 어느 Phase 산출물인지 추적하려면 Phase별 commit 경계가 보존되어 있어야 함 (추론).

### 롤백 기준

- strict 옵션 반영 후 build 실패가 즉시 해소되지 않으면 tsconfig commit revert (오류 해소는 별도 브랜치에서 진행).
- 신규 boundary 검사가 기존 정상 코드를 차단하면 검사 규칙 commit revert 후 규칙 재정의.
- 전체 회귀에서 P0급 결함 발견 시 해당 원인 commit revert 및 관련 Phase 재개.

---

## 9. Phase별 배정 요약

### 9.1 주관 Phase 배정 표 (61건 전수, 누락/중복 없음)

| Phase | 건수 | 주관 배정 RF-FINDING |
|---|---:|---|
| Phase 0 | 4 | 032, 036, 051, 061 |
| Phase 1 | 4 | 039, 040, 041, 042 |
| Phase 2 | 5 | 003, 022, 028, 030, 031 |
| Phase 3 | 5 | 020, 021, 034, 035, 037 |
| Phase 4 | 11 | 016, 017, 018, 019, 023, 024, 025, 026, 027, 029, 033 |
| Phase 5 | 11 | 001, 010, 011, 012, 013, 014, 015, 043, 044, 045, 047 |
| Phase 6 | 7 | 002, 005, 006, 007, 008, 009, 048 |
| Phase 7 | 11 | 046, 049, 050, 052, 053, 054, 055, 056, 057, 058, 059 |
| Phase 8 | 3 | 004, 038, 060 |
| **합계** | **61** | RF-FINDING-001 ~ 061 각 1회 배정 |

### 9.2 여러 Phase에 걸치는 항목 (주관/연관 구분) (추론)

| RF ID | 주관 Phase | 연관 Phase | 비고 |
|---|---|---|---|
| 032 | 0 | 3 (guard 회귀 테스트 보강), 4 (RF-033 hook 이동 시 guard 보존) | P0 핫픽스 후 구조 이동 추적 |
| 036 | 0 | 2 (storage adapter), 3 (보상 정책 정식화) | 게시판 앱 최소 수정 |
| 051 | 0 | 7 (RF-052 env/config 체계와 합류) | |
| 061 | 0 | 6 (`useAdminUrlState` correction hook 전체 도입) | Phase 0은 safe parser까지 |
| 003 | 2 | 3 (RF-020/037 error·stale), 5 (RF-044), 6 (RF-009), 8 (RF-004) | 도메인별 점진 이관 |
| 040 | 1 | 2 (apiClient 변경과 함께) | 기본값 `unknown` 전환은 장기 |
| 041 | 1 | 5 (workflow 리팩토링과 함께 완성) | |
| 042 | 1 | 5 (Simulation2Page 호출 교체 완성) | |
| 022 | 2 | — (Phase 0 ④와 storage 경계 문서 공유) | |
| 028 | 2 | 3 (retryable error 정책을 query/hook에 명시) | |
| 021 | 3 | 2 (CMS service), 5 (RF-044 typed view model) | |
| 016~019 | 4 | 3 (stale guard 테스트 전제), 5 (RF-019 view 분리, RF-010) | |
| 029 | 4 | 2 (query key helper 사전 정리 가능), 6 (RF-002 tab 분해 전제) | |
| 033 | 4 | 3 (RF-035와 같은 폴링 루프), 5 (RF-001 분해의 핵심 축) | |
| 044 | 5 | 1 (DTO 원칙), 3 (RF-021) | CMS shape 확인 필요 |
| 045 | 5 | 1 (RF-039 연계), 4 (RF-033 hook이 parser 소비) | |
| 047 | 5 | 7 (RF-050 공통 util 합류) | |
| 013 | 5 | 2 (sanitizer/service 경계 결정) | |
| 048 | 6 | 7 (common formatter 합류) | |
| 004 | 8 | 2 (service 경계 확정 전제) | |
| 038 | 8 | 0 (오류량 사전 측정), 1·7 (타입 개선 누적 전제) | |
| 058 | 7 | 8 (RF-038 옵션 강화와 연계) | |

### 9.3 로드맵 4.4 잠정 매핑 대비 확정 변경 내역 (추론)

로드맵 4.4는 "(추론, 후속 Phase 문서에서 확정)"으로 표기된 잠정 매핑이며, 본 문서에서 아래와 같이 확정했다. 우선순위(P0~P3) 자체는 변경 없음.

| RF ID | 로드맵 잠정 | 본 문서 확정 | 사유 (추론) |
|---|---|---|---|
| 047 | Phase 7 | Phase 5 (1단계 선행) | RF-001 분해 전 pure helper 이동이 선행되어야 함 (원문 "행동 변경 없이 pure function부터 이동", "hook 분리 시 설정이 page와 함께 이동") |
| 048 | Phase 7 | Phase 6 (RF-002 직전) | 원문 "admin tab 분리 시 중복 복사 또는 대형 파일 유지 발생" — tab 분해 전 분리가 목적에 부합 |
| 001 | Phase 4~6 | Phase 5 주관 | hook 축은 Phase 4(033), presenter 축은 Phase 5, 잔여 컨테이너 정리는 Phase 5 내 단계로 흡수 |
| 002 | Phase 4~6 | Phase 6 주관 | query/mutation hook(029)은 Phase 4 선행, tab container 분해는 page/container 계층 작업 |
| 021 | Phase 2~3 | Phase 3 주관 | 근본 문제가 error/loading 처리(loading 고착)이므로 async/error 안정화 Phase에 배정 |
| 029 | Phase 2~3 일부 | Phase 4 주관 | React Query hook 캡슐화가 본체. key helper 선행분만 Phase 2 연관 |
| 008, 015, 023 | Phase 7~8 및 이후 (P3) | 각각 Phase 6 / 5 / 4 | 영역 정합 Phase에서 검토/후순위로 수행. P3 성격(제품 결정 대기, 후순위)은 각 Phase 본문에 명시 |
| 046, 056 | Phase 7~8 및 이후 (P3) | Phase 7 | util/config 정리 Phase와 영역 일치 |

### 9.4 Phase × 우선순위 교차 (주관 기준) (추론)

| Phase | P0 | P1 | P2 | P3 |
|---|---|---|---|---|
| 0 | 032, 036, 051, 061 | - | - | - |
| 1 | - | 039, 040, 041, 042 | - | - |
| 2 | - | 003 | 022, 028, 030, 031 | - |
| 3 | - | 021 | 020, 034, 035, 037 | - |
| 4 | - | 016, 017, 018, 019, 029, 033 | 024, 025, 026, 027 | 023 |
| 5 | - | 001, 013, 041·042(연관) | 010, 011, 012, 014, 043, 044, 045, 047 | 015 |
| 6 | - | 002, 005 | 006, 007, 009, 048 | 008 |
| 7 | - | - | 049, 050, 052, 053, 054, 055, 057, 059 | 046, 056, 058 |
| 8 | - | - | - | 004, 038, 060 |

> 합계 61건 (주관 기준: P0 4 + P1 16 + P2 32 + P3 9 = 61. 로드맵 4.1 분포와 일치, 연관 표기는 중복 집계하지 않음).

### 9.5 실행 전반 공통 원칙 (원본 주의사항 보존)

- **"한 번에 전체 분리 금지. 테스트가 있는 경계부터 작게 이동"** (S3 brief — RF-001/002 공통).
- **"한 번에 전체 이관하지 말고 도메인별 진행"** (S1 brief — RF-003).
- **"guard 테스트 없이 대규모 이동 금지"** (S4 brief — Simulation2Page WS/polling/viz sync).
- **"query key helper를 먼저 안정화 / helper 정리 후 이동"** (S4 brief — AdminPage3).
- **"RLS 정책 확인 전 과도한 UI 차단 금지"** (S2 brief — board edit route).
- **"단번에 전체 strict 전환 금지"** (S1/S6 — RF-038).
- 민감 영역(Phase 0 표): apiClient token refresh, `lib/api/http.ts` WS helper, Simulation2Page WS lifecycle, AdminPage3 invalidation/권한, Supabase delete/upload 순서, `apiRequest<T>` 기본값, admin DTO 통합, parameter mapper — 해당 영역을 다루는 Phase의 위험 요소에 반영됨.
- 각 Phase는 독립 commit 단위로 진행하고, Phase 종료마다 검증 명령어 5종 + 수동 플로우를 통과해야 다음 Phase로 진행한다 (추론).
