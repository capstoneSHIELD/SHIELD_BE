package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.OfflineQualityReportRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineQualityReportRecordTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("offline quality report serializes required JSONL field names")
    void serializesSnakeCaseFields() throws Exception {
        UUID id = UUID.randomUUID();
        OfflineQualityReportRecord record = new OfflineQualityReportRecord(
                id,
                "real_estate",
                2,
                List.of("deposit_amount"),
                List.of("legal judgment"),
                "keyword_mismatch",
                List.of("landlord_response"),
                true
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(record));

        assertThat(json.get("consultation_id").asText()).isEqualTo(id.toString());
        assertThat(json.get("repeat_question_count").asInt()).isEqualTo(2);
        assertThat(json.get("missing_slots").get(0).asText()).isEqualTo("deposit_amount");
        assertThat(json.get("legal_leak_expressions").get(0).asText()).isEqualTo("legal judgment");
        assertThat(json.get("retrieval_failure_type").asText()).isEqualTo("keyword_mismatch");
        assertThat(json.get("dynamic_to_static_candidates").get(0).asText()).isEqualTo("landlord_response");
        assertThat(json.get("review_required").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("offline quality report job writes one JSON object per line")
    void jobSerializesJsonl() {
        OfflineQualityReportJob job = new OfflineQualityReportJob(objectMapper);
        OfflineQualityReportRecord first = new OfflineQualityReportRecord(
                UUID.randomUUID(), "domain-a", 0, List.of(), List.of(), "none", List.of(), false);
        OfflineQualityReportRecord second = new OfflineQualityReportRecord(
                UUID.randomUUID(), "domain-b", 1, List.of("slot"), List.of(), "semantic_mismatch", List.of(), true);

        String jsonl = job.toJsonl(List.of(first, second));

        assertThat(jsonl.split("\\R")).hasSize(2);
        assertThat(jsonl).contains("domain-a", "domain-b", "review_required");
    }
}
