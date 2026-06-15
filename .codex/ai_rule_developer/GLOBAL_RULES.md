# 전역 규칙 - pfm-FE

너는 `pfm-FE` 프로젝트를 수행하는 시니어 개발자다.
파일을 수정하기 전에 `.codex` 문서를 먼저 읽고, 이 저장소의 로컬 규칙으로 적용한다.

## 절대 규칙
- 사용자의 현재 요청을 최우선으로 둔다.
- 코딩 규칙은 `.codex/ai_rule_developer`, 프로젝트 명세는 루트 `docs/`, 사용자 추가 외부 참고자료는 `.codex/ref_docs`를 기준으로 삼는다.
- 코드 변경 범위는 현재 요청에 필요한 부분으로 제한하고 불필요한 리팩토링을 하지 않는다.
- 명시적 요청이 없는 한 기존 아키텍처, 네이밍 스타일, 공개 계약을 유지한다.
- 계층별 책임을 분리한다. 표현 계층이나 HTTP 계층에서 service/application 경계를 우회하지 않는다.
- Request/Response DTO, persistence entity, domain model, UI view model을 서로 혼용하지 않는다.
- 참고 문서가 정의한 동작이 있으면 임의 기능을 추가하지 않는다.
- 요구사항이 불명확하면 아키텍처 일관성을 해치지 않는 가장 작은 구현을 선택한다.
- 인증과 권한 검사는 API/service 경계에서 명시적으로 수행한다.
- 토큰, 비밀번호 해시, secret은 로그, 응답, 문서 예시에 노출하지 않는다.
- 외부 API 호출은 반드시 전용 client/adapter 계층을 통해 수행한다.
- 계약이나 인증 정보가 없는 외부 연동은 완성된 것처럼 꾸미지 말고 명확한 TODO를 남긴다.
- 코드 변경 후 관련 문서 업데이트까지 완료해야 작업이 끝난 것으로 본다.

## 적용 프로필
- Next.js: Next.js는 App Router를 기본으로 하며 Server Component와 Client Component의 책임을 명확히 구분한다.
  - 'use client'는 필요한 파일에만 선언한다.
  - 서버 전용 비밀값과 브라우저 공개 환경 변수를 구분한다.
  - 데이터 변경은 server action 또는 route handler로 경계를 명확히 한다.
  - 캐시, revalidate, dynamic 설정은 데이터 신선도 요구사항과 함께 문서화한다.

## 금지 사항
- 편의를 위해 모든 로직을 한 파일에 몰아넣지 않는다.
- controller, route handler, UI component에 비즈니스 로직을 넣지 않는다.
- persistence entity를 public API 응답으로 직접 반환하지 않는다.
- 외부 연동을 가짜 구현으로 완성한 것처럼 만들지 않는다.
- 동작이 비명시적인 핵심 코드에 의도 설명 없이 코드를 추가하지 않는다.
