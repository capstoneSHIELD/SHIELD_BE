package org.example.shield.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * legal_chunks 테이블에 대한 Spring Data JPA 리포지토리.
 *
 * <p>Layer 2 벡터/전문 검색은 A-5 단계에서 네이티브 쿼리로 추가된다.
 * 본 인터페이스는 기본 CRUD 및 식별 조회만 제공한다.</p>
 */
public interface LegalChunkJpaRepository extends JpaRepository<LegalChunkEntity, Long> {

    /**
     * (law_id, article_no, chunk_index) 조합으로 활성(폐지되지 않은) 청크 조회.
     * DB의 부분 유니크 제약과 일치한다.
     */
    @Query("""
            select lc
              from LegalChunkEntity lc
             where lc.lawId = :lawId
               and lc.articleNo = :articleNo
               and lc.chunkIndex = :chunkIndex
               and lc.abolitionDate is null
            """)
    Optional<LegalChunkEntity> findActiveByNaturalKey(@Param("lawId") String lawId,
                                                     @Param("articleNo") String articleNo,
                                                     @Param("chunkIndex") Short chunkIndex);

    /**
     * 특정 법령의 모든 활성 청크를 chunk_index 오름차순으로 조회.
     */
    @Query("""
            select lc
              from LegalChunkEntity lc
             where lc.lawId = :lawId
               and lc.abolitionDate is null
             order by lc.articleNo asc, lc.chunkIndex asc
            """)
    List<LegalChunkEntity> findActiveByLawId(@Param("lawId") String lawId);

    // ---------------------------------------------------------------------
    // Layer 2 하이브리드 검색 — CTE-split pattern (B-9, post-HNSW audit)
    //
    // 구조:
    //   vec  : HNSW Index Scan 으로 top-K' 후보 (ORDER BY embedding <=> :q LIMIT)
    //   bm   : GIN(content_tsv) 인덱스로 BM25 후보 top-K'
    //   trig : GIST(content_trgm) 인덱스로 trigram 후보 top-K'
    //   pool : 세 후보의 UNION
    //   final: pool 행에 가중 합산 점수 재계산 후 score DESC LIMIT :topK
    //
    // 필터 push:
    //   - category_ids / law_id 필터를 세 CTE 내부에 함께 적용한다. 카테고리가
    //     희귀할 경우 planner 가 GIN/B-tree 인덱스를 우선 선택해 HNSW 풀에서
    //     누락되는 행을 회수한다. PgLegalRetrievalService 가 트랜잭션 시작 시
    //     `hnsw.iterative_scan = relaxed_order` 를 설정해 HNSW 분기에서도
    //     필터 매칭 후보를 누락 없이 채울 수 있다.
    //
    // 검증: scripts/verify_cte_refactor.py — [E] 시나리오에서 희귀 카테고리
    //       (0.16% 셀렉티비티) 5/5 회수 + 1,202ms → 11.7ms 개선.
    //
    // 폴백:
    //   - :keywordQuery 가 sentinel(__shield_never_match__) 이면 bm CTE 가
    //     0 행을 반환하고 vec/trig 만으로 동작한다. EMPTY_QUERY_SENTINEL 참조.
    //   - 임베딩 실패 시 호출자가 영벡터 리터럴을 전달 → vec 분기의 cosine 이
    //     일관되게 1 - 0 = 1 로 나오지 않도록, 영벡터 표시는 호출자가 별도
    //     처리한다(PgLegalRetrievalService.zeroVectorLiteral).
    // ---------------------------------------------------------------------

