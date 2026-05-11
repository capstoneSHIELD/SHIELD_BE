-- Issue #83: 네이버 OAuth 로그인 도입 (V11 — V10 은 PR #82 동시성 보호용)
--
-- users.naver_id 컬럼 추가 (Naver 계정 식별자 저장)
-- - NULLABLE: 기존 Google 사용자는 null
-- - UNIQUE: 동일 Naver ID 로 두 user 생성 방지
--
-- google_id NOT NULL 제약 제거:
-- - 신규 Naver 사용자는 google_id 없이 가입됨
-- - 기존 Google 사용자는 그대로 google_id 보존

ALTER TABLE users
    ADD COLUMN naver_id VARCHAR(255) UNIQUE;

ALTER TABLE users
    ALTER COLUMN google_id DROP NOT NULL;

-- 인덱스는 UNIQUE 제약과 함께 자동 생성됨
