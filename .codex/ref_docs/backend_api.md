# API Specification

## 공통 규칙

- 모든 REST API 기본 경로는 `/api/v1` 이다.
- JSON 요청과 응답 필드명은 camelCase를 사용한다.
- 외부에 노출되는 식별자는 모두 UUID 문자열이다.
- 날짜/시간 필드는 FastAPI/Pydantic 기본 `datetime` 직렬화 문자열로 반환된다.
- 보호된 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더가 필요하다.
- `accessToken` 은 HS256 JWT 이며 서버는 DB token_hash 조회 없이 서명, 만료, `sub` 클레임, 사용자 활성 상태를 검증한다.
- 보호된 HTTP 엔드포인트 응답에는 새 JWT가 `X-New-Access-Token` 헤더로 포함될 수 있다. 클라이언트는 이 헤더가 있으면 저장된 access token을 교체해야 한다.
- `refreshToken` 은 JWT가 아닌 opaque random 문자열이며 서버는 원문을 저장하지 않고 SHA-256 해시만 `user_refresh_tokens.token_hash` 에 저장한다.
- FastAPI Swagger UI(`/docs`), ReDoc(`/redoc`), OpenAPI JSON(`/openapi.json`)은 운영 노이즈를 줄이기 위해 비활성화한다.
- 인증 계정 정보는 `AuthenticationDependency`가 검증 후 `request.state.authenticated_account_dto`에 저장한다.
- 워크벤치 리소스는 본인 소유자 또는 관리자만 접근할 수 있다.
- 관리자 API와 시스템 API는 인증 후 서비스 계층에서 관리자 권한을 추가 검증한다.
- 본문과 쿼리의 기본 타입 검증은 FastAPI/Pydantic이 수행하며, 형식 오류는 기본적으로 `422 Unprocessable Entity`를 반환한다.
- 업스트림에서 HTML 오류 페이지를 반환하더라도 API 서버는 그대로 전달하지 않고 항상 JSON 오류 응답으로 변환한다.

## 공통 에러 응답

애플리케이션 예외는 아래 JSON 구조로 응답한다.

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "사용자 메시지",
    "details": {}
  }
}
```

- `error.code`: 애플리케이션 에러 코드
- `error.message`: 사용자에게 전달할 메시지
- `error.details`: 부가 정보 객체, 없으면 빈 객체

## 공통 에러 코드

- `UNAUTHORIZED`: Authorization 헤더 누락, Bearer 형식 오류, JWT 검증 실패, refresh token 검증 실패, 로그인 실패
- `FORBIDDEN`: 비활성 계정 또는 권한 부족
- `NOT_FOUND`: 조회 대상 리소스 없음
- `VALIDATION_ERROR`: 서비스 계층 비즈니스 검증 실패
- `CONFLICT`: 현재 상태에서 허용되지 않는 요청
- `UPSTREAM_LAB_ERROR`: Lab Server 또는 시각화 업스트림 호출 실패
- `UPSTREAM_REQUEST_ERROR`: 처리되지 않은 업스트림 예외의 공통 JSON 응답
- `AUTHENTICATED_ACCOUNT_NOT_IN_REQUEST_CONTEXT`: 인증 컨텍스트 누락

## Enum 값

- 계정 요청 상태: `pending`, `approved`, `rejected`
- 사용자 역할: `user`, `admin`
- 사용자 상태: `active`, `inactive`
- 대화 세션 상태: `active`, `closed`
- 메시지 역할: `user`, `assistant`, `system`
- 시뮬레이션 상태: `draft`, `ready`, `queued`, `running`, `completed`, `failed`
- 실행 작업 상태: `submitted`, `pending`, `running`, `completed`, `failed`, `cancelled`
- 결과 상태: `completed`, `failed`
- 결과 파일 타입: `input`, `output`, `log`, `metadata`, `image`, `other`
- 시각화 상태: `created`, `active`, `closed`, `failed`

## POST /api/v1/account-requests

설명:
공개 사용자가 관리자 승인형 계정 생성 요청을 등록한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- `name` (`string`, required, 2~100자): 신청자 이름
- `userId` (`string`, required, 4~50자): 로그인 아이디
- `password` (`string`, required, 8~100자): 평문 비밀번호
- `organization` (`string`, optional, 최대 150자): 소속 기관
- `purpose` (`string`, required, 2자 이상): 사용 목적

Response `201 Created`:

```json
{
  "requestId": "uuid",
  "name": "홍길동",
  "userId": "hong-user",
  "organization": "Kookmin University",
  "purpose": "PFM simulation study",
  "status": "pending",
  "requestedAt": "2026-04-01T00:00:00Z"
}
```

Response Fields:
- `requestId`: 계정 요청 UUID
- `name`: 신청자 이름
- `userId`: 요청한 로그인 아이디
- `organization`: 소속 기관
- `purpose`: 사용 목적
- `status`: 계정 요청 상태
- `requestedAt`: 요청 생성 시각

Status Code:
- `201`: 계정 요청 생성 성공
- `409`: 이미 동일 로그인 아이디 계정이 있거나 같은 로그인 아이디의 `pending` 요청이 존재함
- `422`: 요청 본문 형식 오류

Error:
- `CONFLICT`: 중복 로그인 아이디 또는 처리 중인 계정 요청 존재

## GET /api/v1/account-requests/me

설명:
로그인 아이디 기준으로 가장 최근 계정 요청 상태를 조회한다.

Request Path:
- 없음

Request Query:
- `userId` (`string`, required): 조회 대상 로그인 아이디

Request Body:
- 없음

Response `200 OK`:

```json
{
  "requestId": "uuid",
  "userId": "hong-user",
  "status": "approved",
  "requestedAt": "2026-04-01T00:00:00Z",
  "reviewedAt": "2026-04-02T00:00:00Z"
}
```

Response Fields:
- `requestId`: 계정 요청 UUID
- `userId`: 요청한 로그인 아이디
- `status`: 최신 요청 상태
- `requestedAt`: 요청 생성 시각
- `reviewedAt`: 관리자 검토 시각, 미검토면 `null`

Status Code:
- `200`: 조회 성공
- `404`: 해당 로그인 아이디의 요청 이력이 없음
- `422`: 쿼리 파라미터 형식 오류

Error:
- `NOT_FOUND`: 계정 요청 정보 없음

## POST /api/v1/auth/login

설명:
승인된 계정으로 로그인하고 JWT access token과 opaque refresh token을 발급한다.

Request Path:
- 없음

- 없음

Request Body:
- `userId` (`string`, required, 4~50자): 로그인 아이디
- `password` (`string`, required, 8~100자): 평문 비밀번호

Response `200 OK`:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "userId": "account-uuid",
    "loginId": "hong-user",
    "name": "홍길동",
    "organization": "Kookmin University",
    "role": "user",
    "status": "active"
  }
}
```

Response Fields:
- `accessToken`: 보호 API 인증 헤더에 사용하는 HS256 JWT. payload에는 `sub`, `loginId`, `role`, `status`, `iat`, `exp`, `jti` 가 포함된다.
- `refreshToken`: access token 재발급에 사용하는 opaque token. DB에는 SHA-256 해시만 저장되며 access token과 다른 값이다.
- `tokenType`: 항상 `Bearer`
- `expiresIn`: access token 만료 초 단위, 기본 `3600`
- `user.userId`: 사용자 계정 UUID
- `user.loginId`: 로그인 아이디
- `user.name`: 사용자 이름
- `user.organization`: 소속 기관
- `user.role`: 사용자 역할
- `user.status`: 사용자 상태

Status Code:
- `200`: 로그인 성공
- `401`: 로그인 아이디 또는 비밀번호 오류
- `403`: 비활성 계정
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 자격 증명 불일치
- `FORBIDDEN`: 비활성 계정

구현 규칙:
- JWT secret은 `JWT_SECRET` 또는 `ACCESS_TOKEN_SECRET` 환경변수에서 읽는다.
- 개발 모드(`DEV=true`)에서 secret이 없으면 fallback secret을 사용하지만 운영 모드에서는 secret 설정이 필수다.
- 비밀번호는 현재 SHA-256 해시를 사용한다. 운영 보안 강화를 위해 추후 bcrypt 또는 argon2 전환이 필요하다.

## POST /api/v1/auth/refresh

설명:
opaque refresh token을 검증하고 refresh token rotation 후 새 JWT access token과 새 refresh token을 발급한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- `refreshToken` (`string`, required): 로그인 또는 직전 refresh 응답에서 받은 opaque refresh token

Response `200 OK`:

```json
{
  "accessToken": "new-jwt-access-token",
  "refreshToken": "new-opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Response Fields:
- `accessToken`: 새 HS256 JWT access token
- `refreshToken`: 새 opaque refresh token. 기존 refresh token은 `revoked_at` 이 설정되어 재사용할 수 없다.
- `tokenType`: 항상 `Bearer`
- `expiresIn`: access token 만료 초 단위

Status Code:
- `200`: 재발급 성공
- `401`: refresh token 누락, 만료, 폐기, 위조, DB 조회 실패 또는 이전 refresh token 재사용
- `403`: 비활성 계정
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: refresh token 검증 실패
- `FORBIDDEN`: 비활성 계정

구현 규칙:
- refresh token 원문은 DB나 로그에 저장하지 않는다.
- 이미 `revoked_at` 이 설정된 refresh token으로 재발급을 요청하면 `401` 을 반환하고, 해당 사용자의 활성 refresh token을 모두 폐기한다.
- 만료된 refresh token 또는 DB에 없는 refresh token은 `401` 을 반환한다.

## POST /api/v1/auth/logout

설명:
현재 인증된 사용자의 클라이언트 로그아웃을 처리하고 전달된 refresh token을 폐기한다.

Request Header:
- `Authorization: Bearer {accessToken}` (`string`, required): JWT access token

Request Body:
- `refreshToken` (`string`, optional): 폐기할 opaque refresh token

Response `200 OK`:

```json
{
  "loggedOut": true,
  "userId": "account-uuid"
}
```

Response Fields:
- `loggedOut`: 클라이언트 로그아웃 처리 여부
- `userId`: 인증된 사용자 계정 UUID

Status Code:
- `200`: 로그아웃 처리 성공
- `401`: access token 인증 실패
- `403`: 비활성 계정

구현 규칙:
- refresh token이 전달되면 해당 token 해시의 `revoked_at` 을 설정한다.
- access token은 JWT라 서버가 즉시 폐기하지 않으며 짧은 만료 시간과 클라이언트 삭제로 처리한다.

## GET /api/v1/auth/me

설명:
현재 인증된 계정의 프로필을 조회한다.

응답 헤더:
- `X-New-Access-Token` (`string`, optional): 요청 시점 기준으로 만료 시간이 연장된 새 JWT access token

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "userId": "account-uuid",
  "loginId": "hong-user",
  "name": "홍길동",
  "organization": "Kookmin University",
  "role": "user",
  "status": "active"
}
```

Response Fields:
- `userId`: 사용자 계정 UUID
- `loginId`: 로그인 아이디
- `name`: 사용자 이름
- `organization`: 소속 기관
- `role`: 사용자 역할
- `status`: 사용자 상태

Status Code:
- `200`: 조회 성공
- `401`: 인증 헤더 또는 JWT 검증 실패
- `403`: 비활성 계정

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 비활성 계정

## GET /api/v1/admin/account-requests

설명:
관리자가 계정 요청 목록을 조회한다.

Request Path:
- 없음

Request Query:
- `status` (`string`, optional): `pending`, `approved`, `rejected` 중 하나
- `page` (`integer`, optional, 기본값 `1`): 페이지 번호
- `size` (`integer`, optional, 기본값 `20`, 최대 `100`): 페이지 크기

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "requestId": "uuid",
      "name": "홍길동",
      "userId": "hong-user",
      "organization": "Kookmin University",
      "purpose": "PFM simulation study",
      "status": "pending",
      "requestedAt": "2026-04-01T00:00:00Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

