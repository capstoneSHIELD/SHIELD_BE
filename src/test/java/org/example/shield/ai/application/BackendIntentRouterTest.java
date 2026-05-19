package org.example.shield.ai.application;

import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.ExtractedSlot;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendIntentRouterTest {

    private SlotLedgerService slotLedgerService;
    private IntentFixedResponseTemplateService fixedResponses;
    private BackendIntentRouter router;

    @BeforeEach
    void setUp() {
        slotLedgerService = mock(SlotLedgerService.class);
        fixedResponses = mock(IntentFixedResponseTemplateService.class);
        when(fixedResponses.get("ask_legal_advice")).thenReturn("legal advice blocked");
        when(fixedResponses.get("greeting")).thenReturn("hello");
        router = new BackendIntentRouter(
                slotLedgerService,
                new PendingConfirmationHeuristic(),
                fixedResponses);
    }

    @Test
    @DisplayName("shadow mode never skips Cohere")
    void route_shadowModeContinues() {
        ReflectionTestUtils.setField(router, "shadowMode", true);
        ReflectionTestUtils.setField(router, "enableAskLegalAdviceSkip", true);

        IntentRouteDecision decision = router.route(
                Consultation.create(UUID.randomUUID(), null, null, null),
                response(DialogueIntent.ASK_LEGAL_ADVICE, List.of()),
                List.of(),
                "이길 수 있나요?");

        assertThat(decision.skipCohere()).isFalse();
        assertThat(decision.reason()).isEqualTo("shadow_mode");
    }

    @Test
    @DisplayName("ASK_LEGAL_ADVICE can store mixed high-confidence slots and return fixed response")
    void route_askLegalAdviceMixedUtterance() {
        ReflectionTestUtils.setField(router, "shadowMode", false);
        ReflectionTestUtils.setField(router, "enableAskLegalAdviceSkip", true);
        ReflectionTestUtils.setField(router, "enableSlotAutoUpdate", true);
        ReflectionTestUtils.setField(router, "autoCollectThreshold", 0.85);
        ReflectionTestUtils.setField(router, "pendingLowerBound", 0.65);

        Consultation consultation = Consultation.create(UUID.randomUUID(), null, null, null);
        IntentRouterResponse intent = response(DialogueIntent.ASK_LEGAL_ADVICE, List.of(
                new ExtractedSlot("static_001", "30000000", "보증금 3천만원", 0.91, "money", false)));

        IntentRouteDecision decision = router.route(consultation, intent, List.of(), "보증금은 3천만원이고 이길 수 있나요?");

        assertThat(decision.skipCohere()).isTrue();
        assertThat(decision.responseText()).isEqualTo("legal advice blocked");
        verify(slotLedgerService).applyExtractedSlots(consultation, intent, 0.85, 0.65);
    }

    @Test
    @DisplayName("greeting flag controls fixed response skip")
    void route_greeting() {
        ReflectionTestUtils.setField(router, "shadowMode", false);
        ReflectionTestUtils.setField(router, "enableGreetingSkip", true);

        IntentRouteDecision decision = router.route(
                Consultation.create(UUID.randomUUID(), null, null, null),
                response(DialogueIntent.GREETING, List.of()),
                List.of(),
                "안녕하세요");

        assertThat(decision.skipCohere()).isTrue();
        assertThat(decision.responseText()).isEqualTo("hello");
        verify(slotLedgerService, never()).applyExtractedSlots(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    @DisplayName("CONFIRM affirmative promotes pending slot only under strict conditions")
    void route_confirmAffirmative() {
        ReflectionTestUtils.setField(router, "shadowMode", false);
        ReflectionTestUtils.setField(router, "enableConfirm", true);
        ReflectionTestUtils.setField(router, "autoCollectThreshold", 0.85);
        when(fixedResponses.get("confirm_affirmative")).thenReturn("confirmed");

        Consultation consultation = Consultation.create(UUID.randomUUID(), null, null, null);
        when(slotLedgerService.hasPending(consultation)).thenReturn(true);
        when(slotLedgerService.confirmPending(consultation)).thenReturn(true);
        List<Message> history = List.of(Message.createAiMessage(
                UUID.randomUUID(),
                "보증금이 3000만원이라고 말씀하신 게 맞나요?",
                null,
                null,
                null,
                null));

        IntentRouteDecision decision = router.route(
                consultation,
                response(DialogueIntent.CONFIRM, List.of()),
                history,
                "네 맞아요");

        assertThat(decision.skipCohere()).isTrue();
        assertThat(decision.responseText()).isEqualTo("confirmed");
    }

    private IntentRouterResponse response(DialogueIntent intent, List<ExtractedSlot> slots) {
        return new IntentRouterResponse(
                "2.0",
                intent,
                0.91,
                slots,
                CaseTypeResult.empty(),
                List.of("query"),
                List.of(),
                false,
                null);
    }
}
