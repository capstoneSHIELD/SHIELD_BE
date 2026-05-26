package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 답변 텍스트에서 법령/판례 인용을 정규식으로 추출하여 평가셋의
 * expected 참조와 매칭하는 평가기 (P5.2 Commit 3 — reference mention coverage).
 *
 * <h3>보수적 명명 (P5.2 피드백)</h3>
 * <ul>
 *   <li>이름은 "citation coverage" 가 아니라 <b>reference mention coverage</b></li>
 *   <li>정규식 기반 매칭이므로 정확한 인용 적절성(citation correctness)이 아니라
 *       <b>참조 언급률(reference mention rate)</b>만 측정함을 명시</li>
 *   <li>LLM judge 기반 정확 평가는 별도 plan으로 분리</li>
 * </ul>
 *
 * <h3>지원 패턴</h3>
 * <ul>
 *   <li>법령: {@code 민법 제618조}, {@code 상법 105조}, {@code 형법제250조} 등
 *       (공백/제/조 표기 변형 허용)</li>
 *   <li>판례: {@code 2020다12345}, {@code 2021므44444} 등 사건번호</li>
 * </ul>
 *
 * <h3>한계</h3>
 * <ul>
 *   <li>정규식 매칭이므로 의역·풀어쓴 인용은 감지 못함</li>
 *   <li>잘못된 인용(존재하지 않는 조문)은 구분 못함</li>
 *   <li>low-evidence 항목에서 기대 참조가 없는 경우 분모가 0이라 rate는 null</li>
 * </ul>
 */
@Component
public class CitationCoverageEvaluator {

    /**
     * 법령 조문 패턴. 예: "민법 제618조", "민법제618조", "민법 618조".
     * - 법령명: 한글 1~10자 + "법" (민법/상법/형법/주택임대차보호법 등, 총 2~11자)
     * - 분리: 공백 0개 이상
     * - "제" 0~1회
     * - 조문번호: 1~4 자리 숫자 + "조" + 선택적 "의N"
     */
    private static final Pattern STATUTE_REF = Pattern.compile(
            "([가-힣]{1,10}법)\\s*제?(\\d{1,4})\\s*조(?:의\\d+)?"
    );

    /**
     * 판례 사건번호 패턴. 예: "2020다12345", "2021므44444", "2018두77777".
     * 한글 1자(다/므/두/가합/구단 등 사건구분) + 숫자.
     */
    private static final Pattern CASE_REF = Pattern.compile(
            "(\\d{4})\\s*([가-힣]{1,3})(\\d{3,6})"
    );

    /**
     * 답변 텍스트에 대해 reference mention coverage 평가.
     *
     * @param answerText 평가 대상 (LLM 답변 본문)
     * @param item       평가셋 항목 (기대 참조 포함)
     * @return {@link CoverageResult} — {@code expectedReferenceMentionRate}는 분모 0이면 null
     */
    public CoverageResult evaluate(String answerText, RagEvalItem item) {
        Set<String> mentioned = extractMentions(answerText);
        Set<String> expected = expectedRefIds(item);

        if (expected.isEmpty()) {
            // low-evidence 항목 또는 expected 없음 → coverage 정의 불가
            return new CoverageResult(null, mentioned.size(), 0);
        }
        int hits = (int) expected.stream().filter(mentioned::contains).count();
        double rate = (double) hits / expected.size();
        return new CoverageResult(rate, mentioned.size(), hits);
    }

    Set<String> extractMentions(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> mentions = new LinkedHashSet<>();
        // 법령 추출
        Matcher statuteMatcher = STATUTE_REF.matcher(text);
        while (statuteMatcher.find()) {
            String lawName = statuteMatcher.group(1);
            String articleNo = statuteMatcher.group(2);
            mentions.add(normalizeLaw(lawName, articleNo));
        }
        // 판례 추출
        Matcher caseMatcher = CASE_REF.matcher(text);
        while (caseMatcher.find()) {
            String year = caseMatcher.group(1);
            String kind = caseMatcher.group(2);
            String number = caseMatcher.group(3);
            mentions.add("case:" + year + kind + number);
        }
        return mentions;
    }

    Set<String> expectedRefIds(RagEvalItem item) {
        if (item == null) {
            return Set.of();
        }
        Set<String> expected = new LinkedHashSet<>();
        // expectedLawRefs는 (lawId, articleNo) 형식 — 직접 매칭은 어렵지만,
        // articleNo 안에 "제618조" 같은 표기가 들어가므로 그대로 매칭 키로 사용.
        for (RagEvalLawRef ref : item.expectedLawRefs()) {
            if (ref != null && ref.articleNo() != null && !ref.articleNo().isBlank()) {
                String lawName = inferLawName(ref.lawId());
                String articleNo = normalizeArticleNo(ref.articleNo());
                expected.add(normalizeLaw(lawName, articleNo));
            }
        }
        // expectedChunkIds는 "law-civil:제618조" 또는 "case:2020다12345" 형식
        for (String id : item.expectedChunkIds()) {
            if (id == null || id.isBlank()) continue;
            if (id.startsWith("case:")) {
                expected.add(id);
            } else if (id.contains(":")) {
                String[] parts = id.split(":", 2);
                String lawName = inferLawName(parts[0]);
                String articleNo = normalizeArticleNo(parts[1]);
                expected.add(normalizeLaw(lawName, articleNo));
            }
        }
        return expected;
    }

    /**
     * "법-civil" 또는 "law-civil" 같은 lawId → "민법" 추정.
     * 매핑되지 않은 lawId는 그대로 키로 사용.
     */
    private static String inferLawName(String lawId) {
        if (lawId == null) return "";
        return switch (lawId.toLowerCase()) {
            case "law-civil", "civil", "법-civil" -> "민법";
            case "law-commercial", "commercial" -> "상법";
            case "law-criminal", "criminal" -> "형법";
            case "law-labor", "labor" -> "근로기준법";
            default -> lawId;
        };
    }

    /**
     * "제618조" / "618조" / "618" → "제618조" 정규화.
     */
    private static String normalizeArticleNo(String articleNo) {
        if (articleNo == null) return "";
        String trimmed = articleNo.trim();
        Matcher m = Pattern.compile("(\\d+)").matcher(trimmed);
        if (m.find()) {
            return "제" + m.group(1) + "조";
        }
        return trimmed;
    }

    /**
     * 법령명 + 조항번호를 일관된 키로. 공백 제거하고 "제N조" 형식 강제.
     */
    private static String normalizeLaw(String lawName, String articleNo) {
        String name = lawName == null ? "" : lawName.trim();
        String article = articleNo == null ? "" : articleNo.trim();
        if (!article.startsWith("제")) {
            article = "제" + article;
        }
        if (!article.endsWith("조") && article.matches(".*\\d+$")) {
            article = article + "조";
        }
        return name + "/" + article;
    }

    /**
     * 평가 결과.
     *
     * @param expectedReferenceMentionRate 기대 참조 중 답변에 언급된 비율 (0..1).
     *                                     기대 참조가 없으면 null.
     * @param totalMentions                답변에서 추출된 모든 참조 수 (잘못된 인용 포함)
     * @param expectedHits                 기대 참조 중 매칭된 수
     */
    public record CoverageResult(
            Double expectedReferenceMentionRate,
            int totalMentions,
            int expectedHits
    ) { }
}