Response Fields:
- `items[].requestId`: 계정 요청 UUID
- `items[].name`: 신청자 이름
- `items[].userId`: 요청한 로그인 아이디
- `items[].organization`: 소속 기관
- `items[].purpose`: 사용 목적
- `items[].status`: 계정 요청 상태
- `items[].requestedAt`: 요청 생성 시각
- `page`: 현재 페이지
- `size`: 페이지 크기
- `total`: 전체 요청 수

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 비활성 계정
- `403`: 관리자 권한 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 비활성 계정
- `FORBIDDEN`: 관리자 권한 없음

## POST /api/v1/chat-sessions

설명:
인증된 사용자가 새로운 대화 세션을 생성한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- `title` (`string`, required, 1~200자): 세션 제목

Response `201 Created`:

```json
{
  "sessionId": "session-uuid",
  "title": "Al-Si planning",
  "status": "active",
  "linkedSimulationId": null,
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:00:00Z"
}
```

Response Fields:
- `sessionId`: 대화 세션 UUID
- `title`: 세션 제목
- `status`: 세션 상태
- `linkedSimulationId`: 연결된 시뮬레이션 UUID, 없으면 `null`
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

Status Code:
- `201`: 생성 성공
- `401`: 인증 실패
- `403`: 비활성 계정
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 비활성 계정

## GET /api/v1/chat-sessions

설명:
현재 사용자의 대화 세션 목록을 title 검색 조건과 page 기반으로 조회한다.

Request Path:
- 없음

Request Query:
- `title` (`string`, optional): 제목 부분 검색어. 값이 있으면 title 에 해당 문자열이 포함된 세션만 조회한다.
- `page` (`integer`, optional, 기본값 `1`): 페이지 번호
- `size` (`integer`, optional, 기본값 `20`, 최대 `100`): 페이지 크기

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "sessionId": "session-uuid",
      "title": "Al-Si planning",
      "status": "active",
      "linkedSimulationId": "simulation-uuid",
      "createdAt": "2026-04-01T00:00:00Z",
      "updatedAt": "2026-04-01T00:10:00Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

Response Fields:
- `items[].sessionId`: 대화 세션 UUID
- `items[].title`: 세션 제목
- `items[].status`: 세션 상태
- `items[].linkedSimulationId`: 연결된 시뮬레이션 UUID, 없으면 `null`
- `items[].createdAt`: 생성 시각
- `items[].updatedAt`: 마지막 수정 시각
- `page`: 현재 페이지
- `size`: 페이지 크기
- `total`: 전체 대화 세션 수

현재 구현 규칙:
- `title` 검색어는 앞뒤 공백을 제거한 뒤 `chat_sessions.title LIKE %검색어%` 조건으로 조회한다.
- 검색 조건에 맞는 세션이 없으면 오류가 아니라 `items: []`, `total: 0` 을 반환한다.
- 응답 정렬은 `updatedAt DESC`, `createdAt DESC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 비활성 계정
- `422`: 쿼리 파라미터 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 비활성 계정

## GET /api/v1/chat-sessions/{sessionId}

설명:
대화 세션 상세 정보를 조회한다.

Request Path:
- `sessionId` (`string`, required): 대화 세션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "sessionId": "session-uuid",
  "title": "Al-Si planning",
  "status": "active",
  "linkedSimulationId": "simulation-uuid",
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:10:00Z"
}
```

Response Fields:
- `sessionId`: 대화 세션 UUID
- `title`: 세션 제목
- `status`: 세션 상태
- `linkedSimulationId`: 연결된 시뮬레이션 UUID, 없으면 `null`
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 소유자 또는 관리자 아님
- `404`: 세션 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음

## PATCH /api/v1/chat-sessions/{sessionId}

설명:
대화 세션 제목을 변경하고, 세션에 연결된 시뮬레이션 제목도 같은 값으로 함께 변경한다.

Request Path:
- `sessionId` (`string`, required): 대화 세션 UUID

Request Query:
- 없음

Request Body:
- `title` (`string`, required, 1~200자): 변경할 세션 제목

Response `200 OK`:

```json
{
  "sessionId": "session-uuid",
  "title": "Renamed Al-Si planning",
  "status": "active",
  "linkedSimulationId": "simulation-uuid",
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:10:00Z"
}
```

Response Fields:
- `sessionId`: 대화 세션 UUID
- `title`: 변경 후 세션 제목
- `status`: 세션 상태
- `linkedSimulationId`: 연결된 시뮬레이션 UUID, 없으면 `null`
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

현재 구현 규칙:
- 대화 세션 제목이 변경되면 `linkedSimulationId` 로 연결된 시뮬레이션 제목도 같은 값으로 수정한다.
- `linkedSimulationId` 가 없거나 연결된 시뮬레이션을 찾을 수 없으면 `chatSessionId` 기준으로 세션 하위 시뮬레이션을 찾아 제목을 수정한다.
- 연결된 시뮬레이션이 없으면 대화 세션 제목만 변경한다.

Status Code:
- `200`: 수정 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 세션 없음
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음

## DELETE /api/v1/chat-sessions/{sessionId}

설명:
대화 세션과 세션에 종속된 채팅 기록, 시뮬레이션, 실행 작업, 실행 이벤트, 결과, 시각화 로컬 레코드를 물리 삭제한다. 삭제 전 세션에 종속된 각 실행 작업의 랩서버 결과 삭제 API를 호출해 실제 output/result 파일을 먼저 삭제한다.

Request Path:
- `sessionId` (`string`, required): 대화 세션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "sessionId": "session-uuid",
  "status": "deleted",
  "deletedSessionCount": 1,
  "deletedMessageCount": 4,
  "deletedSimulationCount": 1,
  "deletedJobCount": 1,
  "deletedJobEventCount": 2,
  "deletedResultCount": 1,
  "deletedVisualizationCount": 1,
  "labResultDeletions": [
    {
      "jobId": "job-uuid",
      "labJobId": "lab-job-id",
      "status": "deleted",
      "deleted": true,
      "deletedFileCount": 42,
      "deletedBytes": 1048576,
      "message": "Simulation outputs deleted",
      "upstreamResponse": {
        "status": "deleted"
      }
    }
  ]
}
```

Response Fields:
- `sessionId`: 삭제 요청 대상 대화 세션 UUID
- `status`: 삭제 처리 상태, 현재 값 `deleted`
- `deletedSessionCount`: 삭제된 `chat_sessions` 건수
- `deletedMessageCount`: 삭제된 `chat_messages` 건수
- `deletedSimulationCount`: 삭제된 `simulations` 건수
- `deletedJobCount`: 삭제된 `simulation_jobs` 건수
- `deletedJobEventCount`: 삭제된 `simulation_job_events` 건수
- `deletedResultCount`: 삭제된 `simulation_results` 건수
- `deletedVisualizationCount`: 삭제된 `visualizations` 건수
- `labResultDeletions[].jobId`: 로컬 실행 작업 UUID
- `labResultDeletions[].labJobId`: 랩서버 작업 ID, 없으면 `null`
- `labResultDeletions[].status`: 랩서버 결과 삭제 상태. `deleted`, `no_outputs`, `not_found`, `no_lab_job` 중 하나
- `labResultDeletions[].deleted`: 랩서버에서 실제 output 디렉터리를 삭제했는지 여부
- `labResultDeletions[].deletedFileCount`: 랩서버가 보고한 삭제 파일 수
- `labResultDeletions[].deletedBytes`: 랩서버가 보고한 삭제 바이트 수
- `labResultDeletions[].message`: 랩서버 또는 API 서버의 결과 삭제 메시지
- `labResultDeletions[].upstreamResponse`: 랩서버 원본 응답 요약, 없으면 `null`

현재 구현 규칙:
- 삭제 대상 세션은 기존 워크벤치 접근 검증을 통과해야 한다.
- 세션에 종속된 실행 작업 중 `submitted`, `pending`, `running` 상태가 하나라도 있으면 삭제하지 않고 `409` 를 반환한다.
- 랩서버 결과 삭제는 로컬 DB 삭제보다 먼저 수행한다.
- 랩서버 결과 삭제 경로는 최신 Lab Server 계약에 따라 `DELETE /api/v1/users/{userUuid}/simulations/{labJobId}/outputs` 를 우선 사용한다. 배포 차이가 있으면 `DELETE /api/v1/users/{userUuid}/simulations/{labJobId}/results`, `DELETE /api/v1/pfm/users/{userUuid}/simulations/{labJobId}/outputs`, `DELETE /api/v1/pfm/users/{userUuid}/simulations/{labJobId}/results` 를 순서대로 후보로 사용한다.
- 랩서버 작업이 이미 없어서 `404` 를 반환하면 삭제할 원격 결과가 없는 것으로 보고 로컬 삭제를 계속한다.
- 랩서버가 `409` 를 반환하면 로컬 삭제를 수행하지 않고 `409` 를 반환한다.
- 로컬 DB 삭제는 `visualizations -> simulation_results -> simulation_job_events -> simulation_jobs -> simulations -> chat_messages -> chat_sessions` 순서로 한 트랜잭션에서 수행한다.
- 삭제 범위는 `simulations.chat_session_id` 로 대상 세션에 종속된 시뮬레이션이며, 다른 세션이 소유한 재사용 시뮬레이션은 삭제하지 않는다.

Status Code:
- `200`: 삭제 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 세션 없음
- `409`: 실행 중/대기 중 작업이 있거나 랩서버가 결과 삭제 가능 상태가 아니라고 응답한 경우
- `502`: 랩서버 결과 삭제 호출 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음
- `CONFLICT`: 삭제할 수 없는 실행 작업 상태
- `UPSTREAM_LAB_ERROR`: 랩서버 결과 삭제 호출 실패

## GET /api/v1/chat-sessions/{sessionId}/messages

설명:
대화 세션 메시지 목록을 조회한다.

Request Path:
- `sessionId` (`string`, required): 대화 세션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "messageId": "message-uuid",
      "role": "user",
      "content": "cooling rate is 100",
      "createdAt": "2026-04-01T00:00:00Z"
    }
  ]
}
```

Response Fields:
- `items[].messageId`: 메시지 UUID
- `items[].role`: 메시지 역할
- `items[].content`: 메시지 본문
- `items[].createdAt`: 메시지 생성 시각

