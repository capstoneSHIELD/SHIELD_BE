package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.application.LegalRetrievalService;
import org.example.shield.ai.dto.LegalChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LegalRetrievalService Stub 구현.
 * MongoDB 연동 미구현 시 빈 리스트를 반환.
 * 실제 MongoDB Atlas Vector Search 구현체가 @Primary로 등록되면 교체됨.
 *
 * spring.data.mongodb.uri가 설정되지 않았을 때 활성화 (기본값).
 */
@Component
@ConditionalOnProperty(name = "rag.retrieval.stub", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StubLegalRetrievalService implements LegalRetrievalService {

    @Override
    public List<LegalChunk> retrieve(String vectorQuery, List<String> bm25Keywords,
                                     List<String> lawIds, int topK) {
        log.warn("MongoDB 연동 미구현, RAG 검색 스킵 — vectorQuery='{}', lawIds={}, topK={}",
                vectorQuery, lawIds, topK);
        return List.of();
    }
}
