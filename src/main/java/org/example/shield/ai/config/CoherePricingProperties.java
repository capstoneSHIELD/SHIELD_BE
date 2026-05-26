package org.example.shield.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cohere 모델별 토큰 단가 (USD per 1M tokens) — application.yml 외부화.
 *
 * <p>P5.1 Commit 4 보완 (refine): 기존 {@code CohereCostCalculator}의 코드 내 static map을
 * yaml로 외부화하여 단가 변경 시 코드 재배포 없이 환경변수로 override 가능.
 *
 * <p>구조 (application.yml):
 * <pre>
 * cohere:
 *   pricing:
 *     command-a-03-2025:
 *       input-per-million: 2.50
 *       output-per-million: 10.00
 *     command-r7b-12-2024:
 *       input-per-million: 0.0375
 *       output-per-million: 0.15
 *     embed-v4.0:
 *       input-per-million: 0.10
 *       output-per-million: 0.0
 * </pre>
 *
 * <p>모델명이 매핑 키로 사용되며, 매핑되지 않은 모델은 0.0 단가 (cost 미집계).
 */
@Component
@ConfigurationProperties(prefix = "cohere")
@Getter
@Setter
public class CoherePricingProperties {

    /**
     * 모델명 → 단가 매핑. 키는 yaml에 정의된 모델명 그대로 (예: {@code command-a-03-2025}).
     */
    private Map<String, ModelPricing> pricing = new HashMap<>();

    @Getter
    @Setter
    public static class ModelPricing {

        /** Input 토큰 100만 개당 USD. */
        private double inputPerMillion;

        /** Output 토큰 100만 개당 USD. embed 모델은 0. */
        private double outputPerMillion;
    }
}
