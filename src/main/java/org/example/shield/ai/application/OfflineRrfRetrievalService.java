package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.domain.LegalCaseJpaRepository;
import org.example.shield.ai.domain.LegalCaseJpaRepository.LegalCaseRow;
import org.example.shield.ai.domain.LegalChunkJpaRepository;
import org.example.shield.ai.domain.LegalChunkJpaRepository.LegalChunkRow;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.MixedRetrievalResult;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.dto.RrfFusionInput;
import org.example.shield.ai.dto.RrfFusionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF (Reciprocal Rank Fusion) 기반 offline 검색 서비스 (Phase P5.4 Commit 5).
 *
 * <p>운영 weighted SQL과 별도로, path-specific repository 메서드를 호출해
 * 3개 ranked list를 만든 후 {@link RrfFusionService}로 fuse한 결과를 반환.
 *
 * <p><b>본 서비스는 production 호출 경로에 연결되지 않는다.</b>
 * Offline 평가 스크립트({@code OfflineQualityReportJob} 또는 직접 호출)에서만 사용.
 * production은 {@code AI_RAG_FUSION_MODE=weighted} 고정.
 *
 * <p>가드 조건: {@code app.ai.rag.rrf-offline.enabled=true} 일 때만 호출.
 * 운영 코드가 실수로 호출하면 {@link IllegalStateException}.
 */
@Service
@Slf4j
public class OfflineRrfRetrievalService {

    private static final String SOURCE_VECTOR = "vector";
    private static final String SOURCE_BM25 = "bm25";
    private static final String SOURCE_TRIGRAM = "trigram";

    @Value("${app.ai.rag.rrf-offline.enabled:false}")
    private boolean rrfOfflineEnabled;

    @Value("${app.ai.rag.rrf-offline.candidate-n:40}")
    private int candidateN;

    private final LegalChunkJpaRepository chunkRepo;
    private final LegalCaseJpaRepository caseRepo;
    private final RrfFusionService rrfFusionService;
    private final QueryEmbeddingService queryEmbeddingService;
    private final CohereApiConfig cohereConfig;

    public OfflineRrfRetrievalService(LegalChunkJpaRepository chunkRepo,
                                      LegalCaseJpaRepository caseRepo,
                                      RrfFusionService rrfFusionService,
                                      QueryEmbeddingService queryEmbeddingService,
                                      CohereApiConfig cohereConfig) {
        this.chunkRepo = chunkRepo;
        this.caseRepo = caseRepo;
        this.rrfFusionService = rrfFusionService;
        this.queryEmbeddingService = queryEmbeddingService;
        this.cohereConfig = cohereConfig;
    }

    /**
     * RRF 기반 법령 검색. weighted SQL과 별도 경로.
     */
    public List<LegalChunk> retrieveLawsRrf(String vectorQuery, String keywordQuery,
                                            String[] categoryIds, int topK) {
        guardOffline();
        String queryVector = embedVector(vectorQuery);
        List<LegalChunkRow> vec = chunkRepo.searchVectorOnly(queryVector, safeCats(categoryIds), candidateN);
        List<LegalChunkRow> bm = chunkRepo.searchBm25Only(keywordQuery, safeCats(categoryIds), candidateN);
        List<LegalChunkRow> tri = chunkRepo.searchTrigramOnly(vectorQuery, safeCats(categoryIds), candidateN);

        return fuseLaws(vec, bm, tri, topK);
    }

    /**
     * RRF 기반 판례 검색.
     */
    public List<Precedent> retrieveCasesRrf(String vectorQuery, String keywordQuery,
                                            String[] categoryIds, int topK) {
        guardOffline();
        String queryVector = embedVector(vectorQuery);
        List<LegalCaseRow> vec = caseRepo.searchCasesVectorOnly(queryVector, safeCats(categoryIds), candidateN);
        List<LegalCaseRow> bm = caseRepo.searchCasesBm25Only(keywordQuery, safeCats(categoryIds), candidateN);
        List<LegalCaseRow> tri = caseRepo.searchCasesTrigramOnly(vectorQuery, safeCats(categoryIds), candidateN);

        return fuseCases(vec, bm, tri, topK);
    }

    /**
     * 법령+판례 RRF 병합 검색. 두 코퍼스의 fused 결과를 합쳐 score 기준으로 merge.
     */
    public MixedRetrievalResult retrieveMixedRrf(String vectorQuery, String keywordQuery,
                                                 String[] categoryIds, int topK) {
        List<LegalChunk> laws = retrieveLawsRrf(vectorQuery, keywordQuery, categoryIds, topK);
        List<Precedent> cases = retrieveCasesRrf(vectorQuery, keywordQuery, categoryIds, topK);

        List<RetrievedDocument> merged = new ArrayList<>(laws.size() + cases.size());
        merged.addAll(laws);
        merged.addAll(cases);
        merged.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (merged.size() > topK) {
            merged = new ArrayList<>(merged.subList(0, topK));
        }
        return new MixedRetrievalResult(laws, cases, merged);
    }

