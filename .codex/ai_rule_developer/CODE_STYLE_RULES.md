# 코드 스타일 규칙 - pfm-FE

코드 스타일은 취향이 아니라 이 저장소의 핵심 규칙이다.
저장소에 formatter와 linter가 있으면 그것을 우선한다. formatter가 결정하지 않는 영역은 이 문서를 따른다.

[공백 규칙]
- 메서드 경계는 눈으로 분명히 구분되어야 한다. 정확한 공백 수는 저장소 formatter가 강제하면 formatter를 따른다.
- formatter가 허용하는 범위에서 서로 다른 책임의 로직 블록은 빈 줄로 구분한다.
- 함수 시그니처, 표현식, 쿼리, JSX block, builder chain이 여러 줄에 걸쳐 작성되면 다음 로직 블록 전에는 빈 줄을 둔다.
- validation, transformation, persistence, response mapping을 하나의 빽빽한 블록으로 압축하지 않는다.

[네이밍]
- 짧은 이름보다 명확한 이름을 우선한다.
- 도메인 의미, 사용자 의도, use-case 책임을 설명하는 이름을 사용한다.
- 변수, 메서드, 클래스, 파일, DTO 필드, API 필드, DB 컬럼에서 설명 없는 축약어를 피한다.
- TypeScript 내부 boolean 이름은 `isActive`, `hasPermission`, `shouldRetry`처럼 참/거짓 문장으로 읽혀야 한다.
- TypeScript 내부 collection 이름은 `simulationJobs`처럼 복수성과 의미가 드러나야 한다.
- API field, DB column, upstream DTO처럼 외부 계약이 정한 이름은 해당 계약을 우선한다.

[메서드 규칙]
- 하나의 메서드는 변경 이유가 하나여야 한다.
- validation, authorization, state transition, persistence, external call, response mapping이 섞이면 메서드를 분리한다.
- 반복되거나 중첩된 판단은 private helper로 분리하되, 모호한 helper 이름 뒤에 비즈니스 규칙을 숨기지 않는다.
- Service method는 use-case 이름을 사용하고, UI handler는 event 또는 user intent 이름을 사용한다.
- 주변 도메인이 의미를 명확히 만들지 않는 한 `process`, `handle`, `doWork`, `manage` 같은 포괄적 이름을 사용하지 않는다.

[타입 및 계약]
- 공개 함수, service method, DTO, schema, entity, domain model, view model에는 명시적 타입을 유지한다.
- 공개 계약을 정의하는 위치에서는 추론형 또는 암시적 타입에 의존하지 않는다.
- 입력/출력 모델은 loose dictionary나 untyped object가 아니라 명시적 구조로 표현한다. 단, 프레임워크가 요구하는 경우는 예외다.
- optional field는 명시적으로 표시하고 값이 없을 때의 의미를 문서화한다.

[주석]
- 모든 핵심 클래스와 핵심 메서드에는 의미 있는 설명을 작성한다.
- 언어 관례가 허용하면 JSDoc 스타일 주석을 사용한다.
- 주석은 도메인 규칙, 비명시적 제약, 연동 가정, 상태 전이, 실패 동작을 설명해야 한다.
- private helper도 이름만으로 목적이 분명하지 않으면 주석으로 존재 이유를 설명한다.
- TODO는 누락된 계약, upstream 의존성, 담당자, 후속 조건을 포함해야 한다.
- 코드를 그대로 반복하는 주석은 작성하지 않는다.

[파일 구성]
- 활성 framework 관례가 분리를 요구하면 한 파일은 하나의 주요 class, component, service, repository, adapter를 책임진다.
- 무관한 class, component, hook, controller, service, repository, DTO를 한 파일에 섞지 않는다.
- import는 그룹화하고 사용하지 않는 import는 같은 변경에서 제거한다.
- 활성 framework profile과 맞지 않거나 의미 있는 중복 제거가 아닌 새 directory를 만들지 않는다.

[금지]
- 축약된 변수명
- 의미 없는 메서드명
- 의도 설명 없는 핵심 코드
- 명시적 타입 없는 공개 계약
- 서로 다른 계층을 섞은 파일
- 저장소 formatter와 충돌하는 포맷팅

[Next.js 스타일 메모]
- 'use client'는 필요한 파일에만 선언한다.
- 서버 전용 비밀값과 브라우저 공개 환경 변수를 구분한다.
- 데이터 변경은 server action 또는 route handler로 경계를 명확히 한다.
- 캐시, revalidate, dynamic 설정은 데이터 신선도 요구사항과 함께 문서화한다.
