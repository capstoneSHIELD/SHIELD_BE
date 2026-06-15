# 아키텍처 규칙 - pfm-FE

이 프로젝트는 명확한 계층형 아키텍처를 따른다. 책임과 의존성 방향을 반드시 분리한다.

[디렉토리 구조]
- `app`
- `components`
- `features`
- `services`
- `lib`
- `types`

[계층 책임]
- Presentation/API 계층은 요청 파싱, 응답 변환, 라우팅, 화면 조립만 담당한다.
- Service/Application 계층은 유스케이스 조합과 비즈니스 판단을 담당한다.
- Repository/Adapter/Infrastructure 계층은 DB 접근과 외부 시스템 접근을 담당한다.
- Entity/Domain model은 저장 또는 도메인 상태를 표현하며 public response 객체로 직접 사용하지 않는다.
- Schema/DTO/View model은 외부 계약을 표현하며 persistence model과 분리한다.

[의존성 방향]
- 의존성은 Presentation/API/UI 계층에서 Service/Application, Repository/Adapter, Infrastructure 방향으로 흐른다.
- 상위 계층은 명시적 인터페이스 또는 좁은 모듈을 통해 하위 계층을 호출한다.
- 하위 계층은 UI, HTTP 응답, request, framework 객체를 상위 계층에서 가져오지 않는다.

[프로필별 아키텍처]

### Next.js
- app 디렉토리는 라우트, 레이아웃, 서버 데이터 로딩의 기준 위치로 사용한다.
- Server Component는 데이터 조회와 정적/서버 렌더링 책임을 담당한다.
- Client Component는 브라우저 상태, 이벤트, 인터랙션이 필요한 경우에만 사용한다.
- route handler는 API boundary이며 복잡한 비즈니스 로직은 service 계층으로 분리한다.
- 권장 흐름: Route/Page -> Server Action or Service -> Repository/External API.

## 외부 연동 계층
- 외부 HTTP/SDK 연동은 client 또는 adapter 모듈 뒤에 둔다.
- Service는 비즈니스 의도를 드러내는 좁은 메서드로 adapter를 호출한다.
- Upstream DTO와 내부 DTO를 분리한다.

## 인증 경계
- Authentication은 호출자를 식별하고 authorization은 해당 동작 가능 여부를 결정한다.
- Password hashing, token handling, session persistence는 전용 모듈에 둔다.
- 보호된 service method는 필요한 경우 명시적 actor/user context를 전달받는다.
