package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.CorrectedSlot;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SlotLedgerServiceTest {

    @Mock private ChecklistCoverageService checklistCoverageService;

    private SlotLedgerService service;

    @BeforeEach
    void setUp() {
        service = new SlotLedgerService(
                checklistCoverageService,
                new StaticQuestionSelector(),
                new SlotValueValidator());
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    @DisplayName("initializes slot ledger from checklist coverage when slot_state is missing")
    void ensureInitialized_createsLedger() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        ClassificationCandidate candidate = new ClassificationCandidate(
                List.of("real estate"), List.of("lease"), List.of("deposit"));
        given(checklistCoverageService.buildCoverageItems("real estate", "lease", "deposit", List.of()))
                .willReturn(List.of(
                        new ChecklistCoverageService.CoverageItem("deposit amount", true),
                        new ChecklistCoverageService.CoverageItem("lease end date", false)));

        SlotLedger ledger = service.ensureInitialized(consultation, candidate, List.<Message>of());

        assertThat(ledger).isSameAs(consultation.getSlotState());
        assertThat(ledger.getCaseType().getL1()).isEqualTo("real estate");
        assertThat(ledger.getSlots()).hasSize(2);
        assertThat(ledger.getSlots().get(0).getStatus()).isEqualTo(SlotStatus.COLLECTED);
        assertThat(ledger.getSlots().get(0).getCollectedValue()).isNull();
        assertThat(ledger.getSlots().get(1).getStatus()).isEqualTo(SlotStatus.MISSING);
    }

    @Test
    @DisplayName("reconciles slot ledger when classification narrows from L1 to L3")
    void ensureInitialized_reconcilesNarrowedScope() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        ClassificationCandidate l1Candidate = new ClassificationCandidate(
                List.of("real estate"), List.of(), List.of());
        ClassificationCandidate l3Candidate = new ClassificationCandidate(
                List.of("real estate"), List.of("lease"), List.of("deposit"));
        given(checklistCoverageService.buildCoverageItems("real estate", null, null, List.of()))
                .willReturn(List.of(coverage("static:real-estate:root:root:a1", "party info", true)));
        given(checklistCoverageService.buildCoverageItems("real estate", "lease", "deposit", List.of()))
                .willReturn(List.of(
                        coverage("static:real-estate:root:root:a1", "party info", true),
                        coverage("static:real-estate:lease:deposit:b2", "deposit amount", false)));

        SlotLedger l1Ledger = service.ensureInitialized(consultation, l1Candidate, List.of());
        l1Ledger.getSlots().get(0).setCollectedValue("tenant");
        l1Ledger.getSlots().get(0).setAnsweredAt("2026-05-18T10:00:00");

        SlotLedger l3Ledger = service.ensureInitialized(consultation, l3Candidate, List.of());

        assertThat(l3Ledger.getCaseType().getL2()).isEqualTo("lease");
        assertThat(l3Ledger.getCaseType().getL3()).isEqualTo("deposit");
        assertThat(l3Ledger.getSlots()).hasSize(2);
        assertThat(l3Ledger.getSlots().get(0).getCollectedValue()).isEqualTo("tenant");
        assertThat(l3Ledger.getSlots().get(0).getAnsweredAt()).isEqualTo("2026-05-18T10:00:00");
        assertThat(l3Ledger.getSlots().get(1).getSlotId()).isEqualTo("static:real-estate:lease:deposit:b2");
        assertThat(l3Ledger.getSlots().get(1).getStatus()).isEqualTo(SlotStatus.MISSING);
    }

    @Test
    @DisplayName("legacy static_NNN slot id is upgraded but remains usable for correction fallback")
    void ensureInitialized_migratesLegacyIdAndCorrectsByLegacyId() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotStateItem legacy = SlotStateItem.staticChecklist(
                "static_001", "deposit amount", true, 1, true, SlotValueType.MONEY);
        legacy.setCollectedValue("30000000");
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(legacy));
        consultation.updateSlotState(ledger);
        ClassificationCandidate candidate = new ClassificationCandidate(
                List.of("real estate"), List.of("lease"), List.of("deposit"));
        given(checklistCoverageService.buildCoverageItems("real estate", "lease", "deposit", List.of()))
                .willReturn(List.of(coverage("static:real-estate:lease:deposit:b2", "deposit amount", true)));

        service.ensureInitialized(consultation, candidate, List.of());
        ChatParsedResponse response = new ChatParsedResponse();
        response.setCorrectedSlots(List.of(
                new CorrectedSlot("static_001", "30000000", "50000000", 0.91)));

        boolean changed = service.applyCorrectedSlots(consultation, response, 0.85);

        assertThat(changed).isTrue();
        assertThat(legacy.getSlotId()).isEqualTo("static:real-estate:lease:deposit:b2");
        assertThat(legacy.getLegacySlotId()).isEqualTo("static_001");
        assertThat(legacy.getStatus()).isEqualTo(SlotStatus.PENDING_CONFIRMATION);
        assertThat(legacy.getPendingValue()).isEqualTo("50000000");
    }

    @Test
    @DisplayName("appendAskedQuestion stores the generated question on the next missing slot")
    void appendAskedQuestion_recordsQuestion() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(
                org.example.shield.ai.dto.slot.SlotStateItem.staticChecklist(
                        "static_001", "deposit amount", true, 1, true,
                        org.example.shield.ai.dto.slot.SlotValueType.MONEY),
                org.example.shield.ai.dto.slot.SlotStateItem.staticChecklist(
                        "static_002", "lease end date", true, 2, false,
                        org.example.shield.ai.dto.slot.SlotValueType.DATE)));
        consultation.updateSlotState(ledger);

        boolean changed = service.appendAskedQuestion(consultation, "When did the lease end?");

        assertThat(changed).isTrue();
        assertThat(ledger.getSlots().get(1).getAskedQuestions())
                .containsExactly("When did the lease end?");
    }

    @Test
    @DisplayName("appendAskedQuestion skips out-of-scope missing slots")
    void appendAskedQuestion_skipsOutOfScopeSlot() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotStateItem oldSlot = SlotStateItem.staticChecklist(
                "static:old", "old question", true, 1, false, SlotValueType.TEXT);
        oldSlot.setOutOfScope(true);
        SlotStateItem currentSlot = SlotStateItem.staticChecklist(
                "static:current", "current question", true, 2, false, SlotValueType.TEXT);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(oldSlot, currentSlot));
        consultation.updateSlotState(ledger);

        boolean changed = service.appendAskedQuestion(consultation, "Current?");

        assertThat(changed).isTrue();
        assertThat(oldSlot.getAskedQuestions()).isEmpty();
        assertThat(currentSlot.getAskedQuestions()).containsExactly("Current?");
    }

    @Test
    @DisplayName("applyExtractedSlots follows confidence gate for collected, pending, and ignored slots")
    void applyExtractedSlots_confidenceGate() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(
                org.example.shield.ai.dto.slot.SlotStateItem.staticChecklist(
                        "static_001", "deposit amount", true, 1, false,
                        org.example.shield.ai.dto.slot.SlotValueType.MONEY),
                org.example.shield.ai.dto.slot.SlotStateItem.staticChecklist(
                        "static_002", "lease end date", true, 2, false,
                        org.example.shield.ai.dto.slot.SlotValueType.DATE),
                org.example.shield.ai.dto.slot.SlotStateItem.staticChecklist(
                        "static_003", "landlord response", true, 3, false,
                        org.example.shield.ai.dto.slot.SlotValueType.TEXT)));
        consultation.updateSlotState(ledger);

        IntentRouterResponse intent = new IntentRouterResponse(
                "2.0",
                DialogueIntent.PROVIDE_INFO,
                0.9,
                List.of(
                        new ExtractedSlot("static_001", "30000000", "보증금 3천만원", 0.9, "money", false),
                        new ExtractedSlot("static_002", "작년 12월", "작년 12월", 0.7, "date", true),
                        new ExtractedSlot("static_003", "x", "x", 0.5, "text", false)),
                CaseTypeResult.empty(),
                List.of(),
                List.of(),
                false,
                null);

        boolean changed = service.applyExtractedSlots(consultation, intent, 0.85, 0.65);

        assertThat(changed).isTrue();
        assertThat(ledger.getSlots().get(0).getStatus()).isEqualTo(SlotStatus.COLLECTED);
        assertThat(ledger.getSlots().get(0).getCollectedValue()).isEqualTo("30000000");
        assertThat(ledger.getSlots().get(1).getStatus()).isEqualTo(SlotStatus.PENDING_CONFIRMATION);
        assertThat(ledger.getSlots().get(1).getPendingValue()).isEqualTo("작년 12월");
        assertThat(ledger.getSlots().get(2).getStatus()).isEqualTo(SlotStatus.MISSING);
    }

    @Test
    @DisplayName("applyCorrectedSlots stages collected slot corrections as pending confirmation")
    void applyCorrectedSlots_stagesPendingCorrection() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotStateItem deposit = SlotStateItem.staticChecklist(
                "static_001", "deposit amount", true, 1, true, SlotValueType.MONEY);
        deposit.setCollectedValue("30000000");
        SlotStateItem leaseEnd = SlotStateItem.staticChecklist(
                "static_002", "lease end date", true, 2, false, SlotValueType.DATE);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(deposit, leaseEnd));
        consultation.updateSlotState(ledger);

        ChatParsedResponse response = new ChatParsedResponse();
        response.setCorrectedSlots(List.of(
                new CorrectedSlot("static_001", "30000000", "50,000,000원", 0.91),
                new CorrectedSlot("static_002", null, "2026-05-01", 0.99),
                new CorrectedSlot("static_001", "30000000", "70000000", 0.5)));

        boolean changed = service.applyCorrectedSlots(consultation, response, 0.85);

        assertThat(changed).isTrue();
        assertThat(deposit.getStatus()).isEqualTo(SlotStatus.PENDING_CONFIRMATION);
        assertThat(deposit.getCollectedValue()).isEqualTo("30000000");
        assertThat(deposit.getPendingValue()).isEqualTo("50000000");
        assertThat(leaseEnd.getStatus()).isEqualTo(SlotStatus.MISSING);
    }

    @Test
    @DisplayName("denyPending restores collected status when pending value is a correction")
    void denyPending_restoresCollectedCorrection() {
        Consultation consultation = Consultation.create(UUID.randomUUID(), List.of("real estate"), null, null);
        SlotStateItem deposit = SlotStateItem.staticChecklist(
                "static_001", "deposit amount", true, 1, true, SlotValueType.MONEY);
        deposit.setCollectedValue("30000000");
        deposit.setPendingValue("50000000");
        deposit.setStatus(SlotStatus.PENDING_CONFIRMATION);
        SlotLedger ledger = SlotLedger.empty();
        ledger.setSlots(List.of(deposit));
        consultation.updateSlotState(ledger);

        boolean changed = service.denyPending(consultation);

        assertThat(changed).isTrue();
        assertThat(deposit.getStatus()).isEqualTo(SlotStatus.COLLECTED);
        assertThat(deposit.getCollectedValue()).isEqualTo("30000000");
        assertThat(deposit.getPendingValue()).isNull();
    }

    private ChecklistCoverageService.CoverageItem coverage(String slotId, String label, boolean collected) {
        return new ChecklistCoverageService.CoverageItem(
                slotId,
                label,
                true,
                SlotValueType.TEXT,
                "test.path",
                "test-node",
                collected);
    }
}
