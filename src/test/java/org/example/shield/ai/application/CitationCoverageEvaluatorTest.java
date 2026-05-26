package org.example.shield.ai.application;

import org.example.shield.ai.application.CitationCoverageEvaluator.CoverageResult;
import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalLawRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CitationCoverageEvaluator} (reference mention coverage) 검증.
 *
 * <p>regex 기반 한계 (의역 인식 불가, 정확성 미평가)는 본 evaluator의 의도된 제약.
 * 본 테스트는 다음을 검증:
 * <ol>
 *   <li>정상 인용 → expected와 매칭</li>
 *   <li>부분 인용 → 부분 hit rate</li>
 *   <li>인용 없음 → rate 0</li>
 *   <li>low-evidence (expected 비어있음) → rate null (분모 0 보호)</li>
 *   <li>판례 사건번호 추출</li>
 *   <li>법령 표기 변형 (제/공백/조) 수용</li>
 * </ol>
 */
class CitationCoverageEvaluatorTest {

    private final CitationCoverageEvaluator evaluator = new CitationCoverageEvaluator();

    private RagEvalItem itemWith(List<RagEvalLawRef> lawRefs, List<String> chunkIds, boolean lowEvidence) {
        return new RagEvalItem(
                "Q1", "dev",
                "ask_legal_advice", lowEvidence, "statute_only",
                null, null, null, null,
                "real_estate_lease", "query",
                List.of(),
                chunkIds,
                lawRefs,
                List.of(),
                Map.of(),
                "baseline", "seed", "reviewer", "2026-05-26");
    }

    @Test
    @DisplayName("정상 인용 — expected 모두 hit (rate 1.0)")
    void fullCoverage() {
        RagEvalItem item = itemWith(
                List.of(new RagEvalLawRef("law-civil", "제618조")),
                List.of(),
                false);

        CoverageResult result = evaluator.evaluate(
                "민법 제618조에 따라 임대차 계약은...", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(1.0);
        assertThat(result.expectedHits()).isEqualTo(1);
        assertThat(result.totalMentions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("부분 인용 — 2개 중 1개만 hit (rate 0.5)")
    void partialCoverage() {
        RagEvalItem item = itemWith(
                List.of(
                        new RagEvalLawRef("law-civil", "제618조"),
                        new RagEvalLawRef("law-civil", "제623조")
                ),
                List.of(),
                false);

        CoverageResult result = evaluator.evaluate(
                "민법 제618조에 따라...", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(0.5);
        assertThat(result.expectedHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("인용 없음 — rate 0")
    void noCoverage() {
        RagEvalItem item = itemWith(
                List.of(new RagEvalLawRef("law-civil", "제618조")),
                List.of(),
                false);

        CoverageResult result = evaluator.evaluate(
                "답변에 법령 인용이 전혀 없습니다.", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(0.0);
        assertThat(result.expectedHits()).isZero();
    }

    @Test
    @DisplayName("low-evidence — expected 비어있으면 rate null (분모 0 보호)")
    void lowEvidenceReturnsNullRate() {
        RagEvalItem item = itemWith(List.of(), List.of(), true);

        CoverageResult result = evaluator.evaluate(
                "민법 제618조라는 답변이 있어도", item);

        assertThat(result.expectedReferenceMentionRate()).isNull();
        assertThat(result.expectedHits()).isZero();
        assertThat(result.totalMentions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("판례 인용 — case:YYYY사건구분NNNNN 형식 매칭")
    void caseReferenceMatch() {
        RagEvalItem item = itemWith(
                List.of(),
                List.of("case:2020다12345"),
                false);

        CoverageResult result = evaluator.evaluate(
                "대법원 2020다12345 판결을 참고하면...", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(1.0);
        assertThat(result.expectedHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("법령 표기 변형 — 제/공백 없는 형태도 매칭")
    void statuteFormatVariations() {
        RagEvalItem item = itemWith(
                List.of(new RagEvalLawRef("law-civil", "제618조")),
                List.of(),
                false);

        // "민법 618조" (제 없음) — pattern은 제?를 허용
        CoverageResult result = evaluator.evaluate("민법 618조에 따라", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("expectedChunkIds — 'law-civil:제618조' 형식 정상 파싱")
    void chunkIdAsExpected() {
        RagEvalItem item = itemWith(
                List.of(),
                List.of("law-civil:제618조"),
                false);

        CoverageResult result = evaluator.evaluate(
                "민법 제618조 임대차 정의에 따라", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("주요 특별법 lawId — 주택/상가 임대차보호법 한글 명칭과 매칭")
    void specialLeaseLawIdsMatchKoreanNames() {
        RagEvalItem item = itemWith(
                List.of(new RagEvalLawRef("law-housing-lease", "제3조")),
                List.of("law-commercial-building-lease:제10조"),
                false);

        CoverageResult result = evaluator.evaluate(
                "주택임대차보호법 제3조와 상가건물 임대차보호법 제10조에 따라", item);

        assertThat(result.expectedReferenceMentionRate()).isEqualTo(1.0);
        assertThat(result.expectedHits()).isEqualTo(2);
    }

    @Test
    @DisplayName("null/blank 답변 텍스트 안전 처리")
    void nullAnswerSafe() {
        RagEvalItem item = itemWith(
                List.of(new RagEvalLawRef("law-civil", "제618조")),
                List.of(),
                false);

        assertThat(evaluator.evaluate(null, item).totalMentions()).isZero();
        assertThat(evaluator.evaluate("", item).totalMentions()).isZero();
        assertThat(evaluator.evaluate("   ", item).totalMentions()).isZero();
    }
}
