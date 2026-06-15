# 리팩토링 실행 순서 (Wave 기반 구체화)

> 작성일: 2026-06-12. 계획 문서 13종 전수 리뷰(`plan-review-report.md`) 결과를 반영하여
> `phased-refactoring-plan.md`(Phase 0~8)와 `refactoring-task-backlog.md`(RF-TASK-001~090)를
> **실행 가능한 세션 단위(Wave)** 로 재배열한 문서다.
> **본 문서의 Wave 순서가 Phase 번호 순서와 다른 구간에서는 본 문서를 우선한다.**
> (예: helper 분리 T045(Phase 5)가 hook 추출 T043(Phase 4)보다 선행 — `dependency-aware-sequence.md` 4.5의 파일 직렬 원칙 준수)

---

## 1. 설계 원칙

| 원칙 | 내용 |
|---|---|
| 트랙 4개 분리 | **트랙 S**(PFM-Simulation2, 크리티컬 패스) / **트랙 A**(PFM-Admin, 준임계) / **트랙 C**(CMS·게시판 — **사용자 승인 게이트 필수**, "게시판 앱 수정 지양" 규칙) / **트랙 Q**(독립 quick-win, 세션 여유분 충전재) |
| 공통 기반 → 분기 → 수렴 | Wave 0~3(공통 기반) → S/A/C/Q 4트랙 병렬 → W11(수렴) → W12(종결) |
| 같은 파일은 항상 직렬 | `Simulation2Page.tsx`, `AdminPage3.tsx`, `lib/apiClient.ts`는 Wave를 넘어 항상 직렬 (dep-seq 4.5) |
| Wave = 1 세션 단위 | Wave당 task 3~7개 = 사람+AI 1회 리팩토링 세션 규모. 민감 영역 Wave(S4, S7)는 의도적으로 소형 유지 |
| Phase 게이트 → 트랙 게이트 | "Phase 전체 완료 후 다음 Phase" 대신 **트랙별 진입 조건**으로 운영 (CMS 승인 지연이 PFM 트랙을 막지 않도록) |
| 승인 게이트 | 트랙 C 전체와 W1의 T005는 게시판 앱(Supabase) 수정이므로 착수 전 사용자 합의 필요. 커밋/PR에 "P0 결함 수정/리팩토링 근거 + 최소 수정 범위" 명시 |

> **명칭 주의**: 본 문서의 트랙 **S/A/C/Q**는 `dependency-aware-sequence.md` 3장의 병렬 그룹 **A(PFM)/B(CMS)/C(독립 소형)**와 별개 체계다. 대응: 그룹 A ≈ 트랙 S+A, 그룹 B ≈ 트랙 C, 그룹 C ≈ 트랙 Q. dep-seq의 "그룹 C(독립 소형)"를 본 문서의 "트랙 C(CMS·게시판 — **승인 게이트 필수**)"로 오독하지 말 것.

---

## 2. Wave 실행 표

> 상태 열은 진행하며 갱신: ⬜ 미착수 / 🔵 진행 중 / ✅ 완료 / ⏸ 보류

