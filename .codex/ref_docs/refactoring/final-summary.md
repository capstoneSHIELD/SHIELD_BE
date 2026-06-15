# 리팩토링 계획 최종 요약 (Final Summary)

> **대상 독자**: 의사결정자 (리팩토링 착수 승인/우선순위 결정권자)
> **기반 문서** (모두 `C:\pfm-FE\.codex\ref_docs\refactoring\` 하위):
> - `consolidated-findings.md` — 통합 이슈 61건 (RF-FINDING-001~061, 코드리뷰 session1~6 통합)
> - `refactoring-priority-roadmap.md` — P0~P3 우선순위 배정
> - `phased-refactoring-plan.md` — Phase 0~8 실행 계획
> - `risk-and-impact-map.md` — 위험 17건 (RF-RISK-001~017)
> - `refactoring-task-backlog.md` — 실행 Task 90건 (RF-TASK-001~090, T090은 전수 리뷰 후 T030에서 분리)
>
> 본 문서의 파일 경로·라인·문제 요약은 코드리뷰 원본 근거를 그대로 옮긴 것이며, 요약 과정의 새 판단은 "(추론)", 원본에서 미확정인 사항은 "확인 필요"로 표기한다.

---

## 1. 현재 코드베이스의 핵심 구조 문제 (5개로 압축)

61건의 통합 이슈는 아래 5개 근본 문제로 수렴한다. (추론: 압축 분류는 본 요약의 판단이며, 개별 근거는 consolidated-findings 2장 참조)

### 1.1 거대 컨테이너 2개 — 코드베이스 최대 회귀 위험 지점

| 파일 | 규모 | 보유 책임 | 관련 이슈 |
|---|---|---|---|
| `components/pages/Simulation2Page.tsx` | **3,347줄** | chat / workflow / parameter 편집 / job 모니터(WebSocket+polling) / result / visualization의 상태·API orchestration·렌더링 전부 | RF-FINDING-001 (S1~S6 전 세션이 지적, High) |
| `components/pages/AdminPage3.tsx` | **2,942줄** | URL state, 권한, React Query 9개 그룹(me/health/ready/account/users/simulation/jobs/results/viz), mutation, tab/table/dialog UI 전부 | RF-FINDING-002 (S1~S6, High) |

단위 테스트 불가 수준이고, stale closure/race 방어 코드가 내부에 누적되어 있어 변경 영향도와 회귀 위험이 코드베이스에서 가장 크다 (원본 표현 그대로).

### 1.2 server state를 local state + refreshKey로 관리 — race condition 상존

simulation2의 목록/결과 카드 4종(`JobResultListCard`, `SimulationListCard`, `SessionListCard`, `ResultExplorerPanel`)과 CMS 목록 2종이 server state를 component local state로 들고, 빠른 전환·검색·refresh 시 **이전 응답이 새 UI를 덮는 stale response guard가 없다** (RF-FINDING-016~020). 같은 계열의 정점이 P0인 job polling in-flight guard 부재(RF-FINDING-032)다.

### 1.3 타입/DTO 중복 + 약한 type safety (strict off)

- `tsconfig.json`에서 `strict: false`, `noImplicitAny: false`, `strictNullChecks: false`, `allowJs: true` — 계약 위반이 컴파일에서 걸러지지 않음 (RF-FINDING-038).
- `SimulationStatus`/`JobStatus`/`VisualizationStatus` status union과 `Composition`/`JobSummary`/`ResultSummary` 계열 DTO가 일반 API·admin API·workflow에 **중복 정의**되어 backend 계약 변경 시 일부 계층만 갱신되는 drift 위험 (RF-FINDING-039).
- `apiRequest<T = any>` + `as T` 단정, `WorkflowState.parameters: Record<string, any>`, PATCH body를 page에서 `Record<string, any>`로 직접 조립 (RF-FINDING-040/041/042).

### 1.4 CMS/게시판의 Supabase UI 직접 결합

HomePage·NoticeBoardPage·EditNoticePage·GalleryBoardPage 등 7개 page/component가 Supabase query/mutation/storage upload·remove를 **UI에서 직접 수행** (RF-FINDING-003, High). RLS/권한/스토리지 정책 변경과 mutation 회귀에 취약하고 테스트가 어렵다. 게시판 앱은 프로젝트 규칙상 "수정 지양" 영역이라 작업 시 별도 합의가 필요하다.

### 1.5 즉시 수정해야 할 P0 수준 버그 후보 4건

구조 문제와 별개로, 사용자/데이터에 실질 피해가 가능한 결함 4건이 식별되었다 (2장 상세).

---

## 2. 가장 먼저 해결해야 할 문제 — P0 4건

구조 리팩토링과 **독립적으로 핫픽스 가능**하며, Phase 0 선행 트랙에서 국소 수정(guard/parser/보상 처리/helper)으로 처리한다. 각 건은 독립 commit으로 진행해 개별 revert가 가능하다.

| # | 항목 | 이슈 ID | 위치 | 이렇게 먼저 고쳐야 하는 이유 |
|---|---|---|---|---|
| ① | job polling fallback **in-flight guard** 도입 | RF-FINDING-032 | `Simulation2Page.tsx:1605` | `setInterval(async)`에 guard가 없어 tick마다 `getJob`→`listJobEvents`→`listSimulationResults`가 겹쳐 실행될 수 있음 — 중복 호출과 **job 상태 순서 꼬임**이 핵심 사용자 플로우(시뮬레이션 모니터링)에서 발생 가능. 같은 파일의 visualization sync guard(:2057/:2100)가 기준 패턴으로 이미 존재 |
| ② | AdminPage3 URL **NaN-safe parser** | RF-FINDING-061 | `AdminPage3.tsx:498` (489, 918) | `Number(searchParams...)`가 NaN이 될 수 있어 invalid query에서 page/size와 **query key가 불안정** — admin 운영 화면의 deep link가 깨질 수 있음. pure parser 도입만으로 저난이도 해소 |
| ③ | EditNoticePage **attachment rollback** | RF-FINDING-036 | `EditNoticePage.tsx:107` | storage remove/upload 후 DB update 실패 시 보상(rollback)이 없어 **파일-DB attachment 불일치 = 운영 데이터 불일치** 가능. 단, 게시판 앱(Supabase) 영역이므로 최소 수정 + 사전 합의 필요. 실제 storage path/URL parsing은 확인 필요 |
| ④ | required public **env helper** 도입 | RF-FINDING-051 | `lib/supabaseClient.ts:5-6`, `ContactPage.tsx:26-29` | Supabase·EmailJS env를 non-null assertion(`!`)으로 직접 사용 — 배포 누락 시 **불명확한 runtime failure**. `getRequiredPublicEnv`로 명확한 초기화 오류/disabled fallback 전환. env 이름/Vercel 배포 설정 확인 후 적용 |

---

## 3. 리팩토링 전체 방향 — 하위 계층부터 위로

핵심 원칙: **타입/계약 → 계층 → 비동기 → 구조** 순으로 의존 관계를 정렬한다. 하위 계층(type/DTO)을 먼저 단일화해야 상위 작업(hook/component 분해)의 계약 drift를 컴파일과 테스트가 잡아줄 수 있다 (추론, phased-plan Phase 1 목적 원문 기반).

```
Phase 0  준비/안전장치 + P0 핫픽스 ── baseline 기록, 민감 영역 확정
   ↓
Phase 1  type / DTO / API contract ── status union·DTO shared module 단일화
   ↓
Phase 2  API client / service layer ── timeout/signal, token storage 단일화, CMS service 이관 시작
   ↓
Phase 3  async / polling / error ── stale guard·error 피드백 국소 안정화 (구조 이동 없음)
   ↓
Phase 4  hook / state ── server state hook 추출, admin query/mutation 캡슐화, WS lifecycle 격리
   ↓
Phase 5  component ── Simulation2Page presenter 분해 (ResultWorkspace→ChatPanel→ParameterPanel)
   ↓
Phase 6  page / route / container ── auth gate 표준화, AdminPage3 tab 분해
   ↓
Phase 7  util / config ── 중복 helper·constant·env/config 단일 출처화
   ↓
Phase 8  검증 ── 전체 회귀, CMS boundary 검사 추가, strict 옵션 단계 강화
```

실행 전반의 공통 원칙 (세션 brief 원문 보존):
- "한 번에 전체 분리 금지. 테스트가 있는 경계부터 작게 이동" (거대 컨테이너 2개)
- "guard 테스트 없이 대규모 이동 금지" (Simulation2Page WS/polling)
- "query key helper를 먼저 안정화 후 이동" (AdminPage3)
- "한 번에 전체 이관하지 말고 도메인별 진행" (CMS/Supabase)
- "단번에 전체 strict 전환 금지" (tsconfig)
- 1 Task = 독립 commit(이상), Phase 종료마다 검증 5종(`lint`/`build`/`test:run`/`test:coverage`/`test:boundaries`) + 수동 플로우 통과

---

## 4. 예상 효과

| 영역 | 기대 효과 | 근거 이슈 |
|---|---|---|
| 회귀 위험 | 코드베이스 최대 회귀 위험 지점 2개(3,347줄/2,942줄)의 변경 영향도 축소, 단위 테스트 가능화 | RF-FINDING-001, 002 |
| 데이터 정합 | polling 중복 호출·상태 순서 꼬임 제거, 목록 빠른 전환 시 이전 응답 반영(데이터 섞임) 재현 불가, 파일-DB attachment 불일치 방지 | RF-FINDING-032, 016~020, 036 |
| 계약 안정성 | backend contract 변경 시 계층 간 DTO drift를 컴파일 타임에 검출, admin/job/result 화면 상태 불일치 방지 | RF-FINDING-039~042 |
| 운영 안정성 | admin query key 단일 출처화로 invalidation 누락·중복 요청 방지, env 배포 누락 시 명확한 실패 메시지 | RF-FINDING-029, 051, 061 |
| 유지보수성 | UI-persistence 결합 해소(CMS service 경계), 중복 helper/constant 단일 출처화, 신규 기능 추가 시 결합도 감소 | RF-FINDING-003, 047~050, 055 |
| 장기 안전망 | CMS boundary 정적 검사 추가, strict 옵션 단계 강화로 검출력 자체 상승 | RF-FINDING-004, 038 |

(추론) 효과의 정량 측정은 Phase 0 baseline(테스트/커버리지/tsc 오류량) 대비 Phase 8 비교 리포트로 확인한다.

---

## 5. 가장 큰 위험 (risk-and-impact-map 기준, High 12건 중 핵심 4축)

| 위험 축 | 내용 | 위험 ID |
|---|---|---|
| **simulation2 워크플로우 회귀** | 3,347줄 컨테이너 분해 중 chat→생성→parameter→job 제출→모니터링→결과/시각화로 이어지는 **단일 플로우의 상태 전이·ref·cleanup이 끊어질 수 있음**. 완화: lifecycle 단위 단계 분리 + 단계마다 production 수동 검증 | RF-RISK-001 |
| **polling/WS lifecycle 깨짐** | guard 도입·hook 추출 시 terminal status 판정/WS fallback 진입·복귀 조건을 잘못 건드리면 job이 끝나도 폴링이 계속되거나 조기 중단. reconnect timer·`beforeunload`·cleanup ref가 끊기면 연결 중복/누수. 완화: 중단 조건 무변경 최소 diff, cleanup 경로 문서화 후 이동 | RF-RISK-006, 013 |
| **backend contract 미확인 영역** | admin DTO가 일반 DTO와 의도적으로 다른 계약인지 **확인 필요 상태**에서 통합하면 잘못된 단일화 발생. 완화: 명세 확인 전에는 alias/mapper 연결만, 필드 삭제/통합 금지 | RF-RISK-004 |
| **두 앱(게시판/시뮬레이션) 경계 침범** | 게시판 앱(Supabase)과 시뮬레이션 앱(자체 백엔드)은 인증부터 충돌 금지가 프로젝트 불변 조건. auth storage/error model/service layer 공통화가 두 앱 정책을 한 모듈로 섞으면 경계가 흐려짐. 완화: "공유 금지 목록"(auth/session storage, API client, error envelope, service layer) Phase 0 확정 | RF-RISK-011 |

보조 위험: 테스트/검증 안전망 자체가 약한 상태(strict off, 전용 typecheck 스크립트 없음, boundary 검사 일부 한정, 커버리지 미확인)에서 대형 분해를 진행하면 회귀가 머지 후에야 발견된다 (RF-RISK-016, High) — Phase 0 baseline과 Phase 3 guard 테스트 보강이 그 대응이다.

---

## 6. 권장 진행 순서 (Phase 0~8 + P0 핫픽스 트랙)

| Phase | 한 줄 요약 | Task 수 |
|---|---|---:|
| **P0 핫픽스 트랙** | Phase 0 안에서 선행 — polling guard / URL NaN parser / attachment rollback / env helper, 각각 독립 commit으로 즉시 처리 | (4) |
| **Phase 0** | baseline 검증 기록 + 민감 영역 목록 확정 + P0 핫픽스 4건 | 6 |
| **Phase 1** | status union/DTO shared module 단일화, PATCH body builder, call site 타입 강화 (admin DTO 명세 확인 선행) | 8 |
| **Phase 2** | apiClient timeout/signal(opt-in), token storage adapter 단일화, CMS service 골격 + notice 도메인 이관, legacy chat/EmailJS adapter | 9 |
| **Phase 3** | HomePage loading 고착 제거, CMS list stale guard, polling 실패 가시화 — 구조 이동 없는 국소 안정화 + guard 테스트 고정 | 7 |
| **Phase 4** | 목록 카드 4종 server state hook 추출(stale guard), admin query key/mutation hook 캡슐화, WS lifecycle hook 격리 | 14 |
| **Phase 5** | Simulation2Page pure helper 분리 → presenter 3종(ResultWorkspace/ChatPanel/ParameterPanel) 단계 분해, CMS typed model | 17 |
| **Phase 6** | auth gate 표준화, board id parser/세션 소유권 정리, AdminPage3 tab별 container 분해 | 11 |
| **Phase 7** | sanitizer/download/constant 중복 제거, env canonical 정리, remotePatterns allowlist, lint 정책 보완 | 11 |
| **Phase 8** | 전체 회귀 검증(baseline 비교), CMS boundary 검사 추가, strict 옵션 오류량 측정 → 단계 반영, 사이클 종결 | 6 |

합계 90 Task (Phase 표 89건 + 전수 리뷰 후 추가된 RF-TASK-090: EditNoticePage attachment 보상 회귀 테스트 — CMS 트랙/게시판 승인 게이트 하 수행). Phase 간 병렬은 dependency-aware-sequence의 병렬 그룹(PFM / CMS / 독립 소형) 범위에서만 허용하며, `Simulation2Page.tsx`/`AdminPage3.tsx`를 다루는 Task는 직렬 진행한다 (백로그 운영 규칙).

---

## 7. 실제 코드 수정 전 확인해야 할 사항

원본 "확인 필요" 30건 및 별도 출처(테스트 현황 — S1 brief/RF-RISK-016) 중, **착수 전 필수 해소 대상 4건**과 담당 Task는 아래와 같다. 해소 전에는 해당 영역 작업이 보류(⏸) 대상이다.

| # | 확인 항목 | 미확인 시 영향 | 담당 Task / 차단되는 작업 |
|---|---|---|---|
| 1 | **backend admin DTO 계약** — admin DTO가 일반 DTO와 의도적으로 다른 계약인지 백엔드 명세 확인 (S6 원문) | 잘못된 DTO 통합 → admin/job/result 화면 상태 불일치. 미확인 시 alias 연결로 우회 가능 | RF-TASK-007 → RF-TASK-010 (Phase 1 admin DTO 정리) |
| 2 | **Supabase RLS/권한 정책** — 현재 UI 직접 호출 구조가 의도된 설계인지, edit route 접근 제어가 RLS로 보호되는지 (S1/S2 원문) | service 이관 시 권한 동작 변경, 과도한 UI 차단. "RLS 확인 전 과도한 차단 금지" | RF-TASK-015 → RF-TASK-019~021 (CMS 이관), RF-TASK-064 (edit route 권한) |
| 3 | **legacy 유지 여부** — `api/chat.js`+`legacyAiChat.ts`가 제품 범위에 포함되는지, `PFMSimulationPage`/legacy admin(cmsl2004/20042) 운영 범위인지 (S1/S3/S5 원문) | 유지 전제 수리 또는 제거 모두 헛작업/회귀 위험 | RF-TASK-015/024 → RF-TASK-022(chat 표준화), 029(legacy polling), 080(legacy parser) |
| 4 | **테스트 현황** — 코드리뷰는 session1~6까지 수행되었고 테스트 영역 전용 세션(Session 7)은 미수행. 기존 테스트 커버리지·회귀 시나리오 미확인 (S1 brief "추가 조사 필요", RF-RISK-016) | 안전망 없는 상태에서 대형 분해 시 회귀가 머지 후 발견 | RF-TASK-001 (Phase 0 baseline: 검증 5종 + tsc 오류량 실측 기록) |

추가로, 개별 작업의 착수 조건이 되는 "확인 필요" 항목 (착수 직전 해소):

- ③ attachment rollback의 실제 storage path/URL parsing (RF-TASK-002 → RF-TASK-005)
- ④ env 이름/Vercel 배포 설정 (RF-TASK-002 → RF-TASK-006, RF-TASK-079)
- CMS HTML sanitize 정책 — 관리자 trusted input 여부/저장 시점 sanitize 여부 (RF-TASK-059, 확인 전 sanitize 추가 금지)
- CMS content 데이터 shape — pageKey별 자유 schema 의도 여부 (RF-TASK-060)
- `images.remotePatterns` 전체 host 허용이 CMS 요구사항인지 (RF-TASK-081, 확인 전 적용 금지)
- strict 옵션별 오류량 — 측정 후 단계 반영 (RF-TASK-087→088)
- **게시판 앱 영역 Task 전체** — 프로젝트 규칙(수정 지양)상 착수 전 사용자 명시 합의 (P0 ③④ 포함)

---

## 8. 핵심 수치 요약

### 8.1 이슈/Task 규모

| 항목 | 수치 | 출처 |
|---|---:|---|
| 코드리뷰 원본 raw ID (session1~6, 인벤토리 제외) | 237건 | consolidated-findings 7.1 |
| **통합 이슈 (RF-FINDING)** | **61건** | consolidated-findings 7.2 |
| 좋은 패턴 (기준 패턴, 이슈 아님) | 14건 | consolidated-findings 6장 |
| 원본 "확인 필요" 보존 항목 | 30건 | consolidated-findings 5장 |
| **실행 Task (RF-TASK)** | **90건** (RF-TASK-001~090) | task-backlog 4장 (61건 RF 전부 최소 1개 Task에 연결, 누락 없음. T090은 전수 리뷰 후 T030에서 분리) |
| 위험 항목 (RF-RISK) | 17건 (High 12 / Medium 5) | risk-and-impact-map 6장 |

### 8.2 우선순위 분포 (61건 전수 배정, roadmap 4.1)

| 우선순위 | 건수 | 비율 | 비고 |
|---|---:|---:|---|
| P0 (즉시 수정) | 4 | 6.6% | RF-FINDING-032, 036, 051, 061 |
| P1 (구조 개선) | 16 | 26.2% | 거대 컨테이너 분해, CMS service, DTO 단일화 등 |
| P2 (코드 품질) | 32 | 52.5% | 중복 제거, guard, config 정리 등 |
| P3 (장기 개선) | 9 | 14.8% | strict 강화, boundary 확대, 도구 검증 등 |

심각도 분포: High 15 / Medium 31 / Low 11 / Suggestion 4. 난이도·위험도 모두 High인 항목 6건(001, 002, 003, 029, 033, 038)은 단계 분할이 필수 (roadmap 4.3, 추론).

### 8.3 Phase별 Task 수 (task-backlog 4장)

| Phase | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 합계 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Task 수 | 6 | 8 | 9 | 7 | 14 | 17 | 11 | 11 | 6 | **89 (+추가 T090 = 90)** |
| 주관 RF-FINDING 수 | 4 | 4 | 5 | 5 | 11 | 11 | 7 | 11 | 3 | **61** |

---

> **의사결정 요청 사항 (추론)**: ① P0 핫픽스 4건 중 게시판 앱 영역(③④)의 착수 합의, ② 7장 필수 확인 4건(admin DTO 명세 / RLS 정책 / legacy 범위 / baseline 측정)의 확인 주체·기한 지정, ③ Phase 0 착수 승인. 이 3가지가 결정되면 RF-TASK-001부터 즉시 실행 가능하다.
>
> 생성: 2026-06-12. 본 문서는 기반 문서 5종의 수치·근거와 정합하도록 작성되었다.