    // === 내부 헬퍼 ===

    private void guardOffline() {
        if (!rrfOfflineEnabled) {
            throw new IllegalStateException(
                    "OfflineRrfRetrievalService called but app.ai.rag.rrf-offline.enabled=false. " +
                    "Production must use weighted SQL — this service is for offline comparison only.");
        }
    }

    private String embedVector(String query) {
        if (query == null || query.isBlank()) {
            return "[" + "0,".repeat(cohereConfig.getEmbedDimension() - 1) + "0]";
        }
        float[] vec = queryEmbeddingService.embedQuery(query);
        if (vec == null || vec.length == 0) {
            return "[" + "0,".repeat(cohereConfig.getEmbedDimension() - 1) + "0]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private List<LegalChunk> fuseLaws(List<LegalChunkRow> vec, List<LegalChunkRow> bm,
                                      List<LegalChunkRow> tri, int topK) {
        // chunk id = "law:{lawName}:{articleNo}"
        Map<String, LegalChunkRow> rowsById = new LinkedHashMap<>();
        List<RrfFusionInput> vecRanked = toRrfInputs(vec, SOURCE_VECTOR, rowsById, this::lawId);
        List<RrfFusionInput> bmRanked = toRrfInputs(bm, SOURCE_BM25, rowsById, this::lawId);
        List<RrfFusionInput> triRanked = toRrfInputs(tri, SOURCE_TRIGRAM, rowsById, this::lawId);

        List<RrfFusionResult> fused = rrfFusionService.fuse(
                List.of(vecRanked, bmRanked, triRanked), topK);

        List<LegalChunk> out = new ArrayList<>(fused.size());
        for (RrfFusionResult r : fused) {
            LegalChunkRow row = rowsById.get(r.id());
            if (row != null) {
                out.add(toLegalChunk(row, r.rrfScore()));
            }
        }
        return out;
    }

    private List<Precedent> fuseCases(List<LegalCaseRow> vec, List<LegalCaseRow> bm,
                                      List<LegalCaseRow> tri, int topK) {
        Map<String, LegalCaseRow> rowsById = new LinkedHashMap<>();
        List<RrfFusionInput> vecRanked = toRrfInputs(vec, SOURCE_VECTOR, rowsById, this::caseId);
        List<RrfFusionInput> bmRanked = toRrfInputs(bm, SOURCE_BM25, rowsById, this::caseId);
        List<RrfFusionInput> triRanked = toRrfInputs(tri, SOURCE_TRIGRAM, rowsById, this::caseId);

        List<RrfFusionResult> fused = rrfFusionService.fuse(
                List.of(vecRanked, bmRanked, triRanked), topK);

        List<Precedent> out = new ArrayList<>(fused.size());
        for (RrfFusionResult r : fused) {
            LegalCaseRow row = rowsById.get(r.id());
            if (row != null) {
                out.add(toPrecedent(row, r.rrfScore()));
            }
        }
        return out;
    }

    private <R> List<RrfFusionInput> toRrfInputs(List<R> rows, String source,
                                                 Map<String, R> rowsById,
                                                 java.util.function.Function<R, String> idFn) {
        List<RrfFusionInput> inputs = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            R row = rows.get(i);
            String id = idFn.apply(row);
            rowsById.putIfAbsent(id, row);
            inputs.add(new RrfFusionInput(id, source, i + 1, extractScore(row)));
        }
        return inputs;
    }

    private String lawId(LegalChunkRow row) {
        return "law:" + safe(row.getLawName()) + ":" + safe(row.getArticleNo());
    }

    private String caseId(LegalCaseRow row) {
        return "case:" + safe(row.getCaseNo()) + ":" + safe(row.getCourt());
    }

    private double extractScore(Object row) {
        if (row instanceof LegalChunkRow r) return r.getScore() == null ? 0.0 : r.getScore();
        if (row instanceof LegalCaseRow r) return r.getScore() == null ? 0.0 : r.getScore();
        return 0.0;
    }

    private LegalChunk toLegalChunk(LegalChunkRow row, double rrfScore) {
        return new LegalChunk(
                row.getLawName(), row.getArticleNo(), row.getArticleTitle(),
                row.getContent(), row.getEffectiveDate(), row.getSourceUrl(),
                rrfScore);
    }

    private Precedent toPrecedent(LegalCaseRow row, double rrfScore) {
        return new Precedent(
                row.getCaseNo(), row.getCourt(), row.getCaseName(),
                row.getDecisionDate(), row.getCaseType(),
                row.getHeadnote(), row.getHolding(),
                row.getSourceUrl(), rrfScore);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String[] safeCats(String[] cats) {
        return cats == null ? new String[0] : cats;
    }
}
