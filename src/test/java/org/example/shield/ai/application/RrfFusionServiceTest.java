package org.example.shield.ai.application;

import org.example.shield.ai.dto.RrfFusionInput;
import org.example.shield.ai.dto.RrfFusionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionServiceTest {

    private final RrfFusionService service = new RrfFusionService();

    @Test
    @DisplayName("RRF combines ranks across retrieval sources and rewards repeated candidates")
    void fuse_rewardsRepeatedCandidates() {
        List<RrfFusionResult> results = service.fuse(List.of(
                List.of(
                        new RrfFusionInput("law-a", "dense", 1, 0.92),
                        new RrfFusionInput("law-b", "dense", 2, 0.80)
                ),
                List.of(
                        new RrfFusionInput("law-b", "sparse", 1, 0.70),
                        new RrfFusionInput("law-a", "sparse", 10, 0.55)
                )
        ), 60, 10);

        assertThat(results).extracting(RrfFusionResult::id)
                .containsExactly("law-b", "law-a");
        assertThat(results.get(0).sources()).containsExactly("dense", "sparse");
        assertThat(results.get(0).bestRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("RRF ignores blank candidates and respects limit")
    void fuse_ignoresBlankAndLimits() {
        List<RrfFusionResult> results = service.fuse(List.of(
                List.of(
                        new RrfFusionInput("", "dense", 1, 0.9),
                        new RrfFusionInput("law-a", "dense", 1, 0.8),
                        new RrfFusionInput("law-b", "dense", 2, 0.7)
                )
        ), 60, 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("law-a");
    }
}
