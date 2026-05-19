package org.example.shield.ai.application;

import org.example.shield.ai.domain.ConsultationDynamicPlan;
import org.example.shield.ai.domain.ConsultationDynamicPlanRepository;
import org.example.shield.ai.domain.DynamicPlanSlot;
import org.example.shield.ai.domain.DynamicPlanSlotRepository;
import org.example.shield.ai.dto.DynamicPlanDriftResult;
import org.example.shield.ai.dto.DynamicPlanSlotMismatch;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.consultation.domain.Consultation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DynamicPlanDriftDetector {

    private final ConsultationDynamicPlanRepository planRepository;
    private final DynamicPlanSlotRepository slotRepository;

    public DynamicPlanDriftDetector(
            ConsultationDynamicPlanRepository planRepository,
            DynamicPlanSlotRepository slotRepository
    ) {
        this.planRepository = planRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public DynamicPlanDriftResult detect(Consultation consultation) {
        if (consultation == null || consultation.getId() == null) {
            return empty(null, null);
        }
        ConsultationDynamicPlan plan = planRepository
                .findFirstByConsultationIdOrderByPlanVersionDesc(consultation.getId())
                .orElse(null);
        if (plan == null) {
            return compare(consultation.getId(), null, List.of(), consultation.getSlotState());
        }
        List<DynamicPlanSlot> planSlots = slotRepository.findAllByPlanIdOrderByPriorityAsc(plan.getId());
        return compare(consultation.getId(), plan.getId(), planSlots, consultation.getSlotState());
    }

    public DynamicPlanDriftResult compare(
            UUID consultationId,
            UUID planId,
            List<DynamicPlanSlot> planSlots,
            SlotLedger slotState
    ) {
        Map<String, DynamicPlanSlot> dynamicSlots = normalizePlanSlots(planSlots);
        Map<String, SlotStateItem> slotStateSlots = normalizeSlotState(slotState);

        List<String> missingInSlotState = dynamicSlots.keySet().stream()
                .filter(slotId -> !slotStateSlots.containsKey(slotId))
                .toList();
        List<String> missingInDynamicPlan = slotStateSlots.keySet().stream()
                .filter(slotId -> !dynamicSlots.containsKey(slotId))
                .toList();

        List<DynamicPlanSlotMismatch> statusMismatches = new ArrayList<>();
        List<DynamicPlanSlotMismatch> valueMismatches = new ArrayList<>();
        for (String slotId : dynamicSlots.keySet()) {
            DynamicPlanSlot dynamic = dynamicSlots.get(slotId);
            SlotStateItem cached = slotStateSlots.get(slotId);
            if (cached == null) {
                continue;
            }
            compareField(statusMismatches, slotId, "status",
                    dynamic.getStatus() == null ? null : dynamic.getStatus().name(),
                    cached.getStatus() == null ? null : cached.getStatus().name());
            compareField(statusMismatches, slotId, "asked",
                    bool(dynamic.getAskedAt() != null || notBlank(dynamic.getQuestionText())),
                    bool(cached.getAskedQuestions() != null && !cached.getAskedQuestions().isEmpty()));
            compareField(statusMismatches, slotId, "answered",
                    bool(dynamic.getAnsweredAt() != null),
                    bool(notBlank(cached.getAnsweredAt())));
            compareField(valueMismatches, slotId, "collected_value",
                    dynamic.getCollectedValue(), cached.getCollectedValue());
            compareField(valueMismatches, slotId, "pending_value",
                    dynamic.getPendingValue(), cached.getPendingValue());
        }

        boolean drift = !missingInSlotState.isEmpty()
                || !missingInDynamicPlan.isEmpty()
                || !statusMismatches.isEmpty()
                || !valueMismatches.isEmpty();
        return new DynamicPlanDriftResult(
                consultationId,
                planId,
                drift,
                missingInSlotState,
                missingInDynamicPlan,
                statusMismatches,
                valueMismatches,
                LocalDateTime.now()
        );
    }

    private DynamicPlanDriftResult empty(UUID consultationId, UUID planId) {
        return new DynamicPlanDriftResult(
                consultationId, planId, false, List.of(), List.of(), List.of(), List.of(), LocalDateTime.now());
    }

    private Map<String, DynamicPlanSlot> normalizePlanSlots(List<DynamicPlanSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }
        return slots.stream()
                .filter(slot -> notBlank(slot.getSlotId()))
                .sorted(Comparator.comparingInt(DynamicPlanSlot::getPriority))
                .collect(Collectors.toMap(
                        slot -> normalize(slot.getSlotId()),
                        slot -> slot,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, SlotStateItem> normalizeSlotState(SlotLedger ledger) {
        if (ledger == null || ledger.getSlots() == null || ledger.getSlots().isEmpty()) {
            return Map.of();
        }
        return ledger.getSlots().stream()
                .filter(slot -> notBlank(slot.getSlotId()))
                .sorted(Comparator.comparingInt(SlotStateItem::getPriority))
                .collect(Collectors.toMap(
                        slot -> normalize(slot.getSlotId()),
                        slot -> slot,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private void compareField(
            List<DynamicPlanSlotMismatch> mismatches,
            String slotId,
            String field,
            String dynamicValue,
            String slotStateValue
    ) {
        String left = normalizeNullable(dynamicValue);
        String right = normalizeNullable(slotStateValue);
        if (!left.equals(right)) {
            mismatches.add(new DynamicPlanSlotMismatch(slotId, field, dynamicValue, slotStateValue));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }

    private String bool(boolean value) {
        return String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
