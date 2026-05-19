package org.example.shield.ai.application;

import org.example.shield.ai.domain.ConsultationDynamicPlan;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPlanIncrementalUpdateTest {

    private DynamicPlanService service;

    @BeforeEach
    void setUp() {
        service = new DynamicPlanService(null, null, null);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "regenerateConfidenceThreshold", 0.65);
        ReflectionTestUtils.setField(service, "regenerateInvalidatedSlotCount", 3);
        ReflectionTestUtils.setField(service, "regenerateRepeatedCorrectionCount", 2);
    }

    @Test
    @DisplayName("regenerates when there is no current plan")
    void shouldRegenerate_noPlan() {
        assertThat(service.shouldRegeneratePlan(null, intent(false, 0.0), 0, 0)).isTrue();
    }

    @Test
    @DisplayName("regenerates when plan confidence is lower than threshold")
    void shouldRegenerate_lowConfidence() {
        assertThat(service.shouldRegeneratePlan(plan(0.64), intent(false, 0.0), 0, 0)).isTrue();
    }

    @Test
    @DisplayName("regenerates when topic changed with confident case type")
    void shouldRegenerate_topicChanged() {
        assertThat(service.shouldRegeneratePlan(plan(0.8), intent(true, 0.85), 0, 0)).isTrue();
    }

    @Test
    @DisplayName("regenerates when invalidated slots or repeated corrections cross thresholds")
    void shouldRegenerate_correctionThresholds() {
        assertThat(service.shouldRegeneratePlan(plan(0.8), intent(false, 0.0), 3, 0)).isTrue();
        assertThat(service.shouldRegeneratePlan(plan(0.8), intent(false, 0.0), 0, 2)).isTrue();
    }

    @Test
    @DisplayName("does not regenerate for status-only update conditions")
    void shouldRegenerate_statusOnly() {
        assertThat(service.shouldRegeneratePlan(plan(0.8), intent(false, 0.0), 1, 1)).isFalse();
    }

    private ConsultationDynamicPlan plan(double confidence) {
        return ConsultationDynamicPlan.create(
                UUID.randomUUID(),
                1,
                "부동산 거래",
                "부동산 임대차",
                "보증금 및 차임",
                BigDecimal.valueOf(confidence));
    }

    private IntentRouterResponse intent(boolean topicChanged, double caseTypeConfidence) {
        return new IntentRouterResponse(
                "2.0",
                DialogueIntent.PROVIDE_INFO,
                0.9,
                List.of(),
                new CaseTypeResult("부동산 거래", "부동산 임대차", "보증금 및 차임", caseTypeConfidence),
                List.of(),
                List.of(),
                topicChanged,
                null);
    }
}
