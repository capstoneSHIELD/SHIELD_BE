package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CorrectedSlot;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotLedgerService {

    private final ChecklistCoverageService checklistCoverageService;
    private final StaticQuestionSelector staticQuestionSelector;
    private final SlotValueValidator slotValueValidator;

    @Autowired(required = false)
    private AiRagOperationalMetrics operationalMetrics;

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

        SlotLedger ledger = existing != null && existing.hasSlots()
                ? existing
                : SlotLedger.empty();
        ledger.setCaseType(new SlotLedger.CaseType(
                candidate.firstDomain(),
                candidate.firstSubDomain(),
                candidate.firstTag()));
        reconcileSlots(ledger, coverage);
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
        if (hasAskedQuestion(next, question)) {
            recordRepeatedQuestion(next.getSlotId());
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
            if (slot == null || slot.isOutOfScope() || extracted.confidence() < pendingLowerBound) {
                continue;
            }

            SlotValueValidator.Result validation = slotValueValidator.validate(
                    SlotValueType.from(extracted.valueType()),
                    extracted.value());
            if (validation.ignored()) {
                recordSlotPollutionCandidate("value_validation_ignored");
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

    public boolean applyCorrectedSlots(
            Consultation consultation,
            ChatParsedResponse response,
            double correctionThreshold
    ) {
        if (!enabled || consultation == null || response == null || !response.hasCorrectedSlots()) {
            return false;
        }
        SlotLedger ledger = consultation.getSlotState();
        if (ledger == null || !ledger.hasSlots()) {
            return false;
        }

        boolean changed = false;
        for (CorrectedSlot corrected : response.getCorrectedSlots()) {
            if (corrected == null || corrected.confidence() < correctionThreshold) {
                continue;
            }
            SlotStateItem slot = findSlot(ledger, corrected.slotId());
            if (slot == null || slot.isOutOfScope() || slot.getStatus() != SlotStatus.COLLECTED) {
                continue;
            }
            if (slot.getCollectedValue() == null || slot.getCollectedValue().isBlank()) {
                continue;
            }

            SlotValueValidator.Result validation = slotValueValidator.validate(
                    slot.getValueType(),
                    corrected.newValue());
            if (validation.ignored()) {
                recordSlotPollutionCandidate("correction_validation_ignored");
                continue;
            }

            String pendingValue = validation.collectedValue() != null
                    ? validation.collectedValue()
                    : validation.pendingValue();
            if (pendingValue == null || pendingValue.isBlank()) {
                recordSlotPollutionCandidate("correction_empty_pending_value");
                continue;
            }
            if (pendingValue.equals(slot.getCollectedValue())) {
                continue;
            }

            slot.setStatus(SlotStatus.PENDING_CONFIRMATION);
            slot.setPendingValue(pendingValue);
            slot.setConfidence(corrected.confidence());
            slot.setUpdatedAt(SlotStateItem.now());
            ledger.touch();
            changed = true;
            log.info("Slot correction staged: slotId={}, legacySlotId={}, confidence={}",
                    slot.getSlotId(), slot.getLegacySlotId(), corrected.confidence());
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
        pending.setStatus(pending.getCollectedValue() == null || pending.getCollectedValue().isBlank()
                ? SlotStatus.MISSING
                : SlotStatus.COLLECTED);
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

    private void reconcileSlots(SlotLedger ledger, List<ChecklistCoverageService.CoverageItem> coverage) {
        List<SlotStateItem> existing = ledger.getSlots() == null
                ? List.of()
                : new ArrayList<>(ledger.getSlots());
        List<SlotStateItem> reconciled = new ArrayList<>();
        Set<SlotStateItem> used = new HashSet<>();

        int priority = 1;
        for (ChecklistCoverageService.CoverageItem item : coverage) {
            String stableId = stableOrFallbackId(item);
            SlotStateItem slot = findByCurrentOrLegacyId(existing, stableId);
            if (slot == null) {
                slot = findLegacyByLabel(existing, item.label(), used);
            }

            if (slot == null) {
                slot = SlotStateItem.staticChecklist(
                        stableId,
                        item.label(),
                        item.required(),
                        priority,
                        item.collected(),
                        item.valueType(),
                        item.sourcePath(),
                        item.nodeId());
            } else {
                migrateLegacyIdIfNeeded(slot, stableId);
                updateMetadata(slot, item, priority);
                if (slot.getStatus() == SlotStatus.MISSING && item.collected()) {
                    slot.setStatus(SlotStatus.COLLECTED);
                    slot.setAnsweredAt(slot.getAnsweredAt() == null
                            ? SlotStateItem.now()
                            : slot.getAnsweredAt());
                }
            }

            slot.setOutOfScope(false);
            reconciled.add(slot);
            used.add(slot);
            priority++;
        }

        existing.stream()
                .filter(slot -> !used.contains(slot))
                .sorted(Comparator.comparingInt(SlotStateItem::getPriority))
                .forEach(slot -> {
                    slot.setOutOfScope(true);
                    slot.setUpdatedAt(SlotStateItem.now());
                    reconciled.add(slot);
                });

        ledger.setSlots(reconciled);
    }

    private void updateMetadata(
            SlotStateItem slot,
            ChecklistCoverageService.CoverageItem item,
            int priority
    ) {
        slot.setLabel(item.label());
        slot.setRequired(item.required());
        slot.setPriority(priority);
        slot.setSource(SlotSource.STATIC_CHECKLIST);
        slot.setValueType(item.valueType() == null ? SlotValueType.TEXT : item.valueType());
        slot.setSourcePath(item.sourcePath());
        slot.setNodeId(item.nodeId());
        slot.setUpdatedAt(SlotStateItem.now());
    }

    private void migrateLegacyIdIfNeeded(SlotStateItem slot, String stableId) {
        if (stableId == null || stableId.isBlank() || stableId.equals(slot.getSlotId())) {
            return;
        }
        if (slot.getSlotId() != null && slot.getSlotId().startsWith("static_")
                && (slot.getLegacySlotId() == null || slot.getLegacySlotId().isBlank())) {
            slot.setLegacySlotId(slot.getSlotId());
        }
        slot.setSlotId(stableId);
    }

    private SlotStateItem findByCurrentOrLegacyId(List<SlotStateItem> slots, String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return null;
        }
        return slots.stream()
                .filter(slot -> slotId.equals(slot.getSlotId()) || slotId.equals(slot.getLegacySlotId()))
                .findFirst()
                .orElse(null);
    }

    private SlotStateItem findLegacyByLabel(
            List<SlotStateItem> slots,
            String label,
            Set<SlotStateItem> used
    ) {
        String normalized = normalizeLabel(label);
        if (normalized.isBlank()) {
            return null;
        }
        return slots.stream()
                .filter(slot -> !used.contains(slot))
                .filter(slot -> slot.getSlotId() != null && slot.getSlotId().startsWith("static_"))
                .filter(slot -> normalized.equals(normalizeLabel(slot.getLabel())))
                .findFirst()
                .orElse(null);
    }

    private String stableOrFallbackId(ChecklistCoverageService.CoverageItem item) {
        if (item.slotId() != null && !item.slotId().isBlank()) {
            return item.slotId();
        }
        return "static:unknown:root:root:" + Integer.toHexString(normalizeLabel(item.label()).hashCode());
    }

    private SlotStateItem findSlot(SlotLedger ledger, String slotId) {
        if (slotId == null || slotId.isBlank() || ledger.getSlots() == null) {
            return null;
        }
        return findByCurrentOrLegacyId(ledger.getSlots(), slotId);
    }

    private SlotStateItem selectPending(Consultation consultation) {
        if (!enabled || consultation == null || consultation.getSlotState() == null) {
            return null;
        }
        if (consultation.getSlotState().getSlots() == null) {
            return null;
        }
        return consultation.getSlotState().getSlots().stream()
                .filter(slot -> !slot.isOutOfScope())
                .filter(slot -> slot.getStatus() == SlotStatus.PENDING_CONFIRMATION)
                .min(Comparator.comparingInt(SlotStateItem::getPriority))
                .orElse(null);
    }

    private boolean hasAskedQuestion(SlotStateItem slot, String question) {
        if (slot == null || slot.getAskedQuestions() == null || question == null) {
            return false;
        }
        String normalized = question.trim().replaceAll("\\s+", " ");
        return slot.getAskedQuestions().contains(normalized);
    }

    private String normalizeLabel(String label) {
        return ChecklistTokenizer.normalizeForMatch(label == null ? "" : label);
    }

    private void recordRepeatedQuestion(String slotId) {
        if (operationalMetrics != null) {
            operationalMetrics.recordRepeatedSlotQuestionCandidate(slotId);
        }
    }

    private void recordSlotPollutionCandidate(String reason) {
        if (operationalMetrics != null) {
            operationalMetrics.recordSlotLedgerPollutionCandidate(reason);
        }
    }
}