현재 구현 규칙:
- 응답 정렬은 `messageOrder ASC`, `createdAt ASC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 세션 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음

## POST /api/v1/chat-sessions/{sessionId}/messages

설명:
사용자 메시지를 저장하고, 연결 시뮬레이션이 없으면 자동으로 초안을 보장한 뒤 LLM 보조 응답 생성을 시도한다.

Request Path:
- `sessionId` (`string`, required): 대화 세션 UUID

Request Query:
- 없음

Request Body:
- `content` (`string`, required, 1자 이상): 사용자 메시지 본문

Response `201 Created`:

```json
{
  "userMessage": {
    "messageId": "user-message-uuid",
    "role": "user",
    "content": "cooling rate is 100",
    "createdAt": "2026-04-01T00:00:00Z"
  },
  "assistantMessage": {
    "messageId": "assistant-message-uuid",
    "role": "assistant",
    "content": "파라미터 생성 결과를 시뮬레이션 초안에 반영하지 못했습니다. 응답에 저장 가능한 파라미터 값이 없어 아직 시뮬레이션은 준비되지 않았습니다. 합금 조성, 상, 격자, 냉각 속도 정보를 다시 확인해주세요.",
    "createdAt": "2026-04-01T00:00:01Z"
  },
  "simulationDraft": {
    "simulationId": "simulation-uuid",
    "status": "draft",
    "requiresUserConfirmation": null,
    "backendAction": null,
    "duplicateDecision": null,
    "duplicateSignature": null,
    "draftUpdateAllowed": false,
    "draftUpdateFailureReason": "empty_current_param"
  }
}
```

Response Fields:
- `userMessage.messageId`: 저장된 사용자 메시지 UUID
- `userMessage.role`: 항상 `user`
- `userMessage.content`: 사용자 메시지 본문
- `userMessage.createdAt`: 저장 시각
- `assistantMessage`: 보조 응답 메시지 객체 또는 `null`
- `assistantMessage.messageId`: 보조 응답 메시지 UUID
- `assistantMessage.role`: 항상 `assistant`
- `assistantMessage.content`: 보조 응답 본문
- `assistantMessage.createdAt`: 보조 응답 저장 시각
- `simulationDraft.simulationId`: 세션에 연결된 시뮬레이션 UUID
- `simulationDraft.status`: 연결된 시뮬레이션 상태
- `simulationDraft.requiresUserConfirmation`: 레거시 duplicate workflow 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.backendAction`: 레거시 worker metadata 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.duplicateDecision`: 레거시 duplicate 후속 선택 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.duplicateSignature`: 레거시 중복 실험 signature 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.duplicateCandidateCount`: 레거시 중복 후보 개수 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.matchedSimulationIds`: 레거시 중복 후보 식별자 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.existingExperiment`: 레거시 기존 실험 metadata 호환 필드. 최신 worker public 응답에서는 항상 `null` 이다.
- `simulationDraft.draftUpdateAllowed`: 이번 LLM 응답의 `parameter` 를 시뮬레이션 초안에 반영했거나 반영 가능한지 여부. assistant 응답이 없으면 `null`
- `simulationDraft.draftUpdateFailureReason`: 초안 반영이 차단된 경우의 사유. 예: `missing_current_param`, `empty_current_param`, `simulation_not_editable`

현재 구현 규칙:
- 먼저 사용자 메시지를 저장한다.
- 세션에 연결된 시뮬레이션이 없으면 세션 제목 기준 `draft` 시뮬레이션을 생성한다.
- 연결된 시뮬레이션의 현재 파라미터와 최신 사용자 메시지를 LLM proxy `/llm-api/v1/check-param` 으로 전달한다. API 서버는 pfm-llm-worker 를 직접 호출하지 않는다.
- 최신 사용자 메시지는 `msg`, 대화 세션은 `sessionId` 로 전달하고, `conversationHistory` 에는 최신 사용자 메시지를 제외한 과거 USER/ASSISTANT 메시지만 최근 5턴(최대 10개)까지 시간순으로 전달한다.
- 세션에 레거시 `DUPLICATE_CONFIRMATION` pending action 이 있으면 저장된 원본 JSON 을 다음 proxy 요청 body 의 `duplicatePendingState` 필드로 첨부할 수 있다. 최신 worker public 응답은 새 duplicate pending state 를 반환하지 않는다.
- LLM proxy/worker 최신 public 응답은 `canRun`, `parameter`, `missingFields`, `llmResponse` 네 필드로 고정된다.
- `llmResponse` 는 assistant 메시지 본문으로 저장한다. 레거시 응답 호환을 위해 `llm_msg` 도 읽을 수 있지만 신규 계약에서는 사용하지 않는다.
- `parameter` 는 시뮬레이션 parameter cache 저장 대상이다. `parameter` 가 `null` 이거나 빈 객체이면 초안을 덮어쓰지 않는다.
- `canRun` 은 시뮬레이션 실행 가능 상태 cache 저장 대상이다. `parameter` 가 저장되면 `canRun=true` 인 경우 `ready`, `false` 인 경우 `draft` 상태로 보정한다.
- `missingFields` 는 worker validator 보조 정보다. API 서버는 응답 로그와 내부 판단 보조 정보로 보존할 수 있으며, 영구 저장은 필수가 아니다.
- `parameter` payload 가 worker `SimulationParameter` flat shape(`alloy`, `dimension`, `gridSize`, `mpiPartition`, `coolingRate`, `temperatureRange`, `interfaceWidth`, `nucleationInterval`, `phases`)이면 API 서버는 저장 전에 기존 nested canonical shape 로 정규화한다. 매핑은 `alloy -> alloy_system/composition.solvent`, `dimension -> dimension`, `gridSize -> domain.grid`, `mpiPartition -> domain.mpi`, `coolingRate -> process.cooling_rate`, `temperatureRange.initial -> thermodynamics.temp_max`, `temperatureRange.final -> thermodynamics.temp_min`, `interfaceWidth -> phase_field.interface_width`, `nucleationInterval -> nucleation.check_period`, `phases -> phases` 이다. 기존 nested payload 값은 제거하거나 덮어쓰지 않는다.
- 최신 worker 응답에서는 `task_type`, `validation_status`, `backend_action`, `duplicate_pending_state` 를 public JSON 으로 받지 않는다. 레거시 응답이 들어온 경우에만 기존 호환 로직이 metadata 를 해석한다.
- LLM proxy 호출 실패 시에는 로컬 검증 메시지와 로컬 `missingFields` 를 보조 응답으로 만들며 초안 갱신은 수행하지 않는다.

Status Code:
- `201`: 메시지 저장 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 세션 없음
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음

## POST /api/v1/simulations

설명:
사용자가 시뮬레이션 초안을 생성한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- `title` (`string`, required, 1~200자): 시뮬레이션 제목
- `chatSessionId` (`string`, optional): 연결할 대화 세션 UUID

Response `201 Created`:

```json
{
  "simulationId": "simulation-uuid",
  "title": "Al-Si solidification",
  "status": "draft",
  "chatSessionId": "session-uuid",
  "simulationType": null,
  "alloySystem": null,
  "composition": null,
  "parameters": {},
  "isExecutable": false,
  "missingFields": [
    "simulationType",
    "alloySystem",
    "composition",
    "coolingRate",
    "dimension",
    "gridSize"
  ],
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:00:00Z"
}
```

Response Fields:
- `simulationId`: 시뮬레이션 UUID
- `title`: 시뮬레이션 제목
- `status`: 시뮬레이션 상태
- `chatSessionId`: 연결된 대화 세션 UUID, 없으면 `null`
- `simulationType`: 시뮬레이션 타입
- `alloySystem`: 합금계
- `composition`: 조성 정보 객체
- `parameters`: 세부 파라미터 객체
- `isExecutable`: 실행 가능 여부
- `missingFields`: 실행을 위해 누락된 필드 이름 목록
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

현재 구현 규칙:
- 연결할 세션이 이미 다른 시뮬레이션과 연결돼 있으면 생성할 수 없다.
- 새 시뮬레이션은 항상 `draft` 상태로 시작한다.

Status Code:
- `201`: 생성 성공
- `401`: 인증 실패
- `403`: 세션 접근 권한 없음
- `404`: 연결 대상 세션 없음
- `409`: 세션에 이미 연결된 시뮬레이션이 있음
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 세션 없음
- `CONFLICT`: 이미 연결된 시뮬레이션 존재

## GET /api/v1/simulations

설명:
현재 사용자의 시뮬레이션 목록을 조회한다.

Request Path:
- 없음

Request Query:
- `status` (`string`, optional): `draft`, `ready`, `queued`, `running`, `completed`, `failed`
- `page` (`integer`, optional, 기본값 `1`): 페이지 번호
- `size` (`integer`, optional, 기본값 `20`, 최대 `100`): 페이지 크기

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "simulationId": "simulation-uuid",
      "title": "Al-Si solidification",
      "status": "ready",
      "createdAt": "2026-04-01T00:00:00Z",
      "updatedAt": "2026-04-01T00:10:00Z",
      "lastExecutedAt": null
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

Response Fields:
- `items[].simulationId`: 시뮬레이션 UUID
- `items[].title`: 시뮬레이션 제목
- `items[].status`: 시뮬레이션 상태
- `items[].createdAt`: 생성 시각
- `items[].updatedAt`: 마지막 수정 시각
- `items[].lastExecutedAt`: 마지막 실행 시각, 없으면 `null`
- `page`: 현재 페이지
- `size`: 페이지 크기
- `total`: 전체 시뮬레이션 수

현재 구현 규칙:
- 응답 정렬은 `updatedAt DESC`, `createdAt DESC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패

Error:
- `UNAUTHORIZED`: 인증 실패

## GET /api/v1/simulations/{simulationId}

설명:
시뮬레이션 상세 정보를 조회한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "simulationId": "simulation-uuid",
  "title": "Al-Si solidification",
  "status": "ready",
  "chatSessionId": "session-uuid",
  "simulationType": "equiaxed_solidification",
  "alloySystem": "Al-Si",
  "composition": {
    "Si": 7.0
  },
  "parameters": {
    "coolingRate": 100,
    "dimension": "2D",
    "gridSize": [200, 1, 200]
  },
  "isExecutable": true,
  "missingFields": [],
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:10:00Z"
}
```

Response Fields:
- `simulationId`: 시뮬레이션 UUID
- `title`: 시뮬레이션 제목
- `status`: 시뮬레이션 상태
- `chatSessionId`: 연결된 대화 세션 UUID, 없으면 `null`
- `simulationType`: 시뮬레이션 타입
- `alloySystem`: 합금계
- `composition`: 조성 정보 객체
- `parameters`: 세부 파라미터 객체
- `isExecutable`: 실행 가능 여부
- `missingFields`: 누락 필드 목록
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음

## PATCH /api/v1/simulations/{simulationId}

설명:
시뮬레이션 제목 또는 파라미터를 수정한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- `title` (`string`, optional): 시뮬레이션 제목
- `parameters` (`object`, optional): 수정할 파라미터 맵

현재 구현 규칙:
- `parameters` 에 들어온 값은 기존 파라미터와 merge 된다.
- `parameters.simulationType`, `parameters.alloySystem`, `parameters.composition` 은 응답의 최상위 필드로 분리되어 저장된다.
- 실행 가능 필수 항목은 `simulationType`, `alloySystem`, `composition`, `coolingRate`, `dimension`, `gridSize` 다.
- 누락 필드가 없으면 `status` 는 `ready`, 하나라도 있으면 `draft` 다.
- `queued`, `running` 상태에서는 수정할 수 없다.

Response `200 OK`:

```json
{
  "simulationId": "simulation-uuid",
  "title": "Al-Si solidification 2D",
  "status": "ready",
  "chatSessionId": "session-uuid",
  "simulationType": "equiaxed_solidification",
  "alloySystem": "Al-Si",
  "composition": {
    "Si": 7.0
  },
  "parameters": {
    "coolingRate": 100,
    "dimension": "2D",
    "gridSize": [200, 1, 200]
  },
  "isExecutable": true,
  "missingFields": [],
  "warnings": [
    {
      "field": "parameters.phases[1]",
      "code": "phase_alias_applied",
      "reason": "Aliased phase name normalized to canonical TDB phase FCC_A1.",
      "severity": "info",
      "original": "ALPHA",
      "normalized": "FCC_A1"
    }
  ],
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": "2026-04-01T00:10:00Z"
}
```

Response Fields:
- `simulationId`: 시뮬레이션 UUID
- `title`: 수정 후 제목
- `status`: 수정 후 상태
- `chatSessionId`: 연결된 대화 세션 UUID
- `simulationType`: 최상위 시뮬레이션 타입
- `alloySystem`: 최상위 합금계
- `composition`: 최상위 조성 정보
- `parameters`: 나머지 세부 파라미터
- `isExecutable`: 실행 가능 여부
- `missingFields`: 누락 필드 목록
- `warnings`: PFM payload 전처리기가 자동 보정 또는 권장 위반에 대해 남긴 항목 목록 (`SimulationPayloadPreprocessor`). 빈 배열일 수 있다.
- `createdAt`: 생성 시각
- `updatedAt`: 마지막 수정 시각

현재 구현 규칙 (PFM payload preprocessor):
- 상태가 `ready` 가 되는 시점에 `SimulationPayloadPreprocessor` 가 phase alias / alloySystem-composition / gridSize-mpiPartition / outputGa / liquidus / 일반 sanity 를 검증하고 자동 보정한다.
- 자동 보정된 값은 `parameters` / `composition` 응답에 그대로 반영되고 `warnings` 에 어떤 보정이 일어났는지 기록된다.
- 보정 불가능한 위반(예: `LIQUID:S` 입력, alloySystem-solvent 불일치, `gridSize % mpiPartition != 0`, `interfaceWidth` 가 2~6 밖, `solute` 합 ≥ 0.5)은 `422 VALIDATION_ERROR` 로 거부한다.

Status Code:
- `200`: 수정 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음
- `409`: 수정 불가 상태
- `422`: PFM payload 전처리기가 보정 불가능한 모순을 발견함

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음
- `CONFLICT`: `queued` 또는 `running` 상태 시뮬레이션 수정 시도
- `VALIDATION_ERROR`: PFM payload 전처리기 검증 실패 (matrixPhase 가 phases 에 없음, alloySystem 외 원소가 solutes 에 포함, mpiPartition 이 gridSize 와 나누어떨어지지 않음 등)

## GET /api/v1/simulations/{simulationId}/input-preview

설명:
시뮬레이션 입력 파일 미리보기를 조회한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "simulationId": "simulation-uuid",
  "filename": "AA_PFM_input.TXT",
  "content": "! ---------------------------------------------------------------------------------------\n!   PFM Simulation Input File\n!   Generated for: AlSi_test_12345678\n!   Kunsan National University - ICAPE Lab\n! ---------------------------------------------------------------------------------------\n\n! Elements, phases and grains in the given alloy\n\n     nABT=2\n     AB1=AL\n     AB2=SI\n\n! Initial composition (weight fraction)\n    WF2=0.07\n\n! Phases of interest\n     nPHAT=2\n     PH1=LIQUID:L\n     PH2=FCC_A1\n\n! Initial matrix phase\n   Matrix=PH1\n\n! Grains for each phase\n       NG1=1\n   CYCLE1=1\n       NG2=30\n   CYCLE2=1\n\n   IRAD=1\n...",
  "summary": "Al-Si, 2D, coolingRate=100, grid=[200, 1, 200]",
  "generatedAt": "2026-04-01T00:10:00Z"
}
```