### 공통 기반 (모든 트랙 선행)

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **W0** — baseline + P0 즉시형 | ⬜ | T001 baseline 기록 / T002 민감영역·확인필요 수집 / T003 polling in-flight guard / T004 URL NaN-safe parser / T085 madge 순환 검증(선행 1회) | T001 후 T003·T004 병렬(파일 상이). T002·T085 동시 진행 가능 | 없음. 단 T003 수동 검증에 백엔드 가동 + job 실행 환경 필요 | **G0**: 5종 스크립트 baseline 문서화 + `npx tsc --noEmit` 오류량 기록. Playwright: polling 겹침 없음(`browser_network_requests`), `/cmsl20043?page=abc` 정상. 순환 발견 시 병렬 트랙 가정 재검토 | 중 |
| **W1** — 조건형 P0 + 확인 필요 일괄 해소 | ⬜ | T005 attachment rollback(조건형·승인 게이트) / T006 env helper(조건형) / T007 admin DTO 명세 확인 / T015 RLS·legacy chat 확인 / T024 legacy 화면 유지 확인 | T007/T015/T024 병렬(조사). T005/T006은 확인 해소 즉시 | T005: 게시판 승인 + storage path 실물 확인 + 백업 경로 확인. T006: Vercel env 대조 | 결정 기록 4종(admin DTO / RLS / legacy chat / legacy page) — 미해소 시 ⏸ 마킹 + 우회 경로(T010 alias) 확정. T005/T006 후 build·test:run + 첨부 실패/env fallback 수동 확인 | 중상 |
| **W2** — shared 타입 기반 | ⬜ | T008 shared status/DTO 모듈(alias) / T009 일반 API 4파일 shared 교체 / T011 workflow stage mapper / T012 parameters 타입 3분리 | T008→T009 직렬. T011·T012는 T008 후 병렬 | W1의 T007 결과 기록 (미확인이어도 alias 경로로 진입 가능) | **G1**: 파일별 commit마다 build+test:run (strict off라 화면 상태 표기 수동 확인 병행) | 저~중 |
| **W3** — API 계약 마무리 | ⬜ | T010 admin DTO 정리(명세 미확인 시 alias 유지 ⏸) / T013 buildUpdateSimulationBody builder / T014 apiRequest call site 타입+parser | T010 ∥ T013 병렬. T014는 T009 후 | W2 완료 | builder 단위 테스트 + Playwright: parameter 수정(PATCH)→job submit/update/restore 전 플로우. 기본 generic 무변경 확인 | 중 |

### 트랙 S — PFM Simulation2 (크리티컬 패스)

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **S4** — API client (민감 영역 단독 세션) | 🔵 | T016 timeout/AbortSignal opt-in / T017 retryable 정책 / T018 token storage adapter / T023 sendContactEmail adapter / T079 PFM env canonical | T016→T017→T018 **직렬**(`lib/apiClient.ts` 동일 파일). T023·T079 병렬 | W3 완료(T014). T023/T079는 T006 완료 | **G2**: 로그인→refresh→401 retry Playwright **필수**(최중요 수동 게이트). refresh hang 시 무한 대기 없음. `lib/api/errors.ts` 모델 보존. test:boundaries. T079 complete with legacy fallback preserved; T023 remains pending. | **상** |
| **S5** — Simulation2 정지작업 | ⬜ | **T045 pure helper 분리(전진)** / T046 extractWarnings guard / T028 getJob 실패 카운트 / **T030 P0 안정화 회귀 테스트(PFM 분)** / T029 legacy guard 이식·격리 | T045→T046→T028→T030 직렬(Simulation2Page). T029 병렬(PFMSimulationPage, T024 결정 필요) | S4 완료. T024 결정 | **G3**: helper 단위 테스트. 다운로드 파일명/warning 표시 무변경. 네트워크 차단/복구 시 notice + polling 겹침 없음. **test:coverage baseline 대비 악화 없음**(hook 이동 전 안전망 확보) | 중 |
| **S6** — list/catalog hook + 정책 문서 | ⬜ | T035 query 정책 문서화 / T036 useSimulationList / T037 useSimulationJobResults / T038 ResultExplorer hooks / T039 useChatSessions | T036~T039 병렬 가능(4파일 상이) — 실무상 2+2 분할 권장 | S4 완료(T016/T017) | hook별 stale guard 단위 테스트. 목록·field·세션 빠른 전환 시 이전 응답 미반영. `sync:false` 정책/refreshKey contract/parent callbacks 보존 | 중 |
| **S7** — WS lifecycle (최고 위험, 의도적 소형) | ⬜ | **T047 JobMonitorMessageDto parser 정의부(선행)** / T043 useJobMonitorSession / T044 useVisualizationSession | T047→T043→T044 **완전 직렬**. T043은 세션 내 3단계: ① 현행 동작 테스트 → ② hook 골격+이동 → ③ fallback/reconnect/beforeunload 검증 | S5 완료(T030, T045) + S6 완료 | **G4**: hook 이동 직후마다 전체 시뮬레이션 워크플로우 Playwright (WS 연결→이벤트→polling fallback→terminal status→result 표시 / viz 시작·종료 반복 시 연결 누수 없음). reconnect timer·stale token guard 테스트. 실패 시 즉시 revert | **최상** |
| **S8** — presenter 1차 | ⬜ | T049 chat/event log stable key / T050 ResultWorkspace 분리 / T051 ChatPanel 분리 / T055 SessionListCard view 분리 / T062 PFM auth gate | T049→T050→T051 직렬. T055(T039 후)·T062(T018 후) 병렬 | S7 완료(T044), S6 완료(T038/T039) | **G6**: presenter 분리 commit마다 전체 워크플로우 Playwright 회귀. T062: 미인증→redirect→로그인→복귀, redirect 루프 없음 | 상 |
| **S9** — Simulation2 분해 완결 | ⬜ | T052 ParameterPanel 분리 / T053 잔여 컨테이너 정리 / T054 WorkspaceTabsCard props 정리 | 직렬 | S8 완료(T051) + W3(T012/T013) | parameter→job submit/update/restore 플로우(민감 영역) + test:boundaries. 분해 전/후 책임 목록 기록. T053 잔여는 후속 백로그화(전량 분해 비강제) | 상 |

