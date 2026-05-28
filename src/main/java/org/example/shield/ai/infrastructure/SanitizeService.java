package org.example.shield.ai.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 사용자 입력 Sanitization 서비스 (P0-III, Layer 0).
 *
 * 1. NFC 정규화 — 유니코드 정규화 (합성 문자 통일)
 * 2. 역할 구분자 무력화 — "AI:", "USER:", "SYSTEM:" 등 프롬프트 인젝션 벡터 차단
 * 3. PII 패턴 차단 — 주민등록번호, 연락처, 이메일, 신분증 번호, 인증번호,
 *    비밀번호, API key/token 등 거부
 */
@Component
@Slf4j
public class SanitizeService {

    // 역할 구분자 패턴: 줄 시작 또는 줄바꿈 후 역할명: 형태
    private static final Pattern ROLE_DELIMITER = Pattern.compile(
            "(?mi)^(AI|USER|ASSISTANT|SYSTEM|CHATBOT|HUMAN)\\s*:",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // PII 패턴들
    private static final Pattern RRN_PATTERN = Pattern.compile(
            "\\b\\d{6}[- ]?[1-4]\\d{6}\\b");
    private static final Pattern FOREIGN_REGISTRATION_PATTERN = Pattern.compile(
            "\\b\\d{6}[- ]?[5-8]\\d{6}\\b");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "\\b\\d{3,4}-\\d{2,6}-\\d{2,6}\\b");
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:01[016789][- ]?\\d{3,4}[- ]?\\d{4}|(?:02|0[3-6][1-5]|070)[- ]?\\d{3,4}[- ]?\\d{4})(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
            "(?iu)(?:여권\\s*(?:번호)?|passport\\s*(?:no\\.?|number)?)\\s*(?:[:：=]|은|는)?\\s*[A-Z]{1,2}\\d{7,8}\\b");
    private static final Pattern DRIVER_LICENSE_PATTERN = Pattern.compile(
            "(?iu)(?:운전\\s*면허\\s*(?:번호)?|driver'?s?\\s*license\\s*(?:no\\.?|number)?)\\s*(?:[:：=]|은|는)?\\s*\\d{2}[- ]?\\d{2}[- ]?\\d{6}[- ]?\\d{2}\\b");
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "(?iu)(?:인증\\s*번호|OTP|one[- ]?time\\s*password|verification\\s*code|확인\\s*코드)\\s*(?:[:：=]|은|는)?\\s*\\d{4,8}\\b");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?iu)(?:비밀\\s*번호|비번|패스워드|password|passcode|pwd)\\s*(?:[:：=]|은|는|is)\\s*[\\x21-\\x7E]{4,64}");
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(?:\\bsk-[A-Za-z0-9_-]{12,}\\b|\\bghp_[A-Za-z0-9]{20,}\\b|\\bgithub_pat_[A-Za-z0-9_]{20,}\\b|\\bAKIA[0-9A-Z]{16}\\b|\\bBearer\\s+[A-Za-z0-9._~+/=-]{10,}\\b|\\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|secret[_-]?key|client[_-]?secret)\\s*[:=]\\s*[A-Za-z0-9._~+/=-]{8,}\\b)");

    private static final String PII_BLOCK_MESSAGE =
            "[개인정보 보호를 위해 주민등록번호, 연락처, 이메일, 신분증 번호, 인증번호, 비밀번호 등 민감 정보는 입력할 수 없습니다. " +
            "해당 정보를 제외하고 다시 말씀해 주세요.]";

    private static final List<Pattern> PII_PATTERNS = List.of(
            RRN_PATTERN,
            FOREIGN_REGISTRATION_PATTERN,
            ACCOUNT_PATTERN,
            CARD_PATTERN,
            PHONE_PATTERN,
            EMAIL_PATTERN,
            PASSPORT_PATTERN,
            DRIVER_LICENSE_PATTERN,
            OTP_PATTERN,
            PASSWORD_PATTERN,
            TOKEN_PATTERN
    );

    /**
     * 사용자 입력 텍스트 sanitize.
     *
     * @param rawUserText 원본 사용자 입력
     * @return sanitize된 텍스트
     * @throws PiiDetectedException PII 패턴 검출 시
     */
    public String sanitizeUserText(String rawUserText) {
        if (rawUserText == null || rawUserText.isBlank()) {
            return rawUserText;
        }

        // 1. NFC 정규화
        String normalized = Normalizer.normalize(rawUserText, Normalizer.Form.NFC);

        // 2. PII 패턴 검사 (차단)
        for (Pattern pii : PII_PATTERNS) {
            if (pii.matcher(normalized).find()) {
                log.warn("PII 패턴 검출됨. 입력 거부.");
                throw new PiiDetectedException(PII_BLOCK_MESSAGE);
            }
        }

        // 3. 역할 구분자 무력화 (zero-width space 삽입)
        String sanitized = ROLE_DELIMITER.matcher(normalized)
                .replaceAll(mr -> mr.group(1) + "\u200B:");

        return sanitized;
    }

    /**
     * PII 검출 시 발생하는 예외.
     */
    public static class PiiDetectedException extends RuntimeException {
        public PiiDetectedException(String message) {
            super(message);
        }
    }
}
