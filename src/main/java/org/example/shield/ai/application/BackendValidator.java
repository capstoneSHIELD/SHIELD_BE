package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DynamicPlanProposal;
import org.example.shield.ai.dto.DynamicPlanSlotProposal;
import org.example.shield.ai.dto.ValidatedDynamicPlan;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackendValidator {

    private static final Pattern LEGAL_JUDGMENT = Pattern.compile(
            "(승소|패소|인용|기각|위법|불법|적법|합법|가능합니다|받을 수 있|인정됩니다)");

    private final OntologyService ontologyService;
    private final ChecklistAliasIndex checklistAliasIndex;
    private final GuardrailFilter guardrailFilter;

    public ValidatedDynamicPlan validate(DynamicPlanProposal proposal) {
        if (proposal == null) {
            return new ValidatedDynamicPlan(CaseTypeResult.empty(), 0.0, List.of(), List.of("proposal is null"));
        }

        List<String> rejections = new ArrayList<>();
        if (!validCaseType(proposal.caseType())) {
            rejections.add("caseType is outside ontology");
        }

        List<DynamicPlanSlotProposal> accepted = new ArrayList<>();
        for (DynamicPlanSlotProposal slot : proposal.slots()) {
            String rejection = rejectionReason(proposal.caseType(), slot);
            if (rejection == null) {
                accepted.add(normalizeSlot(slot));
            } else {
                rejections.add(slot.id() + ": " + rejection);
                log.warn("Dynamic plan slot rejected: slotId={}, reason={}", slot.id(), rejection);
            }
        }

        return new ValidatedDynamicPlan(
                proposal.caseType(),
                proposal.planConfidence(),
                accepted,
                rejections);
    }

    private boolean validCaseType(CaseTypeResult caseType) {
        if (caseType == null || caseType.l1() == null || !ontologyService.contains(caseType.l1())) {
            return false;
        }
        if (caseType.l2() != null && !caseType.l2().isBlank()
                && !ontologyService.isChildOf(caseType.l2(), caseType.l1())) {
            return false;
        }
        if (caseType.l3() != null && !caseType.l3().isBlank()
                && (caseType.l2() == null || !ontologyService.isChildOf(caseType.l3(), caseType.l2()))) {
            return false;
        }
        return true;
    }

    private String rejectionReason(CaseTypeResult caseType, DynamicPlanSlotProposal slot) {
        if (slot == null) {
            return "slot is null";
        }
        if (slot.id() == null || slot.id().isBlank()) {
            return "slot id is blank";
        }
        if (slot.label() == null || slot.label().isBlank()) {
            return "label is blank";
        }
        if (containsLegalJudgment(slot.question())) {
            return "question contains legal judgment";
        }
        if (slot.source() == SlotSource.STATIC_CHECKLIST) {
            if (slot.staticMappingId() == null || slot.staticMappingId().isBlank()) {
                return "static slot has no staticMappingId";
            }
            if (checklistAliasIndex.findByStaticMappingId(slot.staticMappingId()).isEmpty()) {
                return "staticMappingId is unknown";
            }
            return null;
        }

        List<String> keywords = List.of(
                nullToEmpty(slot.id()),
                nullToEmpty(slot.staticMappingId()),
                nullToEmpty(caseType == null ? null : caseType.l1()),
                nullToEmpty(slot.validationHint()));
        if (checklistAliasIndex.resolve(slot.label(), keywords).isEmpty()) {
            return "dynamic slot is unmappable to static alias";
        }
        return null;
    }

    private DynamicPlanSlotProposal normalizeSlot(DynamicPlanSlotProposal slot) {
        int priority = slot.priority() <= 0 ? 100 : slot.priority();
        return new DynamicPlanSlotProposal(
                slot.id(),
                slot.label(),
                slot.source() == null ? SlotSource.DYNAMIC : slot.source(),
                slot.staticMappingId(),
                slot.required(),
                priority,
                slot.status(),
                slot.question(),
                slot.validationHint(),
                slot.skipCondition());
    }

    private boolean containsLegalJudgment(String text) {
        if (text == null) return false;
        return guardrailFilter.containsForbiddenText(text) || LEGAL_JUDGMENT.matcher(text).find();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
