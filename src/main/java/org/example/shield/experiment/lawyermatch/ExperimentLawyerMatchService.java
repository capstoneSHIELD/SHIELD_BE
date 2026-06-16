package org.example.shield.experiment.lawyermatch;

import org.example.shield.experiment.lawyermatch.ExperimentLawyerCorpusStore.Snapshot;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerCorpusStore.StoredLawyer;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.LawyerMatchResult;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchQuery;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.ScoreComponents;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.SyntheticLawyerRow;
import org.example.shield.lawyer.application.LawyerEmbeddingTextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "shield.experiment.adapter", name = "enabled", havingValue = "true")
public class ExperimentLawyerMatchService {

    private static final int VECTOR_DIMENSION = 256;

    private final ExperimentLawyerCorpusStore corpusStore;
    private final LawyerEmbeddingTextBuilder textBuilder;

    public ExperimentLawyerMatchService(
            ExperimentLawyerCorpusStore corpusStore,
            LawyerEmbeddingTextBuilder textBuilder
    ) {
        this.corpusStore = corpusStore;
        this.textBuilder = textBuilder;
    }

    public CorpusLoadResponse loadCorpus(CorpusLoadRequest request) {
        String corpusId = request == null || blank(request.corpusId())
                ? "lawyers-v1"
                : request.corpusId().trim();
        List<SyntheticLawyerRow> rows = request == null || request.lawyers() == null
                ? List.of()
                : request.lawyers();

        List<String> rejected = new ArrayList<>();
        List<StoredLawyer> accepted = new ArrayList<>();
        for (SyntheticLawyerRow row : rows) {
            if (row == null || blank(row.lawyerId())) {
                rejected.add(row == null ? "(null)" : String.valueOf(row.lawyerId()));
                continue;
            }
            if (!blank(row.verificationStatus()) && !"VERIFIED".equalsIgnoreCase(row.verificationStatus())) {
                rejected.add(row.lawyerId());
                continue;
            }
            List<String> practiceNodeIds = mergePracticeNodeIds(row);
            List<String> domains = clean(row.domains());
            List<String> subDomains = clean(row.subDomains());
            List<String> tags = clean(row.tags());
            String embeddingText = blank(row.embeddingText())
                    ? textBuilder.build(domains, subDomains, tags, row.bio())
                    : row.embeddingText().trim();
            accepted.add(new StoredLawyer(
                    row.lawyerId().trim(),
                    blank(row.displayName()) ? row.lawyerId().trim() : row.displayName().trim(),
                    practiceNodeIds,
                    domains,
                    subDomains,
                    tags,
                    row.bio() == null ? "" : row.bio().trim(),
                    embeddingText,
                    vectorize(embeddingText)
            ));
        }

        corpusStore.replace(corpusId, accepted);
        int coverageNodeCount = corpusStore.snapshot().coverageNodeIds().size();
        return new CorpusLoadResponse(corpusId, accepted.size(), rejected.size(), rejected, coverageNodeCount);
    }

    public PreflightResponse preflight(PreflightRequest request) {
        Snapshot snapshot = corpusStore.snapshot();
        List<String> requiredPracticeNodeIds = clean(request == null ? null : request.requiredPracticeNodeIds());
        List<String> missing = requiredPracticeNodeIds.stream()
                .filter(nodeId -> !snapshot.coverageNodeIds().contains(nodeId))
                .toList();
        QueryCompatibility compatibility = checkQueryCompatibility(request == null ? null : request.query());
        Map<String, Double> weights = request == null || request.hybridMatchWeights() == null
                ? Map.of()
                : request.hybridMatchWeights();
        boolean weightsAccepted = weights.isEmpty()
                || (positive(weights.get("cosine"))
                && positive(weights.get("fieldOverlap"))
                && positive(weights.get("keywordOverlap")));
        String errorType = null;
        String errorMessage = null;
        if (!snapshot.loaded()) {
            errorType = "config_error";
            errorMessage = "Synthetic lawyer corpus is not loaded.";
        } else if (!missing.isEmpty()) {
            errorType = "corpus_coverage_gap";
            errorMessage = "Synthetic lawyer corpus does not cover all required practice nodes.";
        } else if (!weightsAccepted) {
            errorType = "hybrid_weight_drift";
            errorMessage = "Hybrid match weights must include positive cosine, fieldOverlap, and keywordOverlap values.";
        }
        return new PreflightResponse(
                snapshot.loaded(),
                snapshot.lawyers().size(),
                snapshot.coverageNodeIds().size(),
                missing.size(),
                missing,
                compatibility.compatible(),
                compatibility.rebuiltHash(),
                compatibility.suppliedHash(),
                weightsAccepted,
                weights,
                errorType,
                errorMessage
        );
    }

