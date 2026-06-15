# Refactoring Brief

| 우선순위 | 리팩토링 대상 | 현재 문제 | 개선 방향 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|---|
| P0: 즉시 수정 필요 | env config helper | `lib/supabaseClient.ts:5-6`, `components/pages/ContactPage.tsx:26-29`에서 non-null assertion 사용 | `getRequiredPublicEnv`, integration별 config module 도입 | 중간 | env 이름과 배포 설정 확인 후 적용 |
| P1: 구조 개선 필요 | simulation status/job status 타입 단일화 | `SimulationStatus`, `JobStatus`, `VisualizationStatus` 중복 | API status union을 shared DTO로 모으고 workflow stage는 mapper로 분리 | 큼 | admin API가 별도 계약인지 확인 필요 |
| P1: 구조 개선 필요 | simulation parameter 타입 분리 | `WorkflowState.parameters`, patch body가 `Record<string, any>` | `SimulationParametersDto`, `EditableParams`, `UpdateSimulationBody` mapper 분리 | 큼 | `Simulation2Page` workflow 리팩토링과 함께 진행 |
| P1: 구조 개선 필요 | admin/common DTO 경계 정리 | `lib/api/admin.ts`가 job/result/viz DTO와 wrapper를 함께 보유 | shared DTO + admin extension type 구조로 정리 | 큼 | API 응답 field 차이 확인 필요 |
| P1: 구조 개선 필요 | API response parser 전략 | `apiRequest<T = any>`, `JSON.parse() as T` | endpoint wrapper type 명시, 핵심 응답 guard/schema 도입 | 중간 | 전체 call site 영향이 커서 단계 적용 |
| P2: 코드 품질 개선 | Simulation2Page helper 분리 | page 내부에 parser/formatter/mapper/domain util 집중 | `simulation2` feature helper로 이동하고 테스트 추가 | 중간 | 행동 변경 없이 pure function부터 이동 |
| P2: 코드 품질 개선 | CMS content type 정의 | CMS public/edit 화면에 `any` 다수 | pageKey별 DTO/view model/form model 정의 | 중간 | CMS 데이터 shape 확인 필요 |
| P2: 코드 품질 개선 | validation schema 연결 | zod 의존성은 있으나 request/form schema 사용 근거 부족 | legacy chat, simulation update payload부터 schema 적용 | 중간 | 과도한 schema 도입보다 핵심 boundary 우선 |
| P2: 코드 품질 개선 | config/constant 정리 | polling interval, reconnect delay, colormap 옵션이 흩어짐 | workflow/trame/config constant로 분리 | 낮음 | 실제로 도메인별 옵션이 다른지 확인 |
| P3: 장기 개선 | strict compiler option 단계 강화 | strict 계열 옵션이 꺼짐 | 디렉터리/feature 단위로 오류 해소 후 옵션 강화 | 큼 | 단번에 전체 strict 전환은 리스크 큼 |
| P3: 장기 개선 | dead code/import 품질 게이트 | unused 검출 꺼짐, cycle 미확인 | ESLint/tsc/dependency analyzer 도입 | 중간 | 기존 미사용 코드 정리와 병행 |

## 먼저 건드리면 안 되는 민감한 영역

- `apiRequest<T>` 기본값을 전역에서 즉시 바꾸는 작업은 전체 API call site에 영향을 준다.
- admin DTO 통합은 백엔드 admin response가 일반 response와 같은지 확인한 뒤 진행해야 한다.
- `Simulation2Page` parameter mapper 변경은 job submit/update/restore 흐름과 함께 테스트해야 한다.

## 안전하게 먼저 개선 가능한 영역

- env config helper 추가 후 기존 env 접근을 한두 파일부터 교체
- `Simulation2Page` 내부 pure formatter/parser를 동작 변경 없이 파일 분리
- colormap, polling interval 같은 단순 constant 위치 정리

## Session 7과 연결할 항목

- strict 옵션 강화 전 타입체크 오류량 측정
- helper 분리 후 unit test 추가 대상 선정
- API parser/schema 도입 후 contract test 필요성 검토
- accessibility/performance 리뷰에서 큰 component 분리 효과 재확인
