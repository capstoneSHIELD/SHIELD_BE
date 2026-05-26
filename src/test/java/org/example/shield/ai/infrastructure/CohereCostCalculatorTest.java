package org.example.shield.ai.infrastructure;

import org.example.shield.ai.config.CoherePricingProperties;
import org.example.shield.ai.config.CoherePricingProperties.ModelPricing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CohereCostCalculator} 단가 계산 검증.
 *
 * <p>P5.1 Commit 4 refine: 단가표가 {@link CoherePricingProperties}로 외부화됨.
 * 본 테스트는 (1) properties 주입 시 정확한 계산, (2) 알 수 없는 모델은 0.0,
 * (3) null/음수 토큰 안전 처리, (4) properties 미주입 시 fallback을 검증한다.
 */
class CohereCostCalculatorTest {

    private CohereCostCalculator calc;

    @BeforeEach
    void setUp() {
        // application.yml과 동일한 단가표 (테스트용)
        CoherePricingProperties props = new CoherePricingProperties();
        Map<String, ModelPricing> pricing = new HashMap<>();
        pricing.put("command-a-03-2025", pricing(2.50, 10.00));
        pricing.put("command-r7b-12-2024", pricing(0.0375, 0.15));
        pricing.put("embed-v4.0", pricing(0.10, 0.0));
        pricing.put("rerank-v3.5", pricing(2.00, 0.0));
        props.setPricing(pricing);
        calc = new CohereCostCalculator(props);
    }

    private static ModelPricing pricing(double in, double out) {
        ModelPricing p = new ModelPricing();
        p.setInputPerMillion(in);
        p.setOutputPerMillion(out);
        return p;
    }

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
        double cost = calc.estimate("command-r7b-12-2024", 100_000, 50_000);
        assertThat(cost).isCloseTo(0.01125, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("embed-v4.0: input만 청구, output은 무시")
    void embedModelOutputIgnored() {
        double cost = calc.estimate("embed-v4.0", 1_000_000, 999_999);
        assertThat(cost).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("알 수 없는 모델은 0.0 반환")
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
        double cost = calc.estimate("command-a-03-2025", 1_000_000, null);
        assertThat(cost).isCloseTo(2.50, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("Properties 미주입(no-arg constructor) → 모든 모델 0.0 (안전 default)")
    void noPropertiesFallback() {
        CohereCostCalculator emptyCalc = new CohereCostCalculator();
        assertThat(emptyCalc.estimate("command-a-03-2025", 1_000_000, 1_000_000)).isZero();
    }

    @Test
    @DisplayName("Properties.pricing이 null이면 안전 default")
    void nullPricingMapFallback() {
        CoherePricingProperties props = new CoherePricingProperties();
        props.setPricing(null);
        CohereCostCalculator c = new CohereCostCalculator(props);
        assertThat(c.estimate("command-a-03-2025", 1_000_000, 1_000_000)).isZero();
    }
}
