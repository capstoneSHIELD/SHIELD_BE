# 리팩토링 계획 문서 전수 리뷰 보고

> 리뷰일: 2026-06-12. 대상: `C:\pfm-FE\.codex\ref_docs\refactoring` 계획 문서 13종 전체.
> 본 보고는 리뷰 발견 사항과 조치 내역을 기록하며, 교정된 실행 순서는 `refactoring-execution-order.md`에 반영되어 있다.

---

## 1. 리뷰 방법

3개 차원으로 병렬 감사를 수행했다.

| 차원 | 검증 내용 | 방식 |
|---|---|---|
| ① ID/상호참조 정합성 | RF-FINDING 61건의 P0~P3/Phase 배정이 6개 문서 간 일치하는지, RF-TASK 의존성 무결성(존재성/역전/순환), 수치 일관성 | **전수** (정규식 추출 후 프로그램 대조) |
| ② 실행 가능성/순서 논리 | dep-seq ↔ backlog ↔ phased-plan 순서 모순, P0 독립성, 크리티컬 패스/병목, task 크기, 검증 게이트 배치 | 전수 정독 + 순서 시뮬레이션 |
| ③ 근거 충실도/코드 정합 | 대상 파일 실존(36개), 검증 명령어 실재, 라인 앵커 표본(5+8건), "확인 필요" 전달 일관성, 민감 영역 반영(6건 표본) | 실물 코드 대조 (읽기 전용) |

## 2. 종합 판정

- **핵심 실행 라인(consolidated-findings → roadmap → phased-plan → backlog → traceability → prompts)의 ID 체계와 Phase/우선순위 배정은 완전 정합.** RF-FINDING 61건 전수에서 4개 핵심 문서 간 모순 0건, RF-TASK 의존성 역전·순환 0건, prompts↔backlog 작업 범위 완전 일치.
- **코드 정합성 우수**: 지목된 36개 파일 전부 실존, 라인 앵커 표본 전건 일치(코드리뷰 시점 HEAD 5861ee5와 현재 코드 동일 — 밀림 0건), 검증 명령어 전부 실재.
- **결함은 두 갈래에 집중**: (a) 03:16~03:17에 생성된 보조 문서 2종(dependency-aware-sequence, verification-strategy)이 03:48 phased-plan의 확정 배정(9.3)을 미반영한 채 잔존 — Critical 2건. (b) Phase 번호 순서를 그대로 실행하면 발생하는 순서 결함(helper/hook 역전, 게시판 승인 게이트가 PFM 크리티컬 패스를 잠그는 숨은 결합 등) — Critical 2건.
- 발견 총 26건(중복 병합 후): **Critical 4 / Major 9 / Minor 13**. 전 건 조치 완료 (문서 수정 21건 + Wave 설계 반영 5건).

## 3. 발견 사항 및 조치 내역

조치 구분: **[수정]** = 기존 문서 직접 수정 완료 / **[Wave]** = `refactoring-execution-order.md` 설계에 반영 / **[기록]** = 본 보고에 기록(실행 시 참고)

### 3.1 Critical (4건)

| # | 차원 | 발견 내용 | 조치 |
|---|---|---|---|
| C-1 | ① | **RF-FINDING-061 후속 hook화 Phase 모순**: dep-seq 4.2와 verification-strategy "P0 재검증 규칙"은 Phase 4로, backlog(T069)·phased-plan 9.2·traceability는 Phase 6으로 기재 | [수정] 두 보조 문서를 "Phase 6 (RF-TASK-069)"으로 정정 |
| C-2 | ① | **verification-strategy F033/F020 Phase 스왑**: F033(WS hook 분리)을 Phase 3, F020을 Phase 4 검증 대상으로 잘못 기재 (확정은 정반대). Phase 3 수동 게이트가 Phase 4 작업을 요구 | [수정] 4장 Phase-RF 매핑 전체를 phased-plan 9.1 확정 표 기준으로 재작성 |
| C-3 | ② | **Simulation2Page 파일 내 순서 역전**: dep-seq 4.5는 helper 분리(F047)→hook 추출(F033/001) 순서를 명시했으나, backlog는 T043(Phase 4)이 T045(Phase 5)보다 선행하고 T043 선행 컬럼에 T045 부재 — Phase 순서대로 가면 helper를 든 채 WS hook을 이동 | [수정] T043 선행에 T045 추가. [Wave] S5(T045 전진)→S7(T043) 순서로 확정 |
| C-4 | ② | **게시판 승인 게이트가 PFM 크리티컬 패스를 잠금**: T030(T043의 필수 선행)의 선행에 T005(EditNoticePage, 게시판 앱)가 포함 — 승인 지연 시 게시판과 무관한 PFM 핵심 체인(T030→T043→T044→T050~053) 전체 차단 | [수정] T030을 PFM 분으로 재정의(선행에서 T005 제거), CMS 분은 신규 **RF-TASK-090** 분리(총 90 task). traceability·prompts 동기 반영. [Wave] T090은 C1 배치 |