### 트랙 A — PFM Admin (W3 직후 분기, 트랙 S와 병렬)

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **A4** — Admin 데이터 계층 | ✅ | T040 buildAdminQueryKeys / T041 mutation hook 캡슐화 / T042 enabled query+refresh 정리 / T067 admin guard presenter+error boundary 전략 / T068 formatter/file util 분리 | T040→T041→T042→T068 직렬(AdminPage3). T067은 2026-06-12 완료(`phase6-execution-log.md`) | W3 완료(T010 — alias ⏸여도 우회 진입 가능). T035 문서 참조 권장 | **G5**: admin job sync/cancel 후 invalidation 누락 없음. field files 중복 요청 재현 불가. 권한 차단 보존. adminPolling interval 무변경. T067 manual admin fallback smoke는 backend-backed 계정 환경으로 이월 | 상 |
| **A5** — Admin URL/tab 분해 | ⬜ | T069 useAdminUrlState / T070 tab 분해 1차(Overview/AccountRequests) / T071 tab 분해 2차(잔여 — **1세션 1~2 tab**, 실질 2~3회 세션) | 전부 직렬(AdminPage3) | A4 완료(T068) | **G5**: tab 분리 commit마다 해당 tab 목록/mutation/dialog + **deep link 기존 query 조합 호환** 수동 확인. 권한 차단 회귀 없음 | 상 |

### 트랙 C — CMS·게시판 (승인 게이트 필수, W2~S7과 병렬 가능)

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **C1** — CMS service 골격 + 타입 정의 | ⬜ | T019 service/storage adapter 골격 / T020 notice 도메인 이관 / T025 useHomeContent / **T060 CMS shape 확인+타입 정의부(전진)** / **T090 attachment 보상 회귀 테스트(T005 후속)** | T019→T020 직렬. T025·T060은 T019 후 병렬 | **사용자 승인 게이트(게시판 앱 수정 합의)** + T015 RLS 확인 + T005 완료 | **G7**: adapter/hook 단위 테스트. notice 목록/상세/수정(첨부) Playwright(local `/board/news`). home 에러 시 error UI·loading 고착 재현 불가. **storage delete/upload 순서 무변경** | 중 |
| **C2** — CMS 도메인 확장 | ⬜ | T021 gallery/home/research 이관 / T026 공통 list stale guard(notice 분은 T020만으로 진입) / T027 pin/delete 실패 피드백 / T065 parseBoardId+not-found | T021 직렬(도메인별 commit). T026·T027·T065 이후 병렬 | C1 완료 | stale guard 단위 테스트. 검색/페이지 빠른 전환 정합. `/board/news/abc` invalid id에서 not-found UI·무한 loading 재현 불가 | 중 |
| **C3** — CMS typed model 적용 + 정리 | ⬜ | T057 NewsPage presenter / T059 sanitize 정책 확인+경계 이동 / T061 CMS typed model 단계 적용 | 병렬 가능(파일 상이). T061은 T060 정의 소비 | C2 완료(T026), C1(T060) | 기존 CMS 콘텐츠 렌더링 무변경(sanitize 확인 전 적용 금지). edit form 저장 수동 확인 | 저~중 |
| **C4** — board gate/세션 정리 | ⬜ | T063 LegacyAdminGate / T064 edit route 권한 명시 / T066 session ownership 단일화 | T063→T064/T066 | C3 + T062 완료(S8). 패턴 참조만이면 T062와 병렬 완화 가능(보수적으로 순서 유지). T015 RLS 확인 필수 | legacy admin gate 동작. **권한 있는 사용자가 기존보다 막히지 않음**(RLS 확인 전 과도한 차단 금지). PFM/Supabase gate 추상화 분리 유지 | 중 |

