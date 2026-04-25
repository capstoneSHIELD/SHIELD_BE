package org.example.shield.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.MixedRetrievalResult;
import org.example.shield.ai.dto.RagResult;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 파이프라인 오케스트레이터.
 * Layer 1(의도 분류) → Layer 2(법률·판례 검색) → Layer 3(컨텍스트 빌드) 를 조율.
 *
 * <p>{@code rag.retrieval.include-cases=true} 인 경우 C-5 경로로 법령 + 판례를 병합해 불러온다.
 * 기본값은 {@code false} 로 법령 전용 경로 유지 — Phase C-4 까지의 동작 호환성.</p>
 */
@Service
@Slf4j
public class RagPipelineService {

    private final IntentClassificationService intentClassificationService;
    private final CategoryLawMappingService categoryLawMappingService;
    private final LegalRetrievalService legalRetrievalService;
    private final RagContextBuilder ragContextBuilder;
    private final boolean includeCases;

    public RagPipelineService(IntentClassificationService intentClassificationService,
                              CategoryLawMappingService categoryLawMappingService,
                              LegalRetrievalService legalRetrievalService,
                              RagContextBuilder ragContextBuilder,
                              @Value("${rag.retrieval.include-cases:false}") boolean includeCases) {
        this.intentClassificationService = intentClassificationService;
        this.categoryLawMappingService = categoryLawMappingService;
        this.legalRetrievalService = legalRetrievalService;
        this.ragContextBuilder = ragContextBuilder;
        this.includeCases = includeCases;
        log.info("RagPipelineService 초기화 — include-cases={}", includeCases);
    }

    /**
     * RAG 파이프라인 실행.
     *
     * @param chatHistory    대화 내역
     * @param domain         상담 대분류 (온톨로지 L1)
     * @param consultationId 로깅용 상담 ID
     * @return RAG 컨텍스트 + Layer1 분류 결과를 묶은 {@link RagResult}.
     *         실패 시 {@link RagResult#empty()} (context 빈 문자열, classification null).
     *         호출자는 classification 을 활성 도메인 결정 등에 재사용 가능.
     */
    public RagResult execute(List<Message> chatHistory, String domain, Object consultationId) {
        try {
            // Layer 1: 의도 분류
            IntentClassificationResult classification =
                    intentClassificationService.classify(chatHistory, domain);

            // Layer 2: 온톨로지 매핑으로 law_id / category_ids 해결 (Issue #65 복구)
            // YAML 의 law_id 가 slug 포맷으로 통일되고 lsi_code 가 보조 필드로 분리되어
            // CategoryLawMappingService.resolveLawIds 가 곧장 DB slug 와 호환되는 값을 반환.
            List<String> matchedNodeIds = classification.matchedNodeIds();
            List<String> lawIds = categoryLawMappingService.resolveLawIds(matchedNodeIds);
            List<String> categoryIds = categoryLawMappingService.resolveCategoryIds(matchedNodeIds);
            log.debug("RAG 필터 해결: consultationId={}, matchedNodes={}, lawIds={}, categoryIds={}",
                    consultationId, matchedNodeIds, lawIds, categoryIds);

            String vectorQuery = classification.retrievalQueries().isEmpty()
                    ? domain + " 관련 법률"
                    : classification.retrievalQueries().get(0);

            String ragContext;
            int hits;
            if (includeCases) {
                MixedRetrievalResult mixed = legalRetrievalService.retrieveMixed(
                        vectorQuery,
                        classification.keywords().core(),
                        categoryIds,
                        lawIds,
                        3);
                ragContext = ragContextBuilder.build(mixed, classification.intentSummary());
                hits = mixed.size();
                if (!ragContext.isEmpty()) {
                    log.info("RAG 컨텍스트 생성 완료 (mixed): consultationId={}, " +
                                    "lawIdsApplied={}, categoryIdsApplied={}, laws={}, cases={}, merged={}",
                            consultationId, lawIds.size(), categoryIds.size(),
                            mixed.laws().size(), mixed.cases().size(), hits);
                }
            } else {
                List<LegalChunk> chunks = legalRetrievalService.retrieve(
                        vectorQuery,
                        classification.keywords().core(),
                        lawIds, 3);
                ragContext = ragContextBuilder.build(chunks, classification.intentSummary());
                hits = chunks.size();
                if (!ragContext.isEmpty()) {
                    log.info("RAG 컨텍스트 생성 완료: consultationId={}, lawIdsApplied={}, chunks={}",
                            consultationId, lawIds.size(), hits);
                }
            }
            return new RagResult(ragContext, classification);

        } catch (Exception e) {
            log.warn("RAG 파이프라인 실패, 폴백 (RAG 없이 진행): consultationId={}, error={}",
                    consultationId, e.getMessage());
            return RagResult.empty();
        }
    }
}
