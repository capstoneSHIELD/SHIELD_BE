package org.example.shield.ai.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CohereCostCalculator} 단가 계산 검증.
 *
 * <p>단가는 코드 내 정적 매핑이므로 테스트는 (1) 알려진 모델의 정확한 계산,
 * (2) 알 수 없는 모델은 0.0, (3) null/음수 토큰 안전 처리를 검증한다.
 */
class CohereCostCalculatorTest {

    private final CohereCostCalculator calc = new CohereCostCalculator();

    @Test
    @DisplayName("command-a-03-2025: input $2.50/M + output $10.00/M 정확 계산")
    void chatModelPricing() {
        // 1M input → $2.50, 500K output → $5.00 → 합계 $7.50
        double cost = calc.estimate("command-a-03-2025", 1_000_000, 500_000);
        assertThat(cost).isCloseTo(7.50, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("command-r7b-12-2024: classify 단가 정확 계산")
    void classifyModelPricing() {
        // 100K input × $0.0375/M = $0.00375
        // 50K output × $0.15/M = $0.0075
        // 합계 ~$0.01125
        double cost = calc.estimate("command-r7b-12-2024", 100_000, 50_000);
        assertThat(cost).isCloseTo(0.01125, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("embed-v4.0: input만 청구, output은 무시")
    void embedModelOutputIgnored() {
        // 1M input × $0.10/M = $0.10. output은 단가가 0이라 영향 없음.
        double cost = calc.estimate("embed-v4.0", 1_000_000, 999_999);
        assertThat(cost).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("알 수 없는 모델은 0.0 반환 (silent fallback)")
    void unknownModelReturnsZero() {
        double cost = calc.estimate("not-a-real-model", 1_000_000, 1_000_000);
        assertThat(cost).isZero();
    }

    @Test
    @DisplayName("null 토큰은 0으로 처리")
    void nullTokensAreSafe() {
        double cost = calc.estimate("command-a-03-2025", null, null);
        assertThat(cost).isZero();
    }

    @Test
    @DisplayName("음수 토큰은 0으로 처리")
    void negativeTokensAreSafe() {
        double cost = calc.estimate("command-a-03-2025", -10, -20);
        assertThat(cost).isZero();
    }

    @Test
    @DisplayName("일부만 null인 경우 — 다른 한쪽만 계산")
    void mixedNullTokens() {
        // input만 있음: 1M × $2.50/M = $2.50
        double cost = calc.estimate("command-a-03-2025", 1_000_000, null);
        assertThat(cost).isCloseTo(2.50, org.assertj.core.data.Offset.offset(1e-6));
    }
}
