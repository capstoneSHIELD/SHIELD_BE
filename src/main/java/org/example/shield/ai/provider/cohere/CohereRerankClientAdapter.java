package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.infrastructure.CohereRerankClient;
import org.example.shield.ai.infrastructure.CohereRerankResponse;
import org.example.shield.ai.provider.AiRerankClient;
import org.example.shield.ai.provider.RerankResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cohere {@link AiRerankClient} adapter (Phase P5.4 Commit 1).
 *
 * <p>{@link CohereRerankClient}의 raw 응답을 provider-neutral {@link RerankResult}로 변환한다.
 * Cohere 응답 shape를 application 계층에 누설하지 않는다.
 */
@Component
public class CohereRerankClientAdapter implements AiRerankClient {

    private final CohereRerankClient cohereRerankClient;

    public CohereRerankClientAdapter(CohereRerankClient cohereRerankClient) {
        this.cohereRerankClient = cohereRerankClient;
    }

    @Override
    public RerankResult rerank(String model, String query, List<String> documents, int topN) {
        CohereRerankClient.RerankCallResult call =
                cohereRerankClient.callRerank(model, query, documents, topN);

        CohereRerankResponse response = call.response();
        List<RerankResult.RerankedItem> items = response.getResults().stream()
                .map(r -> new RerankResult.RerankedItem(r.getIndex(), r.getRelevanceScore()))
                .toList();

        Integer searchUnits = null;
        if (response.getMeta() != null && response.getMeta().getBilledUnits() != null) {
            searchUnits = response.getMeta().getBilledUnits().getSearchUnits();
        }

        return new RerankResult(items, call.latencyMs(), searchUnits);
    }
}
