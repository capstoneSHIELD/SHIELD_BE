package org.example.shield.ai.dto;

/**
 * 법률 조문 청크 데이터.
 * Layer 2 벡터 검색 결과로 반환되는 개별 법률 조문 단위.
 */
public record LegalChunk(
        String lawName,
        String articleNo,
        String articleTitle,
        String content,
        String effectiveDate,
        String sourceUrl,
        double score
) {
}
