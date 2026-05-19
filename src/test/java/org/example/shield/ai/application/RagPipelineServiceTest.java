package org.example.shield.ai.application;

import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RagPipelineServiceTest {

    @Mock private IntentClassificationService intentClassificationService;
    @Mock private CategoryLawMappingService categoryLawMappingService;
    @Mock private LegalRetrievalService legalRetrievalService;
    @Mock private RagContextBuilder ragContextBuilder;
    @Mock private RagMetrics ragMetrics;
    @Mock private IntentAwareRetrievalPolicy intentAwareRetrievalPolicy;
    @Mock private RetrievalScoreGate retrievalScoreGate;

    private final List<String> matchedNodeIds = List.of("law-001-02-02");
    private final List<String> resolvedCategoryIds = List.of("group:leasing", "group:jeonse");
    private final List<String> resolvedLawIds = List.of("LSI249999");
    private final List<String> keywords = List.of("보증금");
    private final String query = "임대차 보증금 반환";

    @BeforeEach
    void setUp() {
        given(intentAwareRetrievalPolicy.decide(any(), eq(3)))
                .willReturn(RetrievalStrategyDecision.baseline(3, "test"));
        given(retrievalScoreGate.filter(anyList(), eq(RetrievalScoreMethod.WEIGHTED)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(categoryLawMappingService.resolveLawIds(matchedNodeIds)).willReturn(resolvedLawIds);
        given(categoryLawMappingService.resolveCategoryIds(matchedNodeIds)).willReturn(resolvedCategoryIds);
    }

    @Test
    @DisplayName("law-only RAG maps ontology node ids to DB category_ids before retrieval")
    void executeDetailed_lawOnlyUsesResolvedCategoryIds() {
        IntentRouterResponse intent = intent();
        LegalChunk chunk = chunk();
        given(legalRetrievalService.retrieve(
                query,
                keywords,
                resolvedCategoryIds,
                resolvedLawIds,
                3))
                .willReturn(List.of(chunk));
        given(ragContextBuilder.build(List.of(chunk), "lease deposit"))
                .willReturn("RAG CONTEXT");

        RagPipelineResult result = service(false)
                .executeDetailed(List.<Message>of(), "부동산 거래", "cid", intent);

        assertThat(result.ragContext()).isEqualTo("RAG CONTEXT");
        verify(categoryLawMappingService).resolveCategoryIds(matchedNodeIds);
        verify(legalRetrievalService).retrieve(query, keywords, resolvedCategoryIds, resolvedLawIds, 3);
    }

    @Test
    @DisplayName("mixed law/case RAG maps ontology node ids to DB category_ids before retrieval")
    void executeDetailed_mixedUsesResolvedCategoryIds() {
        IntentRouterResponse intent = intent();
        LegalChunk chunk = chunk();
        MixedRetrievalResult raw = new MixedRetrievalResult(
                List.of(chunk),
                List.of(),
                List.<RetrievedDocument>of(chunk));
        given(legalRetrievalService.retrieveMixed(
                query,
                keywords,
                resolvedCategoryIds,
                resolvedLawIds,
                3))
                .willReturn(raw);
        given(ragContextBuilder.build(any(MixedRetrievalResult.class), eq("lease deposit")))
                .willReturn("MIXED RAG CONTEXT");

        RagPipelineResult result = service(true)
                .executeDetailed(List.<Message>of(), "부동산 거래", "cid", intent);

        assertThat(result.ragContext()).isEqualTo("MIXED RAG CONTEXT");
        verify(categoryLawMappingService).resolveCategoryIds(matchedNodeIds);
        verify(legalRetrievalService).retrieveMixed(query, keywords, resolvedCategoryIds, resolvedLawIds, 3);
    }

    private RagPipelineService service(boolean includeCases) {
        return new RagPipelineService(
                intentClassificationService,
                categoryLawMappingService,
                legalRetrievalService,
                ragContextBuilder,
                ragMetrics,
                intentAwareRetrievalPolicy,
                retrievalScoreGate,
                includeCases);
    }

    private IntentRouterResponse intent() {
        IntentClassificationResult classification = new IntentClassificationResult(
                "2.0",
                "lease deposit",
                List.of(new IntentClassificationResult.MatchedNode("law-001-02-02", "보증금 및 차임", 0.95)),
                new IntentClassificationResult.Keywords(keywords, List.of()),
                List.of(query));
        return new IntentRouterResponse(
                "2.0",
                DialogueIntent.PROVIDE_INFO,
                0.95,
                List.of(),
                CaseTypeResult.empty(),
                List.of(query),
                List.of(),
                false,
                classification);
    }

    private LegalChunk chunk() {
        return new LegalChunk(
                "주택임대차보호법",
                "제3조",
                "대항력 등",
                "임대차 관련 조문",
                "2026-01-01",
                "https://law.go.kr",
                0.91);
    }
}
