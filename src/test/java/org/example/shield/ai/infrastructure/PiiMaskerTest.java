package org.example.shield.ai.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PiiMasker} 검증 (P5.2 Commit 4 refine).
 *
 * <p>5개 결정적 PII 카테고리(RRN/CARD/ACCOUNT/PHONE/EMAIL) 마스킹 검증.
 * 이름·주소는 false positive 위험으로 의도적 제외 (LLM redactor 후속 작업).
 */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    @DisplayName("주민등록번호 마스킹")
    void masksRrn() {
        assertThat(masker.mask("주민번호 901101-1234567 입니다"))
                .contains("[RRN]")
                .doesNotContain("901101-1234567");
    }

    @Test
    @DisplayName("카드번호 마스킹")
    void masksCard() {
        assertThat(masker.mask("카드번호 1234-5678-1234-5678"))
                .contains("[CARD]")
                .doesNotContain("1234-5678-1234-5678");
    }

    @Test
    @DisplayName("계좌번호 마스킹")
    void masksAccount() {
        assertThat(masker.mask("계좌 110-123-456789"))
                .contains("[ACCOUNT]")
                .doesNotContain("110-123-456789");
    }

    @Test
    @DisplayName("휴대전화 마스킹")
    void masksPhone() {
        assertThat(masker.mask("연락처 010-1234-5678"))
                .contains("[PHONE]")
                .doesNotContain("010-1234-5678");
    }

    @Test
    @DisplayName("이메일 마스킹")
    void masksEmail() {
        assertThat(masker.mask("이메일은 user@example.com 입니다"))
                .contains("[EMAIL]")
                .doesNotContain("user@example.com");
    }

    @Test
    @DisplayName("복합 — 여러 PII 동시 마스킹")
    void masksMixedPii() {
        String input = "010-9876-5432 / user@test.com / 901101-1234567 / 1234-5678-1234-5678";
        String masked = masker.mask(input);

        assertThat(masked)
                .contains("[PHONE]")
                .contains("[EMAIL]")
                .contains("[RRN]")
                .contains("[CARD]")
                .doesNotContain("010-9876-5432")
                .doesNotContain("user@test.com")
                .doesNotContain("901101-1234567")
                .doesNotContain("1234-5678-1234-5678");
    }

    @Test
    @DisplayName("null/blank 입력 그대로 반환")
    void nullOrBlankPassthrough() {
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask("")).isEmpty();
        assertThat(masker.mask("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("PII 없는 텍스트는 변경 없음 (false positive 없음)")
    void noPiiUnchanged() {
        String input = "법령 제618조에 따른 임대차 정의는 다음과 같습니다.";
        assertThat(masker.mask(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("이름 / 주소는 의도적으로 마스킹하지 않음 (false positive 보호)")
    void namesAndAddressesNotMasked() {
        // 한글 이름이나 주소는 NER 모델 도입 전까지 보존 — judge에 그대로 전송
        assertThat(masker.mask("김철수 씨가 서울시 강남구에 거주합니다"))
                .isEqualTo("김철수 씨가 서울시 강남구에 거주합니다");
    }
}
