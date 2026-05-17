package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentRouterResponse;
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
}