    public MatchResponse match(MatchRequest request) {
        long start = System.nanoTime();
        Snapshot snapshot = corpusStore.snapshot();
        int topK = clampTopK(request == null ? null : request.topK());
        if (!snapshot.loaded()) {
            return new MatchResponse(
                    request == null ? null : request.caseId(),
                    request == null ? null : request.matchingMode(),
                    request == null ? null : request.classificationMode(),
                    topK,
                    false,
                    List.of(),
                    elapsedMillis(start),
                    "config_error",
                    "Synthetic lawyer corpus is not loaded."
            );
        }

        MatchQuery query = request == null ? null : request.query();
        QueryCompatibility compatibility = checkQueryCompatibility(query);
        String queryText = buildCurrentServiceQueryText(query);
        double[] queryVector = vectorize(queryText);
        List<LawyerMatchResult> results = snapshot.lawyers().stream()
                .map(lawyer -> toResult(lawyer, cosine(queryVector, lawyer.vector())))
                .sorted(Comparator.comparingDouble(LawyerMatchResult::score).reversed()
                        .thenComparing(LawyerMatchResult::lawyerId))
                .limit(topK)
                .toList();
        List<LawyerMatchResult> ranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            LawyerMatchResult result = results.get(i);
            ranked.add(new LawyerMatchResult(
                    i + 1,
                    result.lawyerId(),
                    result.practiceNodeIds(),
                    result.tags(),
                    result.score(),
                    result.scoreComponents()
            ));
        }

        return new MatchResponse(
                request == null ? null : request.caseId(),
                request == null ? null : request.matchingMode(),
                request == null ? null : request.classificationMode(),
                topK,
                compatibility.compatible(),
                ranked,
                elapsedMillis(start),
                null,
                null
        );
    }

    public String buildCurrentServiceQueryText(MatchQuery query) {
        if (query == null) {
            return "";
        }
        return textBuilder.build(
                clean(query.domains()),
                clean(query.subDomains()),
                clean(query.tags()),
                query.briefContent()
        );
    }

    public static String sha256Hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private LawyerMatchResult toResult(StoredLawyer lawyer, double cosine) {
        return new LawyerMatchResult(
                0,
                lawyer.lawyerId(),
                lawyer.practiceNodeIds(),
                lawyer.tags(),
                cosine,
                new ScoreComponents(cosine, null, null, null)
        );
    }

    private QueryCompatibility checkQueryCompatibility(MatchQuery query) {
        if (query == null || blank(query.queryTextHash())) {
            String rebuiltText = buildCurrentServiceQueryText(query);
            return new QueryCompatibility(true, sha256Hash(rebuiltText), null);
        }
        String rebuiltText = buildCurrentServiceQueryText(query);
        String rebuiltHash = sha256Hash(rebuiltText);
        String suppliedHash = query.queryTextHash().trim();
        return new QueryCompatibility(rebuiltHash.equalsIgnoreCase(suppliedHash), rebuiltHash, suppliedHash);
    }

    private List<String> mergePracticeNodeIds(SyntheticLawyerRow row) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, row.practiceNodeIds());
        if (!blank(row.primaryNodeId())) {
            values.add(row.primaryNodeId().trim());
        }
        add(values, row.secondaryNodeIds());
        return List.copyOf(values);
    }

    private static void add(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (!blank(value)) {
                target.add(value.trim());
            }
        }
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        add(cleaned, values);
        return List.copyOf(cleaned);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean positive(Double value) {
        return value != null && value > 0.0;
    }

    private static int clampTopK(Integer value) {
        if (value == null) {
            return 10;
        }
        return Math.max(1, Math.min(value, 100));
    }

    private static long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static double[] vectorize(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        if (blank(text)) {
            return vector;
        }
        Set<String> seen = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}-]+")) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), VECTOR_DIMENSION);
            double weight = seen.add(token) ? 1.0 : 0.35;
            vector[index] += weight;
        }
        double norm = Math.sqrt(dot(vector, vector));
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
        return vector;
    }

    private static double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            return 0.0;
        }
        return Math.max(0.0, dot(left, right));
    }

    private static double dot(double[] left, double[] right) {
        double total = 0.0;
        for (int i = 0; i < left.length; i++) {
            total += left[i] * right[i];
        }
        return total;
    }

    private record QueryCompatibility(boolean compatible, String rebuiltHash, String suppliedHash) {
    }
}
