package org.example.shield.ai.infrastructure;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 응답 텍스트에서 PII(개인식별정보) 패턴을 식별하고 토큰으로 치환하는 컴포넌트
 * (P5.2 Commit 4 refine — {@code OutputComplianceShadowJudge}에서 추출).
 *
 * <p>본 마스킹은 shadow judge가 외부 평가 시스템으로 텍스트를 전송하기 전 사용된다.
 * <b>원본 텍스트는 저장하지 않으며</b>, 본 클래스의 결과만 로그/DB에 기록되어야 한다.
 *
 * <h3>적용 패턴</h3>
 * <ul>
 *   <li>주민등록번호 ({@code 920101-1234567}) — RRN</li>
 *   <li>카드번호 ({@code 1234-5678-1234-5678}) — CARD</li>
 *   <li>계좌번호 ({@code 110-123-456789}) — ACCOUNT</li>
 *   <li>휴대전화 ({@code 010-1234-5678}) — PHONE</li>
 *   <li>이메일 ({@code user@example.com}) — EMAIL</li>
 * </ul>
 *
 * <h3>의도적으로 제외된 패턴</h3>
 * <ul>
 *   <li><b>한글 이름</b> — 단순 regex로는 일반 단어와 구분 불가 (예: "법령 제618<u>조에</u>" 같은
 *       어절이 성씨 + 1자로 잘못 매칭). LLM judge가 컨텍스트로 직접 처리하는 게 정확.</li>
 *   <li><b>주소</b> — 도로명·지번 변형이 너무 다양해 regex로 안정 매칭 어려움.
 *       false negative보다는 위 5개 결정적 PII를 우선 보호.</li>
 * </ul>
 *
 * <p>이름·주소 마스킹은 후속 작업에서 NER 모델 또는 LLM 기반 redactor로 도입 검토.
 */
@Component
public class PiiMasker {

    private static final Pattern RRN = Pattern.compile("\\b\\d{6}[- ]?[1-4]\\d{6}\\b");
    private static final Pattern CARD = Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    private static final Pattern ACCOUNT = Pattern.compile("\\b\\d{3,4}-\\d{2,6}-\\d{2,6}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    /**
     * 텍스트에 PII 마스킹 적용.
     *
     * @param text 마스킹 대상 (null/blank 그대로 반환)
     * @return PII가 토큰으로 치환된 텍스트
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = RRN.matcher(text).replaceAll("[RRN]");
        masked = CARD.matcher(masked).replaceAll("[CARD]");
        masked = PHONE.matcher(masked).replaceAll("[PHONE]");
        masked = ACCOUNT.matcher(masked).replaceAll("[ACCOUNT]");
        masked = EMAIL.matcher(masked).replaceAll("[EMAIL]");
        return masked;
    }
}
