package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.shield.ai.domain.ConsultationDynamicPlan;
import org.example.shield.ai.domain.ConsultationDynamicPlanRepository;
import org.example.shield.ai.domain.DynamicPlanSlot;
import org.example.shield.ai.domain.DynamicPlanSlotRepository;
import org.example.shield.ai.dto.DynamicPlanProposal;
import org.example.shield.ai.dto.DynamicPlanSlotProposal;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.ValidatedDynamicPlan;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.consultation.domain.Consultation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DynamicPlanService {

    private final ConsultationDynamicPlanRepository planRepository;
    private final DynamicPlanSlotRepository slotRepository;
    private final BackendValidator backendValidator;

    @Value("${app.ai.dynamic-plan.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.dynamic-plan.regenerate-confidence-threshold:0.65}")
    private double regenerateConfidenceThreshold;

    @Value("${app.ai.dynamic-plan.regenerate-invalidated-slot-count:3}")
    private int regenerateInvalidatedSlotCount;

    @Value("${app.ai.dynamic-plan.regenerate-repeated-correction-count:2}")
    private int regenerateRepeatedCorrectionCount;

    @Transactional
    public ConsultationDynamicPlan saveValidatedPlan(UUID consultationId, DynamicPlanProposal proposal) {
        ValidatedDynamicPlan validated = backendValidator.validate(proposal);
        ConsultationDynamicPlan existing = planRepository
                .findFirstByConsultationIdOrderByPlanVersionDesc(consultationId)
                .orElse(null);
        int version = existing == null ? 1 : existing.getPlanVersion() + 1;

        ConsultationDynamicPlan plan = planRepository.save(ConsultationDynamicPlan.create(
                consultationId,
                version,
                validated.caseType().l1(),
                validated.caseType().l2(),
                validated.caseType().l3(),
                BigDecimal.valueOf(validated.planConfidence())));
        for (DynamicPlanSlotProposal slot : validated.slots()) {
            slotRepository.save(toEntity(plan.getId(), slot));
        }
        return plan;
    }

    @Transactional
    public ConsultationDynamicPlan saveValidatedPlanAndSync(Consultation consultation, DynamicPlanProposal proposal) {
        if (consultation == null || consultation.getId() == null) {
            return null;
        }
        ConsultationDynamicPlan plan = saveValidatedPlan(consultation.getId(), proposal);
        SlotLedger cache = buildSlotStateCache(consultation.getId());
        if (cache != null) {
            consultation.updateSlotState(cache);
        }
        return plan;
    }

    @Transactional(readOnly = true)
    public SlotLedger buildSlotStateCache(UUID consultationId) {
        ConsultationDynamicPlan plan = planRepository
                .findFirstByConsultationIdOrderByPlanVersionDesc(consultationId)
                .orElse(null);
        if (plan == null) {
            return null;
        }
        List<DynamicPlanSlot> slots = slotRepository.findAllByPlanIdOrderByPriorityAsc(plan.getId());
        SlotLedger ledger = SlotLedger.empty();
        ledger.setCaseType(new SlotLedger.CaseType(
                plan.getCaseTypeL1(),
                plan.getCaseTypeL2(),
                plan.getCaseTypeL3()));
        ledger.setSlots(slots.stream()
                .map(this::toSlotStateItem)
                .toList());
        ledger.touch();
        return ledger;
    }

    public boolean shouldRegeneratePlan(
            ConsultationDynamicPlan currentPlan,
            IntentRouterResponse intent,
            int invalidatedSlotCount,
            int repeatedCorrectionCount
    ) {
        if (!enabled) {
            return false;
        }
        if (currentPlan == null) {
            return true;
        }
        if (intent != null && intent.topicChanged()
                && intent.caseType() != null
                && intent.caseType().confidence() >= 0.80) {
            return true;
        }
        if (invalidatedSlotCount >= regenerateInvalidatedSlotCount) {
            return true;
        }
        if (repeatedCorrectionCount >= regenerateRepeatedCorrectionCount) {
            return true;
        }
        return currentPlan.getPlanConfidence() != null
                && currentPlan.getPlanConfidence().doubleValue() < regenerateConfidenceThreshold;
    }

    public boolean syncSlotStateCacheFromPlan(Consultation consultation) {
        if (!enabled || consultation == null || consultation.getId() == null) {
            return false;
        }
        SlotLedger cache = buildSlotStateCache(consultation.getId());
        if (cache == null) {
            return false;
        }
        consultation.updateSlotState(cache);
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private DynamicPlanSlot toEntity(UUID planId, DynamicPlanSlotProposal slot) {
        return DynamicPlanSlot.create(
                planId,
                slot.id(),
                slot.label(),
                slot.source() == null ? SlotSource.DYNAMIC : slot.source(),
                slot.staticMappingId(),
                slot.required(),
                slot.priority(),
                slot.status() == null ? SlotStatus.MISSING : slot.status(),
                null,
                null,
                slot.validationHint(),
                slot.question(),
                null,
                null);
    }

    private SlotStateItem toSlotStateItem(DynamicPlanSlot slot) {
        SlotStateItem item = SlotStateItem.staticChecklist(
                slot.getSlotId(),
                slot.getLabel(),
                slot.isRequired(),
                slot.getPriority(),
                slot.getStatus() == SlotStatus.COLLECTED,
                SlotValueType.from(slot.getValidationHint()));
        item.setSource(slot.getSource());
        item.setStatus(slot.getStatus());
        item.setCollectedValue(slot.getCollectedValue());
        item.setPendingValue(slot.getPendingValue());
        item.setAnsweredAt(asString(slot.getAnsweredAt()));
        item.setUpdatedAt(SlotStateItem.now());
        if (slot.getQuestionText() != null && !slot.getQuestionText().isBlank()) {
            item.appendAskedQuestion(slot.getQuestionText());
        }
        return item;
    }

    private String asString(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
