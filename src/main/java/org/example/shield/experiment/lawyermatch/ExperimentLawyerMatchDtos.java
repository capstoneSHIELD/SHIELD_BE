package org.example.shield.experiment.lawyermatch;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;

public final class ExperimentLawyerMatchDtos {

    private ExperimentLawyerMatchDtos() {
    }

    public record CorpusLoadRequest(
            String corpusId,
            List<SyntheticLawyerRow> lawyers
    ) {
    }

    public record CorpusLoadResponse(
            String corpusId,
            int acceptedCount,
            int rejectedCount,
            List<String> rejectedLawyerIds,
            int coverageNodeCount
    ) {
    }

    public record SyntheticLawyerRow(
            @JsonAlias({"lawyer_id"}) String lawyerId,
            @JsonAlias({"display_name"}) String displayName,
            @JsonAlias({"practice_node_ids"}) List<String> practiceNodeIds,
            @JsonAlias({"primary_node_id"}) String primaryNodeId,
            @JsonAlias({"secondary_node_ids"}) List<String> secondaryNodeIds,
            List<String> domains,
            @JsonAlias({"sub_domains", "subDomains"}) List<String> subDomains,
            List<String> tags,
            @JsonAlias({"experience_years"}) Integer experienceYears,
            String region,
            String bio,
            @JsonAlias({"embedding_text"}) String embeddingText,
            @JsonAlias({"verification_status"}) String verificationStatus
    ) {
    }

    public record PreflightRequest(
            List<String> requiredPracticeNodeIds,
            Map<String, Double> hybridMatchWeights,
            MatchQuery query
    ) {
    }

    public record PreflightResponse(
            boolean corpusLoaded,
            int lawyerCount,
            int coverageNodeCount,
            int missingPracticeNodeCount,
            List<String> missingPracticeNodeIds,
            boolean currentServiceCompatible,
            String rebuiltQueryTextHash,
            String suppliedQueryTextHash,
            boolean hybridWeightsAccepted,
            Map<String, Double> hybridMatchWeights,
            String errorType,
            String errorMessage
    ) {
    }

    public record MatchRequest(
            String caseId,
            String matchingMode,
            String classificationMode,
            Integer topK,
            MatchQuery query
    ) {
    }

    public record MatchQuery(
            String briefContent,
            @JsonAlias({"input_node_ids", "predNodeIds", "goldNodeIds"}) List<String> inputNodeIds,
            String labelSource,
            @JsonAlias({"resolvedDomains"}) List<String> domains,
            @JsonAlias({"resolvedSubDomains", "sub_domains"}) List<String> subDomains,
            @JsonAlias({"resolvedTags"}) List<String> tags,
            String queryText,
            String queryTextHash
    ) {
    }

    public record MatchResponse(
            String caseId,
            String matchingMode,
            String classificationMode,
            int topK,
            boolean currentServiceCompatible,
            List<LawyerMatchResult> results,
            long latencyMs,
            String errorType,
            String errorMessage
    ) {
    }

    public record LawyerMatchResult(
            int rank,
            String lawyerId,
            List<String> practiceNodeIds,
            List<String> tags,
            double score,
            ScoreComponents scoreComponents
    ) {
    }

    public record ScoreComponents(
            Double cosine,
            Double fieldOverlap,
            Double keywordOverlap,
            Double hybridScore
    ) {
    }
}
