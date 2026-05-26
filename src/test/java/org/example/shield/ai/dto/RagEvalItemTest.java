package org.example.shield.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RagEvalItem} 스키마 v1.6 검증 (P5.2 Commit 1).
 *
 * <p>새 필드 {@code dialogueIntent}, {@code lowEvidence}, {@code mixedType}가:
 * <ol>
 *   <li>JSON 직렬화 ↔ 역직렬화 라운드트립에서 보존</li>
 *   <li>v1.5 JSON에서 누락 시 default 값으로 채워짐</li>
 *   <li>{@code dialogue_intent} 같은 snake_case alias 인식</li>
 * </ol>
 */
class RagEvalItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("v1.6 — 신규 필드 round-trip 직렬화")
    void v16FieldsRoundTrip() throws Exception {
        RagEvalItem original = new RagEvalItem(
                "Q1",
                "real_estate_lease",
                "보증금 반환 절차",
                List.of(),
                List.of(),
                "baseline",
                "seed",
                "ai-rag");

        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"dialogueIntent\":\"ask_legal_advice\"");
        assertThat(json).contains("\"lowEvidence\":false");
        assertThat(json).contains("\"mixedType\":\"statute_only\"");

        RagEvalItem parsed = mapper.readValue(json, RagEvalItem.class);
        assertThat(parsed.dialogueIntent()).isEqualTo("ask_legal_advice");
        assertThat(parsed.lowEvidence()).isFalse();
        assertThat(parsed.mixedType()).isEqualTo("statute_only");
    }

    @Test
    @DisplayName("v1.5 호환 — 누락 필드는 default 적용 (BC)")
    void v15CompatibleParse() throws Exception {
        // v1.5 jsonl에는 dialogue_intent / low_evidence / mixed_type 필드가 없음
        String v15Json = """
                {
                  "id": "C1-Q01",
                  "domain": "real_estate_lease",
                  "query": "전세 계약이 끝났는데...",
                  "expectedDocumentIds": ["law:민법:제317조"]
                }
                """;

        RagEvalItem item = mapper.readValue(v15Json, RagEvalItem.class);

        assertThat(item.id()).isEqualTo("C1-Q01");
        assertThat(item.dialogueIntent()).isEqualTo("ask_legal_advice"); // default
        assertThat(item.lowEvidence()).isFalse();                         // primitive default
        assertThat(item.mixedType()).isEqualTo("statute_only");           // default
        assertThat(item.expectedDocumentIds()).containsExactly("law:민법:제317조");
    }

    @Test
    @DisplayName("snake_case alias — dialogue_intent / low_evidence / mixed_type 인식")
    void snakeCaseAliases() throws Exception {
        String json = """
                {
                  "id": "Q1",
                  "domain": "real_estate_lease",
                  "query": "test",
                  "dialogue_intent": "greeting",
                  "low_evidence": true,
                  "mixed_type": "mixed",
                  "expectedDocumentIds": []
                }
                """;

        RagEvalItem item = mapper.readValue(json, RagEvalItem.class);

        assertThat(item.dialogueIntent()).isEqualTo("greeting");
        assertThat(item.lowEvidence()).isTrue();
        assertThat(item.mixedType()).isEqualTo("mixed");
    }

    @Test
    @DisplayName("dialogueIntent / mixedType의 blank 입력 → default")
    void blankFieldsFallbackToDefault() {
        RagEvalItem item = new RagEvalItem(
                "Q1", "dev",
                "  ", false, "  ",   // blank dialogueIntent + blank mixedType
                null, null, null, null,
                "domain", "query",
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                "baseline", "seed", "reviewer", "2026-05-26");

        assertThat(item.dialogueIntent()).isEqualTo("ask_legal_advice");
        assertThat(item.mixedType()).isEqualTo("statute_only");
    }

    @Test
    @DisplayName("low-evidence + 빈 expectedDocumentIds 조합 정상 생성")
    void lowEvidenceAllowsEmptyExpectedDocs() {
        RagEvalItem item = new RagEvalItem(
                "LE-1", "dev",
                "ask_legal_advice", true, "statute_only",
                null, null, null, null,
                "real_estate_lease", "모호한 질문",
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                "baseline", "seed", "reviewer", "2026-05-26");

        assertThat(item.lowEvidence()).isTrue();
        assertThat(item.expectedDocumentIds()).isEmpty();
    }
}
