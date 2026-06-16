package org.example.shield.experiment.lawyermatch;

import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchQuery;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.SyntheticLawyerRow;
import org.example.shield.lawyer.application.LawyerEmbeddingTextBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentLawyerMatchServiceTest {

    private final LawyerEmbeddingTextBuilder textBuilder = new LawyerEmbeddingTextBuilder();
    private final ExperimentLawyerMatchService service = new ExperimentLawyerMatchService(
            new ExperimentLawyerCorpusStore(),
            textBuilder
    );

    @Test
    void corpus_preflight_and_match_use_in_memory_cosine_candidates() {
        service.loadCorpus(new CorpusLoadRequest("test-corpus", List.of(
                lawyer("L-007-01-05-001", List.of("law-007-01-05"), List.of("law-007"), List.of("law-007-01")),
                lawyer("L-004-02-01-001", List.of("law-004-02-01"), List.of("law-004"), List.of("law-004-02"))
        )));
        MatchQuery query = query(
                "전세보증금을 돌려받지 못했습니다.",
                List.of("law-007-01-05"),
                "oracle"
        );

        var preflight = service.preflight(new PreflightRequest(
                List.of("law-007-01-05"),
                Map.of("cosine", 0.6, "fieldOverlap", 0.25, "keywordOverlap", 0.15),
                query
        ));
        var match = service.match(new MatchRequest(
                "CASE-1",
                "ORACLE_LABELS_COSINE_ONLY",
                "C_HYBRID_RUNTIME",
                1,
                query
        ));

        assertThat(preflight.errorType()).isNull();
        assertThat(preflight.currentServiceCompatible()).isTrue();
        assertThat(match.currentServiceCompatible()).isTrue();
        assertThat(match.results()).hasSize(1);
        assertThat(match.results().get(0).lawyerId()).isEqualTo("L-007-01-05-001");
        assertThat(match.results().get(0).scoreComponents().cosine()).isGreaterThan(0.0);
    }

    @Test
    void query_hash_mismatch_is_reported_as_current_service_incompatible() {
        service.loadCorpus(new CorpusLoadRequest("test-corpus", List.of(
                lawyer("L-007-01-05-001", List.of("law-007-01-05"), List.of("law-007"), List.of("law-007-01"))
        )));
        MatchQuery query = new MatchQuery(
                "전세보증금을 돌려받지 못했습니다.",
                List.of("law-007-01-05"),
                "oracle",
                List.of("law-007"),
                List.of("law-007-01"),
                List.of("law-007-01-05"),
                "outdated-runner-query",
                "sha256:bad"
        );

        var match = service.match(new MatchRequest(
                "CASE-1",
                "ORACLE_LABELS_COSINE_ONLY",
                "C_HYBRID_RUNTIME",
                1,
                query
        ));

        assertThat(match.currentServiceCompatible()).isFalse();
    }

    private SyntheticLawyerRow lawyer(
            String lawyerId,
            List<String> practiceNodeIds,
            List<String> domains,
            List<String> subDomains
    ) {
        return new SyntheticLawyerRow(
                lawyerId,
                "Synthetic Lawyer " + lawyerId,
                practiceNodeIds,
                practiceNodeIds.get(0),
                List.of(),
                domains,
                subDomains,
                practiceNodeIds,
                5,
                "synthetic-region",
                "Synthetic benchmark profile.",
                null,
                "VERIFIED"
        );
    }

    private MatchQuery query(String content, List<String> nodeIds, String labelSource) {
        List<String> domains = List.of("law-007");
        List<String> subDomains = List.of("law-007-01");
        String text = textBuilder.build(domains, subDomains, nodeIds, content);
        return new MatchQuery(
                content,
                nodeIds,
                labelSource,
                domains,
                subDomains,
                nodeIds,
                text,
                ExperimentLawyerMatchService.sha256Hash(text)
        );
    }
}
