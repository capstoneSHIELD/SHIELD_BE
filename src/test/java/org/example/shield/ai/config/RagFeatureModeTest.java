package org.example.shield.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RagFeatureMode} 파싱 동작 검증.
 *
 * <p>Phase P5에서 신규 mode flag(off|shadow|sampled|enforce)는 모두 이 enum을
 * 거쳐 파싱되므로, 다음 두 가지 동작이 강제되어야 한다:
 * <ol>
 *   <li>null/blank는 OFF로 fall back (default-off 원칙)</li>
 *   <li>매칭 실패는 silent fallback이 아니라 startup fail-fast</li>
 * </ol>
 */
class RagFeatureModeTest {

    @Nested
    @DisplayName("정상 입력")
    class ValidInputs {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
                "off,      OFF",
                "shadow,   SHADOW",
                "sampled,  SAMPLED",
                "enforce,  ENFORCE",
                "OFF,      OFF",
                "SHADOW,   SHADOW",
                "Sampled,  SAMPLED",
                "EnForCe,  ENFORCE",
                "'  shadow  ', SHADOW"
        })
        @DisplayName("대소문자/공백 무시하고 정확히 매칭")
        void parsesValidValues(String raw, RagFeatureMode expected) {
            assertThat(RagFeatureMode.fromOrThrow(raw, "TEST_FLAG"))
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Null / blank 처리")
    class NullAndBlank {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n"})
        @DisplayName("null·빈문자·공백만 있는 값은 OFF (default-off)")
        void returnsOffForNullOrBlank(String raw) {
            assertThat(RagFeatureMode.fromOrThrow(raw, "TEST_FLAG"))
                    .isEqualTo(RagFeatureMode.OFF);
        }
    }

    @Nested
    @DisplayName("Invalid 입력은 fail-fast")
    class FailFast {

        @ParameterizedTest
        @ValueSource(strings = {
                "on",          // off 오타
                "true",        // boolean 잔재
                "false",
                "disabled",
                "shadowmode",  // 접미사 오타
                "ENFORCED",
                "0",
                "1",
                "weighted"     // 다른 enum 값
        })
        @DisplayName("매칭 안 되는 값은 IllegalStateException")
        void throwsOnInvalidValue(String raw) {
            assertThatThrownBy(() -> RagFeatureMode.fromOrThrow(raw, "AI_RAG_RETRIEVAL_GATE_MODE"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(raw)
                    .hasMessageContaining("AI_RAG_RETRIEVAL_GATE_MODE")
                    .hasMessageContaining("off|shadow|sampled|enforce");
        }

        @Test
        @DisplayName("에러 메시지에 flag 이름과 허용 값이 포함된다")
        void errorMessageIncludesDiagnostics() {
            assertThatThrownBy(() ->
                    RagFeatureMode.fromOrThrow("bogus", "MY_FLAG"))
                    .hasMessageContaining("'bogus'")
                    .hasMessageContaining("MY_FLAG")
                    .hasMessageContaining("Allowed");
        }
    }
}
