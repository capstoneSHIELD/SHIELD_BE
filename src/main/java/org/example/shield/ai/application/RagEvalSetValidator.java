package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.example.shield.ai.dto.RagEvalSetValidationResult;
import org.example.shield.ai.dto.RagRetrievalHint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RagEvalSetValidator {

    public static final Map<String, Long> V1_1_SPLIT_COUNTS = Map.of(
            "dev", 80L,
            "calibration", 60L,
            "holdout", 60L);
    public static final double MIN_DOUBLE_LABEL_RATE = 0.20d;
    private static final Set<String> VALID_SPLITS = V1_1_SPLIT_COUNTS.keySet();

    public RagEvalSetValidationResult validateV1_1(
            List<RagEvalItem> items,
            Map<String, RagRetrievalHint> hintsByNodeId
    ) {
        return validate(items, V1_1_SPLIT_COUNTS, hintsByNodeId, true);
    }

    public RagEvalSetValidationResult validate(
            List<RagEvalItem> items,
            Map<String, Long> expectedSplitCounts,
            Map<String, RagRetrievalHint> hintsByNodeId,
            boolean requireDoubleLabelRate
    ) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RagEvalItem> safeItems = items == null ? List.of() : items;

        for (RagEvalItem item : safeItems) {
            validateItem(item, failures, warnings, hintsByNodeId);
        }

        Map<String, Long> splitCounts = safeItems.stream()
                .filter(item -> item != null && item.id() != null)
                .collect(Collectors.groupingBy(
                        item -> normalizeSplit(item.split()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        if (expectedSplitCounts != null && !expectedSplitCounts.isEmpty()) {
            expectedSplitCounts.forEach((split, expected) -> {
                long actual = splitCounts.getOrDefault(split, 0L);
                if (actual != expected) {
                    failures.add("split count mismatch: " + split + " expected=" + expected + " actual=" + actual);
                }
            });
        }

        Map<String, Long> reviewersById = safeItems.stream()
                .filter(item -> item != null && !isBlank(item.id()) && !isBlank(item.reviewer()))
                .collect(Collectors.groupingBy(
                        RagEvalItem::id,
                        Collectors.mapping(RagEvalItem::reviewer, Collectors.collectingAndThen(
                                Collectors.toSet(),
                                reviewers -> (long) reviewers.size()))));
        int uniqueItems = (int) safeItems.stream()
                .filter(item -> item != null && !isBlank(item.id()))
                .map(RagEvalItem::id)
                .distinct()
                .count();
        int doubleLabeled = (int) reviewersById.values().stream()
                .filter(count -> count >= 2)
                .count();
        double doubleLabelRate = uniqueItems == 0 ? 0.0 : doubleLabeled / (double) uniqueItems;
        if (requireDoubleLabelRate && doubleLabelRate < MIN_DOUBLE_LABEL_RATE) {
            failures.add("double label rate below 20%: " + doubleLabelRate);
        }

        return new RagEvalSetValidationResult(
                failures.isEmpty(),
                failures,
                warnings,
                splitCounts,
                safeItems.size(),
                doubleLabeled,
                doubleLabelRate);
    }

    private void validateItem(
            RagEvalItem item,
            List<String> failures,
            List<String> warnings,
            Map<String, RagRetrievalHint> hintsByNodeId
    ) {
        if (item == null) {
            failures.add("item is null");
            return;
        }
        String label = isBlank(item.id()) ? "<blank-id>" : item.id();
        if (isBlank(item.id())) {
            failures.add("id is blank");
        }
        String split = normalizeSplit(item.split());
        if (!VALID_SPLITS.contains(split)) {
            failures.add(label + " has invalid split: " + item.split());
        }
        if (isBlank(item.nodeId())) {
            failures.add(label + " nodeId is blank");
        }
        if (isBlank(item.query())) {
            failures.add(label + " query is blank");
        }
        if (isBlank(item.source())) {
            warnings.add(label + " source is blank");
        }
        if (isBlank(item.reviewer())) {
            warnings.add(label + " reviewer is blank");
        }
        if (!hasExpectedDocument(item)) {
            failures.add(label + " has no expected document reference");
        }
        for (RagEvalLawRef ref : item.expectedLawRefs()) {
            if (ref == null || isBlank(ref.articleNo())) {
                failures.add(label + " has invalid expectedLawRef");
            }
        }
        item.relevanceJudgments().forEach((docId, grade) -> {
            if (isBlank(docId)) {
                failures.add(label + " has blank relevance judgment id");
            }
            if (grade == null || grade < 0 || grade > 3) {
                failures.add(label + " has out-of-range relevance grade: " + docId + "=" + grade);
            }
        });
        validateLeakage(item, split, failures, warnings, hintsByNodeId);
    }

    private void validateLeakage(
            RagEvalItem item,
            String split,
            List<String> failures,
            List<String> warnings,
            Map<String, RagRetrievalHint> hintsByNodeId
    ) {
        if (!"holdout".equals(split)) {
            return;
        }
        String label = item.id();
        String source = item.source() == null ? "" : item.source().toLowerCase(Locale.ROOT);
        if (source.contains("yaml") || source.contains("evidence") || source.contains("hint")) {
            failures.add(label + " holdout source is leakage-prone: " + item.source());
        }
        if (hintsByNodeId == null || isBlank(item.nodeId())) {
            return;
        }
        RagRetrievalHint hint = hintsByNodeId.get(item.nodeId());
        if (hint == null || hint.queryTerms().isEmpty()) {
            return;
        }
        Set<String> hintTerms = hint.queryTerms().stream()
                .filter(term -> !isBlank(term))
                .map(term -> term.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String keyword : item.keywords()) {
            if (keyword != null && hintTerms.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                warnings.add(label + " holdout keyword exactly matches L3 hint term: " + keyword);
            }
        }
    }

    private boolean hasExpectedDocument(RagEvalItem item) {
        return !item.expectedChunkIds().isEmpty()
                || !item.expectedLawRefs().isEmpty()
                || !item.expectedDocumentIds().isEmpty();
    }

    private String normalizeSplit(String split) {
        return split == null || split.isBlank() ? "dev" : split.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
