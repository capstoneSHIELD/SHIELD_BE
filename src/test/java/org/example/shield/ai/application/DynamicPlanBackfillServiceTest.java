package org.example.shield.ai.application;

import org.example.shield.ai.domain.ConsultationDynamicPlan;
import org.example.shield.ai.domain.ConsultationDynamicPlanRepository;
import org.example.shield.ai.domain.DynamicPlanSlotRepository;
import org.example.shield.ai.dto.DynamicPlanBackfillResult;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.common.enums.ConsultationStatus;
import org.example.shield.consultation.domain.Consultation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicPlanBackfillServiceTest {

    @Mock ConsultationDynamicPlanRepository planRepository;
    @Mock DynamicPlanSlotRepository slotRepository;
    @Mock DynamicPlanService dynamicPlanService;

    @Test
    @DisplayName("dry-run backfill does not write dynamic plan tables")
    void dryRunDoesNotWrite() {
        Consultation consultation = consultationWithSlotState();
        DynamicPlanBackfillService service = new DynamicPlanBackfillService(
                planRepository, slotRepository, dynamicPlanService, false);
        when(planRepository.findFirstByConsultationIdOrderByPlanVersionDesc(consultation.getId()))
                .thenReturn(Optional.empty());

        DynamicPlanBackfillResult result = service.backfill(List.of(consultation), false);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.convertible()).isEqualTo(1);
        assertThat(result.written()).isZero();
        verify(planRepository, never()).save(any());
        verify(slotRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute backfill writes plan and slots only when execute flag is enabled")
    void executeWritesWhenEnabled() {
        Consultation consultation = consultationWithSlotState();
        ConsultationDynamicPlan savedPlan = ConsultationDynamicPlan.create(
                consultation.getId(), 1, "부동산", "임대차", "보증금", null);
        ReflectionTestUtils.setField(savedPlan, "id", UUID.randomUUID());
        DynamicPlanBackfillService service = new DynamicPlanBackfillService(
                planRepository, slotRepository, dynamicPlanService, true);
        when(planRepository.findFirstByConsultationIdOrderByPlanVersionDesc(consultation.getId()))
                .thenReturn(Optional.empty());
        when(planRepository.save(any())).thenReturn(savedPlan);
        when(dynamicPlanService.buildSlotStateCache(consultation.getId())).thenReturn(consultation.getSlotState());

        DynamicPlanBackfillResult result = service.backfill(List.of(consultation), true);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.written()).isEqualTo(1);
        verify(planRepository).save(any());
        verify(slotRepository).save(any());
    }

    private Consultation consultationWithSlotState() {
        Consultation consultation = Consultation.create(
                UUID.randomUUID(), List.of("부동산"), List.of("임대차"), List.of("보증금"));
        ReflectionTestUtils.setField(consultation, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(consultation, "status", ConsultationStatus.COLLECTING);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setCaseType(new SlotLedger.CaseType("부동산", "임대차", "보증금"));
        SlotStateItem slot = SlotStateItem.staticChecklist(
                "deposit", "보증금", true, 1, true, SlotValueType.MONEY);
        slot.setCollectedValue("30000000");
        ledger.setSlots(List.of(slot));
        consultation.updateSlotState(ledger);
        return consultation;
    }
}
