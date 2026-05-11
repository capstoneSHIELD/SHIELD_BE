-- 카카오 OAuth 로그인 도입
--
-- users.kakao_id 컬럼 추가 (Kakao 계정 식별자 저장)
-- - NULLABLE: Google/Naver 사용자는 null
-- - UNIQUE: 동일 Kakao ID 로 두 user 생성 방지
-- - 카카오 user id 는 Long 이지만 provider 간 일관성을 위해 VARCHAR 로 저장
--   (Google/Naver 도 String, 코드의 OAuthUserInfo.providerId 가 String)

ALTER TABLE users
    ADD COLUMN kakao_id VARCHAR(255) UNIQUE;

-- 인덱스는 UNIQUE 제약과 함께 자동 생성됨
