package org.example.shield.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * legal_cases 테이블 JPA 리포지토리.
 *
 * <p>C-3 단계에서는 기본 CRUD/자연키/단순 필터만 제공했고, C-5 (Issue #42)에서
 * 법령(legal_chunks) 과 동일한 3-way 하이브리드(pgvector + BM25 + pg_trgm) 검색 네이티브
 * 쿼리를 추가하여 {@code PgLegalRetrievalService} 가 법령·판례를 병합 랭킹할 수 있게 한다.
 * 하이브리드 SQL 은 {@code scripts/eval_rag.py} 의 {@code case_sql} 을 직접 이식한 것으로,
 * trigram 은 판결요지(holding) 에만 적용한다 — 판례는 요지 중심 검색이기 때문.</p>
 */
public interface LegalCaseJpaRepository extends JpaRepository<LegalCaseEntity, Long> {

    /**
     * (case_no, court, decision_date) 자연키로 판례 조회.
     * 자연키 유니크 제약은 Flyway V6에서 관리한다.
     */
    @Query("""
            select lc
              from LegalCaseEntity lc
             where lc.caseNo = :caseNo
               and lc.court = :court
               and lc.decisionDate = :decisionDate
            """)
    Optional<LegalCaseEntity> findByNaturalKey(@Param("caseNo") String caseNo,
                                               @Param("court") String court,
                                               @Param("decisionDate") LocalDate decisionDate);

    /**
     * 사건번호로 해당 사건의 모든 심급 판결 조회 (선고일 오름차순).
     */
    @Query("""
            select lc
              from LegalCaseEntity lc
             where lc.caseNo = :caseNo
             order by lc.decisionDate asc
            """)
    List<LegalCaseEntity> findAllByCaseNoOrderByDecisionDateAsc(@Param("caseNo") String caseNo);

    /**
     * 사건 유형 + 선고일 범위로 조회 (최근 판례 우선).
     */
    @Query("""
            select lc
              from LegalCaseEntity lc
             where lc.caseType = :caseType
               and lc.decisionDate between :from and :to
             order by lc.decisionDate desc
            """)
    List<LegalCaseEntity> findRecentByCaseType(@Param("caseType") String caseType,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /**
     * 임베딩이 아직 없는(C-4 백필 대상) 판례 개수.
     */
    @Query("""
            select count(lc)
              from LegalCaseEntity lc
             where lc.embedding is null
            """)
    long countMissingEmbedding();

    // ---------------------------------------------------------------------
    // C-5 (Issue #42) — 3-way 하이브리드 검색 (pgvector + BM25 + pg_trgm)
    // B-9: CTE-split pattern. legal_chunks 와 동일한 구조이며 차이는 다음과 같다.
    //  - trigram 컬럼이 holding(판결요지) 이다. idx_legal_cases_holding_trgm 활용.
    //  - law_id 필터가 없는 대신 선택적 case_type IN 필터를 제공.
    //  - Projection 은 LegalCaseRow 인터페이스로 별도.
    // ---------------------------------------------------------------------

    /**
     * 3-way 하이브리드 검색 (case_type 필터 없음).
     *
     * <p>{@code :queryVector} 는 pgvector 리터럴 문자열 {@code "[0.1,0.2,...]"}.
     * {@code :categoryIds} 는 {@code String[]}. null/빈 배열이면 카테고리 필터 미적용.</p>
     */
    @Query(value = """
            WITH vec AS (
              SELECT id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS sim
                FROM legal_cases
               WHERE embedding IS NOT NULL
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               ORDER BY embedding <=> CAST(:queryVector AS vector)
               LIMIT 40
            ), bm AS (
              SELECT id, ts_rank(content_tsv, to_tsquery('simple', :keywordQuery), 1) AS rk
                FROM legal_cases
               WHERE content_tsv @@ to_tsquery('simple', :keywordQuery)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), trig AS (
              -- holding 컬럼 직접 비교 — GIN(gin_trgm_ops) 인덱스 활용. NULL 은 `% v` 가 NULL 이
              -- 되어 WHERE 에서 자연 배제되고, LegalChunkJpaRepository 와 패턴 일관성을 갖춘다.
              SELECT id, similarity(holding, CAST(:vectorQuery AS text)) AS sm
                FROM legal_cases
               WHERE holding % CAST(:vectorQuery AS text)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), pool AS (
              SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
            )
            SELECT lc.id              AS id,
                   lc.case_no         AS caseNo,
                   lc.court           AS court,
                   lc.case_name       AS caseName,
                   to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
                   lc.case_type       AS caseType,
                   lc.headnote        AS headnote,
                   lc.holding         AS holding,
                   lc.source_url      AS sourceUrl,
                   ( COALESCE(v.sim, 0) * :vectorWeight
                   + COALESCE(b.rk,  0) * :keywordWeight
                   + COALESCE(t.sm,  0) * :trigramWeight) AS score
              FROM pool p
              JOIN legal_cases lc ON lc.id = p.id
         LEFT JOIN vec  v ON v.id  = lc.id
         LEFT JOIN bm   b ON b.id  = lc.id
         LEFT JOIN trig t ON t.id  = lc.id
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalCaseRow> search3WayCases(@Param("queryVector") String queryVector,
                                       @Param("vectorQuery") String vectorQuery,
                                       @Param("keywordQuery") String keywordQuery,
                                       @Param("categoryIds") String[] categoryIds,
                                       @Param("vectorWeight") double vectorWeight,
                                       @Param("keywordWeight") double keywordWeight,
                                       @Param("trigramWeight") double trigramWeight,
                                       @Param("topK") int topK);

    /**
     * 3-way 하이브리드 검색 (case_type 필터 포함, 예: 민사/가사).
     * {@code :caseTypes} 가 null/empty 이면 호출부에서 필터 없는 버전을 써야 한다.
     */
    @Query(value = """
            WITH vec AS (
              SELECT id, 1 - (embedding <=> CAST(:queryVector AS vector)) AS sim
                FROM legal_cases
               WHERE embedding IS NOT NULL
                 AND case_type IN (:caseTypes)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               ORDER BY embedding <=> CAST(:queryVector AS vector)
               LIMIT 40
            ), bm AS (
              SELECT id, ts_rank(content_tsv, to_tsquery('simple', :keywordQuery), 1) AS rk
                FROM legal_cases
               WHERE case_type IN (:caseTypes)
                 AND content_tsv @@ to_tsquery('simple', :keywordQuery)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), trig AS (
              -- holding 컬럼 직접 비교 (search3WayCases 와 동일 — GIN 인덱스 활용 + 일관성)
              SELECT id, similarity(holding, CAST(:vectorQuery AS text)) AS sm
                FROM legal_cases
               WHERE case_type IN (:caseTypes)
                 AND holding % CAST(:vectorQuery AS text)
                 AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                       OR category_ids && CAST(:categoryIds AS text[]) )
               LIMIT 40
            ), pool AS (
              SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
            )
            SELECT lc.id              AS id,
                   lc.case_no         AS caseNo,
                   lc.court           AS court,
                   lc.case_name       AS caseName,
                   to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
                   lc.case_type       AS caseType,
                   lc.headnote        AS headnote,
                   lc.holding         AS holding,
                   lc.source_url      AS sourceUrl,
                   ( COALESCE(v.sim, 0) * :vectorWeight
                   + COALESCE(b.rk,  0) * :keywordWeight
                   + COALESCE(t.sm,  0) * :trigramWeight) AS score
              FROM pool p
              JOIN legal_cases lc ON lc.id = p.id
         LEFT JOIN vec  v ON v.id  = lc.id
         LEFT JOIN bm   b ON b.id  = lc.id
         LEFT JOIN trig t ON t.id  = lc.id
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalCaseRow> search3WayCasesByTypes(@Param("queryVector") String queryVector,
                                              @Param("vectorQuery") String vectorQuery,
                                              @Param("keywordQuery") String keywordQuery,
                                              @Param("categoryIds") String[] categoryIds,
                                              @Param("caseTypes") java.util.Collection<String> caseTypes,
                                              @Param("vectorWeight") double vectorWeight,
                                              @Param("keywordWeight") double keywordWeight,
                                              @Param("trigramWeight") double trigramWeight,
                                              @Param("topK") int topK);

    // === P5.4 Commit 4 — Path-specific 판례 검색 (RRF offline 비교용) ===

    /**
     * 판례 벡터 경로 단독 ranked list.
     */
    @Query(value = """
            SELECT lc.id              AS id,
                   lc.case_no         AS caseNo,
                   lc.court           AS court,
                   lc.case_name       AS caseName,
                   to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
                   lc.case_type       AS caseType,
                   lc.headnote        AS headnote,
                   lc.holding         AS holding,
                   lc.source_url      AS sourceUrl,
                   (1 - (lc.embedding <=> CAST(:queryVector AS vector))) AS score
              FROM legal_cases lc
             WHERE lc.embedding IS NOT NULL
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY lc.embedding <=> CAST(:queryVector AS vector)
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalCaseRow> searchCasesVectorOnly(@Param("queryVector") String queryVector,
                                             @Param("categoryIds") String[] categoryIds,
                                             @Param("topK") int topK);

    /**
     * 판례 BM25 경로 단독 ranked list.
     */
    @Query(value = """
            SELECT lc.id              AS id,
                   lc.case_no         AS caseNo,
                   lc.court           AS court,
                   lc.case_name       AS caseName,
                   to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
                   lc.case_type       AS caseType,
                   lc.headnote        AS headnote,
                   lc.holding         AS holding,
                   lc.source_url      AS sourceUrl,
                   ts_rank(lc.content_tsv, to_tsquery('simple', :keywordQuery), 1) AS score
              FROM legal_cases lc
             WHERE lc.content_tsv @@ to_tsquery('simple', :keywordQuery)
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalCaseRow> searchCasesBm25Only(@Param("keywordQuery") String keywordQuery,
                                           @Param("categoryIds") String[] categoryIds,
                                           @Param("topK") int topK);

    /**
     * 판례 trigram 경로 단독 ranked list. {@code holding} 컬럼 기준 (법령은 content).
     */
    @Query(value = """
            SELECT lc.id              AS id,
                   lc.case_no         AS caseNo,
                   lc.court           AS court,
                   lc.case_name       AS caseName,
                   to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
                   lc.case_type       AS caseType,
                   lc.headnote        AS headnote,
                   lc.holding         AS holding,
                   lc.source_url      AS sourceUrl,
                   similarity(lc.holding, CAST(:vectorQuery AS text)) AS score
              FROM legal_cases lc
             WHERE lc.holding IS NOT NULL
               AND lc.holding % CAST(:vectorQuery AS text)
               AND ( COALESCE(CARDINALITY(CAST(:categoryIds AS text[])), 0) = 0
                     OR lc.category_ids && CAST(:categoryIds AS text[]) )
             ORDER BY score DESC
             LIMIT :topK
            """, nativeQuery = true)
    List<LegalCaseRow> searchCasesTrigramOnly(@Param("vectorQuery") String vectorQuery,
                                              @Param("categoryIds") String[] categoryIds,
                                              @Param("topK") int topK);

    /**
     * Spring Data JPA 네이티브 쿼리용 projection 인터페이스.
     * 서비스 레이어에서 Precedent record 로 변환해 반환한다.
     */
    interface LegalCaseRow {
        Long getId();
        String getCaseNo();
        String getCourt();
        String getCaseName();
        String getDecisionDate();
        String getCaseType();
        String getHeadnote();
        String getHolding();
        String getSourceUrl();
        Double getScore();
    }
}