### 3.2 Major (9건)

| # | 차원 | 발견 내용 | 조치 |
|---|---|---|---|
| M-1 | ① | verification-strategy 4장에 확정 전(前) 매핑 잔존 (Phase 1 행에 038/043/044/045/046/057, Phase 2 행에 059, Phase 7 행에 047/048/060, Phase 5 행에 F002 분리분) | [수정] C-2와 함께 4장 재작성으로 해소 |
| M-2 | ①② | dep-seq ↔ backlog 작업 순서 모순 4건 (helper/hook 순서(=C-3), Phase 7 잔여 범위, F001 완결 위치, F023~026 배치) | [수정] dep-seq 서두에 "execution-order 우선" 안내 추가. [Wave] 순서 확정으로 해소 |
| M-3 | ①② | **madge 순환 검증 시점 모순**: dep-seq 4.6은 Phase 0 실행 요구, backlog T085는 Phase 8에만 배치 — 병렬 트랙 가정의 전제 검증이 작업 종료 후에 옴 | [수정] dep-seq 4.6과 T085를 "Phase 0 선행 1회 + Phase 8 재실행·비교"로 명확화. [Wave] T085를 W0에 배치 |
| M-4 | ① | roadmap 4.3 난이도/위험도 분포 수치 오집계 (난이도 Medium 14/Low 40 → 실제 18/36, 위험도 Medium 18/Low 36 → 실제 20/34) | [수정] 4.3 정정 |
| M-5 | ② | **CMS DTO 순서 역전**: dep-seq는 RF-044(CMS 타입 정의) 선행을 명시했으나 backlog는 T060을 Phase 5에 두고 선행을 T025로 지정 — service/hook을 untyped로 만들고 재작업하는 의도치 않은 2-pass | [수정] T060에 "타입 정의부 CMS 트랙 첫 세션 전진 가능" 명시. [Wave] T060을 C1에 배치 |
| M-6 | ② | **WS parser 닭-달걀**: T047(parser)의 선행이 T043인데 T043이 parser를 소비해야 함 — raw 계약으로 hook 추출 후 시그니처 재수정(민감 영역 2회 개방) | [수정] T047에 "정의부는 T043 착수 직전 선행 수행" 명시. [Wave] S7에서 T047→T043 직렬 |
| M-7 | ② | **P0 4건 중 2건은 즉시 착수 불가**: T005는 승인+storage 확인+백업 경로 3중 숨은 선행, T006은 Vercel env 대조 선행. T003 수동 검증도 백엔드 가동 환경 전제 | [Wave] P0를 즉시형(T003/T004 — W0)과 조건형(T005/T006 — W1)으로 분리, 진입 조건 명시 |
| M-8 | ② | **이월 유연성과 하드 선행 충돌**: T026(공통 stale guard)이 T021(gallery — 이월 가능으로 명시됨) 완료를 하드 선행으로 요구 — 이월 시 T026 영구 차단 | [수정] T026 선행을 도메인별 분리 (notice 분: T020 / gallery 분: T021 완료 시) |
| M-9 | ① | "확인 필요 30건" 산정 기준 불명 (5장 raw 34건, alias 중복 4건 제외 기준 미기재) — 5개 문서에 수치 전파됨 | [수정] consolidated-findings 7.2에 산정 기준 1줄 추가. final-summary 7장 도입문 보정 |

### 3.3 Minor (13건)

