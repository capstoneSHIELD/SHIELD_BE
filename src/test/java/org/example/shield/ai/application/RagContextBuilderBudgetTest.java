package org.example.shield.ai.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.MixedRetrievalResult;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.dto.RetrievedDocument;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RagContextBuilder} P5.3 Commit 5 budget shadow 검증.
 *
 * <p>핵심 속성:
 * <ol>
 *   <li>{@code mode=off} (기본) — 기존 동작 동일, 메트릭 미발행</li>
 *   <li>{@code mode=shadow} — token 추정 + would-drop 카운트 메트릭, 결과는 baseline 동일</li>
 *   <li>{@code mode=enforce} — UnsupportedOperationException (본 plan 범위 밖)</li>
 *   <li>{@code mode=invalid} — fail-fast</li>
 * </ol>
 */
class RagContextBuilderBudgetTest {

    private SimpleMeterRegistry registry;
    private AiRagOperationalMetrics metrics;
    private RagContextBuilder builder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRagOperationalMetrics(registry);
        builder = new RagContextBuilder(metrics);
        ReflectionTestUtils.setField(builder, "budgetModeRaw", "off");
        ReflectionTestUtils.setField(builder, "tokenBudget", 2000);
    }

    private static LegalChunk law(String content) {
        return new LegalChunk("민법", "제618조", "임대차 정의",
                content, "2023-01-01", "https://law.go.kr/civil/618", 0.9);
    }

    private static Precedent caseDoc(String holding) {
        return new Precedent(
                "2024다12345", "대법원", "임대차 분쟁",
                "2024-03-15", "민사", "https://law.go.kr/case/1",
                "판시사항 요약", holding, 0.85);
    }

    private static MixedRetrievalResult mixed(List<LegalChunk> laws, List<Precedent> cases) {
        List<RetrievedDocument> merged = new java.util.ArrayList<>();
        if (laws != null) merged.addAll(laws);
        if (cases != null) merged.addAll(cases);
        return new MixedRetrievalResult(
                laws == null ? List.of() : laws,
                cases == null ? List.of() : cases,
                merged);
    }

    @Nested
    @DisplayName("mode=off (기본) — 기존 동작 + 메트릭 미발행")
    class OffMode {

        @Test
        @DisplayName("정상 빌드 + 메트릭 0건")
        void offModeNoMetrics() {
            String result = builder.build(
                    mixed(List.of(law("임대차 정의")), List.of()),
                    "테스트 요약",
                    /*budgetTokens*/ 2000);

            assertThat(result).contains("참고 법령");
            // off에서는 context.budget 메트릭이 등록조차 안 됨
            assertThat(registry.find(AiRagOperationalMetrics.CONTEXT_BUDGET).counters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("mode=shadow — 토큰 추정 + 메트릭 + baseline 결과")
    class ShadowMode {

        @BeforeEach
        void enableShadow() {
            ReflectionTestUtils.setField(builder, "budgetModeRaw", "shadow");
        }

        @Test
        @DisplayName("예산 충분 — 추정 토큰 + kept 메트릭")
        void withinBudget() {
            String result = builder.build(
                    mixed(List.of(law("짧은 내용")), List.of()),
                    "테스트",
                    2000);

            assertThat(result).contains("참고 법령");
            // estimated 토큰 메트릭 발행 확인
            assertThat(registry.counter(AiRagOperationalMetrics.CONTEXT_BUDGET,
                    "kind", "total", "action", "estimated").count())
                    .isGreaterThan(0);
            // 예산 내라 kept 발행
            assertThat(registry.counter(AiRagOperationalMetrics.CONTEXT_BUDGET,
                    "kind", "total", "action", "kept").count())
                    .isGreaterThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("예산 초과 — would-drop 메트릭 발행 + 결과는 baseline 동일")
        void overBudgetRecordsDrops() {
            // 매우 작은 budget (10 tokens)로 예산 초과 강제
            String hugeContent = "임대차 정의는 매우 긴 내용입니다. ".repeat(50);
            List<LegalChunk> laws = List.of(
                    law(hugeContent),
                    law(hugeContent),
                    law(hugeContent));

            String result = builder.build(
                    mixed(laws, List.of(caseDoc("판례 요약"))),
                    "테스트",
                    /*budgetTokens*/ 10);

            // 결과는 baseline (모든 chunk 포함) — shadow는 trim 안 함
            assertThat(result).contains("참고 법령");
            assertThat(result).contains("참고 판례");

            // dropped 메트릭이 기록됨
            double droppedStatute = registry.counter(AiRagOperationalMetrics.CONTEXT_BUDGET,
                    "kind", "statute", "action", "dropped").count();
            double droppedCase = registry.counter(AiRagOperationalMetrics.CONTEXT_BUDGET,
                    "kind", "case", "action", "dropped").count();
            double trimmed = registry.counter(AiRagOperationalMetrics.CONTEXT_BUDGET,
                    "kind", "total", "action", "trimmed").count();

            assertThat(droppedStatute + droppedCase).isGreaterThan(0);
            assertThat(trimmed).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("mode=enforce — 본 plan 범위 밖, throw")
    class EnforceMode {

        @Test
        @DisplayName("UnsupportedOperationException")
        void enforceThrows() {
            ReflectionTestUtils.setField(builder, "budgetModeRaw", "enforce");

            assertThatThrownBy(() -> builder.build(
                    mixed(List.of(law("c")), List.of()),
                    "test",
                    100))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("ENFORCE");
        }
    }

    @Nested
    @DisplayName("Mode parsing")
    class ModeParsing {

        @Test
        @DisplayName("invalid mode → IllegalStateException")
        void invalidModeFailFast() {
            ReflectionTestUtils.setField(builder, "budgetModeRaw", "bogus");

            assertThatThrownBy(() -> builder.build(
                    mixed(List.of(law("c")), List.of()),
                    "test",
                    100))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bogus");
        }

        @Test
        @DisplayName("test-friendly no-arg constructor — 메트릭 미주입 안전 동작")
        void noArgConstructorSafe() {
            RagContextBuilder bare = new RagContextBuilder();
            ReflectionTestUtils.setField(bare, "budgetModeRaw", "shadow");
            ReflectionTestUtils.setField(bare, "tokenBudget", 2000);

            // 메트릭 없이도 정상 빌드
            String result = bare.build(
                    mixed(List.of(law("c")), List.of()),
                    "test",
                    2000);
            assertThat(result).contains("참고 법령");
        }
    }

    @Nested
    @DisplayName("Token estimation")
    class TokenEstimation {

        @Test
        @DisplayName("estimateTokens — null/empty → 0")
        void emptyTextZeroTokens() {
            assertThat(RagContextBuilder.estimateTokens(null)).isZero();
            assertThat(RagContextBuilder.estimateTokens("")).isZero();
        }

        @Test
        @DisplayName("estimateTokens — 한글 30자 ≈ 20 토큰 (1.5자/token)")
        void koreanTextEstimation() {
            String text = "가".repeat(30);
            long tokens = RagContextBuilder.estimateTokens(text);
            assertThat(tokens).isEqualTo(20L);
        }
    }
}
