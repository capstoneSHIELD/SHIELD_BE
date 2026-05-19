package org.example.shield.ai.application;

public record IntentRouteDecision(
        boolean skipCohere,
        String responseText,
        String reason
) {
    public static IntentRouteDecision continueToCohere(String reason) {
        return new IntentRouteDecision(false, null, reason);
    }

    public static IntentRouteDecision fixedResponse(String responseText, String reason) {
        return new IntentRouteDecision(true, responseText, reason);
    }
}