### 트랙 Q — 독립 quick-win (세션 여유분 충전재)

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **Q1** — quick-win ① | 🔵 | T031 slider empty guard / T032 use-mobile tri-state / T033 use-toast 구독 정리 / T048 index key 제거(carousel/template) / T056 modal 접근성 / T073 next.config 주석 | 전부 병렬(선행 없음, 파일 전부 상이) | W0 완료(baseline 확보) | lint+test:run+build + 해당 화면 수동 스냅샷. T048/T056은 CMS 인접 컴포넌트 — 변경 사실 고지 권장. T031/T032/T033/T048/T056 완료(`q-execution-log.md`), T073 완료(`w11-execution-log.md`) | 저 |
| **Q2** — quick-win ② | 🔵 | T034 LanguageProvider 검토 / T058 slider variants / T075 withQuery 타입 / T076 ColorBends / T078 constant 정리 / T022 legacy chat 표준화 | 전부 병렬. T022는 T015 결정 필요 | W1 완료 | lint+test:run. T034 review-only, T022/T058/T076/T078 complete. T022 live Gemini/manual UI smoke carried forward. T075는 `lib/api/http.ts` 민감 영역 — **런타임 무변경, 타입만** + test:boundaries | 저 |

> 트랙 Q는 어느 시점이든 여유 세션을 채우는 용도로 쓰되, **민감 영역 Wave(S4/S7/A4)와 같은 세션에 섞지 않는다** (diff 오염 방지).

### 수렴 및 종결

| Wave | 상태 | 포함 Task | Wave 내 병렬성 | 진입 조건 | 종료 게이트 | 위험도 |
|---|---|---|---|---|---|---|
| **W11** — util/config 수렴 | 🔵 | T074 재-export 제거 / T077 sanitize/blob util 통합 / T080 legacy parser 분리 / T081 remotePatterns allowlist / T082 formatRelativeTime / T083 lint·JS route 정책 / T072 route layout 검토(기록만) | 대부분 병렬. T077은 T045(S5)+T068(A4) 합류 지점 — 단일 소유로 진행 | S5/A4/Q2/W1 결과물. T081은 도메인 목록 확인 전 적용 금지 | build 필수(next.config). 이미지 렌더링 local+production 확인(미표시 시 즉시 revert). 업로드 파일명/다운로드 무변경. T072/T074/T075/T076/T078/T082 complete; T077/T080/T081/T083 remain gated or carried forward. | 저~중 |
| **W12** — 최종 검증·종결 | ⬜ | T084 전체 회귀+baseline 비교 / T086 CMS boundary 검사 / T087 strict 오류량 측정 / T088 strict 옵션 단계 반영 / T089 잔여 확인 필요 정리+종결 (T085는 W0 결과와 재실행·비교) | T086·T087 병렬 → T088 → T089 | 모든 트랙 필수 task 완료 (P3 검토성 항목 제외 가능). **트랙 C가 승인 미득으로 ⏸이면**: 해당 task를 이월(⏸ 사유 기재)로 남기고 PFM(S/A)+Q 범위로 **부분 종결 허용** — 이때 T086(CMS boundary, RF-003 이관 전제)도 함께 이월하고, T089에 미해소 항목을 기록 | **G8**: 5종 명령 baseline 대비 동등 이상. Playwright 3개 영역(시뮬레이션 전체 워크플로우/admin/게시판) 전수 회귀 리포트. strict 적용/이월 내역 기록. 61건 finding 처리 상태 표 | 저 (T088만 중) |

---

## 3. 크리티컬 패스와 병목

### 3.1 크리티컬 패스 (90개 task 중 최장 체인, 15-task 깊이)

```
T001 → T007 → T008 → T009 → T014 → T016 → [T045 → T028 → T030] → T047 → T043 → T044
     → T050 → T051 → T052 → T053 → T084 → T089
(Wave: W0 → W1 → W2 → W3 → S4 → S5 → S7 → S8 → S9 → W12)
```

