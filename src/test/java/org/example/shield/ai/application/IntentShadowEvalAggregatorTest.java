package org.example.shield.ai.application;

import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentShadowEvalSummary;
import org.example.shield.ai.dto.LegalAdviceLabelRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntentShadowEvalAggregatorTest {

    private final IntentShadowEvalAggregator aggregator = new IntentShadowEvalAggregator();

    @Test
    @DisplayName("aggregator reports intent accuracy and skip false positive rate")
    void summarize() {
        IntentShadowEvalSummary summary = aggregator.summarize(List.of(
                label(DialogueIntent.ASK_LEGAL_ADVICE, DialogueIntent.ASK_LEGAL_ADVICE, false),
                label(DialogueIntent.GREETING, DialogueIntent.IRRELEVANT, true)
        ));

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.correctIntent()).isEqualTo(1);
        assertThat(summary.intentAccuracy()).isEqualTo(0.5);
        assertThat(summary.skipFalsePositiveRate()).isEqualTo(0.5);
    }

    private LegalAdviceLabelRecord label(DialogueIntent expected, DialogueIntent actual, boolean skipFalsePositive) {
        return new LegalAdviceLabelRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                expected,
                actual,
                expected == DialogueIntent.ASK_LEGAL_ADVICE,
                false,
                skipFalsePositive,
                "dev",
                null);
    }
}