Response Fields:
- `simulationId`: 시뮬레이션 UUID
- `filename`: 생성된 입력 파일명
- `content`: 입력 파일 미리보기 문자열
- `summary`: 입력 요약 문자열
- `generatedAt`: 응답 생성 기준 시각

현재 구현 규칙:
- `isExecutable` 이 `false` 면 조회할 수 없다.
- 미리보기가 비어 있으면 즉시 생성해서 DB에 저장한다.
- 미리보기 내용은 ICAPE PFM 솔버 입력 파일 형식의 `AA_PFM_input.TXT` 본문으로 구성된다.
- 본문에는 원소(`nABT`, `AB*`), 조성(`WF*`), 상(`nPHAT`, `PH*`), 매트릭스 상, 결정립, 공정 조건, 격자/MPI 분할, phase-field/핵생성/열역학/이방성 파라미터가 포함된다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음
- `409`: 아직 실행 가능한 상태가 아님

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음
- `CONFLICT`: 입력 파일 생성 불가 상태

## POST /api/v1/simulations/{simulationId}/jobs

설명:
시뮬레이션 실행 작업 생성을 시도한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- `autoVisualization` (`boolean`, required): 자동 시각화 요청 여부

Response `201 Created`:

```json
{
  "jobId": "job-uuid",
  "simulationId": "simulation-uuid",
  "status": "submitted",
  "submittedAt": "2026-04-01T00:20:00Z",
  "labJobId": "LAB-001",
  "warnings": [
    {
      "field": "parameters.mpiPartition[2]",
      "code": "mpi_axis_partition_corrected",
      "reason": "gridSize[2] is 1, so Z axis cannot be split.",
      "severity": "warning",
      "original": 2,
      "applied": 1
    }
  ],
  "expectedProcessCount": 4
}
```

Response Fields:
- `jobId`: 실행 작업 UUID
- `simulationId`: 대상 시뮬레이션 UUID
- `status`: 생성 직후 작업 상태
- `submittedAt`: 제출 시각
- `labJobId`: 외부 랩서버 작업 식별자, 없으면 `null`
- `warnings`: PFM payload 전처리기가 랩서버 제출 직전에 자동 보정 또는 권장 위반에 대해 남긴 항목 목록. 빈 배열일 수 있다.
- `expectedProcessCount`: 정규화된 `mpiPartition` 의 곱. 랩서버 `mpiexec -n` 값과 일치해야 한다.

현재 구현 규칙:
- 실행 가능한 시뮬레이션만 제출할 수 있다.
- 같은 시뮬레이션에 `submitted`, `pending`, `running` 작업이 이미 있으면 새 작업을 만들 수 없다.
- 제출 전에 내부 시뮬레이션 파라미터를 랩 계약 기준으로 한 번 더 검증한다.
- 제출 전에 입력 미리보기를 보장한다.
- 입력 미리보기 직후 `SimulationPayloadPreprocessor` 가 최종 방어선으로 phase alias, alloy 정합성, gridSize/mpiPartition, outputGa, liquidus, 일반 sanity 를 다시 한 번 검증하고 자동 보정한다. 보정된 `mpiPartition` 으로 lab payload 를 만들기 때문에 `mpiexec -n` 값은 항상 `expectedProcessCount` 와 일치한다.
- 랩서버 명세의 `user_uuid` 는 별도 랩서버 계정 식별자가 아니라 API 서버에서 사용하는 사용자 UUID를 의미한다. 제출 시에는 인증 사용자 UUID를 사용하고, 기존 랩 작업 조회/취소/결과 조회 시에는 해당 작업 소유자 UUID를 사용한다.
- 전처리기에서 보정 불가능한 위반이 발견되면 랩서버 호출 전에 `422 VALIDATION_ERROR` 로 거부한다. 이 경로는 LLM 이 `gridSize=[500,500,1] + mpiPartition=[2,2,2]` 같은 솔버 SIGSEGV 를 유발하는 모순을 만들었을 때 발동한다.
- 랩 Gateway 또는 PFM Service로 실행 제출 요청을 보낸다. Gateway 모드에서는 `lab_server_api_docs.md` 기준으로 `POST /api/v1/users/{userUuid}/simulations` 를 사용하며, 제출 body 에는 `user_uuid` 와 명세에 없는 `output_ga` 를 넣지 않는다.
- 실행 취소는 Gateway 모드에서 `lab_server_api_docs.md` 기준 경로인 `POST /api/v1/users/{userUuid}/simulations/{labJobId}/stop` 을 사용한다.
- 업스트림이 제출을 수락하면 작업과 이벤트가 저장되고 시뮬레이션 상태가 `queued` 로 바뀐다.
- 내부 검증 또는 업스트림 `422` 검증 실패는 `422 VALIDATION_ERROR` 로 변환한다.
- 업스트림 네트워크/권한/서버 오류는 `502 UPSTREAM_LAB_ERROR` 와 함께 운영용 진단 정보가 `details` 에 포함된다.

Status Code:
- `201`: 제출 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음
- `409`: 실행 불가 상태 또는 진행 중 작업 존재
- `422`: 요청 본문 형식 오류 또는 시뮬레이션 파라미터 검증 실패
- `502`: 업스트림 랩서버 연동 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음
- `CONFLICT`: 실행 가능 조건 미충족 또는 활성 작업 존재
- `VALIDATION_ERROR`: 시뮬레이션 파라미터 최소값 또는 범위 검증 실패
- `UPSTREAM_LAB_ERROR`: 랩서버 제출 실패

Response `422 Unprocessable Entity`:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "시뮬레이션 파라미터가 랩서버 검증을 통과하지 못했습니다.",
    "details": {
      "source": "lab-server",
      "upstreamStatus": 422,
      "upstreamErrorType": "http_4xx_response",
      "invalidFields": [
        "nucleationCheckInterval",
        "outputInterval"
      ],
      "validationErrors": [
        {
          "field": "nucleationCheckInterval",
          "sourceField": "nucleation_check_interval",
          "reason": "greater_than_equal",
          "message": "Input should be greater than or equal to 100",
          "actual": 10,
          "minimum": 100
        }
      ]
    }
  }
}
```

Validation Details Fields:
- `source`: 검증 오류를 반환한 시스템, 현재는 `lab-server`
- `upstreamStatus`: 업스트림 HTTP 상태 코드
- `upstreamErrorType`: 내부 업스트림 실패 분류값
- `invalidFields`: 프론트에서 강조 표시할 필드 이름 목록
- `validationErrors[]`: 필드별 상세 검증 오류 목록
- `validationErrors[].field`: 내부 camelCase 기준 필드 이름
- `validationErrors[].sourceField`: 업스트림 원본 snake_case 필드 이름
- `validationErrors[].reason`: 업스트림 검증 타입
- `validationErrors[].message`: 업스트림 검증 메시지
- `validationErrors[].actual`: 실제 전송된 값
- `validationErrors[].minimum` / `maximum`: 업스트림이 제공한 제약값

Response `502 Bad Gateway`:

```json
{
  "error": {
    "code": "UPSTREAM_LAB_ERROR",
    "message": "랩서버 실행 작업 제출에 실패했습니다.",
    "details": {
      "operation": "submit",
      "upstreamStatus": 403,
      "upstreamErrorType": "http_4xx_response",
      "retryable": false,
      "configuredLabServerMode": "lab-gateway-proxy",
      "labApiKeyConfigured": true,
      "cloudflareAccessJwtConfigured": false,
      "cloudflareServiceTokenConfigured": false,
      "browserHeaderBundleConfigured": true,
      "failureCategory": "cloudflare_browser_signature_block",
      "actionGuide": "https://lab.cmsl-kookmin.com 프록시 호출에 필요한 Cloudflare/브라우저 호환 헤더를 설정하세요."
    }
  }
}
```

Error Details Fields:
- `operation`: 실패한 랩 연동 작업 종류, `submit`
- `upstreamStatus`: 업스트림 HTTP 상태 코드, 없으면 `null`
- `upstreamErrorType`: 내부 업스트림 실패 분류값
- `retryable`: 재시도 가능 여부
- `configuredLabServerMode`: 현재 항상 `lab-gateway-proxy`
- `labApiKeyConfigured`: 랩 API Key 헤더 설정 여부
- `cloudflareAccessJwtConfigured`: Cloudflare Access JWT 헤더 설정 여부
- `cloudflareServiceTokenConfigured`: Cloudflare 서비스 토큰 헤더 설정 여부
- `browserHeaderBundleConfigured`: submit/cancel 공통 브라우저 헤더 JSON 묶음 설정 여부
- `failureCategory`: 운영 판단용 상위 실패 분류값
- `actionGuide`: 운영자가 바로 확인할 권장 조치

## GET /api/v1/simulations/{simulationId}/jobs

설명:
시뮬레이션에 연결된 실행 작업 목록을 조회한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "jobId": "job-uuid",
      "status": "submitted",
      "mpiNodes": [],
      "mpiProcesses": null,
      "submittedAt": "2026-04-01T00:20:00Z",
      "startedAt": null,
      "finishedAt": null
    }
  ]
}
```

Response Fields:
- `items[].jobId`: 실행 작업 UUID
- `items[].status`: 작업 상태
- `items[].mpiNodes`: 랩서버 MPI 노드 배정 목록. 예: `["i003:5"]` 는 `i003` 노드에서 MPI 프로세스/CPU 슬롯 5개 사용
- `items[].mpiProcesses`: 랩서버 MPI 프로세스/CPU 슬롯 총 개수, 없으면 `null`
- `items[].submittedAt`: 제출 시각
- `items[].startedAt`: 시작 시각, 없으면 `null`
- `items[].finishedAt`: 종료 시각, 없으면 `null`

