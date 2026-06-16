package org.example.shield.experiment.lawyermatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(prefix = "shield.experiment.adapter", name = "enabled", havingValue = "true")
public class ExperimentLawyerCorpusStore {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public void replace(String corpusId, List<StoredLawyer> lawyers) {
        Set<String> coverage = lawyers.stream()
                .flatMap(lawyer -> lawyer.practiceNodeIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        snapshot.set(new Snapshot(corpusId, List.copyOf(lawyers), coverage));
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public record Snapshot(
            String corpusId,
            List<StoredLawyer> lawyers,
            Set<String> coverageNodeIds
    ) {
        static Snapshot empty() {
            return new Snapshot(null, List.of(), Set.of());
        }

        boolean loaded() {
            return corpusId != null && !lawyers.isEmpty();
        }
    }

    public record StoredLawyer(
            String lawyerId,
            String displayName,
            List<String> practiceNodeIds,
            List<String> domains,
            List<String> subDomains,
            List<String> tags,
            String bio,
            String embeddingText,
            double[] vector
    ) {
    }
}
