package org.example.shield.ai.application;

import org.example.shield.ai.domain.ConsultationDynamicPlan;
import org.example.shield.ai.domain.ConsultationDynamicPlanRepository;
import org.example.shield.ai.domain.DynamicPlanSlot;
import org.example.shield.ai.domain.DynamicPlanSlotRepository;
import org.example.shield.ai.dto.DynamicPlanBackfillResult;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.consultation.domain.Consultation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DynamicPlanBackfillService {

    private final ConsultationDynamicPlanRepository planRepository;
    private final DynamicPlanSlotRepository slotRepository;
    private final DynamicPlanService dynamicPlanService;
    private final boolean executeEnabled;

    public DynamicPlanBackfillService(
            ConsultationDynamicPlanRepository planRepository,
            DynamicPlanSlotRepository slotRepository,
            DynamicPlanService dynamicPlanService,
            @Value("${app.ai.dynamic-plan.backfill.execute-enabled:false}") boolean executeEnabled
    ) {
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
        this.dynamicPlanService = dynamicPlanService;
        this.executeEnabled = executeEnabled;
    }

    @Transactional
    public DynamicPlanBackfillResult backfill(List<Consultation> consultations, boolean execute) {
        boolean write = execute && executeEnabled;
        if (consultations == null || consultations.isEmpty()) {
            return new DynamicPlanBackfillResult(!write, 0, 0, 0, List.of());
        }

        int inspected = 0;
        int convertible = 0;
        int written = 0;
        List<String> skipped = new ArrayList<>();
        for (Consultation consultation : consultations) {
            inspected++;
            String reason = skipReason(consultation);
            if (reason != null) {
                skipped.add(reason);
                continue;
            }
            convertible++;
            if (write) {
                writePlan(consultation);
                SlotLedger cache = dynamicPlanService.buildSlotStateCache(consultation.getId());
                if (cache != null) {
                    consultation.updateSlotState(cache);
                }
                written++;
            }
        }
        return new DynamicPlanBackfillResult(!write, inspected, convertible, written, skipped);
    }

    private String skipReason(Consultation consultation) {
        if (consultation == null) {
            return "consultation:null";
        }
        if (consultation.getId() == null) {
            return "consultation:missing_id";
        }
        SlotLedger ledger = consultation.getSlotState();
        if (ledger == null || ledger.getSlots() == null || ledger.getSlots().isEmpty()) {
            return consultation.getId() + ":missing_slot_state";
        }
        if (planRepository.findFirstByConsultationIdOrderByPlanVersionDesc(consultation.getId()).isPresent()) {
            return consultation.getId() + ":plan_exists";
        }
        return null;
    }

    private void writePlan(Consultation consultation) {
        SlotLedger ledger = consultation.getSlotState();
        SlotLedger.CaseType caseType = ledger.getCaseType() == null
                ? new SlotLedger.CaseType()
                : ledger.getCaseType();
        ConsultationDynamicPlan plan = planRepository.save(ConsultationDynamicPlan.create(
                consultation.getId(),
                1,
                caseType.getL1(),
                caseType.getL2(),
                caseType.getL3(),
                null));
        UUID planId = plan.getId();
        for (SlotStateItem slot : ledger.getSlots()) {
            if (slot == null || slot.getSlotId() == null || slot.getSlotId().isBlank()) {
                continue;
            }
            slotRepository.save(DynamicPlanSlot.create(
                    planId,
                    slot.getSlotId(),
                    slot.getLabel() == null ? slot.getSlotId() : slot.getLabel(),
                    slot.getSource() == null ? SlotSource.DYNAMIC : slot.getSource(),
                    null,
                    slot.isRequired(),
                    slot.getPriority(),
                    slot.getStatus() == null ? SlotStatus.MISSING : slot.getStatus(),
                    slot.getCollectedValue(),
                    slot.getPendingValue(),
                    slot.getValueType() == null ? null : slot.getValueType().name().toLowerCase(),
                    firstQuestion(slot),
                    askedAt(slot),
                    answeredAt(slot)));
        }
    }

    private String firstQuestion(SlotStateItem slot) {
        if (slot.getAskedQuestions() == null || slot.getAskedQuestions().isEmpty()) {
            return null;
        }
        return slot.getAskedQuestions().get(0);
    }

    private LocalDateTime askedAt(SlotStateItem slot) {
        return firstQuestion(slot) == null ? null : LocalDateTime.now();
    }

    private LocalDateTime answeredAt(SlotStateItem slot) {
        if (slot.getAnsweredAt() == null || slot.getAnsweredAt().isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(slot.getAnsweredAt());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }
}