현재 구현 규칙:
- `sync=true` 이면 시뮬레이션에 연결된 각 `labJobId` 기준으로 랩서버 HTTP 상태 상세(Gateway `GET /api/v1/users/{userUuid}/simulations/{labJobId}`)를 조회해 실행 상태와 MPI 자원 정보를 동기화한다.
- `sync=false` 이면 랩서버를 호출하지 않고 `simulation_jobs` 캐시만 반환한다.
- 직전 랩서버 상태 상세 동기화에서 `502`, `503`, `504`, `530` 같은 일시 업스트림 실패가 발생한 `jobId` 는 짧은 메모리 쿨다운 동안 재동기화를 건너뛰고 DB 캐시를 반환할 수 있다.
- 결과 메타데이터(output summary/fields/log/basic) 동기화는 비싼 작업이므로 본 엔드포인트에서는 수행하지 않으며, 결과 도메인 엔드포인트(`GET /simulations/{id}/results` 등)에서만 발생한다.
- 응답 정렬은 `submittedAt DESC`, `createdAt DESC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음

## GET /api/v1/jobs/{jobId}

설명:
개별 실행 작업 상세를 조회한다.

Request Path:
- `jobId` (`string`, required): 실행 작업 UUID

Request Query:
- `sync` (`boolean`, optional, default `true`): `true` 이면 응답 전 랩서버 HTTP 상태 상세를 조회해 실행 상태와 MPI 자원 캐시를 갱신한다. `false` 이면 외부 호출 없이 DB 캐시만 반환한다.

Request Body:
- 없음

Response `200 OK`:

```json
{
  "jobId": "job-uuid",
  "simulationId": "simulation-uuid",
  "resultId": "result-uuid",
  "labJobId": "LAB-001",
  "status": "running",
  "progress": 42.5,
  "currentStep": "meshing",
  "mpiNodes": ["i003:5"],
  "mpiProcesses": 5,
  "submittedAt": "2026-04-01T00:20:00Z",
  "startedAt": "2026-04-01T00:21:00Z",
  "finishedAt": null,
  "errorMessage": null
}
```

Response Fields:
- `jobId`: 실행 작업 UUID
- `simulationId`: 대상 시뮬레이션 UUID
- `resultId`: 동기화된 결과 UUID, 결과 레코드가 아직 만들어지지 않았으면 `null`
- `labJobId`: 외부 랩서버 작업 식별자
- `status`: 작업 상태
- `progress`: 진행률 퍼센트, 없으면 `null`
- `currentStep`: 현재 단계 설명, 없으면 `null`
- `mpiNodes`: 랩서버 MPI 노드 배정 목록. 예: `["i003:5"]` 는 `i003` 노드에서 MPI 프로세스/CPU 슬롯 5개 사용
- `mpiProcesses`: 랩서버 MPI 프로세스/CPU 슬롯 총 개수, 없으면 `null`
- `submittedAt`: 제출 시각
- `startedAt`: 시작 시각, 없으면 `null`
- `finishedAt`: 종료 시각, 없으면 `null`
- `errorMessage`: 실패 메시지, 없으면 `null`

현재 구현 규칙:
- `sync=true` 이면 대상 `labJobId` 기준으로 랩서버 HTTP 상태 상세(Gateway `GET /api/v1/users/{userUuid}/simulations/{labJobId}`)를 조회해 실행 상태와 MPI 자원 정보를 동기화한다.
- `sync=false` 이면 랩서버를 호출하지 않고 `simulation_jobs` 캐시만 반환한다.
- 직전 랩서버 상태 상세 동기화에서 `502`, `503`, `504`, `530` 같은 일시 업스트림 실패가 발생한 `jobId` 는 짧은 메모리 쿨다운 동안 재동기화를 건너뛰고 DB 캐시를 반환할 수 있다.
- 결과 메타데이터(output summary/fields/log/basic) 동기화는 본 엔드포인트에서 수행하지 않는다. 결과는 사용자가 결과 도메인 엔드포인트(`GET /results/{resultId}` 등)를 조회할 때 동기화된다.
- `resultId` 는 응답 시점에 `simulation_results` 테이블에서 `job_id` 로 조회한 값이다. 결과 레코드가 아직 만들어지지 않았으면 `null` 이며, 이 경우 클라이언트는 `GET /api/v1/simulations/{simulationId}/results` 또는 `GET /api/v1/results/{resultId}` 호출로 결과 생성을 트리거하고 후속 폴링으로 기다린다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 실행 작업 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 실행 작업 없음

## POST /api/v1/jobs/{jobId}/cancel

설명:
실행 작업 취소를 시도한다.

Request Path:
- `jobId` (`string`, required): 실행 작업 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "jobId": "job-uuid",
  "status": "cancelled",
  "cancelledAt": "2026-04-01T00:25:00Z"
}
```

Response Fields:
- `jobId`: 실행 작업 UUID
- `status`: 취소 후 상태
- `cancelledAt`: 취소 시각

현재 구현 규칙:
- `completed`, `failed`, `cancelled` 상태는 취소할 수 없다.
- `labJobId` 가 없으면 취소를 시도하지 않는다.
- 랩 Gateway 또는 PFM Service로 취소 요청을 보낸다.
- 취소 요청도 실행 제출과 동일한 브라우저 헤더, Cloudflare Access 헤더 구성을 사용한다.
- 취소가 성공하면 lab 결과 메타데이터를 1회 동기화한다. 취소는 사용자 적극 행동이라 폴링 경로가 아니므로 호출당 1회 sync 비용은 허용한다. 부분 결과(부분 timestep 출력, 로그, BASIC.DAT 등)가 lab 에 이미 존재하는 경우를 정확히 감지하기 위함이다.
- 부모 시뮬레이션 상태는 결과 동기화 결과로 만들어진 결과 레코드 기준으로 재계산한다. 결과 레코드가 `completed` 면 시뮬레이션을 `completed`, 그 외(레코드 없음 또는 `failed`)이면 `failed` 로 갱신한다. "취소 ≠ 실패" 의미를 정확히 표현하기 위함이다. 시뮬레이션 상태가 이미 `completed` 또는 `failed` 면 덮어쓰지 않는다.
- 업스트림 취소가 실패하면 `502 UPSTREAM_LAB_ERROR` 와 함께 운영용 진단 정보가 `details` 에 포함된다.

Status Code:
- `200`: 취소 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 실행 작업 없음
- `409`: 취소 불가 상태 또는 랩서버 작업 번호 없음
- `502`: 업스트림 랩서버 취소 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 실행 작업 없음
- `CONFLICT`: 이미 종료된 작업 또는 `labJobId` 없음
- `UPSTREAM_LAB_ERROR`: 랩서버 취소 실패

Response `502 Bad Gateway`:

```json
{
  "error": {
    "code": "UPSTREAM_LAB_ERROR",
    "message": "랩서버 실행 취소에 실패했습니다.",
    "details": {
      "operation": "cancel",
      "upstreamStatus": 403,
      "upstreamErrorType": "http_4xx_response",
      "retryable": false,
      "configuredLabServerMode": "lab-gateway-proxy",
      "labApiKeyConfigured": true,
      "cloudflareAccessJwtConfigured": false,
      "cloudflareServiceTokenConfigured": false,
      "browserHeaderBundleConfigured": true,
      "failureCategory": "cloudflare_browser_signature_block",
      "actionGuide": "https://lab.cmsl-kookmin.com 프록시 호출에 필요한 Cloudflare/브라우저 호환 헤더를 설정하세요."
    }
  }
}
```

Error Details Fields:
- `operation`: 실패한 랩 연동 작업 종류, `cancel`
- 나머지 `details` 필드는 `POST /api/v1/simulations/{simulationId}/jobs` 와 동일하다.

## WS /api/v1/jobs/{jobId}/monitor/ws

설명:
실행 작업에 연결된 랩서버 모니터링 WebSocket을 프록시한다.

인증:
- 브라우저 제약을 고려해 `Authorization: Bearer <accessToken>` 헤더 또는 `accessToken` 쿼리 파라미터를 허용한다.
- `accessToken` 은 JWT access token이며 refresh token은 WebSocket 인증에 사용할 수 없다.

Request Path:
- `jobId` (`string`, required): 실행 작업 UUID

Request Query:
- `accessToken` (`string`, optional): WebSocket 인증용 JWT access token
- `sync` (`boolean`, optional, default `true`): `true` 이면 연결 직전 랩서버 HTTP 상태 상세로 실행 상태와 MPI 자원 캐시를 1회 동기화한다. `false` 이면 사전 동기화 없이 저장된 연결 정보로 프록시를 시작한다.

현재 구현 규칙:
- 연결 직전 `sync=true` 이면 대상 `labJobId` 의 실행 상태와 MPI 자원 정보를 랩서버 HTTP 상태 상세로 1회 동기화한다. 결과 메타데이터는 별도 동기화하지 않는다.
- 직전 랩서버 상태 상세 동기화에서 `502`, `503`, `504`, `530` 같은 일시 업스트림 실패가 발생한 `jobId` 는 짧은 메모리 쿨다운 동안 연결 전 재동기화를 건너뛸 수 있다.
- 연결 시 실행 작업 접근 권한과 `labJobId` 존재 여부를 먼저 검증한다.
- WebSocket은 HTTP 응답 헤더로 access token을 갱신하지 않는다. JWT가 만료되면 클라이언트가 `/auth/refresh` 로 새 access token을 받은 뒤 재연결해야 한다.
- 업스트림 랩 모니터링 WebSocket에는 실행 제출/취소와 동일한 브라우저 헤더, Cloudflare Access 헤더 구성을 사용한다.
- 인증과 접근 권한 검증이 끝나면 클라이언트 WebSocket을 먼저 `accept` 한 뒤 업스트림 연결을 시도한다. 업스트림 실패 시 에러 JSON 을 보낸 후 `1013` 으로 닫는다.
- 업스트림에는 기본 `subscribe` 메시지를 자동 전송하지 않는다. 현재 랩서버는 연결 직후 상태 또는 에러 메시지를 먼저 보내는 동작을 기준으로 프록시한다.
- 업스트림 초기 메시지가 `NOT_FOUND` 이거나 첫 메시지 전 조기 종료되면 `.env` 의 `PFM_LAB_MONITOR_WS_MAX_RETRIES`, `PFM_LAB_MONITOR_WS_RETRY_BACKOFF_MS` 설정값 기준으로 재시도한다.
- 업스트림 텍스트/바이너리 메시지는 가공하지 않고 클라이언트로 그대로 릴레이한다.

Handshake Failure:
- `1008`: 인증 실패, 접근 권한 없음, 실행 작업 없음, `labJobId` 없음
- `1013`: 업스트림 랩 모니터링 WebSocket 연결 실패

업스트림 실패 메시지 예시:

```json
{
  "type": "error",
  "code": "UPSTREAM_NOT_FOUND",
  "message": "Simulation 'bd24ac09' not found"
}
```

업스트림 상태 메시지 예시:

```json
{
  "type": "status",
  "simulation_id": "uuid-string",
  "state": "running"
}
```

## GET /api/v1/jobs/{jobId}/events

설명:
실행 작업 이벤트 목록을 조회한다.

Request Path:
- `jobId` (`string`, required): 실행 작업 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "eventId": "event-uuid",
      "type": "JOB_SUBMITTED",
      "message": "랩서버에 실행 요청이 제출되었습니다.",
      "createdAt": "2026-04-01T00:20:00Z"
    }
  ]
}
```

Response Fields:
- `items[].eventId`: 이벤트 UUID
- `items[].type`: 이벤트 타입 문자열
- `items[].message`: 이벤트 메시지
- `items[].createdAt`: 이벤트 생성 시각

현재 구현 규칙:
- `sync=true` 이고 `labJobId` 가 있으면, 응답 전에 랩서버 HTTP 상태 상세를 조회한다.
- 랩서버 상태와 로컬 상태가 다르면 실행 작업 상태, 최신 진행 payload, MPI 자원 정보, 부모 시뮬레이션 상태를 DB에 반영한다.
- `sync=false` 이면 상태 동기화와 이벤트 생성 없이 기존 이벤트만 조회한다.
- 직전 랩서버 상태 상세 동기화에서 `502`, `503`, `504`, `530` 같은 일시 업스트림 실패가 발생한 `jobId` 는 짧은 메모리 쿨다운 동안 재동기화를 건너뛰므로 새 sync 이벤트가 생성되지 않을 수 있다.
- 결과 메타데이터(output summary/fields/log/basic) 동기화는 본 엔드포인트에서 수행하지 않는다.
- 상태 또는 진행 정보가 바뀌면 동기화 결과를 새 이벤트로 저장한 뒤 목록에 포함한다.
- 응답 정렬은 `createdAt ASC`, `id ASC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 실행 작업 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 실행 작업 없음

