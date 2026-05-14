-- Issue #72: 동시성 보호 — 낙관적 락(@Version) + Brief 1인 선임 DB 제약
--
-- 1. consultations / briefs / users 에 version 컬럼 추가
--    JPA @Version 으로 same-row 다중 writer race 차단.
--    기존 row 는 DEFAULT 0 으로 자동 초기화 → 첫 UPDATE 시 1로 증가.
--
-- 2. deliveries 에 partial unique index 추가
--    "한 brief 당 CONFIRMED delivery 1개" 비즈니스 invariant 를 DB 레벨에서 강제.
--    낙관적 락 (Brief.@Version + acceptBy) 이 1차 방어, partial unique 가 마지막 보증선.

ALTER TABLE consultations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE briefs        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_deliveries_brief_confirmed
  ON deliveries(brief_id) WHERE status = 'CONFIRMED';

COMMENT ON COLUMN consultations.version IS '낙관적 락(@Version). MessageService/AnalysisService/BriefService 동시 갱신 race 차단.';
COMMENT ON COLUMN briefs.version        IS '낙관적 락(@Version). 변호사 동시 수락 race 차단 (Brief.acceptBy).';
COMMENT ON COLUMN users.version         IS '낙관적 락(@Version). Refresh Token rotation 동시 race 부분 차단.';
COMMENT ON INDEX  uk_deliveries_brief_confirmed IS '한 brief 당 CONFIRMED delivery 1개 강제 (1인 선임 원칙).';