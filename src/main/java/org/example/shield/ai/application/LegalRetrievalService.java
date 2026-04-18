package org.example.shield.ai.application;

import org.example.shield.ai.dto.LegalChunk;

import java.util.List;

/**
 * Layer 2: 법률 검색 서비스 인터페이스.
 * MongoDB Atlas Vector Search 하이브리드 검색을 수행.
 * 현재는 Stub 구현 — 실제 MongoDB 연동 시 교체.
 */
public interface LegalRetrievalService {

    /**
     * 하이브리드 검색 (벡터 + BM25).
     *
     * @param vectorQuery   벡터 검색용 자연어 쿼리
     * @param bm25Keywords  BM25 키워드 검색용 핵심 키워드
     * @param lawIds        검색 범위를 한정하는 법령 ID 목록
     * @param topK          반환할 최대 청크 수
     * @return 관련 법률 조문 청크 목록
     */
    List<LegalChunk> retrieve(
            String vectorQuery,
            List<String> bm25Keywords,
            List<String> lawIds,
            int topK
    );
}
