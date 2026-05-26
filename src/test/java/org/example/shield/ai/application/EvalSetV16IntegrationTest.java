package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.RagEvalItem;
import org.example.shield.ai.dto.RagEvalSetValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.6 평가셋 파일 검증 (P5.2 Commit 2).
 *
 * <p>{@code eval/eval-set.v1.6.jsonl} 50건이 다음 조건을 만족하는지 확인:
 * <ol>
 *   <li>JSONL 파싱 성공 (RagEvalItem 직접 매핑)</li>
 *   <li>5개 intent 카테고리 각 10건씩</li>
 *   <li>RagEvalSetValidator를 통과 (low-evidence 항목은 expected 부재 허용)</li>
 * </ol>
 */
class EvalSetV16IntegrationTest {

    private static final Path EVAL_PATH = Path.of("eval/eval-set.v1.6.jsonl");

    private final RagEvalJsonlReader reader = new RagEvalJsonlReader(new ObjectMapper());

    @Test
    @DisplayName("v1.6 jsonl — 50건 모두 파싱")
    void parses50Items() {
        List<RagEvalItem> items = reader.read(EVAL_PATH);
        assertThat(items).hasSize(50);
    }

    @Test
    @DisplayName("v1.6 jsonl — 5개 intent 카테고리 각 10건 분포")
    void intentDistribution() {
        List<RagEvalItem> items = reader.read(EVAL_PATH);
        Map<String, Long> countByIntent = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        RagEvalItem::dialogueIntent,
                        java.util.stream.Collectors.counting()));

        assertThat(countByIntent.get("greeting")).isEqualTo(10L);
        assertThat(countByIntent.get("irrelevant")).isEqualTo(10L);
        assertThat(countByIntent.get("change_topic")).isEqualTo(10L);
        // ask_legal_advice는 low-evidence 10 + mixed 10 = 20건
        assertThat(countByIntent.get("ask_legal_advice")).isEqualTo(20L);
    }

    @Test
    @DisplayName("v1.6 jsonl — low_evidence + mixed 분포")
    void lowEvidenceAndMixedCounts() {
        List<RagEvalItem> items = reader.read(EVAL_PATH);

        long lowEvidence = items.stream().filter(RagEvalItem::lowEvidence).count();
        // GREETING 10 + IRRELEVANT 10 + LOW-EVIDENCE 10 = 30건
        assertThat(lowEvidence).isEqualTo(30L);

        long mixed = items.stream()
                .filter(item -> "mixed".equals(item.mixedType()))
                .count();
        assertThat(mixed).isEqualTo(10L);
    }

    @Test
    @DisplayName("v1.6 jsonl — RagEvalSetValidator 통과 (low_evidence 허용)")
    void passesValidator() {
        List<RagEvalItem> items = reader.read(EVAL_PATH);
        RagEvalSetValidator validator = new RagEvalSetValidator();

        RagEvalSetValidationResult result = validator.validate(
                items,
                Map.of("dev", 50L),
                Map.of(),
                false);

        // low_evidence 항목의 "no expected document" failure는 발생하지 않아야 함
        assertThat(result.failures())
                .as("low-evidence 항목은 expected 부재 허용 → failures에 'no expected document' 없어야 함")
                .noneSatisfy(f -> assertThat(f).contains("no expected document reference"));
    }
}
