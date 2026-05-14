package org.example.shield.ai.infrastructure;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagMetricsTest {

    private SimpleMeterRegistry registry;
    private RagMetrics ragMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        ragMetrics = new RagMetrics(registry);
    }

    @Test
    @DisplayName("timeClassify — success outcome 으로 timer 등록")
    void timeClassify_success() {
        String result = ragMetrics.timeClassify(() -> "classified");

        assertThat(result).isEqualTo("classified");
        Timer timer = registry.find(RagMetrics.METRIC_CLASSIFY).tag("outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("timeClassify — RuntimeException 시 failure outcome + 예외 재전파")
    void timeClassify_failure() {
        assertThatThrownBy(() -> ragMetrics.timeClassify(() -> {
            throw new RuntimeException("classify boom");
        })).isInstanceOf(RuntimeException.class).hasMessage("classify boom");

        Timer timer = registry.find(RagMetrics.METRIC_CLASSIFY).tag("outcome", "failure").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("startPipeline + stopPipelineSuccess — success outcome 으로 timer 등록")
    void pipeline_success() {
        Timer.Sample sample = ragMetrics.startPipeline();
        ragMetrics.stopPipelineSuccess(sample);

        Timer timer = registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("stopPipelineEmpty — empty outcome 분기")
    void pipeline_empty() {
        Timer.Sample sample = ragMetrics.startPipeline();
        ragMetrics.stopPipelineEmpty(sample);

        Timer timer = registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "empty").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("stopPipelineFailure — failure outcome 분기")
    void pipeline_failure() {
        Timer.Sample sample = ragMetrics.startPipeline();
        ragMetrics.stopPipelineFailure(sample);

        Timer timer = registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "failure").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("세 outcome 시리즈가 동일 metric 이름 아래 별개 timer 로 분리됨")
    void pipeline_threeOutcomes_areSeparateSeries() {
        ragMetrics.stopPipelineSuccess(ragMetrics.startPipeline());
        ragMetrics.stopPipelineSuccess(ragMetrics.startPipeline());
        ragMetrics.stopPipelineEmpty(ragMetrics.startPipeline());
        ragMetrics.stopPipelineFailure(ragMetrics.startPipeline());

        assertThat(registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "success").timer().count()).isEqualTo(2);
        assertThat(registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "empty").timer().count()).isEqualTo(1);
        assertThat(registry.find(RagMetrics.METRIC_PIPELINE_TOTAL).tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
