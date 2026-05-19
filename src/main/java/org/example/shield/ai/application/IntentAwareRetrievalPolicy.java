package org.example.shield.ai.application;

import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.RetrievalStrategyDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntentAwareRetrievalPolicy {

    @Value("${app.ai.rag.intent-aware.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.intent-router.thresholds.default.auto-collect:0.85}")
    private double highConfidenceThreshold;

    @Value("${app.ai.intent-router.thresholds.default.pending-lower-bound:0.65}")
    private double mediumConfidenceThreshold;

    public RetrievalStrategyDecision decide(IntentRouterResponse intent, int defaultTopK) {
        int safeDefaultTopK = Math.max(1, defaultTopK);
        if (!enabled || intent == null) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, enabled ? "missing_intent" : "disabled");
        }

        double confidence = intent.intentConfidence();
        if (confidence < mediumConfidenceThreshold) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "low_confidence_baseline");
        }
        if (confidence < highConfidenceThreshold) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "medium_confidence_conservative");
        }

        DialogueIntent dialogueIntent = intent.dialogueIntent();
        return switch (dialogueIntent) {
            case GREETING, IRRELEVANT, ASK_LEGAL_ADVICE ->
                    new RetrievalStrategyDecision(true, true, safeDefaultTopK, "high_confidence_rag_skip");
            case CHANGE_TOPIC ->
                    new RetrievalStrategyDecision(false, true, Math.max(safeDefaultTopK, 10), "high_confidence_broad_search");
            case PROVIDE_INFO, CORRECT_INFO, CONFIRM, END_CONSULTATION ->
                    new RetrievalStrategyDecision(false, true, safeDefaultTopK, "high_confidence_default_search");
        };
    }
}
