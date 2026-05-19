package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SlotValueValidator {

    private static final Pattern MONEY = Pattern.compile("^[0-9,\\s]+원?$");
    private static final Pattern DATE_FULL = Pattern.compile("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})$");
    private static final Pattern DATE_MONTH = Pattern.compile("^(\\d{4})[-./](\\d{1,2})$");
    private static final Pattern DATE_YEAR = Pattern.compile("^\\d{4}$");
    private static final Pattern KOREAN_DATE_LIKE = Pattern.compile(
            "(작년|올해|지난|다음|이번|\\d{1,2}\\s*월|\\d{1,2}\\s*일|\\d{4}\\s*년)");

    public Result validate(SlotValueType valueType, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Result.ignore();
        }

        SlotValueType effectiveType = valueType == null ? SlotValueType.TEXT : valueType;
        String raw = rawValue.trim();
        return switch (effectiveType) {
            case MONEY -> validateMoney(raw);
            case DATE -> validateDate(raw);
            case TEXT -> validateText(raw);
        };
    }

    private Result validateMoney(String raw) {
        if (MONEY.matcher(raw).matches()) {
            String normalized = raw.replaceAll("[^0-9]", "");
            if (!normalized.isBlank()) {
                return Result.collected(normalized);
            }
        }
        return Result.pending(raw);
    }

    private Result validateDate(String raw) {
        var full = DATE_FULL.matcher(raw);
        if (full.matches()) {
            return Result.collected(String.format("%s-%02d-%02d",
                    full.group(1),
                    Integer.parseInt(full.group(2)),
                    Integer.parseInt(full.group(3))));
        }

        var month = DATE_MONTH.matcher(raw);
        if (month.matches()) {
            return Result.collected(String.format("%s-%02d",
                    month.group(1),
                    Integer.parseInt(month.group(2))));
        }

        if (DATE_YEAR.matcher(raw).matches()) {
            return Result.collected(raw);
        }

        if (KOREAN_DATE_LIKE.matcher(raw).find()) {
            return Result.pending(raw);
        }
        return Result.pending(raw);
    }

    private Result validateText(String raw) {
        String normalized = raw.trim();
        if (normalized.length() < 2) {
            return Result.ignore();
        }
        return Result.collected(normalized);
    }

    public record Result(
            SlotStatus status,
            String collectedValue,
            String pendingValue,
            boolean ignored
    ) {
        static Result collected(String value) {
            return new Result(SlotStatus.COLLECTED, value, null, false);
        }

        static Result pending(String value) {
            return new Result(SlotStatus.PENDING_CONFIRMATION, null, value, false);
        }

        static Result ignore() {
            return new Result(SlotStatus.MISSING, null, null, true);
        }
    }
}