| # | 차원 | 발견 내용 | 조치 |
|---|---|---|---|
| m-1 | ① | T030/T069 "P0 후속" 우선순위 표기 비일관 | [수정] "P0(후속)"으로 통일 (T030/T090) |
| m-2 | ① | traceability F038 행(S1-TYPE-001/S6-TYPE-001) "(연관 0)" 표기 누락 | [수정] "8 (연관 0)"으로 통일 |
| m-3 | ① | phased-plan 행 641 키릴 문자 'м' 오타 ("мutation") | [수정] "mutation" 정정 |
| m-4 | ① | roadmap 4.4 잠정 매핑에 확정 결과 안내 부재 | [수정] "확정은 phased-plan 9.1/9.3 참조" 각주 추가 |
| m-5 | ① | backlog 검증 메모 F014 매핑이 주관/동반 미구분 (T051 오독 가능) | [수정] "014(T048/049, T051 동반)" 구분 표기 |
| m-6 | ② | query 정책 문서화(T035)가 admin mutation hook(T041)의 권장 선행인데 미연결 | [Wave] S6에 T035 배치(A4 진입 전 참조 권장 명시) |
| m-7 | ② | T028 완료 조건의 "한 commit" 문구가 운영 규칙(1 task 1 commit) 및 T030 범위와 모호 | [기록] 실행 시 T028 commit은 자체 변경+시나리오 검증만, guard 회귀 테스트 코드는 T030 전담 |
| m-8 | ② | Phase 게이트(전체 완료 후 다음 Phase) 규칙이 트랙 간 무의존 작업을 형식상 차단 | [Wave] 트랙별 게이트로 재정의 (설계 원칙 5) |
| m-9 | ② | task 크기 부적정: T043 과대(1세션 초과 위험), T071 과대(5개 tab 일괄), T053 개방형. T007/T015/T024/T017 과소 | [수정] T043 세션 내 3단계, T071 "1세션 1~2 tab" 명시. [Wave] 조사형 task는 W1에 일괄 묶음 |
| m-10 | ② | T002 수집 범위가 P0 관련에 한정 — 후속 차단 요인(WS 구조/CMS shape/colormap/이미지 도메인)이 늦게 발견됨 | [수정] T002 수집 목록 확장 |
| m-11 | ② | T063(CMS gate)의 T062(PFM gate) 선행이 기술 의존이 아닌 패턴 참조 의존 — 불필요 종속 | [기록] 보수적으로 순서 유지하되, 패턴 공유만이면 병렬 완화 가능 (C4 진입 조건에 명시) |
| m-12 | ③ | "3347/2942 lines"는 비공백 기준(실제 총 3632/3076줄) — 산정 기준 미기재로 불일치 오인 가능 | [수정] consolidated-findings/roadmap 첫 등장부에 "(비공백 기준)" 주석 |
| m-13 | ③ | README 착수 전 확인 예시에 legacy 항목 누락 / verification-strategy `adminPolling.ts` 경로 모호 | [수정] README 예시 추가, `components/pages/adminPolling.ts` 전체 경로 병기 |

## 4. 통과 확인 항목 (결함 없음)

| 검증 항목 | 결과 |
|---|---|
| RF-FINDING 61건의 P0~P3·주관 Phase 배정 — 핵심 4문서(roadmap/phased-plan/backlog/traceability) 간 일치 | ✅ 전수 통과 |
| RF-TASK 의존성: 존재하지 않는 선행 0건, Phase 역전 0건, 순환 0건 | ✅ 전수 통과 |
| prompts ↔ backlog Phase별 작업 범위/명칭/민감 영역 표기 일치 | ✅ 전수 통과 |
| traceability 205행의 RF-TASK 참조 실존, RF-FINDING 61건 전수 추적 | ✅ 전수 통과 |
| RF-RISK-001~017의 관련 이슈 참조 실존 | ✅ 전수 통과 |
| 대상 파일 실존 (지목 36개 + 부가 8개) — 미존재 파일 0건 | ✅ 통과 |
| 라인 앵커: Simulation2Page:1605(polling, in-flight guard 부재 확인), AdminPage3:498(NaN), EditNoticePage:107(rollback 부재), supabaseClient:5-6(env `!`), tsconfig(8/10/29/32) 외 8건 | ✅ 전건 일치, 밀림 0건 |
| 검증 명령어 5종 실재, typecheck/e2e 부재 기술 정확 | ✅ 통과 |
| 민감 영역 → phased-plan 위험 요소/prompts 주의사항 반영 (6건 표본) | ✅ 통과 |
| 게시판 앱/시뮬레이션 앱 경계 규칙의 문서 간 일관 반영 | ✅ 통과 |

## 5. 잔여 권고 (실행 시 참고)

1. **W0 착수 전**: T003 수동 검증용 백엔드 가동 + job 실행 가능 환경 확인. 불가 시 unit test로 대체하고 환경 확보 후 재검증.
2. **T007(admin DTO 명세) 회신 지연 대비**: alias 우회(T010 ⏸)로 진행하는 결정을 W1에서 명시적으로 기록할 것.
3. **트랙 C 착수 전**: 게시판 앱 수정 승인을 사용자에게 명시적으로 받을 것 (CLAUDE.md "게시판 앱 수정 지양" 규칙).
4. **코드 변동 감시**: 본 리뷰의 라인 앵커는 HEAD 5861ee5 기준. 리팩토링 착수 전 새 커밋이 쌓이면 W0의 T002에서 앵커 재확인 필요.
5. **Session 7(test/perf/a11y) 코드리뷰**: 미수행 상태. W12(T087/T088) 전에 수행하면 Phase 8 검증 항목이 보강됨.

---

## 6. 2차 정합성 재검증 (2026-06-12, 코드리뷰 원본 대조 포함)