## GET /api/v1/simulations/{simulationId}/results

설명:
시뮬레이션 결과 목록을 조회한다.

Request Path:
- `simulationId` (`string`, required): 시뮬레이션 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "resultId": "result-uuid",
      "jobId": "job-uuid",
      "status": "completed",
      "createdAt": "2026-04-01T00:30:00Z"
    }
  ]
}
```

Response Fields:
- `items[].resultId`: 결과 UUID
- `items[].jobId`: 생성 원인 실행 작업 UUID
- `items[].status`: 결과 상태
- `items[].createdAt`: 결과 생성 시각

현재 구현 규칙:
- 응답 전에 시뮬레이션에 연결된 모든 `labJobId` 기준으로 랩서버 상태, 출력 요약, 출력 필드만 읽어 `simulation_results` 를 갱신한다. Lab Gateway 가 `502`, `503`, `504`, `530` 같은 일시 장애를 반환하면 기존 `simulation_results` 캐시를 유지하고 현재 동기화만 건너뛸 수 있다.
- 랩서버 작업이 아직 종료되지 않았더라도 출력 파일이나 로그/BASIC 아티팩트가 발견되면 결과 레코드를 먼저 생성할 수 있다.
- 전체 출력 파일 카탈로그는 결과 목록 조회에서 동기화하지 않는다.
- 응답 정렬은 `createdAt DESC` 이다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시뮬레이션 없음
- `502`: 랩서버 결과 동기화 실패. 단, 일시 업스트림 장애는 기존 결과 캐시가 있으면 캐시 응답으로 흡수될 수 있다.

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시뮬레이션 없음
- `UPSTREAM_REQUEST_ERROR`: 랩서버 결과 동기화 실패

## GET /api/v1/results/{resultId}

설명:
결과 상세 메타데이터와 출력 요약을 경량 조회한다.

Request Path:
- `resultId` (`string`, required): 결과 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "resultId": "result-uuid",
  "simulationId": "simulation-uuid",
  "jobId": "job-uuid",
  "status": "completed",
  "completedAt": "2026-04-01T00:30:00Z",
  "files": [
    {
      "fileId": "file-uuid",
      "name": "AA_PFM_input.TXT",
      "type": "input"
    }
  ],
  "summary": {
    "upstreamState": "running",
    "appDirectory": "/data/jobs/LAB-001",
    "trameSessionId": "viz-a1b2c3",
    "fields": ["FE", "NI", "TEMP", "iP1"],
    "totalFiles": 21800,
    "totalSizeBytes": 3221225472,
    "currentTimestep": 12000,
    "currentTemperature": 875.4,
    "solidFraction": 0.42,
    "walltime": 1830.5,
    "logExcerpt": "...",
    "logTotalLines": 1280,
    "basicExcerpt": "...",
    "basicTotalLines": 460,
    "fileCatalogMode": "live",
    "jobStatus": "completed",
    "simulationStatus": "completed",
    "fieldSummaries": [
      {
        "fieldName": "FE",
        "cachedFileCount": 0,
        "firstTimestep": null,
        "lastTimestep": null,
        "totalSizeBytes": null,
        "catalogStatus": "not_loaded"
      }
    ]
  }
}
```

Response Fields:
- `resultId`: 결과 UUID
- `simulationId`: 대상 시뮬레이션 UUID
- `jobId`: 생성 원인 실행 작업 UUID
- `status`: 결과 상태값(`completed`, `failed`). 출력 파일 또는 로그/BASIC 아티팩트가 하나라도 있으면 `completed` 로 분류되며, 사용자가 cancel 한 실행이라도 부분 결과가 있으면 `completed` 로 표시된다. 실행 종료 여부는 `summary.jobStatus` 로 판단해야 한다.
- `completedAt`: 결과 레코드의 완료 시각. 부분 결과 단계에서는 `null`.
- `files[].fileId`: 결과 파일 식별 토큰. (파일 유형, 필드명, 타임스텝, 파일명) 을 base64url 로 인코딩한 무상태 문자열이며, 다운로드 엔드포인트에서 디코딩되어 사용된다. DB 에 저장되지 않는다. 기본 상세에서는 전체 output 파일을 포함하지 않고 input/log/metadata 같은 비출력 파일만 포함한다.
- `files[].name`: 결과 파일명
- `files[].type`: 결과 파일 타입
- `summary.upstreamState`: 랩서버가 보고한 원시 상태 문자열(예: `running`, `completed`, `failed`, `stopped`, `cancelled`). 정규화되지 않은 값이며 일관된 종료 판단은 `summary.jobStatus` 로 하라.
- `summary.appDirectory`: 랩서버에서 작업이 사용 중인 작업 디렉토리 경로, 없으면 `null`.
- `summary.trameSessionId`: 시각화 접근에 필요한 업스트림 Trame 세션 식별자, 없으면 `null`.
- `summary.fields`: 랩서버 출력 필드 목록.
- `summary.totalFiles`: 랩서버 출력 요약의 전체 파일 수, 없으면 `null`.
- `summary.totalSizeBytes`: 랩서버 출력 요약의 전체 파일 크기(바이트), 없으면 `null`.
- `summary.currentTimestep`: 실행 중 진행 중인 현재 타임스텝, 없으면 `null`.
- `summary.currentTemperature`: 실행 중 현재 온도, 없으면 `null`.
- `summary.solidFraction`: 실행 중 고상 비율, 없으면 `null`.
- `summary.walltime`: 실행이 사용한 누적 wallclock 초, 없으면 `null`.
- `summary.logExcerpt`: 표준 로그 tail 텍스트(최대 200줄), 없으면 `null`.
- `summary.logTotalLines`: 표준 로그 전체 라인 수, 없으면 `null`.
- `summary.basicExcerpt`: BASIC.DAT tail 텍스트, 없으면 `null`.
- `summary.basicTotalLines`: BASIC.DAT 전체 라인 수, 없으면 `null`.
- `summary.fileCatalogMode`: 파일 카탈로그 노출 모드. 현재 항상 `live` 이며, 이는 결과 detail 응답에서는 출력 파일 목록을 같이 내려주지 않고 필드 파일 목록 API 호출 시점에 lab 서버에서 라이브로 가져온다는 의미다. (백엔드는 출력 파일 메타데이터를 DB 에 보관하지 않는다.)
- `summary.jobStatus`: 실행 작업 상태(`submitted`, `pending`, `running`, `completed`, `failed`, `cancelled`). 실제 실행이 끝났는지 판단할 때 가장 신뢰할 수 있는 필드다.
- `summary.simulationStatus`: 부모 시뮬레이션 상태(`draft`, `ready`, `queued`, `running`, `completed`, `failed`).
- `summary.fieldSummaries[].fieldName`: 출력 필드명.
- `summary.fieldSummaries[].cachedFileCount`: 결과 상세 응답에서는 항상 `0` 이다. 라이브 집계는 `GET /results/{resultId}/fields` 또는 필드 파일 목록 호출 시점에 lab 서버에서 합성된다.
- `summary.fieldSummaries[].firstTimestep`: 로컬 DB에 캐시된 해당 필드 첫 타임스텝, 없으면 `null`.
- `summary.fieldSummaries[].lastTimestep`: 로컬 DB에 캐시된 해당 필드 마지막 타임스텝, 없으면 `null`.
- `summary.fieldSummaries[].totalSizeBytes`: 로컬 DB에 캐시된 해당 필드 파일 크기 합계, 없으면 `null`.
- `summary.fieldSummaries[].catalogStatus`: 필드별 카탈로그 상태. 결과 상세 응답에서는 항상 `not_loaded` 이며, 사용자가 필드 목록 또는 필드 파일 목록 엔드포인트를 호출하면 `live` 로 갱신된 응답을 받는다.

현재 구현 규칙:
- 응답 전에 대상 결과에 연결된 `labJobId` 기준으로 랩서버 상태, 출력 요약, 출력 필드, 로그/BASIC 요약만 다시 읽어 `simulation_results` 를 갱신한다. Lab Gateway 가 일시 장애를 반환하면 기존 결과 캐시를 보존하고 캐시 기준 상세를 반환할 수 있다.
- 기본 상세 조회는 `/outputs/{field}` 를 호출하지 않으며 전체 output 파일 카탈로그를 동기화하지 않는다.
- `files` 배열에는 전체 output 파일 목록을 포함하지 않는다. output 파일 목록은 `GET /api/v1/results/{resultId}/fields/{fieldName}/files` 에서 lazy 조회한다.
- 로컬 DB에 이미 캐시된 output 파일이 있으면 `summary.fieldSummaries` 에 캐시된 개수와 first/last timestep 만 집계해서 포함한다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 결과 없음
- `502`: 랩서버 결과 동기화 실패. 단, 일시 업스트림 장애는 기존 결과 캐시가 있으면 캐시 응답으로 흡수될 수 있다.

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 결과 없음
- `UPSTREAM_REQUEST_ERROR`: 랩서버 결과 동기화 실패

## GET /api/v1/results/{resultId}/fields

설명:
결과 출력 필드 목록과 로컬 DB에 캐시된 필드별 파일 집계를 조회한다.

