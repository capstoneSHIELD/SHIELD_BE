package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotLedgerService {

    private final ChecklistCoverageService checklistCoverageService;
    private final StaticQuestionSelector staticQuestionSelector;
    private final SlotValueValidator slotValueValidator;

    @Value("${app.ai.slot-ledger.enabled:true}")
    private boolean enabled;

    public SlotLedger ensureInitialized(
            Consultation consultation,
            ClassificationCandidate candidate,
            List<Message> chatHistory
    ) {
        if (!enabled || consultation == null) {
            return consultation == null ? null : consultation.getSlotState();
        }

        SlotLedger existing = consultation.getSlotState();
        if (existing != null && existing.hasSlots()) {
            return existing;
        }
        if (candidate == null || candidate.firstDomain() == null) {
            return existing;
        }

        List<ChecklistCoverageService.CoverageItem> coverage =
                checklistCoverageService.buildCoverageItems(
                        candidate.firstDomain(),
                        candidate.firstSubDomain(),
                        candidate.firstTag(),
                        chatHistory);
        if (coverage.isEmpty()) {
            return existing;
        }

        SlotLedger ledger = SlotLedger.empty();
        ledger.setCaseType(new SlotLedger.CaseType(
                candidate.firstDomain(),
                candidate.firstSubDomain(),
                candidate.firstTag()));

        List<SlotStateItem> slots = new ArrayList<>();
        for (int i = 0; i < coverage.size(); i++) {
            ChecklistCoverageService.CoverageItem item = coverage.get(i);
            int priority = i + 1;
            slots.add(SlotStateItem.staticChecklist(
                    slotId(priority),
                    item.label(),
                    true,
                    priority,
                    item.collected(),
                    inferValueType(item.label())));
        }
        ledger.setSlots(slots);
        ledger.touch();
        consultation.updateSlotState(ledger);
        return ledger;
    }

    public boolean appendAskedQuestion(Consultation consultation, String question) {
        if (!enabled || consultation == null || question == null || question.isBlank()) {
            return false;
        }
        SlotLedger ledger = consultation.getSlotState();
        if (ledger == null || !ledger.hasSlots()) {
            return false;
        }

        SlotStateItem next = staticQuestionSelector.selectNext(ledger.getSlots());
        if (next == null) {
            return false;
        }
        next.appendAskedQuestion(question);
        ledger.touch();
        return true;
    }

    public boolean applyExtractedSlots(
            Consultation consultation,
            IntentRouterResponse intent,
            double autoCollectThreshold,
            double pendingLowerBound
    ) {
        if (!enabled || consultation == null || intent == null || !intent.hasExtractedSlots()) {
            return false;
        }
        SlotLedger ledger = consultation.getSlotState();
        if (ledger == null || !ledger.hasSlots()) {
            return false;
        }

        boolean changed = false;
        for (ExtractedSlot extracted : intent.extractedSlots()) {
            SlotStateItem slot = findSlot(ledger, extracted.slotId());
            if (slot == null || extracted.confidence() < pendingLowerBound) {
                continue;
            }

            SlotValueValidator.Result validation = slotValueValidator.validate(
                    SlotValueType.from(extracted.valueType()),
                    extracted.value());
            if (validation.ignored()) {
                continue;
            }

            if (extracted.confidence() >= autoCollectThreshold
                    && !extracted.needsConfirmation()
                    && validation.status() == SlotStatus.COLLECTED) {
                slot.setStatus(SlotStatus.COLLECTED);
                slot.setCollectedValue(validation.collectedValue());
                slot.setPendingValue(null);
                slot.setConfidence(extracted.confidence());
                slot.setAnsweredAt(SlotStateItem.now());
            } else {
                slot.setStatus(SlotStatus.PENDING_CONFIRMATION);
                slot.setPendingValue(validation.pendingValue() != null
                        ? validation.pendingValue()
                        : validation.collectedValue());
                slot.setConfidence(extracted.confidence());
            }
            slot.setUpdatedAt(SlotStateItem.now());
            changed = true;
        }
        if (changed) {
            ledger.touch();
        }
        return changed;
    }

    public boolean confirmPending(Consultation consultation) {
        SlotStateItem pending = selectPending(consultation);
        if (pending == null || pending.getPendingValue() == null || pending.getPendingValue().isBlank()) {
            return false;
        }
        pending.setCollectedValue(pending.getPendingValue());
        pending.setPendingValue(null);
        pending.setStatus(SlotStatus.COLLECTED);
        pending.setAnsweredAt(SlotStateItem.now());
        pending.setUpdatedAt(SlotStateItem.now());
        consultation.getSlotState().touch();
        return true;
    }

    public boolean denyPending(Consultation consultation) {
        SlotStateItem pending = selectPending(consultation);
        if (pending == null) {
            return false;
        }
        pending.setPendingValue(null);
        pending.setStatus(SlotStatus.MISSING);
        pending.setUpdatedAt(SlotStateItem.now());
        consultation.getSlotState().touch();
        return true;
    }

    public boolean hasPending(Consultation consultation) {
        return selectPending(consultation) != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private SlotStateItem findSlot(SlotLedger ledger, String slotId) {
        if (slotId == null || slotId.isBlank() || ledger.getSlots() == null) {
            return null;
        }
        return ledger.getSlots().stream()
                .filter(s -> slotId.equals(s.getSlotId()))
                .findFirst()
                .orElse(null);
    }

    private SlotStateItem selectPending(Consultation consultation) {
        if (!enabled || consultation == null || consultation.getSlotState() == null) {
            return null;
        }
        if (consultation.getSlotState().getSlots() == null) {
            return null;
        }
        return consultation.getSlotState().getSlots().stream()
                .filter(s -> s.getStatus() == SlotStatus.PENDING_CONFIRMATION)
                .min(java.util.Comparator.comparingInt(SlotStateItem::getPriority))
                .orElse(null);
    }

    private String slotId(int priority) {
        return "static_" + String.format("%03d", priority);
    }

    private SlotValueType inferValueType(String label) {
        if (label == null) {
            return SlotValueType.TEXT;
        }
        if (label.matches(".*(금액|보증금|차임|월세|가격|비용|손해액).*")) {
            return SlotValueType.MONEY;
        }
        if (label.matches(".*(날짜|일시|시점|기간|종료|만료|계약일).*")) {
            return SlotValueType.DATE;
        }
        return SlotValueType.TEXT;
    }
}
