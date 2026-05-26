package org.example.shield.ai.application;

import org.example.shield.ai.application.CitationCoverageEvaluator.CoverageResult;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RagBaselineEvaluationResult;
import org.example.shield.ai.dto.RagBaselineSplitMetrics;
import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RagBaselineEvaluator {

    private final CitationCoverageEvaluator citationCoverageEvaluator;
    private final AiRagOperationalMetrics metrics;

    public RagBaselineEvaluator(CitationCoverageEvaluator citationCoverageEvaluator,
                                @Nullable AiRagOperationalMetrics metrics) {
        this.citationCoverageEvaluator = citationCoverageEvaluator;
        this.metrics = metrics;
    }

    public RagBaselineEvaluationResult evaluate(
            List<RagEvalItem> items,
            Map<String, List<RetrievedDocument>> resultsByEvalId,
            Map<String, Long> latencyMsByEvalId,
            String method
    ) {
        return evaluate(items, resultsByEvalId, latencyMsByEvalId, method, Map.of());
    }

    public RagBaselineEvaluationResult evaluate(
            List<RagEvalItem> items,
            Map<String, List<RetrievedDocument>> resultsByEvalId,
            Map<String, Long> latencyMsByEvalId,
            String method,
            Map<String, String> answerTextsByEvalId
    ) {
        if (items == null || items.isEmpty()) {
            return new RagBaselineEvaluationResult(
                    LocalDate.now(),
                    methodName(method),
                    0,
                    0.0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0,
                    0.0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0,
                    Map.of());
        }

        Metrics overall = evaluateScope(items, resultsByEvalId, latencyMsByEvalId, answerTextsByEvalId, true);
        Map<String, RagBaselineSplitMetrics> splitMetrics = items.stream()
                .filter(item -> item != null && item.id() != null)
                .collect(Collectors.groupingBy(
                        item -> normalizeSplit(item.split()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> evaluateScope(entry.getValue(), resultsByEvalId, latencyMsByEvalId, answerTextsByEvalId, false)
                                .toSplitMetrics(entry.getKey()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        return new RagBaselineEvaluationResult(
                LocalDate.now(),
                methodName(method),
                overall.queryCount(),
                overall.mixedRecallAt5(),
                overall.statuteQueryCount(),
                overall.caseQueryCount(),
                overall.statuteRecallAt5(),
                overall.caseRecallAt5(),
                overall.mixedRecallAt5(),
                overall.mrr(),
                overall.ndcgAt5(),
                overall.gradedNdcgQueryCount(),
                overall.expectedReferenceMentionRate(),
                overall.expectedReferenceMentionQueryCount(),
                overall.emptyRate(),
                overall.latencyP50Ms(),
                overall.latencyP95Ms(),
                0,
                splitMetrics
        );
    }

    private Metrics evaluateScope(
            List<RagEvalItem> items,
            Map<String, List<RetrievedDocument>> resultsByEvalId,
            Map<String, Long> latencyMsByEvalId,
            Map<String, String> answerTextsByEvalId,
            boolean emitCoverageMetrics
    ) {
        int evaluated = 0;
        int mixedHits = 0;
        int statuteQueries = 0;
        int statuteHits = 0;
        int caseQueries = 0;
        int caseHits = 0;
        int empty = 0;
        int gradedNdcgQueries = 0;
        int coverageQueries = 0;
        double reciprocalRankSum = 0.0;
        double ndcgSum = 0.0;
        double coverageRateSum = 0.0;
        List<Long> latencies = new ArrayList<>();

        for (RagEvalItem item : items == null ? List.<RagEvalItem>of() : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            evaluated++;
            List<RetrievedDocument> docs = resultsByEvalId == null
                    ? List.of()
                    : resultsByEvalId.getOrDefault(item.id(), List.of());
            List<RetrievedDocument> top5 = docs.stream().limit(5).toList();
            if (docs.isEmpty()) {
                empty++;
            }

            int firstHitRank = firstRelevantRank(top5, item, ExpectedScope.MIXED);
            if (firstHitRank > 0) {
                mixedHits++;
                reciprocalRankSum += 1.0 / firstHitRank;
            }
            if (!expectedIds(item, ExpectedScope.STATUTE).isEmpty()) {
                statuteQueries++;
                if (hasRelevant(top5, item, ExpectedScope.STATUTE)) {
                    statuteHits++;
                }
            }
            if (!expectedIds(item, ExpectedScope.CASE).isEmpty()) {
                caseQueries++;
                if (hasRelevant(top5, item, ExpectedScope.CASE)) {
                    caseHits++;
                }
            }
            if (item.relevanceJudgments() != null && !item.relevanceJudgments().isEmpty()) {
                gradedNdcgQueries++;
            }
            ndcgSum += ndcgAt5(top5, item);
            if (latencyMsByEvalId != null && latencyMsByEvalId.containsKey(item.id())) {
                latencies.add(latencyMsByEvalId.get(item.id()));
            }

            CoverageStats coverageStats = evaluateCoverage(item, answerTextsByEvalId, emitCoverageMetrics);
            coverageQueries += coverageStats.queryCount();
            coverageRateSum += coverageStats.rateSum();
        }

        return new Metrics(
                evaluated,
                statuteQueries,
                caseQueries,
                ratio(statuteHits, statuteQueries),
                ratio(caseHits, caseQueries),
                ratio(mixedHits, evaluated),
                ratio(reciprocalRankSum, evaluated),
                ratio(ndcgSum, evaluated),
                gradedNdcgQueries,
                ratio(coverageRateSum, coverageQueries),
                coverageQueries,
                ratio(empty, evaluated),
                percentile(latencies, 0.50),
                percentile(latencies, 0.95));
    }

    private CoverageStats evaluateCoverage(
            RagEvalItem item,
            Map<String, String> answerTextsByEvalId,
            boolean emitCoverageMetrics
    ) {
        if (item == null || item.id() == null || answerTextsByEvalId == null || !answerTextsByEvalId.containsKey(item.id())) {
            return CoverageStats.EMPTY;
        }

        String answerText = answerTextsByEvalId.get(item.id());
        CoverageResult coverage = citationCoverageEvaluator.evaluate(answerText, item);
        Set<String> expectedRefs = citationCoverageEvaluator.expectedRefIds(item);
        if (emitCoverageMetrics) {
            recordCoverageMetrics(expectedRefs.size(), coverage);
        }

        Double rate = coverage.expectedReferenceMentionRate();
        if (rate == null) {
            return CoverageStats.EMPTY;
        }
        return new CoverageStats(rate, 1);
    }

    private void recordCoverageMetrics(int expectedCount, CoverageResult coverage) {
        if (metrics == null || coverage == null) {
            return;
        }
        if (coverage.totalMentions() > 0) {
            metrics.recordReferenceMention("answer", "mentioned", coverage.totalMentions());
        }
        if (expectedCount <= 0) {
            return;
        }
        if (coverage.expectedHits() > 0) {
            metrics.recordReferenceMention("expected", "hit", coverage.expectedHits());
        }
        long misses = Math.max(0, expectedCount - coverage.expectedHits());
        if (misses > 0) {
            metrics.recordReferenceMention("expected", "miss", misses);
        }
    }

    private int firstRelevantRank(List<RetrievedDocument> docs, RagEvalItem item, ExpectedScope scope) {
        for (int i = 0; i < docs.size(); i++) {
            if (isRelevant(docs.get(i), item, scope)) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean hasRelevant(List<RetrievedDocument> docs, RagEvalItem item, ExpectedScope scope) {
        return firstRelevantRank(docs, item, scope) > 0;
    }

    private double ndcgAt5(List<RetrievedDocument> docs, RagEvalItem item) {
        double dcg = 0.0;
        boolean graded = item.relevanceJudgments() != null && !item.relevanceJudgments().isEmpty();
        for (int i = 0; i < docs.size(); i++) {
            int grade = relevanceGrade(docs.get(i), item, graded);
            if (grade > 0) {
                dcg += ((Math.pow(2.0, grade) - 1.0) / log2(i + 2));
            }
        }
        List<Integer> idealGrades = idealGrades(item, graded);
        if (idealGrades.isEmpty()) {
            return 0.0;
        }
        double idcg = 0.0;
        for (int i = 0; i < Math.min(5, idealGrades.size()); i++) {
            int grade = idealGrades.get(i);
            idcg += ((Math.pow(2.0, grade) - 1.0) / log2(i + 2));
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private int relevanceGrade(RetrievedDocument doc, RagEvalItem item, boolean graded) {
        if (!graded) {
            return isRelevant(doc, item, ExpectedScope.MIXED) ? 1 : 0;
        }
        for (String id : documentIds(doc)) {
            Integer grade = item.relevanceJudgments().get(id);
            if (grade == null) {
                grade = item.relevanceJudgments().get(normalize(id));
            }
            if (grade != null) {
                return Math.max(0, Math.min(3, grade));
            }
        }
        return 0;
    }

    private List<Integer> idealGrades(RagEvalItem item, boolean graded) {
        if (graded) {
            return item.relevanceJudgments().values().stream()
                    .filter(grade -> grade != null && grade > 0)
                    .map(grade -> Math.max(0, Math.min(3, grade)))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        int expectedCount = expectedLogicalIds(item).size();
        List<Integer> grades = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            grades.add(1);
        }
        return grades;
    }

    private Set<String> expectedLogicalIds(RagEvalItem item) {
        Set<String> expected = new HashSet<>();
        for (String id : item.expectedChunkIds()) {
            if (id != null && !id.isBlank()) {
                expected.add(stripKindPrefix(normalize(id)));
            }
        }
        for (RagEvalLawRef ref : item.expectedLawRefs()) {
            if (ref != null && ref.articleNo() != null && !ref.articleNo().isBlank()) {
                expected.add(normalize(ref.lawId() + ":" + ref.articleNo()));
            }
        }
        for (String id : item.expectedDocumentIds()) {
            if (id != null && !id.isBlank()) {
                expected.add(stripKindPrefix(normalize(id)));
            }
        }
        return expected;
    }

    private boolean isRelevant(RetrievedDocument doc, RagEvalItem item, ExpectedScope scope) {
        if (doc == null || item == null) {
            return false;
        }
        Set<String> expected = expectedIds(item, scope);
        Set<String> actual = documentIds(doc);
        actual.retainAll(expected);
        return !actual.isEmpty();
    }

    private Set<String> expectedIds(RagEvalItem item, ExpectedScope scope) {
        Set<String> expected = new HashSet<>();
        if (scope == ExpectedScope.MIXED || scope == ExpectedScope.STATUTE) {
            for (String id : item.expectedChunkIds()) {
                addExpectedIfKind(expected, id, scope);
            }
            for (RagEvalLawRef ref : item.expectedLawRefs()) {
                if (ref != null && ref.articleNo() != null && !ref.articleNo().isBlank()) {
                    expected.add(normalize(ref.lawId() + ":" + ref.articleNo()));
                    expected.add(normalize("law:" + ref.lawId() + ":" + ref.articleNo()));
                    expected.add(normalize("statute:" + ref.lawId() + ":" + ref.articleNo()));
                }
            }
        }
        for (String id : item.expectedDocumentIds()) {
            addExpectedIfKind(expected, id, scope);
        }
        return expected;
    }

    private void addExpectedIfKind(Set<String> expected, String rawId, ExpectedScope scope) {
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        String id = normalize(rawId);
        String stripped = stripKindPrefix(id);
        boolean law = id.startsWith("law:") || id.startsWith("statute:");
        boolean precedent = id.startsWith("case:") || id.startsWith("precedent:");
        if (scope == ExpectedScope.MIXED
                || (scope == ExpectedScope.STATUTE && !precedent)
                || (scope == ExpectedScope.CASE && (precedent || !law))) {
            expected.add(id);
            expected.add(stripped);
        }
    }

    private Set<String> documentIds(RetrievedDocument doc) {
        Set<String> ids = new HashSet<>();
        if (doc instanceof LegalChunk law) {
            ids.add(normalize(law.lawName() + ":" + law.articleNo()));
            ids.add(normalize("law:" + law.lawName() + ":" + law.articleNo()));
            ids.add(normalize("statute:" + law.lawName() + ":" + law.articleNo()));
            return ids;
        }
        if (doc instanceof Precedent precedent) {
            ids.add(normalize(precedent.caseNo()));
            ids.add(normalize("case:" + precedent.caseNo()));
            ids.add(normalize("precedent:" + precedent.caseNo()));
            return ids;
        }
        return ids;
    }

    private String stripKindPrefix(String id) {
        if (id.startsWith("law:")) {
            return id.substring("law:".length());
        }
        if (id.startsWith("statute:")) {
            return id.substring("statute:".length());
        }
        if (id.startsWith("case:")) {
            return id.substring("case:".length());
        }
        if (id.startsWith("precedent:")) {
            return id.substring("precedent:".length());
        }
        return id;
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

    private double ratio(double numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    private String normalizeSplit(String split) {
        return split == null || split.isBlank() ? "dev" : split.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String methodName(String method) {
        return method == null || method.isBlank() ? "weighted" : method;
    }

    private enum ExpectedScope {
        MIXED,
        STATUTE,
        CASE
    }

    private record CoverageStats(double rateSum, int queryCount) {
        private static final CoverageStats EMPTY = new CoverageStats(0.0, 0);
    }

    private record Metrics(
            int queryCount,
            int statuteQueryCount,
            int caseQueryCount,
            double statuteRecallAt5,
            double caseRecallAt5,
            double mixedRecallAt5,
            double mrr,
            double ndcgAt5,
            int gradedNdcgQueryCount,
            double expectedReferenceMentionRate,
            int expectedReferenceMentionQueryCount,
            double emptyRate,
            double latencyP50Ms,
            double latencyP95Ms
    ) {
        RagBaselineSplitMetrics toSplitMetrics(String split) {
            return new RagBaselineSplitMetrics(
                    split,
                    queryCount,
                    statuteQueryCount,
                    caseQueryCount,
                    statuteRecallAt5,
                    caseRecallAt5,
                    mixedRecallAt5,
                    mrr,
                    ndcgAt5,
                    gradedNdcgQueryCount,
                    expectedReferenceMentionRate,
                    expectedReferenceMentionQueryCount,
                    emptyRate,
                    latencyP50Ms,
                    latencyP95Ms);
        }
    }
}