Request Path:
- `resultId` (`string`, required): 결과 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "resultId": "result-uuid",
  "fields": ["FE", "NI", "TEMP", "iP1"],
  "items": [
    {
      "fieldName": "FE",
      "cachedFileCount": 0,
      "firstTimestep": null,
      "lastTimestep": null,
      "totalSizeBytes": null,
      "catalogStatus": "not_loaded"
    }
  ],
  "totalFiles": 21800,
  "totalSizeBytes": 3221225472,
  "catalogStatus": "live"
}
```

Response Fields:
- `resultId`: 결과 UUID
- `fields[]`: 출력 필드명 목록
- `items[].fieldName`: 출력 필드명
- `items[].cachedFileCount`: 해당 필드의 lab 라이브 파일 수. lab 호출이 비어 있으면 `0`.
- `items[].firstTimestep`: 해당 필드의 첫 타임스텝, 파일이 없으면 `null`
- `items[].lastTimestep`: 해당 필드의 마지막 타임스텝, 파일이 없으면 `null`
- `items[].totalSizeBytes`: 해당 필드 파일 크기 합계, 정보가 없으면 `null`
- `items[].catalogStatus`: 필드별 카탈로그 상태. 파일이 있으면 `live`, lab 응답이 비면 `not_loaded`.
- `totalFiles`: 랩서버 출력 요약의 전체 파일 수
- `totalSizeBytes`: 랩서버 출력 요약의 전체 파일 크기
- `catalogStatus`: 응답 전체의 카탈로그 노출 모드. 현재 항상 `live` 이며, 본 엔드포인트는 출력 필드별 파일 목록을 lab 서버에서 라이브로 가져와 집계한다는 의미다. 결과 파일 메타데이터는 DB 에 보관되지 않는다.

현재 구현 규칙:
- 출력 필드별 집계(파일 수, 첫/마지막 타임스텝, 총 크기) 는 매 호출마다 lab 서버에서 라이브로 합산한다. 비용이 큰 작업이므로 호출자는 결과 화면 진입 시 1 회만 호출하는 것을 권장한다. Lab Gateway 일시 장애 시 필드별 라이브 파일 목록은 빈 목록으로 대체될 수 있다.
- 결과 상태/요약/필드 목록 동기화는 simulation_results 테이블에 저장한다(완료/실패 분류 목적).
- output 파일 메타데이터는 DB 에 저장하지 않는다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 결과 없음
- `502`: 랩서버 결과 동기화 실패. 단, 일시 업스트림 장애는 기존 결과 캐시가 있으면 캐시 응답으로 흡수될 수 있다.

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 결과 없음
- `UPSTREAM_REQUEST_ERROR`: 랩서버 결과 동기화 실패

## GET /api/v1/results/{resultId}/fields/{fieldName}/files

설명:
특정 출력 필드의 파일 목록만 페이지 단위로 조회한다.

Request Path:
- `resultId` (`string`, required): 결과 UUID
- `fieldName` (`string`, required): 출력 필드명

Request Query:
- `page` (`integer`, optional, 기본값 `1`, 1 이상): 페이지 번호
- `size` (`integer`, optional, 기본값 `100`, 1~100): 페이지 크기
- `timestep` (`integer`, optional): 특정 타임스텝만 조회
- `from_timestep` 또는 `fromTimestep` (`integer`, optional): 조회 시작 타임스텝
- `to_timestep` 또는 `toTimestep` (`integer`, optional): 조회 종료 타임스텝
- `refresh` (`boolean`, optional, 기본값 `false`): 캐시가 있어도 해당 필드 파일 목록을 다시 동기화할지 여부

Request Body:
- 없음

Response `200 OK`:

```json
{
  "resultId": "result-uuid",
  "fieldName": "FE",
  "items": [
    {
      "fileId": "T1VUUFVUfEZFfDEwMDAwfEZFXzAwMDAxLnB2dHI",
      "name": "FE_00001.pvtr",
      "type": "output",
      "fieldName": "FE",
      "timestep": 10000,
      "sizeBytes": 524288
    }
  ],
  "page": 1,
  "size": 100,
  "total": 5450,
  "hasNext": true,
  "catalogStatus": "live"
}
```

Response Fields:
- `resultId`: 결과 UUID
- `fieldName`: 출력 필드명
- `items[].fileId`: 결과 파일 식별 토큰. (`OUTPUT`, 필드명, 타임스텝, 파일명) 을 base64url 로 인코딩한 무상태 문자열이다. DB 에 저장되지 않으며 다운로드 엔드포인트에서 그대로 디코딩된다.
- `items[].name`: 출력 파일명
- `items[].type`: 항상 `output`
- `items[].fieldName`: 출력 필드명
- `items[].timestep`: 출력 파일 타임스텝
- `items[].sizeBytes`: 출력 파일 크기, 없으면 `null`
- `page`: 현재 페이지
- `size`: 페이지 크기
- `total`: 조건(필드, 타임스텝 범위)에 맞는 lab 라이브 출력 파일 수.
- `hasNext`: 다음 페이지 존재 여부 `(page * size) < total`.
- `catalogStatus`: 응답 카탈로그 모드. 현재 항상 `live` 이며, 본 엔드포인트는 매 호출마다 lab 서버에서 파일 목록을 가져온다.

현재 구현 규칙:
- lab-server 명세는 `/outputs/{field}` 에서 `page/size` pagination 을 지원하지 않고 `timestep` 단건 필터만 지원한다.
- 본 엔드포인트는 호출될 때마다 lab 에서 해당 필드의 파일 목록을 1 회 받아와 from/to 타임스텝 필터를 적용하고 메모리에서 page/size 슬라이스를 반환한다.
- 결과 파일 메타데이터는 DB 에 저장하지 않으므로 lab 응답이 곧 응답 데이터의 단일 출처이다. Lab Gateway 일시 장애 시 현재 호출의 파일 목록은 빈 목록으로 응답될 수 있으며, 다음 호출에서 다시 라이브 조회한다.
- 호환성을 위해 `refresh` 쿼리 파라미터는 유지되지만, 캐시가 없으므로 `true`/`false` 어떤 값이든 동작 차이는 없다.
- `size` 는 최대 `100` 으로 제한한다.

Status Code:
- `200`: 조회 성공
- `400`: 타임스텝 범위 오류
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 결과 없음 또는 출력 필드 없음
- `502`: 랩서버 결과 동기화 실패. 단, 일시 업스트림 장애는 기존 결과 캐시가 있으면 캐시 응답으로 흡수될 수 있다.

Error:
- `VALIDATION_ERROR`: `from_timestep` 이 `to_timestep` 보다 큰 경우
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 결과 또는 출력 필드 없음
- `UPSTREAM_REQUEST_ERROR`: 랩서버 파일 목록 동기화 실패

## GET /api/v1/results/{resultId}/files/{fileId}/download

설명:
결과 파일 다운로드 응답을 반환한다.

Request Path:
- `resultId` (`string`, required): 결과 UUID
- `fileId` (`string`, required): 결과 파일 식별 토큰. 결과 상세/필드 파일 목록 응답의 `fileId` 값을 그대로 전달한다.

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:
- JSON 본문이 아니라 attachment 응답으로 반환한다.
- 응답 파일명은 `fileId` 토큰에 인코딩된 파일명을 사용한다.
- 응답 미디어 타입은 파일 유형에 따라 결정된다 (output: lab 응답 Content-Type 또는 `application/xml`, log/BASIC: `text/plain; charset=utf-8`, input: `text/plain` 또는 `application/octet-stream`).
- 응답 본문은 파일 바이너리다.

현재 구현 규칙:
- 먼저 결과 리소스 접근 권한을 검증한다.
- `fileId` 를 디코딩해 (`INPUT`/`OUTPUT`/`LOG`/`METADATA`, 필드명, 타임스텝, 파일명) 을 복원한다. 디코딩에 실패하면 404 를 반환한다.
- INPUT 파일은 시뮬레이션 엔터티의 `latest_input_content` 또는 `latest_input_path` 를 그대로 응답한다.
- OUTPUT 파일은 lab 서버의 download 엔드포인트를 프록시해 응답한다.
- LOG 파일은 lab 서버 텍스트 엔드포인트(`/log`) 를 프록시한다.
- BASIC.DAT 는 lab 서버 텍스트 엔드포인트(`/basic`) 를 프록시한다.
- 결과 파일 메타데이터를 DB 에 보관하지 않으므로 본 엔드포인트는 lab 서버 sync 를 수행하지 않는다.

Status Code:
- `200`: 파일 다운로드 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 결과 없음, 파일 메타데이터 없음, 또는 실제 파일 경로 없음
- `502`: 랩서버 결과 동기화 또는 원격 다운로드 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 결과 또는 파일 없음
- `UPSTREAM_REQUEST_ERROR`: 랩서버 결과 동기화 또는 원격 다운로드 실패

## POST /api/v1/results/{resultId}/visualizations

설명:
결과 기반 시각화 세션 생성을 시도한다.

Request Path:
- `resultId` (`string`, required): 결과 UUID

Request Query:
- 없음

Request Body:
- `field` (`string`, required, 1~100자): 초기 시각화 필드명
- `colormap` (`string`, required, 1~50자): 컬러맵 이름
- `viewAngle` (`string`, required, 1~20자): 시점 이름

Response `201 Created`:

```json
{
  "visualizationId": "visualization-uuid",
  "resultId": "result-uuid",
  "status": "created",
  "viewerUrl": "https://lab.cmsl-kookmin.com/api/v1/trame/viewer/viz-a1b2c3",
  "websocketUrl": "wss://lab.cmsl-kookmin.com/trame-app/viz-a1b2c3",
  "createdAt": "2026-04-01T00:40:00Z"
}
```

Response Fields:
- `visualizationId`: 시각화 UUID
- `resultId`: 대상 결과 UUID
- `status`: 생성 직후 시각화 상태
- `viewerUrl`: 외부 뷰어 URL, 없으면 `null`
- `websocketUrl`: 외부 제어 WebSocket URL, 없으면 `null`
- `createdAt`: 생성 시각

현재 구현 규칙:
- 먼저 결과 접근 권한을 검증한다.
- Trame Service 세션 생성 API를 호출할 때 결과 소유자 UUID를 `user_uuid` body 필드로 전달한다.
- Trame Service 세션 생성 body 의 `simulation_id` 는 API 서버 시뮬레이션 UUID나 `lab_simulation_name` 이 아니라 실행 작업의 `labJobId` 를 전달한다.
- Trame Service 세션 생성 body 는 `user_uuid`, `simulation_id`, `config.field`, `config.colormap`, `config.view_angle` 만 포함하며, Lab Server 최신 계약에 따라 `live` 필드는 전달하지 않는다.
- 게이트웨이가 응답을 돌려주면 viewer URL, websocket URL, 필드 목록, 타임스텝 정보를 저장한다. Trame 상세 조회 응답의 `timestep` 필드는 내부 응답의 `currentTimestep` 으로 변환한다.

Status Code:
- `201`: 생성 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 결과 없음
- `422`: 요청 본문 형식 오류
- `502`: 업스트림 시각화 생성 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 결과 없음
- `UPSTREAM_LAB_ERROR`: 시각화 생성 실패

## GET /api/v1/visualizations/{visualizationId}

설명:
시각화 세션 상세를 조회한다.

Request Path:
- `visualizationId` (`string`, required): 시각화 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "visualizationId": "visualization-uuid",
  "resultId": "result-uuid",
  "status": "active",
  "viewerUrl": "https://lab.cmsl-kookmin.com/api/v1/trame/viewer/viz-a1b2c3",
  "websocketUrl": "wss://lab.cmsl-kookmin.com/trame-app/viz-a1b2c3",
  "fieldsAvailable": ["phase", "temperature"],
  "currentTimestep": 12,
  "totalTimesteps": 200
}
```

Response Fields:
- `visualizationId`: 시각화 UUID
- `resultId`: 대상 결과 UUID
- `status`: 시각화 상태
- `viewerUrl`: 외부 뷰어 URL
- `websocketUrl`: 외부 제어 WebSocket URL
- `fieldsAvailable`: 사용 가능한 필드명 목록
- `currentTimestep`: 현재 타임스텝, 없으면 `null`
- `totalTimesteps`: 전체 타임스텝, 없으면 `null`

현재 구현 규칙:
- 먼저 시각화 접근 권한과 연결된 결과 접근 권한을 검증한다.
- 조회 시점에 업스트림 Trame 세션 상태를 다시 읽을 수 있으면 viewer URL, websocket URL, 필드 목록, 타임스텝 메타데이터를 로컬 DB에 갱신한 뒤 응답한다. 이때 시각화에 사용하는 `simulation_id` 컨텍스트는 결과에 연결된 실행 작업의 `labJobId` 다.
- 로컬 시각화 상태가 이미 `closed`, `failed` 이면 업스트림 재동기화 없이 저장된 메타데이터를 그대로 반환한다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시각화 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시각화 없음

## PATCH /api/v1/visualizations/{visualizationId}

설명:
시각화 세션 제어를 요청한다.

Request Path:
- `visualizationId` (`string`, required): 시각화 UUID

Request Query:
- 없음

Request Body:
- `field` (`string`, optional): 변경할 필드명
- `colormap` (`string`, optional): 변경할 컬러맵
- `viewAngle` (`string`, optional): 변경할 시점
- `timestep` (`integer`, optional): 변경할 타임스텝
- `deltaAzimuth` (`number`, optional): 카메라 azimuth 변경량
- `deltaElevation` (`number`, optional): 카메라 elevation 변경량
- `zoom` (`number`, optional): 카메라 zoom 배율
- `panX` (`number`, optional): 카메라 x축 pan 변경량
- `panY` (`number`, optional): 카메라 y축 pan 변경량
- `reset` (`boolean`, optional): 카메라 초기화 여부

Response `200 OK`:

```json
{
  "visualizationId": "visualization-uuid",
  "status": "active",
  "updated": {
    "field": "temperature",
    "colormap": "viridis",
    "viewAngle": "xz",
    "timestep": 120
  }
}
```

Response Fields:
- `visualizationId`: 시각화 UUID
- `status`: 업데이트 후 시각화 상태, 성공 시 `active`
- `updated`: 업스트림에 전달한 변경 payload

