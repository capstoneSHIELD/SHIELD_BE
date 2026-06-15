# Session 6 코드리뷰 문서

## 목적

이 디렉터리는 Session 6에서 수행한 Type / Util / Config / Constant 계층 리뷰 결과를 리팩토링 세션에서 재사용하기 위한 기준 문서다.

## 분석 범위

- TypeScript type/interface/union type
- API request/response DTO
- form type, component props type, workflow state type
- util/helper/formatter/parser/mapper/validator
- constant/config/environment variable
- validation schema와 런타임 파싱 구조
- dead code, import/export 구조의 확인 가능 범위

## 이전 세션과의 연결

| 이전 세션 | 연결되는 Session 6 관점 |
|---|---|
| Session 1 | 전체 구조상 `Simulation2Page`, `AdminPage3`, CMS 화면에 책임과 타입 경계가 집중되어 있다는 분석을 타입/DTO 관점에서 재확인했다. |
| Session 2 | page/container 계층의 과도한 책임이 form type, DTO builder, formatter/parser의 위치 문제로 이어진다. |
| Session 3 | 큰 component 내부의 props/state/model 정의가 재사용성과 테스트 용이성을 낮춘다. |
| Session 4 | page-local state와 workflow state 타입이 넓게 열려 있어 상태 전이 검증이 약하다. |
| Session 5 | API/service 계층의 응답 타입 단정, DTO 중복, request body 조립 문제가 Session 6의 타입 안전성 이슈와 직접 연결된다. |

## 문서 구성

| 파일 | 역할 |
|---|---|
| `type-inventory.md` | 주요 type/interface/DTO/schema 후보 목록 |
| `api-dto-contract-map.md` | API 함수와 request/response type 연결 관계 |
| `type-safety-review.md` | `any`, type assertion, non-null assertion, nullable 처리 리뷰 |
| `type-duplication-review.md` | 중복 type/interface/status union 후보 |
| `util-helper-inventory.md` | util/helper/formatter/parser/mapper 목록 |
| `util-responsibility-review.md` | util/helper 책임 분리 리뷰 |
| `config-constant-env-review.md` | config/env/constant/magic value 리뷰 |
| `validation-formatting-review.md` | validation, formatter, parser, mapper 구조 리뷰 |
| `dead-code-and-import-review.md` | dead code와 import/export 확인 결과 |
| `session6-findings.md` | 주요 문제 후보 종합 목록 |
| `refactoring-brief.md` | 리팩토링 우선순위와 주의사항 |
| `next-session-prompt.md` | 다음 세션 시작용 프롬프트 |

## 리팩토링 세션 참고 순서

1. `session6-findings.md`
2. `refactoring-brief.md`
3. `type-safety-review.md`
4. `type-duplication-review.md`
5. `api-dto-contract-map.md`
6. `util-responsibility-review.md`
7. `config-constant-env-review.md`

## 근거 구분

- "실제 코드 근거"는 파일 경로와 라인 번호가 확인된 항목이다.
- "확인 필요"는 정적 검색만으로 확정할 수 없거나 백엔드 계약 문서, 빌드/분석 도구 실행이 추가로 필요한 항목이다.