    /**
     * 3-way 하이브리드 검색 (법령ID 필터 없음).
     *
     * <p>{@code :queryVector}는 pgvector 리터럴 문자열 형식 {@code "[0.1,0.2,...]"}로 전달.
     * 서비스 레이어에서 {@code float[]} → 문자열 변환을 담당한다.</p>
     *
     * <p>{@code :categoryIds}는 {@code String[]} 배열. null/빈 배열이면 필터가 무시된다.
     * PostgreSQL 배열 겹침 연산자 {@code &&}는 한 원소라도 일치하면 true.</p>
     */
    @Query(value = """
            WITH vec AS (
              SELECT id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS sim
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND embedding IS NOT NULL
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               ORDER BY embedding <=> CAST(:queryVector AS vector)
               LIMIT 40
            ), bm AS (
              SELECT id, ts_rank(content_tsv, to_tsquery('simple', :keywordQuery), 1) AS rk
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND content_tsv @@ to_tsquery('simple', :keywordQuery)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), trig AS (
              SELECT id, similarity(content, CAST(:vectorQuery AS text)) AS sm
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND content % CAST(:vectorQuery AS text)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), pool AS (
              SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
            )
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   ( COALESCE(v.sim, 0) * :vectorWeight
                   + COALESCE(b.rk,  0) * :keywordWeight
                   + COALESCE(t.sm,  0) * :trigramWeight) AS score
              FROM pool p
              JOIN legal_chunks lc ON lc.id = p.id
         LEFT JOIN vec  v ON v.id  = lc.id
         LEFT JOIN bm   b ON b.id  = lc.id
         LEFT JOIN trig t ON t.id  = lc.id
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> search3Way(@Param("queryVector") String queryVector,
                                   @Param("vectorQuery") String vectorQuery,
                                   @Param("keywordQuery") String keywordQuery,
                                   @Param("categoryIds") String[] categoryIds,
                                   @Param("vectorWeight") double vectorWeight,
                                   @Param("keywordWeight") double keywordWeight,
                                   @Param("trigramWeight") double trigramWeight,
                                   @Param("topK") int topK);

    /**
     * 3-way 하이브리드 검색 (법령ID 필터 포함). 카테고리·법령ID 필터를
     * 세 CTE 내부에 push 하여 인덱스 활용을 보장한다.
     */
    @Query(value = """
            WITH vec AS (
              SELECT id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS sim
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND embedding IS NOT NULL
                 AND law_id IN (:lawIds)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               ORDER BY embedding <=> CAST(:queryVector AS vector)
               LIMIT 40
            ), bm AS (
              SELECT id, ts_rank(content_tsv, to_tsquery('simple', :keywordQuery), 1) AS rk
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND law_id IN (:lawIds)
                 AND content_tsv @@ to_tsquery('simple', :keywordQuery)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), trig AS (
              SELECT id, similarity(content, CAST(:vectorQuery AS text)) AS sm
                FROM legal_chunks
               WHERE abolition_date IS NULL
                 AND law_id IN (:lawIds)
                 AND content % CAST(:vectorQuery AS text)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), pool AS (
              SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
            )
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   ( COALESCE(v.sim, 0) * :vectorWeight
                   + COALESCE(b.rk,  0) * :keywordWeight
                   + COALESCE(t.sm,  0) * :trigramWeight) AS score
              FROM pool p
              JOIN legal_chunks lc ON lc.id = p.id
         LEFT JOIN vec  v ON v.id  = lc.id
         LEFT JOIN bm   b ON b.id  = lc.id
         LEFT JOIN trig t ON t.id  = lc.id
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> search3WayByLaws(@Param("queryVector") String queryVector,
                                         @Param("vectorQuery") String vectorQuery,
                                         @Param("keywordQuery") String keywordQuery,
                                         @Param("categoryIds") String[] categoryIds,
                                         @Param("lawIds") Collection<String> lawIds,
                                         @Param("vectorWeight") double vectorWeight,
                                         @Param("keywordWeight") double keywordWeight,
                                         @Param("trigramWeight") double trigramWeight,
                                         @Param("topK") int topK);

    // ---------------------------------------------------------------------
    // [Legacy] B-1 이전의 2-way 하이브리드 (BM25 + trigram) — 하위 호환용.
    // 벡터 경로가 없어도 동작해야 하는 테스트/디버깅 루트.
    // ---------------------------------------------------------------------

    /** 법령ID 필터 없음 버전 */
    @Query(value = """
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   (ts_rank(lc.content_tsv, plainto_tsquery('simple', :vectorQuery), 1) * :vectorWeight
                    + ts_rank(lc.content_tsv, to_tsquery('simple', :keywordQuery), 1) * :keywordWeight
                    + similarity(lc.content, :vectorQuery) * :trigramWeight) AS score
              FROM legal_chunks lc
             WHERE lc.abolition_date IS NULL
               AND ( lc.content_tsv @@ plainto_tsquery('simple', :vectorQuery)
                  OR lc.content_tsv @@ to_tsquery('simple', :keywordQuery)
                  OR lc.content % CAST(:vectorQuery AS text) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> searchHybrid(@Param("vectorQuery") String vectorQuery,
                                     @Param("keywordQuery") String keywordQuery,
                                     @Param("vectorWeight") double vectorWeight,
                                     @Param("keywordWeight") double keywordWeight,
                                     @Param("trigramWeight") double trigramWeight,
                                     @Param("topK") int topK);

    /** 법령ID 필터 포함 버전 */
    @Query(value = """
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   (ts_rank(lc.content_tsv, plainto_tsquery('simple', :vectorQuery), 1) * :vectorWeight
                    + ts_rank(lc.content_tsv, to_tsquery('simple', :keywordQuery), 1) * :keywordWeight
                    + similarity(lc.content, :vectorQuery) * :trigramWeight) AS score
              FROM legal_chunks lc
             WHERE lc.abolition_date IS NULL
               AND lc.law_id IN (:lawIds)
               AND ( lc.content_tsv @@ plainto_tsquery('simple', :vectorQuery)
                  OR lc.content_tsv @@ to_tsquery('simple', :keywordQuery)
                  OR lc.content % CAST(:vectorQuery AS text) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> searchHybridByLaws(@Param("vectorQuery") String vectorQuery,
                                           @Param("keywordQuery") String keywordQuery,
                                           @Param("lawIds") Collection<String> lawIds,
                                           @Param("vectorWeight") double vectorWeight,
                                           @Param("keywordWeight") double keywordWeight,
                                           @Param("trigramWeight") double trigramWeight,
                                           @Param("topK") int topK);

    // === P5.4 Commit 4 — Path-specific 검색 (RRF offline 비교용) ===

    /**
     * 벡터 경로 단독 ranked list. RRF offline 비교용.
     *
     * <p>score = {@code 1 - cosine_distance}. 다른 두 path는 별도 쿼리로 호출.
     * 운영 가중합({@code search3Way})과 동일한 카테고리/abolition_date 필터 적용.
     */
    @Query(value = """
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   (1 - (lc.embedding <=> CAST(:queryVector AS vector))) AS score
              FROM legal_chunks lc
             WHERE lc.abolition_date IS NULL
               AND lc.embedding IS NOT NULL
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY lc.embedding <=> CAST(:queryVector AS vector)
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> searchVectorOnly(@Param("queryVector") String queryVector,
                                         @Param("categoryIds") String[] categoryIds,
                                         @Param("topK") int topK);

    /**
     * BM25 경로 단독 ranked list. RRF offline 비교용.
     *
     * <p>score = {@code ts_rank(content_tsv, query, 1)}.
     */
    @Query(value = """
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   ts_rank(lc.content_tsv, to_tsquery('simple', :keywordQuery), 1) AS score
              FROM legal_chunks lc
             WHERE lc.abolition_date IS NULL
               AND lc.content_tsv @@ to_tsquery('simple', :keywordQuery)
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> searchBm25Only(@Param("keywordQuery") String keywordQuery,
                                       @Param("categoryIds") String[] categoryIds,
                                       @Param("topK") int topK);

    /**
     * Trigram 경로 단독 ranked list. RRF offline 비교용.
     *
     * <p>score = {@code similarity(content, query)}. pg_trgm extension 필요.
     */
    @Query(value = """
            SELECT lc.law_name        AS lawName,
                   lc.article_no      AS articleNo,
                   lc.article_title   AS articleTitle,
                   lc.content         AS content,
                   to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
                   lc.source_url      AS sourceUrl,
                   similarity(lc.content, CAST(:vectorQuery AS text)) AS score
              FROM legal_chunks lc
             WHERE lc.abolition_date IS NULL
               AND lc.content % CAST(:vectorQuery AS text)
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalChunkRow> searchTrigramOnly(@Param("vectorQuery") String vectorQuery,
                                          @Param("categoryIds") String[] categoryIds,
                                          @Param("topK") int topK);

    /**
     * Spring Data JPA 네이티브 쿼리용 projection 인터페이스.
     * 서비스 레이어에서 LegalChunk record 로 변환해 반환한다.
     */
    interface LegalChunkRow {
        String getLawName();
        String getArticleNo();
        String getArticleTitle();
        String getContent();
        String getEffectiveDate();
        String getSourceUrl();
        Double getScore();
    }
}