현재 구현 규칙:
- 요청 본문에서 값이 존재하는 필드만 `updated` 와 업스트림 payload 에 포함된다.
- `field`, `colormap`, `timestep` 은 Trame 통합 control 엔드포인트로 전달한다.
- `deltaAzimuth`, `deltaElevation`, `zoom`, `panX`, `panY`, `reset` 은 Trame camera 엔드포인트로 전달한다.
- `viewAngle` 은 생성 시 config 로 전달되는 호환 필드이며, 새 랩서버 명세에는 수정용 view 엔드포인트가 없어 별도 업스트림 호출을 만들지 않는다.
- 성공 시 저장된 시각화 메타데이터도 함께 갱신한다.

Status Code:
- `200`: 제어 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시각화 없음
- `502`: 업스트림 시각화 제어 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시각화 없음
- `UPSTREAM_LAB_ERROR`: 시각화 제어 실패

## DELETE /api/v1/visualizations/{visualizationId}

설명:
시각화 세션 종료를 요청한다.

Request Path:
- `visualizationId` (`string`, required): 시각화 UUID

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "visualizationId": "visualization-uuid",
  "status": "closed",
  "closedAt": "2026-04-01T00:50:00Z"
}
```

Response Fields:
- `visualizationId`: 시각화 UUID
- `status`: 종료 후 상태
- `closedAt`: 종료 시각

현재 구현 규칙:
- Trame Service 세션 종료 API를 호출한다.
- 성공 시 시각화 상태를 `closed` 로 저장한다.

Status Code:
- `200`: 종료 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시각화 없음
- `502`: 업스트림 시각화 종료 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시각화 없음
- `UPSTREAM_LAB_ERROR`: 시각화 종료 실패

## GET /api/v1/visualizations/{visualizationId}/screenshot

설명:
시각화 스크린샷 생성을 시도한다.

Request Path:
- `visualizationId` (`string`, required): 시각화 UUID

Request Query:
- `width` (`integer`, optional): 요청 너비
- `height` (`integer`, optional): 요청 높이
- `format` (`string`, optional): 요청 포맷

Request Body:
- 없음

Response `200 OK`:
- JSON 본문이 아니라 PNG 바이너리 응답으로 반환한다.
- 응답 `Content-Type` 은 업스트림 시각화 서버가 반환한 값을 그대로 사용한다.

현재 구현 규칙:
- 먼저 시각화 접근 가능 여부를 검증한다.
- `width`, `height` 는 Trame 스크린샷 API로 그대로 전달한다.
- `format` 은 현재 `png` 만 허용한다.
- 업스트림 시각화 서버가 PNG 바이너리를 반환하면 그대로 응답한다.

Status Code:
- `200`: 스크린샷 생성 성공
- `401`: 인증 실패
- `403`: 접근 권한 없음
- `404`: 시각화 없음
- `422`: 지원하지 않는 스크린샷 포맷
- `502`: 스크린샷 생성 실패

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 접근 권한 없음
- `NOT_FOUND`: 시각화 없음
- `VALIDATION_ERROR`: `format` 이 `png` 가 아님
- `UPSTREAM_LAB_ERROR`: 스크린샷 생성 실패

## WS /api/v1/visualizations/{visualizationId}/ws

설명:
시각화 WebSocket 연결을 업스트림 시각화 WebSocket으로 프록시한다.

Request Path:
- `visualizationId` (`string`, required): 시각화 UUID

Request Query:
- `accessToken` (`string`, optional): 브라우저 WebSocket 클라이언트에서 `Authorization` 헤더를 보낼 수 없을 때 사용할 JWT access token

Request Body:
- 없음

현재 구현 규칙:
- `Authorization: Bearer {accessToken}` 헤더 또는 `accessToken` 쿼리로 먼저 인증한다.
- WebSocket은 HTTP 응답 헤더로 access token을 갱신하지 않는다. JWT가 만료되면 클라이언트가 `/auth/refresh` 로 새 access token을 받은 뒤 재연결해야 한다.
- 인증된 사용자 기준으로 해당 시각화 리소스 접근 권한을 먼저 검증한다.
- 인증과 권한 검증이 끝난 뒤 연결을 수락하고 저장된 `websocketUrl` 로 업스트림 WebSocket 연결을 시도한다.
- 시각화 WebSocket 로그와 업스트림 컨텍스트의 `simulationId` 는 결과에 연결된 실행 작업의 `labJobId` 를 사용한다.
- 업스트림 시각화 WebSocket 연결에는 Trame REST 호출과 동일한 API Key, Cloudflare Access, 브라우저 호환 헤더 묶음을 전달한다.
- 저장된 `websocketUrl` 은 항상 `https://lab.cmsl-kookmin.com` 기반 Lab Gateway interactive app relay 경로(`/trame-app/{visualizationId}`)로 생성된다. `PFM_TRAME_SERVER_URL` 이 비어 있거나 다른 주소를 가리켜도 공개 Lab Gateway URL 로 보정된다.
- 브라우저와 업스트림 사이 메시지를 양방향으로 그대로 릴레이한다.
- 업스트림 연결이 불가능하면 close code `1013` 으로 종료한다.

Status Code:
- 인증과 권한 검증을 통과하면 WebSocket handshake 성공 후 프록시 시작, 업스트림 연결 실패 시 close code `1013`
- 인증 실패 또는 접근 권한이 없으면 연결을 거부한다.

Error:
- 별도 애플리케이션 에러 JSON 응답 없음

## GET /health

설명:
인증 없이 컨테이너 liveness 확인에 사용할 API 서버 최소 상태를 조회한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "status": "ok",
  "services": {
    "api": "up"
  },
  "timestamp": "2026-04-01T00:00:00Z"
}
```

Response Fields:
- `status`: API 서버 liveness 상태, 정상 응답이면 `ok`
- `services.api`: API 서버 프로세스 상태, 정상 응답이면 `up`
- `timestamp`: 응답 생성 시각

Status Code:
- `200`: 조회 성공

Error:
- 없음

## GET /api/v1/system/health

설명:
인증 후 관리자 권한이 필요한 시스템 헬스 상태를 조회한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "status": "degraded",
  "services": {
    "api": "up",
    "ai": "down",
    "lab": "down",
    "storage": "up"
  },
  "timestamp": "2026-04-01T00:00:00Z"
}
```

Response Fields:
- `status`: 전체 상태 요약, 현재 고정값 `degraded`
- `services.api`: API 서버 상태, 현재 `up`
- `services.ai`: AI 연동 상태, 현재 `down`
- `services.lab`: Lab 연동 상태, 현재 `down`
- `services.storage`: 저장소 상태, 현재 `up`
- `timestamp`: 응답 생성 시각

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 관리자 권한 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음

## GET /api/v1/system/ready

설명:
인증 후 관리자 권한이 필요한 시스템 준비 상태를 조회한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "ready": false,
  "services": {
    "ai": false,
    "lab": false,
    "storage": true
  }
}
```

Response Fields:
- `ready`: 전체 준비 여부, 현재 `false`
- `services.ai`: AI 연동 준비 여부, 현재 `false`
- `services.lab`: Lab 연동 준비 여부, 현재 `false`
- `services.storage`: 저장소 준비 여부, 현재 `true`

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 관리자 권한 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음

## PATCH /api/v1/admin/account-requests/{requestId}

설명:
관리자가 계정 요청을 승인 또는 거절한다.

Request Path:
- `requestId` (`string`, required): 계정 요청 UUID

Request Query:
- 없음

Request Body:
- `action` (`string`, required): `approve` 또는 `reject`
- `role` (`string`, optional): 승인 시 생성할 역할, `user` 또는 `admin`, 비어 있으면 `user`
- `reason` (`string`, optional): 거절 사유

Response `200 OK`:

```json
{
  "requestId": "uuid",
  "status": "approved",
  "reviewedAt": "2026-04-02T00:00:00Z",
  "reviewedBy": "admin-account-uuid",
  "createdUserId": "created-account-uuid"
}
```

Response Fields:
- `requestId`: 계정 요청 UUID
- `status`: 처리 후 상태
- `reviewedAt`: 검토 완료 시각
- `reviewedBy`: 검토 관리자 계정 UUID
- `createdUserId`: 승인 시 생성된 사용자 계정 UUID, 거절이면 `null`

Status Code:
- `200`: 처리 성공
- `401`: 인증 실패
- `403`: 관리자 권한 없음
- `404`: 계정 요청 없음
- `409`: 이미 처리된 요청
- `422`: 요청 본문 형식 오류

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음
- `NOT_FOUND`: 계정 요청 없음
- `CONFLICT`: 이미 처리된 요청

## GET /api/v1/admin/users

설명:
관리자가 전체 사용자 목록을 조회한다.

Request Path:
- 없음

Request Query:
- 없음

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "userId": "account-uuid",
      "loginId": "hong-user",
      "name": "홍길동",
      "organization": "Kookmin University",
      "role": "user",
      "status": "active",
      "createdAt": "2026-04-01T00:00:00Z"
    }
  ]
}
```

Response Fields:
- `items[].userId`: 사용자 계정 UUID
- `items[].loginId`: 로그인 아이디
- `items[].name`: 사용자 이름
- `items[].organization`: 소속 기관
- `items[].role`: 사용자 역할
- `items[].status`: 사용자 상태
- `items[].createdAt`: 계정 생성 시각

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 관리자 권한 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음

## PATCH /api/v1/admin/users/{userId}

설명:
관리자가 사용자 역할 또는 상태를 수정한다.

Request Path:
- `userId` (`string`, required): 사용자 계정 UUID

Request Query:
- 없음

Request Body:
- `status` (`string`, optional): `active` 또는 `inactive`
- `role` (`string`, optional): `user` 또는 `admin`

현재 구현 규칙:
- `status` 와 `role` 중 하나 이상은 반드시 필요하다.

Response `200 OK`:

```json
{
  "userId": "account-uuid",
  "role": "admin",
  "status": "active",
  "updatedAt": "2026-04-02T00:00:00Z"
}
```

Response Fields:
- `userId`: 사용자 계정 UUID
- `role`: 수정 후 역할
- `status`: 수정 후 상태
- `updatedAt`: 수정 시각

Status Code:
- `200`: 수정 성공
- `400`: 수정할 값이 없음
- `401`: 인증 실패
- `403`: 관리자 권한 없음
- `404`: 사용자 없음
- `422`: 요청 본문 형식 오류

Error:
- `VALIDATION_ERROR`: `status` 와 `role` 이 모두 없음
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음
- `NOT_FOUND`: 사용자 없음

## GET /api/v1/admin/simulations

설명:
관리자가 전체 시뮬레이션 목록을 조회한다.

Request Path:
- 없음

Request Query:
- `status` (`string`, optional): `draft`, `ready`, `queued`, `running`, `completed`, `failed`
- `userId` (`string`, optional): 소유자 계정 UUID
- `page` (`integer`, optional, 기본값 `1`): 페이지 번호
- `size` (`integer`, optional, 기본값 `20`, 최대 `100`): 페이지 크기

Request Body:
- 없음

Response `200 OK`:

```json
{
  "items": [
    {
      "simulationId": "simulation-uuid",
      "title": "Al-Si solidification",
      "owner": {
        "userId": "account-uuid",
        "name": "홍길동"
      },
      "status": "draft",
      "createdAt": "2026-04-01T00:00:00Z",
      "lastExecutedAt": null
    }
  ]
}
```

Response Fields:
- `items[].simulationId`: 시뮬레이션 UUID
- `items[].title`: 시뮬레이션 제목
- `items[].owner.userId`: 소유자 계정 UUID
- `items[].owner.name`: 소유자 이름
- `items[].status`: 시뮬레이션 상태
- `items[].createdAt`: 생성 시각
- `items[].lastExecutedAt`: 마지막 실행 시각, 없으면 `null`

현재 구현 규칙:
- 쿼리 파라미터로 `page`, `size` 를 받지만 응답에는 페이지 메타데이터를 포함하지 않는다.

Status Code:
- `200`: 조회 성공
- `401`: 인증 실패
- `403`: 관리자 권한 없음

Error:
- `UNAUTHORIZED`: 인증 실패
- `FORBIDDEN`: 관리자 권한 없음