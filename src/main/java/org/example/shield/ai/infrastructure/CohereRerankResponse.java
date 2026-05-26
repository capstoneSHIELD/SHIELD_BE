package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Cohere {@code POST /v2/rerank} 응답 DTO (Phase P5.4 Commit 1).
 *
 * <pre>
 * {
 *   "id": "rerank-completion-id",
 *   "results": [
 *     {"index": 2, "relevance_score": 0.95},
 *     {"index": 0, "relevance_score": 0.78},
 *     ...
 *   ],
 *   "meta": {
 *     "billed_units": { "search_units": 1 }
 *   }
 * }
 * </pre>
 *
 * <p>{@code search_units}는 1 = 100 docs 기준 청구 단위.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CohereRerankResponse {

    private String id;
    private List<RerankItem> results;
    private Meta meta;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankItem {
        private int index;

        @JsonProperty("relevance_score")
        private double relevanceScore;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        @JsonProperty("billed_units")
        private BilledUnits billedUnits;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BilledUnits {
        @JsonProperty("search_units")
        private Integer searchUnits;
    }
}
