package org.example.shield.ai.infrastructure;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Cohere 모델별 토큰 단가(USD per 1M tokens) 기반 추정 비용 계산.
 *
 * <p>P5.1 Commit 4 도입. 현재 단가표는 공식 Cohere 가격 페이지(2025년 4분기 기준)를
 * 코드에 정적으로 보유한다. 단가 변경 시 본 클래스만 수정하면 됨.
 *
 * <p>본 클래스는 메트릭 발행(Prometheus)을 위한 정보용으로만 사용된다.
 * 실제 청구는 Cohere 대시보드 기준이므로, 이 추정값은 운영 추세 모니터링용.
 *
 * <p>외부화(application.yml)는 별도 plan으로 분리 (현재는 코드 변경 + 재배포로 단가 갱신).
 */
@Component
public class CohereCostCalculator {

    /**
     * 모델별 토큰 단가 (USD per 1,000,000 tokens).
     * 출처: https://cohere.com/pricing (Phase P5.1 작성 시점 기준).
     */
    private static final Map<String, Pricing> PRICING = Map.of(
            // Chat / Brief generation
            "command-a-03-2025", new Pricing(2.50, 10.00),
            // Lightweight classify
            "command-r7b-12-2024", new Pricing(0.0375, 0.15),
            // Embed (output 없음 — input만 청구)
            "embed-v4.0", new Pricing(0.10, 0.0),
            // Rerank (P5.4에서 활성화 예정)
            "rerank-v3.5", new Pricing(2.00, 0.0)
    );

    private static final Pricing UNKNOWN = new Pricing(0.0, 0.0);

    /**
     * 추정 비용 (USD) 계산.
     *
     * @param model        Cohere 모델 ID
     * @param inputTokens  null/0이면 input 비용 0
     * @param outputTokens null/0이면 output 비용 0
     * @return 추정 비용 USD (≥ 0). 모델이 단가표에 없으면 0.0
     */
    public double estimate(String model, Integer inputTokens, Integer outputTokens) {
        Pricing pricing = PRICING.getOrDefault(model, UNKNOWN);
        double inputCost = pricing.perMillionInput * safeTokens(inputTokens) / 1_000_000.0;
        double outputCost = pricing.perMillionOutput * safeTokens(outputTokens) / 1_000_000.0;
        return inputCost + outputCost;
    }

    private static int safeTokens(Integer tokens) {
        return tokens == null || tokens < 0 ? 0 : tokens;
    }

    /**
     * @param perMillionInput  input 토큰 100만 개당 USD
     * @param perMillionOutput output 토큰 100만 개당 USD
     */
    record Pricing(double perMillionInput, double perMillionOutput) { }
}