코드리뷰 원본(session1~6 전 문서)과 리팩토링 문서 15종을 재대조한 2차 검증의 발견·조치 기록. 1차 리뷰(상기 1~5장)가 주석으로만 교정하고 **선행 작업 컬럼/타 문서에 전파하지 않은 잔여 결함**이 중심이다.

### 6.1 정합 확인 (결함 없음)

- 코드리뷰 원본 S1-*~S6-* 이슈의 RF-FINDING-001~061 병합 전수 재확인 — 누락 0건, 심각도 최고치 채택 규칙 위반 0건 (SEV-DIFF-01~06 기록과 일치).
- 세션 brief의 민감 영역/“안전하게 먼저” 항목이 do-not-touch 문서에 원문 보존됨 — 표본 전건 일치.
- roadmap 4.3 수치(M-4 정정분), verification-strategy 4장 Phase-RF 매핑(C-2 재작성분), dep-seq 4.2(C-1 정정분) — 1차 조치 반영 확인.

### 6.2 발견 및 조치 (전 건 수정 완료)

| # | 구분 | 발견 내용 | 조치 |
|---|---|---|---|
| R2-1 | 순서 | **T047 선행 컬럼 모순(M-6 미완 교정)**: 설명·Wave S7은 T047→T043인데 백로그 T047의 선행 컬럼에 RF-TASK-043이 잔존 — 운영 규칙 1.2.1을 따르면 상호 선행 데드락 | [수정] T047 선행을 RF-TASK-011(+T043 직전 수행 명시)로 정정, T043 선행에 RF-TASK-047(parser 정의부) 추가 |
| R2-2 | 순서 | **T060 선행 컬럼 모순(M-5 미완 교정)**: Wave C1은 T060∥T025 병렬인데 백로그 T060 선행이 RF-TASK-025 | [수정] T060 선행을 RF-TASK-019로 정정(T025 완료 불요 명시) |
| R2-3 | 순서 | **백로그 운영 규칙 6(Phase 게이트)과 Wave 트랙 게이트 충돌(m-8 미완 교정)**: 규칙 6이 Phase 전체 완료 게이트를 유지 | [수정] 규칙 6을 트랙 게이트 우선으로 재기술 (Wave 미사용 시에만 Phase 게이트 복귀) |
| R2-4 | 순서 | **dep-seq 순서 표 4행에 `useAdminUrlState`(RF-061 hook화) 잔존(C-1 부분 교정)**: 확정 배정은 Phase 6/Wave A5(T069) | [수정] 해당 항목에 확정 배정 주석 병기 |
| R2-5 | 안전성 | **트랙/그룹 명칭 충돌**: dep-seq 그룹 C(독립 소형, 충돌 없음) ↔ execution-order 트랙 C(CMS·게시판, **승인 게이트 필수**) — 오독 시 게시판 앱 무승인 수정 위험 | [수정] dep-seq 3장·execution-order 1장·백로그 규칙 6·prompts 규칙 1에 명칭 대응 주석 추가 |
| R2-6 | 안전성 | **W12 종결 게이트가 트랙 C 승인 미득 시 영구 차단**: C-4가 크리티컬 패스 잠금은 풀었으나 최종 종결(T084/T089) 잠금은 잔존 | [수정] W12 진입 조건에 부분 종결 조항 추가 (⏸ 이월 + T086 동반 이월 + T089 기록) |
| R2-7 | 안전성 | **verification-strategy가 Wave 게이트(G0~G8)와 미연결**: Phase 기준 체크리스트만 존재해 Wave 실행 시 적용 시점 불명 | [수정] 1장 원칙 9(Wave 게이트 연결) 추가 |
| R2-8 | 수치 | **T090 추가(89→90) 미전파**: README(3곳)·final-summary(4곳)·traceability 헤더·prompts 헤더/말미에 89 표기 잔존. 백로그 검증 메모 "036(T005/030)"도 T090 분리 미반영 | [수정] 전 위치 90/T090으로 갱신, 검증 메모 036(T005/090) 정정 |
| R2-9 | 표기 | prompts 규칙 1(Phase 순서 엄수)이 규칙 6(Wave 우선)과 충돌 소지 | [수정] 규칙 1에 "6번 Wave 우선 규칙이 상위" 명시 |

### 6.3 잔여 비조치 항목 (의도적 보존)

- risk-and-impact-map RF-RISK-001의 `useSimulationWorkflow` 명칭: 실제 Task에는 없는 hook이나 RF-001 개선 방향 원문 표현이므로 보존 (실행 시 T050~053 presenter 분해로 대체됨을 인지할 것).
- 1차 리뷰 라인 앵커 기준(HEAD 5861ee5)은 유효 — 본 2차 검증 시점에도 신규 커밋 없음(working tree에 본 문서 세트만 untracked). W0 착수 전 T002에서 재확인 권고는 유지.
