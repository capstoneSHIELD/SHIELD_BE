package org.example.shield.ai.application;

import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.example.shield.ai.dto.RetrievedDocument;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RagBaselineEvaluator {

    public RagBaselineEvaluationResult evaluate(
            List<RagEvalItem> items,
            Map<String, List<RetrievedDocument>> resultsByEvalId,
            Map<String, Long> latencyMsByEvalId,
            String method
    ) {
        if (items == null || items.isEmpty()) {
            return new RagBaselineEvaluationResult(LocalDate.now(), methodName(method),
                    0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }

        int evaluated = 0;
        int recallHits = 0;
        double reciprocalRankSum = 0.0;
        double ndcgSum = 0.0;
        List<Long> latencies = new ArrayList<>();

        for (RagEvalItem item : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            evaluated++;
            List<RetrievedDocument> docs = resultsByEvalId == null
                    ? List.of()
                    : resultsByEvalId.getOrDefault(item.id(), List.of());
            List<RetrievedDocument> top5 = docs.stream().limit(5).toList();
            int firstHitRank = firstRelevantRank(top5, item);
            if (firstHitRank > 0) {
                recallHits++;
                reciprocalRankSum += 1.0 / firstHitRank;
            }
            ndcgSum += ndcgAt5(top5, item);
            if (latencyMsByEvalId != null && latencyMsByEvalId.containsKey(item.id())) {
                latencies.add(latencyMsByEvalId.get(item.id()));
            }
        }

        return new RagBaselineEvaluationResult(
                LocalDate.now(),
                methodName(method),
                evaluated,
                evaluated == 0 ? 0.0 : recallHits / (double) evaluated,
                evaluated == 0 ? 0.0 : reciprocalRankSum / evaluated,
                evaluated == 0 ? 0.0 : ndcgSum / evaluated,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                0
        );
    }

    private int firstRelevantRank(List<RetrievedDocument> docs, RagEvalItem item) {
        for (int i = 0; i < docs.size(); i++) {
            if (isRelevant(docs.get(i), item)) {
                return i + 1;
            }
        }
        return 0;
    }

    private double ndcgAt5(List<RetrievedDocument> docs, RagEvalItem item) {
        double dcg = 0.0;
        int relevantCount = 0;
        for (int i = 0; i < docs.size(); i++) {
            if (isRelevant(docs.get(i), item)) {
                relevantCount++;
                dcg += 1.0 / log2(i + 2);
            }
        }
        int idealHits = Math.min(5, Math.max(relevantCount, expectedCount(item)));
        if (idealHits == 0) {
            return 0.0;
        }
        double idcg = 0.0;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private boolean isRelevant(RetrievedDocument doc, RagEvalItem item) {
        if (doc == null || item == null) {
            return false;
        }
        Set<String> expected = expectedIds(item);
        return expected.contains(documentId(doc));
    }

    private Set<String> expectedIds(RagEvalItem item) {
        Set<String> expected = new HashSet<>();
        for (String id : item.expectedChunkIds()) {
            if (id != null && !id.isBlank()) {
                expected.add(normalize(id));
            }
        }
        for (RagEvalLawRef ref : item.expectedLawRefs()) {
            if (ref != null && ref.articleNo() != null && !ref.articleNo().isBlank()) {
                expected.add(normalize(ref.lawId() + ":" + ref.articleNo()));
            }
        }
        return expected;
    }

    private String documentId(RetrievedDocument doc) {
        if (doc instanceof LegalChunk law) {
            return normalize(law.lawName() + ":" + law.articleNo());
        }
        if (doc instanceof Precedent precedent) {
            return normalize(precedent.caseNo());
        }
        return "";
    }

    private int expectedCount(RagEvalItem item) {
        return expectedIds(item).size();
    }

    private double percentile(List<Long> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String methodName(String method) {
        return method == null || method.isBlank() ? "weighted" : method;
    }
}
