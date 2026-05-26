package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Cohere {@code POST /v2/rerank} 요청 DTO (Phase P5.4 Commit 1).
 *
 * <pre>
 * {
 *   "model": "rerank-v3.5",
 *   "query": "사용자 쿼리",
 *   "documents": ["문서1", "문서2", ...],
 *   "top_n": 5,
 *   "return_documents": false
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CohereRerankRequest {

    private String model;
    private String query;
    private List<String> documents;

    @JsonProperty("top_n")
    private Integer topN;

    /** 응답에 documents 본문 포함 여부. 본 시스템은 index만 사용하므로 false. */
    @JsonProperty("return_documents")
    private Boolean returnDocuments;

    public static CohereRerankRequest of(String model, String query, List<String> documents, int topN) {
        return CohereRerankRequest.builder()
                .model(model)
                .query(query)
                .documents(documents)
                .topN(topN)
                .returnDocuments(false)
                .build();
    }
}
