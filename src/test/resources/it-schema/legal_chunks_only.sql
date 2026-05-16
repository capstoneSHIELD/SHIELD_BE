-- Repository IT 전용 최소 스키마.
-- Flyway V2~V12 는 pre-Flyway 운영 스키마(consultations, users 등)가 이미 존재한다고 가정하므로
-- fresh 컨테이너에 그대로 적용 불가. LegalChunkRepositoryIT 는 searchHybrid 의 trigram `%` 회귀
-- 검증만 하면 되므로 legal_chunks 한 테이블 + 필수 확장만 셋업한다.
--
-- 운영 스키마(V3__create_legal_chunks.sql + V4__add_embedding_column.sql)와 컬럼 셋이 동치이도록
-- 유지해야 한다.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE legal_chunks (
    id               BIGSERIAL       PRIMARY KEY,
    law_id           VARCHAR(64)     NOT NULL,
    law_name         VARCHAR(255)    NOT NULL,
    article_no       VARCHAR(32)     NOT NULL,
    chunk_index      SMALLINT        NOT NULL DEFAULT 0,
    article_title    VARCHAR(255),
    content          TEXT            NOT NULL,
    effective_date   DATE,
    abolition_date   DATE,
    source_url       VARCHAR(512),
    category_ids     TEXT[],
    lod_uri          VARCHAR(512),
    legislation_terms TEXT[],
    embedding        vector(1024),
    embedding_model  VARCHAR(64),
    content_tsv      tsvector GENERATED ALWAYS AS (
                         to_tsvector('simple',
                             coalesce(law_name, '')      || ' ' ||
                             coalesce(article_no, '')    || ' ' ||
                             coalesce(article_title, '') || ' ' ||
                             content)
                     ) STORED,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_legal_chunks_tsv          ON legal_chunks USING GIN (content_tsv);
CREATE INDEX idx_legal_chunks_content_trgm ON legal_chunks USING gin (content gin_trgm_ops);
