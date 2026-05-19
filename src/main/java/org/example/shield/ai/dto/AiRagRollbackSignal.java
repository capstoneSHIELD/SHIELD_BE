package org.example.shield.ai.dto;

import java.time.Duration;

public record AiRagRollbackSignal(
        AiRagRolloutFeature feature,
        double primaryRate,
        double secondaryRate,
        double p95LatencyIncreaseMs,
        double costIncreaseRatio,
        double precision,
        double recallAt5DropPercentagePoints,
        int sampleCount,
        int consecutiveCount,
        int highRiskCount,
        int sameSlotPollutionCount,
        int relatedDropCount,
        Duration duration
) {
    public AiRagRollbackSignal {
        if (feature == null) {
            throw new IllegalArgumentException("feature is required");
        }
        duration = duration == null ? Duration.ZERO : duration;
    }

    public static Builder builder(AiRagRolloutFeature feature) {
        return new Builder(feature);
    }

    public static final class Builder {
        private final AiRagRolloutFeature feature;
        private double primaryRate;
        private double secondaryRate;
        private double p95LatencyIncreaseMs;
        private double costIncreaseRatio;
        private double precision;
        private double recallAt5DropPercentagePoints;
        private int sampleCount;
        private int consecutiveCount;
        private int highRiskCount;
        private int sameSlotPollutionCount;
        private int relatedDropCount;
        private Duration duration = Duration.ZERO;

        private Builder(AiRagRolloutFeature feature) {
            this.feature = feature;
        }

        public Builder primaryRate(double value) {
            this.primaryRate = value;
            return this;
        }

        public Builder secondaryRate(double value) {
            this.secondaryRate = value;
            return this;
        }

        public Builder p95LatencyIncreaseMs(double value) {
            this.p95LatencyIncreaseMs = value;
            return this;
        }

        public Builder costIncreaseRatio(double value) {
            this.costIncreaseRatio = value;
            return this;
        }

        public Builder precision(double value) {
            this.precision = value;
            return this;
        }

        public Builder recallAt5DropPercentagePoints(double value) {
            this.recallAt5DropPercentagePoints = value;
            return this;
        }

        public Builder sampleCount(int value) {
            this.sampleCount = value;
            return this;
        }

        public Builder consecutiveCount(int value) {
            this.consecutiveCount = value;
            return this;
        }

        public Builder highRiskCount(int value) {
            this.highRiskCount = value;
            return this;
        }

        public Builder sameSlotPollutionCount(int value) {
            this.sameSlotPollutionCount = value;
            return this;
        }

        public Builder relatedDropCount(int value) {
            this.relatedDropCount = value;
            return this;
        }

        public Builder duration(Duration value) {
            this.duration = value;
            return this;
        }

        public AiRagRollbackSignal build() {
            return new AiRagRollbackSignal(
                    feature,
                    primaryRate,
                    secondaryRate,
                    p95LatencyIncreaseMs,
                    costIncreaseRatio,
                    precision,
                    recallAt5DropPercentagePoints,
                    sampleCount,
                    consecutiveCount,
                    highRiskCount,
                    sameSlotPollutionCount,
                    relatedDropCount,
                    duration
            );
        }
    }
}
