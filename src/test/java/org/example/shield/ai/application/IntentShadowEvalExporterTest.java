package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentShadowEvalRecord;
import org.example.shield.ai.dto.LegalAdviceLabelRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntentShadowEvalExporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IntentShadowEvalExporter exporter =
            new IntentShadowEvalExporter(objectMapper, true, false);

    @Test
    @DisplayName("shadow eval export hashes user text and serializes JSONL without raw text")
    void jsonlExport() throws Exception {
        String hash = exporter.hashUserText("보증금은 3천만 원입니다");
        IntentShadowEvalRecord record = new IntentShadowEvalRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                hash,
                DialogueIntent.PROVIDE_INFO,
                0.91,
                List.of(),
                new CaseTypeResult("부동산", "임대차", "보증금", 0.8),
                false,
                null,
                false,
                LocalDateTime.of(2026, 5, 17, 12, 0));

        String jsonl = exporter.toJsonl(List.of(record));
        JsonNode json = objectMapper.readTree(jsonl);

        assertThat(hash).hasSize(64);
        assertThat(json.get("user_text_hash").asText()).isEqualTo(hash);
        assertThat(json.has("rawText")).isFalse();
    }

    @Test
    @DisplayName("legal advice labels can be exported as CSV")
    void labelCsvExport() {
        LegalAdviceLabelRecord label = new LegalAdviceLabelRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DialogueIntent.ASK_LEGAL_ADVICE,
                DialogueIntent.PROVIDE_INFO,
                true,
                false,
                true,
                "legal",
                "needs review");

        String csv = exporter.toLegalAdviceLabelCsv(List.of(label));

        assertThat(csv).contains("expected_intent,actual_intent");
        assertThat(csv).contains("ASK_LEGAL_ADVICE,PROVIDE_INFO");
    }
}
