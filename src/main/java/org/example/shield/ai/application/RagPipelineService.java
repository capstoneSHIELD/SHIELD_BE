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
    private final boolean includeCases;

    /** Legacy 8-arg constructor — RerankingService 미주입 (BC). */
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
                null, includeCases);
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
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this.intentClassificationService = intentClassificationService;
        this.categoryLawMappingService = categoryLawMappingService;
        this.legalRetrievalService = legalRetrievalService;
        this.ragContextBuilder = ragContextBuilder;
        this.ragMetrics = ragMetrics;
        this.intentAwareRetrievalPolicy = intentAwareRetrievalPolicy;
        this.retrievalScoreGate = retrievalScoreGate;
        this.rerankingService = rerankingService;
        this.includeCases = includeCases;
        log.info("RagPipelineService initialized: include-cases={}, rerank-enabled={}",
                includeCases, rerankingService != null);
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
        try {
            if (intent == null) {
                intent = intentClassificationService.route(chatHistory, domain);
            }
            IntentClassificationResult classification = intent.toClassificationResult();

            List<String> lawIds = categoryLawMappingService.resolveLawIds(
                    classification.matchedNodeIds());
            List<String> categoryIds = categoryLawMappingService.resolveCategoryIds(
                    classification.matchedNodeIds());
            String vectorQuery = classification.retrievalQueries().isEmpty()
                    ? fallbackQuery(domain)
                    : classification.retrievalQueries().get(0);
            RetrievalStrategyDecision retrievalStrategy = intentAwareRetrievalPolicy.decide(intent, 3);
            if (retrievalStrategy.skipRag()) {
                ragMetrics.stopPipelineEmpty(pipelineSample);
                log.info("RAG skipped by intent-aware policy: consultationId={}, reason={}, intent={}",
                        consultationId, retrievalStrategy.reason(), intent.dialogueIntent());
                return new RagPipelineResult(intent, "", List.of());
            }
            int topK = retrievalStrategy.topK();

            String ragContext;
            List<RetrievedDocument> retrievalResults;
            int hits;
            String conversationKey = consultationId == null ? null : consultationId.toString();
            if (includeCases) {
                MixedRetrievalResult rawMixed = legalRetrievalService.retrieveMixed(
                        vectorQuery,
                        classification.keywords().core(),
                        categoryIds,
                        lawIds,
                        topK);
                List<LegalChunk> filteredLaws = retrievalScoreGate.filter(
                        rawMixed.laws(), RetrievalScoreMethod.WEIGHTED);
                List<org.example.shield.ai.dto.Precedent> filteredCases = retrievalScoreGate.filter(
                        rawMixed.cases(), RetrievalScoreMethod.WEIGHTED);
                List<RetrievedDocument> filteredMerged = retrievalScoreGate.filter(
                        rawMixed.merged(), RetrievalScoreMethod.WEIGHTED);
                // P5.4 Commit 2: rerank 적용 (mode=off 기본 → no-op).
                List<RetrievedDocument> rerankedMerged = applyRerank(
                        vectorQuery, filteredMerged, topK, conversationKey);
                MixedRetrievalResult mixed = rebuildMixedAfterRerank(
                        rerankedMerged, filteredLaws, filteredCases);
                ragContext = ragContextBuilder.build(mixed, classification.intentSummary());
                retrievalResults = mixed.merged();
                hits = mixed.size();
                if (!ragContext.isEmpty()) {
                    log.info("RAG context built (mixed): consultationId={}, laws={}, cases={}, merged={}",
                            consultationId, mixed.laws().size(), mixed.cases().size(), hits);
                }
            } else {
                List<LegalChunk> rawChunks = legalRetrievalService.retrieve(
                        vectorQuery,
                        classification.keywords().core(),
                        categoryIds,
                        lawIds,
                        topK);
                List<LegalChunk> chunks = retrievalScoreGate.filter(
                        rawChunks, RetrievalScoreMethod.WEIGHTED);
                // P5.4 Commit 2: rerank 적용.
                List<LegalChunk> reranked = applyRerank(vectorQuery, chunks, topK, conversationKey);
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
            return new RagPipelineResult(intent, ragContext, retrievalResults);

        } catch (Exception e) {
            ragMetrics.stopPipelineFailure(pipelineSample);
            log.warn("RAG pipeline failed, continuing without RAG: consultationId={}, error={}",
                    consultationId, e.getMessage());
            return RagPipelineResult.empty(intent == null
                    ? IntentRouterResponse.fallback(domain)
                    : intent);
        }
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
            log.warn("RerankingService threw unexpected exception, returning candidates as-is: {}",
                    e.getMessage());
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
