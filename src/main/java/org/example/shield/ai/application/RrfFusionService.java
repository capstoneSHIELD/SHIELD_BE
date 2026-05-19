package org.example.shield.ai.application;

import org.example.shield.ai.dto.RrfFusionInput;
import org.example.shield.ai.dto.RrfFusionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RrfFusionService {

    @Value("${app.ai.rag.rrf-k:60}")
    private int defaultRrfK;

    public List<RrfFusionResult> fuse(List<List<RrfFusionInput>> rankedLists, int limit) {
        return fuse(rankedLists, defaultRrfK, limit);
    }

    public List<RrfFusionResult> fuse(List<List<RrfFusionInput>> rankedLists, int rrfK, int limit) {
        if (rankedLists == null || rankedLists.isEmpty() || limit <= 0) {
            return List.of();
        }

        int safeK = Math.max(1, rrfK);
        Map<String, Accumulator> byId = new ConcurrentHashMap<>();
        for (List<RrfFusionInput> rankedList : rankedLists) {
            if (rankedList == null) {
                continue;
            }
            for (int index = 0; index < rankedList.size(); index++) {
                RrfFusionInput item = rankedList.get(index);
                if (item == null || item.id().isBlank()) {
                    continue;
                }
                int rank = item.rank() > 0 ? item.rank() : index + 1;
                byId.computeIfAbsent(item.id(), Accumulator::new)
                        .add(item.source(), rank, item.originalScore(), 1.0d / (safeK + rank));
            }
        }

        return byId.values().stream()
                .map(Accumulator::toResult)
                .sorted(Comparator
                        .comparingDouble(RrfFusionResult::rrfScore).reversed()
                        .thenComparingInt(RrfFusionResult::bestRank)
                        .thenComparing(RrfFusionResult::id))
                .limit(limit)
                .toList();
    }

    private static final class Accumulator {
        private final String id;
        private final Set<String> sources = new LinkedHashSet<>();
        private double score;
        private int bestRank = Integer.MAX_VALUE;
        private double bestOriginalScore = Double.NEGATIVE_INFINITY;

        private Accumulator(String id) {
            this.id = id;
        }

        private void add(String source, int rank, double originalScore, double rrfContribution) {
            sources.add(source);
            score += rrfContribution;
            if (rank < bestRank) {
                bestRank = rank;
            }
            if (originalScore > bestOriginalScore) {
                bestOriginalScore = originalScore;
            }
        }

        private RrfFusionResult toResult() {
            return new RrfFusionResult(
                    id,
                    score,
                    bestRank == Integer.MAX_VALUE ? 0 : bestRank,
                    bestOriginalScore == Double.NEGATIVE_INFINITY ? 0.0d : bestOriginalScore,
                    new ArrayList<>(sources)
            );
        }
    }
}
