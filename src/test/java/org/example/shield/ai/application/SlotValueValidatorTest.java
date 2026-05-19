package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotValueValidatorTest {

    private final SlotValueValidator validator = new SlotValueValidator();

    @Test
    @DisplayName("money accepts numeric strings with commas and won unit")
    void validate_moneyCollected() {
        SlotValueValidator.Result result = validator.validate(SlotValueType.MONEY, "30,000,000원");

        assertThat(result.status()).isEqualTo(SlotStatus.COLLECTED);
        assertThat(result.collectedValue()).isEqualTo("30000000");
        assertThat(result.ignored()).isFalse();
    }

    @Test
    @DisplayName("money mismatch becomes pending confirmation")
    void validate_moneyPending() {
        SlotValueValidator.Result result = validator.validate(SlotValueType.MONEY, "three cheon man won");

        assertThat(result.status()).isEqualTo(SlotStatus.PENDING_CONFIRMATION);
        assertThat(result.pendingValue()).isEqualTo("three cheon man won");
    }

    @Test
    @DisplayName("date normalizes ISO-like dates")
    void validate_dateCollected() {
        SlotValueValidator.Result result = validator.validate(SlotValueType.DATE, "2026.5.7");

        assertThat(result.status()).isEqualTo(SlotStatus.COLLECTED);
        assertThat(result.collectedValue()).isEqualTo("2026-05-07");
    }

    @Test
    @DisplayName("short text is ignored rather than collected")
    void validate_textTooShortIgnored() {
        SlotValueValidator.Result result = validator.validate(SlotValueType.TEXT, " ");

        assertThat(result.ignored()).isTrue();
        assertThat(result.status()).isEqualTo(SlotStatus.MISSING);
    }
}
