package org.example.shield.ai.application;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.MixedRetrievalResult;
import org.example.shield.ai.dto.RagPipelineResult;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.dto.RetrievalStrategyDecision;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.RagMetrics;
import org.example.shield.ai.safety.RagCircuitBreaker;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RagPipelineService {

    private final IntentClassificationService intentClassificationService;
    private final CategoryLawMappingService categoryLawMappingService;
    private final LegalRetrievalService legalRetrievalService;
    private final RagContextBuilder ragContextBuilder;
    private final RagMetrics ragMetrics;
    private final IntentAwareRetrievalPolicy intentAwareRetrievalPolicy;
    private final RetrievalScoreGate retrievalScoreGate;
    private final RerankingService rerankingService;
    private final RagCircuitBreaker circuitBreaker;
    private final boolean includeCases;

    /** Legacy 8-arg constructor — RerankingService + CircuitBreaker 미주입 (BC). */
    public RagPipelineService(IntentClassificationService intentClassificationService,
                              CategoryLawMappingService categoryLawMappingService,
                              LegalRetrievalService legalRetrievalService,
                              RagContextBuilder ragContextBuilder,
                              RagMetrics ragMetrics,
                              IntentAwareRetrievalPolicy intentAwareRetrievalPolicy,
                              RetrievalScoreGate retrievalScoreGate,
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this(intentClassificationService, categoryLawMappingService, legalRetrievalService,
                ragContextBuilder, ragMetrics, intentAwareRetrievalPolicy, retrievalScoreGate,
                null, null, includeCases);
    }

    /** Legacy 9-arg constructor — CircuitBreaker 미주입 (BC). */
    public RagPipelineService(IntentClassificationService intentClassificationService,
                              CategoryLawMappingService categoryLawMappingService,
                              LegalRetrievalService legalRetrievalService,
                              RagContextBuilder ragContextBuilder,
                              RagMetrics ragMetrics,
                              IntentAwareRetrievalPolicy intentAwareRetrievalPolicy,
                              RetrievalScoreGate retrievalScoreGate,
                              @Nullable RerankingService rerankingService,
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this(intentClassificationService, categoryLawMappingService, legalRetrievalService,
                ragContextBuilder, ragMetrics, intentAwareRetrievalPolicy, retrievalScoreGate,
                rerankingService, null, includeCases);
    }

    @Autowired
    public RagPipelineService(IntentClassificationService intentClassificationService,
                              CategoryLawMappingService categoryLawMappingService,
                              LegalRetrievalService legalRetrievalService,
                              RagContextBuilder ragContextBuilder,
                              RagMetrics ragMetrics,
                              IntentAwareRetrievalPolicy intentAwareRetrievalPolicy,
                              RetrievalScoreGate retrievalScoreGate,
                              @Nullable RerankingService rerankingService,
                              @Nullable RagCircuitBreaker circuitBreaker,
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this.intentClassificationService = intentClassificationService;
        this.categoryLawMappingService = categoryLawMappingService;
        this.legalRetrievalService = legalRetrievalService;
        this.ragContextBuilder = ragContextBuilder;
        this.ragMetrics = ragMetrics;
        this.intentAwareRetrievalPolicy = intentAwareRetrievalPolicy;
        this.retrievalScoreGate = retrievalScoreGate;
        this.rerankingService = rerankingService;
        this.circuitBreaker = circuitBreaker;
        this.includeCases = includeCases;
        log.info("RagPipelineService initialized: include-cases={}, rerank-enabled={}, circuit-breaker-enabled={}",
                includeCases, rerankingService != null, circuitBreaker != null);
    }

    /**
     * Backward-compatible context-only entrypoint.
     */
    public String execute(List<Message> chatHistory, String domain, Object consultationId) {
        return executeContextOnly(chatHistory, domain, consultationId);
    }

    public String executeContextOnly(List<Message> chatHistory, String domain, Object consultationId) {
        return executeDetailed(chatHistory, domain, consultationId).ragContext();
    }

    public RagPipelineResult executeDetailed(List<Message> chatHistory, String domain, Object consultationId) {
        return executeDetailed(chatHistory, domain, consultationId, null);
    }

    public RagPipelineResult executeDetailed(
            List<Message> chatHistory,
            String domain,
            Object consultationId,
            IntentRouterResponse providedIntent
    ) {
        Timer.Sample pipelineSample = ragMetrics.startPipeline();
        IntentRouterResponse intent = providedIntent;

        // Circuit breaker: 연속 실패로 OPEN 상태면 즉시 RAG-less 경로로 우회.
        // recordSuccess/Failure 는 try/catch 안에서 outcome 에 따라 호출.
        if (circuitBreaker != null && !circuitBreaker.tryAcquire()) {
            ragMetrics.stopPipelineEmpty(pipelineSample);
            log.info("RAG pipeline skipped — circuit breaker OPEN: consultationId={}, domain={}",
                    consultationId, domain);
            return RagPipelineResult.empty(intent == null
                    ? IntentRouterResponse.fallback(domain)
                    : intent);
        }

        // 진단용 stage 마커 — catch 블록에서 어느 단계에서 실패했는지 식별 가능.
        // 값 도메인: init | intent | resolve_ids | retrieve | score_gate | rerank | context_build
        String stage = "init";
        // catch 블록이 안전하게 참조할 수 있도록 try 밖에서 선언 (실패 시점에 따라 null/빈 값일 수 있음)
        String vectorQuery = null;
        List<String> lawIds = List.of();
        List<String> categoryIds = List.of();
        int topK = -1;
        try {
            stage = "intent";
            if (intent == null) {
                intent = intentClassificationService.route(chatHistory, domain);
            }
            IntentClassificationResult classification = intent.toClassificationResult();

            stage = "resolve_ids";
            lawIds = categoryLawMappingService.resolveLawIds(
                    classification.matchedNodeIds());
            categoryIds = categoryLawMappingService.resolveCategoryIds(
                    classification.matchedNodeIds());
            vectorQuery = classification.retrievalQueries().isEmpty()
                    ? fallbackQuery(domain)
                    : classification.retrievalQueries().get(0);
            RetrievalStrategyDecision retrievalStrategy = intentAwareRetrievalPolicy.decide(intent, 3);
            if (retrievalStrategy.skipRag()) {
                ragMetrics.stopPipelineEmpty(pipelineSample);
                log.info("RAG skipped by intent-aware policy: consultationId={}, reason={}, intent={}",
                        consultationId, retrievalStrategy.reason(), intent.dialogueIntent());
                return new RagPipelineResult(intent, "", List.of());
            }
            topK = retrievalStrategy.topK();

            String ragContext;
            List<RetrievedDocument> retrievalResults;
            int hits;
            String conversationKey = consultationId == null ? null : consultationId.toString();
            if (includeCases) {
                stage = "retrieve";
                MixedRetrievalResult rawMixed = retrieveMixedWithFallback(
                        vectorQuery,
                        classification.keywords().core(),
                        categoryIds,
                        lawIds,
                        topK,
                        consultationId);
                stage = "score_gate";
                List<LegalChunk> filteredLaws = retrievalScoreGate.filter(
                        rawMixed.laws(), RetrievalScoreMethod.WEIGHTED);
                List<org.example.shield.ai.dto.Precedent> filteredCases = retrievalScoreGate.filter(
                        rawMixed.cases(), RetrievalScoreMethod.WEIGHTED);
                List<RetrievedDocument> filteredMerged = retrievalScoreGate.filter(
                        rawMixed.merged(), RetrievalScoreMethod.WEIGHTED);
                // P5.4 Commit 2: rerank 적용 (mode=off 기본 → no-op).
                stage = "rerank";
                List<RetrievedDocument> rerankedMerged = applyRerank(
                        vectorQuery, filteredMerged, topK, conversationKey);
                MixedRetrievalResult mixed = rebuildMixedAfterRerank(
                        rerankedMerged, filteredLaws, filteredCases);
                stage = "context_build";
                ragContext = ragContextBuilder.build(mixed, classification.intentSummary());
                retrievalResults = mixed.merged();
                hits = mixed.size();
                if (!ragContext.isEmpty()) {
                    log.info("RAG context built (mixed): consultationId={}, laws={}, cases={}, merged={}",
                            consultationId, mixed.laws().size(), mixed.cases().size(), hits);
                }
            } else {
                stage = "retrieve";
                List<LegalChunk> rawChunks = retrieveWithFallback(
                        vectorQuery,
                        classification.keywords().core(),
                        categoryIds,
                        lawIds,
                        topK,
                        consultationId);
                stage = "score_gate";
                List<LegalChunk> chunks = retrievalScoreGate.filter(
                        rawChunks, RetrievalScoreMethod.WEIGHTED);
                // P5.4 Commit 2: rerank 적용.
                stage = "rerank";
                List<LegalChunk> reranked = applyRerank(vectorQuery, chunks, topK, conversationKey);
                stage = "context_build";
                ragContext = ragContextBuilder.build(reranked, classification.intentSummary());
                retrievalResults = new ArrayList<>(reranked);
                hits = reranked.size();
                if (!ragContext.isEmpty()) {
                    log.info("RAG context built: consultationId={}, chunks={}", consultationId, hits);
                }
            }

            if (ragContext.isEmpty()) {
                ragMetrics.stopPipelineEmpty(pipelineSample);
            } else {
                ragMetrics.stopPipelineSuccess(pipelineSample);
            }
            // Circuit breaker: 빈 컨텍스트도 "정상 종료"로 간주 — empty 는 RAG-less fallback 으로
            // graceful degrade 되며, 외부 의존성 장애가 아닌 단순히 매칭이 없는 정상 상황도 포함.
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess();
            }
            return new RagPipelineResult(intent, ragContext, retrievalResults);

        } catch (Exception e) {
            ragMetrics.stopPipelineFailure(pipelineSample);
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            // 전체 스택 + cause 체인은 마지막 인자(throwable)로 전달 — SLF4J 컨벤션.
            log.warn("RAG pipeline failed at stage={}, continuing without RAG: "
                            + "consultationId={}, domain={}, vectorQuery='{}', "
                            + "categoryIds={}, lawIds={}, topK={}, includeCases={}, "
                            + "errorType={}, errorMsg={}",
                    stage, consultationId, domain, vectorQuery,
                    categoryIds, lawIds, topK, includeCases,
                    e.getClass().getName(), e.getMessage(), e);
            return RagPipelineResult.empty(intent == null
                    ? IntentRouterResponse.fallback(domain)
                    : intent);
        }
    }

    /**
     * Law-only retrieve fallback chain: 1차 결과가 0건이면 필터를 단계적으로 완화해 재시도.
     *
     * <ol>
     *   <li>1차: 원래 categoryIds + lawIds 로 호출 (가장 좁은 검색)</li>
     *   <li>0건 + lawIds 가 있었음 → lawIds=null 로 재시도 (level=loose)</li>
     *   <li>여전히 0건 + categoryIds 가 있었음 → categoryIds=null 로 재시도 (level=broad)</li>
     * </ol>
     *
     * <p>각 fallback 시도마다 {@link RagMetrics#recordRetrieveRetry} 로 결과를 기록한다.
     * 필터가 처음부터 비어있는 경우(전체 코퍼스 검색)는 재시도하지 않는다 — 더 완화할 여지가 없으므로.</p>
     */
    private List<LegalChunk> retrieveWithFallback(String vectorQuery,
                                                  List<String> bm25Keywords,
                                                  List<String> categoryIds,
                                                  List<String> lawIds,
                                                  int topK,
                                                  Object consultationId) {
        List<LegalChunk> first = legalRetrievalService.retrieve(
                vectorQuery, bm25Keywords, categoryIds, lawIds, topK);
        if (!first.isEmpty()) {
            return first;
        }
        // Fallback level 1: lawIds 제거 (categoryIds 는 유지)
        boolean hadLawIds = lawIds != null && !lawIds.isEmpty();
        if (hadLawIds) {
            log.info("RAG retrieve 0건 — 필터 완화 재시도 (level=loose, drop lawIds): "
                            + "consultationId={}, lawIds={}",
                    consultationId, lawIds);
            List<LegalChunk> loose = legalRetrievalService.retrieve(
                    vectorQuery, bm25Keywords, categoryIds, null, topK);
            if (!loose.isEmpty()) {
                ragMetrics.recordRetrieveRetry("loose", "hit");
                return loose;
            }
            ragMetrics.recordRetrieveRetry("loose", "empty");
        }
        // Fallback level 2: categoryIds 도 제거 (전체 코퍼스)
        boolean hadCategoryIds = categoryIds != null && !categoryIds.isEmpty();
        if (hadCategoryIds) {
            log.info("RAG retrieve 0건 — 필터 완화 재시도 (level=broad, drop categoryIds+lawIds): "
                            + "consultationId={}, categoryIds={}",
                    consultationId, categoryIds);
            List<LegalChunk> broad = legalRetrievalService.retrieve(
                    vectorQuery, bm25Keywords, null, null, topK);
            if (!broad.isEmpty()) {
                ragMetrics.recordRetrieveRetry("broad", "hit");
                return broad;
            }
            ragMetrics.recordRetrieveRetry("broad", "empty");
        }
        return first; // 어떤 fallback 도 회수 못하면 원래 0건 그대로
    }

    /**
     * Mixed (law + case) retrieve fallback chain — {@link #retrieveWithFallback} 와 같은 정책.
     */
    private MixedRetrievalResult retrieveMixedWithFallback(String vectorQuery,
                                                            List<String> bm25Keywords,
                                                            List<String> categoryIds,
                                                            List<String> lawIds,
                                                            int topK,
                                                            Object consultationId) {
        MixedRetrievalResult first = legalRetrievalService.retrieveMixed(
                vectorQuery, bm25Keywords, categoryIds, lawIds, topK);
        if (first.size() > 0) {
            return first;
        }
        boolean hadLawIds = lawIds != null && !lawIds.isEmpty();
        if (hadLawIds) {
            log.info("RAG retrieveMixed 0건 — 필터 완화 재시도 (level=loose, drop lawIds): "
                            + "consultationId={}, lawIds={}",
                    consultationId, lawIds);
            MixedRetrievalResult loose = legalRetrievalService.retrieveMixed(
                    vectorQuery, bm25Keywords, categoryIds, null, topK);
            if (loose.size() > 0) {
                ragMetrics.recordRetrieveRetry("loose", "hit");
                return loose;
            }
            ragMetrics.recordRetrieveRetry("loose", "empty");
        }
        boolean hadCategoryIds = categoryIds != null && !categoryIds.isEmpty();
        if (hadCategoryIds) {
            log.info("RAG retrieveMixed 0건 — 필터 완화 재시도 (level=broad, drop categoryIds+lawIds): "
                            + "consultationId={}, categoryIds={}",
                    consultationId, categoryIds);
            MixedRetrievalResult broad = legalRetrievalService.retrieveMixed(
                    vectorQuery, bm25Keywords, null, null, topK);
            if (broad.size() > 0) {
                ragMetrics.recordRetrieveRetry("broad", "hit");
                return broad;
            }
            ragMetrics.recordRetrieveRetry("broad", "empty");
        }
        return first;
    }

    /**
     * P5.4 Commit 2 — RerankingService가 있으면 후보를 재정렬, 없으면 원본 그대로.
     * mode=off (기본)일 때는 RerankingService 내부에서 weighted top만 반환.
     */
    private <T extends RetrievedDocument> List<T> applyRerank(
            String query, List<T> candidates, int topK, String conversationId) {
        if (rerankingService == null || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        try {
            return rerankingService.rerank(query, candidates, topK, conversationId);
        } catch (Exception e) {
            // RerankingService 내부에서 fallback 처리하지만 안전망.
            log.warn("RerankingService threw unexpected exception, returning candidates as-is: "
                            + "conversationId={}, query='{}', candidates={}, topK={}, "
                            + "rerankerClass={}, errorType={}, errorMsg={}",
                    conversationId, query, candidates.size(), topK,
                    rerankingService.getClass().getName(),
                    e.getClass().getName(), e.getMessage(), e);
            return candidates;
        }
    }

    /**
     * Reranked merged 순서를 기준으로 MixedRetrievalResult 재구성.
     * laws/cases 리스트는 reranked merged에서 type별 필터링.
     */
    private MixedRetrievalResult rebuildMixedAfterRerank(
            List<RetrievedDocument> rerankedMerged,
            List<LegalChunk> originalLaws,
            List<org.example.shield.ai.dto.Precedent> originalCases) {
        if (rerankedMerged == null || rerankedMerged.isEmpty()) {
            return new MixedRetrievalResult(
                    originalLaws == null ? List.of() : originalLaws,
                    originalCases == null ? List.of() : originalCases,
                    List.of());
        }
        // reranked merged 순서대로 laws/cases 다시 분리
        List<LegalChunk> reorderedLaws = new ArrayList<>();
        List<org.example.shield.ai.dto.Precedent> reorderedCases = new ArrayList<>();
        for (RetrievedDocument doc : rerankedMerged) {
            if (doc instanceof LegalChunk law) {
                reorderedLaws.add(law);
            } else if (doc instanceof org.example.shield.ai.dto.Precedent pre) {
                reorderedCases.add(pre);
            }
        }
        // merged에 누락된 항목 (rerank가 일부만 반환한 경우) 은 원본 순서 유지
        if (Objects.nonNull(originalLaws)) {
            for (LegalChunk law : originalLaws) {
                if (!reorderedLaws.contains(law)) {
                    reorderedLaws.add(law);
                }
            }
        }
        if (Objects.nonNull(originalCases)) {
            for (org.example.shield.ai.dto.Precedent pre : originalCases) {
                if (!reorderedCases.contains(pre)) {
                    reorderedCases.add(pre);
                }
            }
        }
        return new MixedRetrievalResult(reorderedLaws, reorderedCases, rerankedMerged);
    }

    private String fallbackQuery(String domain) {
        return domain == null || domain.isBlank()
                ? "legal consultation"
                : domain + " 관련 법률";
    }
}
