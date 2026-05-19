package org.example.shield.ai.application;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PendingConfirmationHeuristic {

    private static final Pattern CONFIRMATION_PROMPT = Pattern.compile(
            "(맞나요|확인|말씀하신|이해했는데)");
    private static final Pattern AFFIRMATIVE = Pattern.compile(
            "^(네|예|맞|맞아요|맞습니다|응|그렇)");
    private static final Pattern NEGATIVE = Pattern.compile(
            "^(아니|아뇨|아닙|달라|틀려)");
    private static final Pattern NEW_INFO = Pattern.compile(
            "(\\d{4}[-./년]\\s*\\d{0,2}|\\d+\\s*(원|만원|천만원|억)|보증금|계약|임대인|임차인|월세|전세)");

    public Decision classify(String previousAiQuestion, String userResponse) {
        if (previousAiQuestion == null || userResponse == null) {
            return Decision.NONE;
        }
        if (!CONFIRMATION_PROMPT.matcher(previousAiQuestion).find()) {
            return Decision.NONE;
        }

        String normalized = userResponse.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return Decision.NONE;
        }

        boolean compact = normalized.length() <= 30;
        boolean hasNewInfo = NEW_INFO.matcher(normalized).find();
        if (!compact && hasNewInfo) {
            return Decision.NONE;
        }

        if (AFFIRMATIVE.matcher(normalized).find()) {
            return Decision.AFFIRMED;
        }
        if (NEGATIVE.matcher(normalized).find()) {
            return Decision.DENIED;
        }
        return Decision.NONE;
    }

    public enum Decision {
        AFFIRMED,
        DENIED,
        NONE
    }
}
