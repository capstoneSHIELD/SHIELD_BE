package org.example.shield.ai.infrastructure;

import org.example.shield.ai.config.CoherePricingProperties;
import org.example.shield.ai.config.CoherePricingProperties.ModelPricing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Cohere 모델별 토큰 단가(USD per 1M tokens) 기반 추정 비용 계산.
 *
 * <p>P5.1 Commit 4 도입, refine 단계에서 단가표를 {@link CoherePricingProperties}로
 * 외부화. application.yml의 {@code cohere.pricing.*}에서 조정 가능.
 *
 * <p>본 클래스는 메트릭 발행(Prometheus)을 위한 정보용으로만 사용된다.
 * 실제 청구는 Cohere 대시보드 기준이므로, 이 추정값은 운영 추세 모니터링용.
 */
@Component
public class CohereCostCalculator {

    /** Properties 없이 생성된 경우의 fallback — 모든 모델 단가 0. */
    private static final Map<String, ModelPricing> EMPTY = Collections.emptyMap();

    private final Map<String, ModelPricing> pricingByModel;

    /**
     * Test-friendly 생성자 (properties 미주입).
     * production에서는 Spring이 properties를 주입한 {@link #CohereCostCalculator(CoherePricingProperties)}를 사용.
     */
    public CohereCostCalculator() {
        this.pricingByModel = EMPTY;
    }

    @Autowired
    public CohereCostCalculator(CoherePricingProperties properties) {
        this.pricingByModel = properties == null || properties.getPricing() == null
                ? EMPTY
                : properties.getPricing();
    }

    /**
     * 추정 비용 (USD) 계산.
     *
     * @param model        Cohere 모델 ID
     * @param inputTokens  null/0이면 input 비용 0
     * @param outputTokens null/0이면 output 비용 0
     * @return 추정 비용 USD (≥ 0). 모델이 단가표에 없으면 0.0
     */
    public double estimate(String model, Integer inputTokens, Integer outputTokens) {
        ModelPricing pricing = pricingByModel.get(model);
        if (pricing == null) {
            return 0.0;
        }
        double inputCost = pricing.getInputPerMillion() * safeTokens(inputTokens) / 1_000_000.0;
        double outputCost = pricing.getOutputPerMillion() * safeTokens(outputTokens) / 1_000_000.0;
        return inputCost + outputCost;
    }

    private static int safeTokens(Integer tokens) {
        return tokens == null || tokens < 0 ? 0 : tokens;
    }
}