### 3.2 준임계 패스 (Admin, 14-task)

```
T001 → T007 → T008 → T009 → T010 → T040 → T041 → T042 → T068 → T069 → T070 → T071 → T084
```

트랙 A는 **W3 직후 분기 가능**하므로(T040은 T010만 필요, alias 우회 허용) S4~S7과 병렬로 흡수되어 전체 기간을 늘리지 않는다.

### 3.3 병목과 보험 장치

| 병목 | 내용 | 보험 장치 |
|---|---|---|
| T007→T008 (양 패스 공통) | backend admin DTO 명세 확인 지연 시 shared 타입 작업 차단 | **alias 우회 경로**(roadmap 4.4 / T010 ⏸) — 명세 미확인이어도 alias로 진행 후 확정 시 정리 |
| S4 (`lib/apiClient.ts`) | T016/T017/T018 동일 파일 직렬, 이후 S5~S7·S6 전부가 이 위에 섬 | **단독 세션으로 보호** — 다른 작업과 같은 세션에 섞지 않음 |
| 게시판 승인 게이트 | 트랙 C 전체 + T005가 사용자 승인에 잠김 | **T030(PFM 분)/T090(CMS 분) 분리**로 승인 지연이 PFM 크리티컬 패스를 차단하지 않음 |
| T003 수동 검증 환경 | polling 관찰에 백엔드 가동 + job 실행 환경 필요 | W0 착수 전 환경 가용성 확인. 불가 시 unit test + 코드 리뷰로 대체하고 환경 확보 후 재검증 |

---

## 4. 검증 게이트 요약 (회귀 조기 발견 지점)

| 게이트 | 시점 | 강제 내용 |
|---|---|---|
| **G0** | W0 종료 | baseline 5종 + tsc 오류량 기록 — 이후 모든 비교의 기준점. **이것 없이 어떤 Wave도 착수 금지** |
| **G1** | W2 각 파일 교체 commit | build+test:run을 commit 단위로 + 화면 상태 표기 수동 확인 (strict off 보완) |
| **G2** | S4 종료 | 로그인→refresh→401 retry 수동 플로우 — 누락 시 이후 모든 PFM Wave에서 증상 재현·원인 격리 불가 |
| **G3** | S5 종료 | test:coverage 악화 없음 — T043 이동 직전 안전망 확보 확인 |
| **G4** | S7 각 hook 이동 직후 | 전체 시뮬레이션 워크플로우 Playwright — 최대 위험 지점, commit 단위 revert 준비 |
| **G5** | A4 T041/T042 직후, A5 각 tab 직후 | invalidation 누락 + deep link 호환 수동 확인 |
| **G6** | S8/S9 각 presenter 직후 | 전체 워크플로우 + test:boundaries |
| **G7** | 트랙 C 각 도메인 이관 직후 | board CRUD(첨부 포함) Playwright local — 운영 데이터 영역, 도메인 단위로 좁게 |
| **G8** | W12 | baseline 대비 전체 비교 + 3개 영역 전수 회귀 |

---

## 5. 전수 리뷰 반영 사항 (순서 교정 요약)

상세는 `plan-review-report.md` 참조. Phase 번호 순서 대비 본 문서가 교정한 핵심:

1. **T045/T046(helper 분리, Phase 5) → S5로 전진** — hook 추출(T043, Phase 4)보다 선행 (dep-seq 4.5 파일 직렬 원칙)
2. **T030을 PFM 분으로 재정의, CMS 분은 T090 분리** — 게시판 승인 게이트가 PFM 크리티컬 패스를 잠그지 않도록
3. **T047 parser 정의부 → T043 직전 선행** — hook이 추출 시점부터 typed parser를 소비
4. **T060 CMS 타입 정의부 → C1 전진** — service/hook 반환 타입 2회 작업 방지
5. **T085 madge → W0 선행 1회** — 병렬 트랙 가정의 전제를 작업 시작 전에 검증
6. **T026 선행 도메인 분리** — gallery 이월 시에도 notice 분 진입 가능
7. **P0 4건을 즉시형(T003/T004 — W0)과 조건형(T005/T006 — W1)으로 분리** — 숨은 선행 조건(승인/storage 확인/Vercel 대조) 명시
