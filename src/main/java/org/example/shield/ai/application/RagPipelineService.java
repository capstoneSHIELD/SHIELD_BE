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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private final boolean includeCases;

    public RagPipelineService(IntentClassificationService intentClassificationService,
                              CategoryLawMappingService categoryLawMappingService,
                              LegalRetrievalService legalRetrievalService,
                              RagContextBuilder ragContextBuilder,
                              RagMetrics ragMetrics,
                              IntentAwareRetrievalPolicy intentAwareRetrievalPolicy,
                              RetrievalScoreGate retrievalScoreGate,
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this.intentClassificationService = intentClassificationService;
        this.categoryLawMappingService = categoryLawMappingService;
        this.legalRetrievalService = legalRetrievalService;
        this.ragContextBuilder = ragContextBuilder;
        this.ragMetrics = ragMetrics;
        this.intentAwareRetrievalPolicy = intentAwareRetrievalPolicy;
        this.retrievalScoreGate = retrievalScoreGate;
        this.includeCases = includeCases;
        log.info("RagPipelineService initialized: include-cases={}", includeCases);
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
                MixedRetrievalResult mixed = new MixedRetrievalResult(
                        filteredLaws, filteredCases, filteredMerged);
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
                ragContext = ragContextBuilder.build(chunks, classification.intentSummary());
                retrievalResults = new ArrayList<>(chunks);
                hits = chunks.size();
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

    private String fallbackQuery(String domain) {
        return domain == null || domain.isBlank()
                ? "legal consultation"
                : domain + " 관련 법률";
    }
}
