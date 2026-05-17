package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackendIntentRouter {

    private final SlotLedgerService slotLedgerService;
    private final PendingConfirmationHeuristic pendingConfirmationHeuristic;
    private final IntentFixedResponseTemplateService fixedResponses;

    @Value("${app.ai.intent-router.shadow-mode:true}")
    private boolean shadowMode;

    @Value("${app.ai.intent-router.enable-ask-legal-advice-skip:false}")
    private boolean enableAskLegalAdviceSkip;

    @Value("${app.ai.intent-router.enable-greeting-skip:false}")
    private boolean enableGreetingSkip;

    @Value("${app.ai.intent-router.enable-irrelevant-skip:false}")
    private boolean enableIrrelevantSkip;

    @Value("${app.ai.intent-router.enable-confirm:false}")
    private boolean enableConfirm;

    @Value("${app.ai.intent-router.enable-slot-auto-update:false}")
    private boolean enableSlotAutoUpdate;

    @Value("${app.ai.intent-router.thresholds.default.auto-collect:0.85}")
    private double autoCollectThreshold;

    @Value("${app.ai.intent-router.thresholds.default.pending-lower-bound:0.65}")
    private double pendingLowerBound;

    public IntentRouteDecision route(
            Consultation consultation,
            IntentRouterResponse intent,
            List<Message> chatHistory,
            String latestUserText
    ) {
        if (intent == null) {
            return IntentRouteDecision.continueToCohere("no_intent");
        }

        if (shadowMode) {
            log.info("Intent router shadow result: intent={}, confidence={}, slots={}",
                    intent.dialogueIntent(), intent.intentConfidence(), intent.extractedSlots().size());
            return IntentRouteDecision.continueToCohere("shadow_mode");
        }

        applySlotUpdatesIfEnabled(consultation, intent);

        DialogueIntent dialogueIntent = intent.dialogueIntent();
        if (dialogueIntent == DialogueIntent.ASK_LEGAL_ADVICE && enableAskLegalAdviceSkip) {
            return IntentRouteDecision.fixedResponse(
                    fixedResponses.get("ask_legal_advice"),
                    "ask_legal_advice");
        }
        if (dialogueIntent == DialogueIntent.GREETING && enableGreetingSkip) {
            return IntentRouteDecision.fixedResponse(fixedResponses.get("greeting"), "greeting");
        }
        if (dialogueIntent == DialogueIntent.IRRELEVANT && enableIrrelevantSkip) {
            return IntentRouteDecision.fixedResponse(fixedResponses.get("irrelevant"), "irrelevant");
        }
        if (dialogueIntent == DialogueIntent.CONFIRM && enableConfirm) {
            return routeConfirm(consultation, intent, chatHistory, latestUserText);
        }

        return IntentRouteDecision.continueToCohere("no_skip");
    }

    private void applySlotUpdatesIfEnabled(Consultation consultation, IntentRouterResponse intent) {
        if (!enableSlotAutoUpdate) {
            return;
        }
        boolean changed = slotLedgerService.applyExtractedSlots(
                consultation, intent, autoCollectThreshold, pendingLowerBound);
        if (changed) {
            log.info("Intent router applied slot updates: intent={}, slots={}",
                    intent.dialogueIntent(), intent.extractedSlots().size());
        }
    }

    private IntentRouteDecision routeConfirm(
            Consultation consultation,
            IntentRouterResponse intent,
            List<Message> chatHistory,
            String latestUserText
    ) {
        if (intent.intentConfidence() < autoCollectThreshold
                || intent.hasExtractedSlots()
                || !intent.correctedSlotIds().isEmpty()
                || !slotLedgerService.hasPending(consultation)) {
            return IntentRouteDecision.continueToCohere("confirm_not_safe_to_skip");
        }

        PendingConfirmationHeuristic.Decision decision = pendingConfirmationHeuristic.classify(
                previousAssistantMessage(chatHistory),
                latestUserText);
        if (decision == PendingConfirmationHeuristic.Decision.AFFIRMED
                && slotLedgerService.confirmPending(consultation)) {
            return IntentRouteDecision.fixedResponse(
                    fixedResponses.get("confirm_affirmative"),
                    "confirm_affirmative");
        }
        if (decision == PendingConfirmationHeuristic.Decision.DENIED
                && slotLedgerService.denyPending(consultation)) {
            return IntentRouteDecision.fixedResponse(
                    fixedResponses.get("confirm_negative"),
                    "confirm_negative");
        }
        return IntentRouteDecision.continueToCohere("confirm_ambiguous");
    }

    private String previousAssistantMessage(List<Message> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return null;
        }
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            Message message = chatHistory.get(i);
            if (message.getRole() == org.example.shield.common.enums.MessageRole.CHATBOT) {
                return message.getContent();
            }
        }
        return null;
    }
}
