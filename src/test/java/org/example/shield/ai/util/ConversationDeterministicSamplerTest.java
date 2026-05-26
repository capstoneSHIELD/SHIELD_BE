package org.example.shield.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConversationDeterministicSampler} 검증.
 *
 * <p>핵심 속성:
 * <ol>
 *   <li>같은 conversationId + 같은 rate → 항상 같은 결과 (deterministic)</li>
 *   <li>rate=0.3에서 N개 conversationId sampling → 비율이 ±5% 이내</li>
 *   <li>edge cases (rate 0, 1, null id) 안전 처리</li>
 * </ol>
 */
class ConversationDeterministicSamplerTest {

    @Test
    @DisplayName("same conversationId + rate → 항상 같은 결과")
    void deterministicSameInputs() {
        String cid = "conv-12345";
        boolean first = ConversationDeterministicSampler.shouldApply(cid, 0.3);
        for (int i = 0; i < 100; i++) {
            assertThat(ConversationDeterministicSampler.shouldApply(cid, 0.3))
                    .isEqualTo(first);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, 0.0})
    @DisplayName("rate 0 이하 → 항상 false")
    void rateZeroOrBelow(double rate) {
        for (int i = 0; i < 50; i++) {
            assertThat(ConversationDeterministicSampler.shouldApply(UUID.randomUUID().toString(), rate))
                    .isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 1.5})
    @DisplayName("rate 1 이상 → 항상 true")
    void rateOneOrAbove(double rate) {
        for (int i = 0; i < 50; i++) {
            assertThat(ConversationDeterministicSampler.shouldApply(UUID.randomUUID().toString(), rate))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("null/blank conversationId → false (안전 default)")
    void nullOrBlankIdReturnsFalse() {
        assertThat(ConversationDeterministicSampler.shouldApply(null, 0.5)).isFalse();
        assertThat(ConversationDeterministicSampler.shouldApply("", 0.5)).isFalse();
        assertThat(ConversationDeterministicSampler.shouldApply("   ", 0.5)).isFalse();
    }

    @Test
    @DisplayName("rate 0.3 → 10000개 sampling 시 분포가 ±5% 이내 (deterministic uniform)")
    void distributionApproximatesRate() {
        int total = 10_000;
        double rate = 0.3;
        int sampled = 0;
        for (int i = 0; i < total; i++) {
            String cid = "conv-" + i;
            if (ConversationDeterministicSampler.shouldApply(cid, rate)) {
                sampled++;
            }
        }
        double observed = (double) sampled / total;
        assertThat(observed)
                .as("sampling 비율 (관찰된 %f vs 목표 %f)", observed, rate)
                .isBetween(rate - 0.05, rate + 0.05);
    }

    @Test
    @DisplayName("sha256Short — 같은 입력 동일 결과, 다른 입력 다른 결과")
    void sha256ShortDeterministic() {
        String hash1 = ConversationDeterministicSampler.sha256Short("conv-A");
        String hash2 = ConversationDeterministicSampler.sha256Short("conv-A");
        String hash3 = ConversationDeterministicSampler.sha256Short("conv-B");

        assertThat(hash1).hasSize(8);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    @DisplayName("sha256Short — null 입력은 빈 문자열")
    void sha256ShortNullSafe() {
        assertThat(ConversationDeterministicSampler.sha256Short(null)).isEmpty();
    }
}
