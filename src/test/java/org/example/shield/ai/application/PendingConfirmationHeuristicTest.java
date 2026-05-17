package org.example.shield.ai.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingConfirmationHeuristicTest {

    private final PendingConfirmationHeuristic heuristic = new PendingConfirmationHeuristic();

    @Test
    @DisplayName("short affirmative answer to a confirmation question confirms pending value")
    void classify_affirmed() {
        assertThat(heuristic.classify("보증금이 3000만원이라고 말씀하신 게 맞나요?", "네 맞아요"))
                .isEqualTo(PendingConfirmationHeuristic.Decision.AFFIRMED);
    }

    @Test
    @DisplayName("short negative answer to a confirmation question denies pending value")
    void classify_denied() {
        assertThat(heuristic.classify("계약 종료일을 지난 12월로 이해했는데 맞나요?", "아니요"))
                .isEqualTo(PendingConfirmationHeuristic.Decision.DENIED);
    }

    @Test
    @DisplayName("new factual information in a long answer is not treated as plain confirm")
    void classify_newInfoFallsBack() {
        String answer = "아니요, 보증금은 4천만원이고 계약 종료일은 2026년 5월 1일입니다.";

        assertThat(heuristic.classify("보증금이 3000만원이라고 말씀하신 게 맞나요?", answer))
                .isEqualTo(PendingConfirmationHeuristic.Decision.NONE);
    }
}
