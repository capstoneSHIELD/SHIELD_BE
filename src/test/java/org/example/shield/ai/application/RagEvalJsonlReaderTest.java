package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.RagEvalItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagEvalJsonlReaderTest {

    private final RagEvalJsonlReader reader =
            new RagEvalJsonlReader(new ObjectMapper().findAndRegisterModules());

    @Test
    @DisplayName("reads v1.1 JSONL eval items")
    void read_v11Jsonl() {
        String jsonl = """
                {"id":"Q1","split":"dev","nodeId":"law-001-02-02","l1":"부동산 거래","l2":"부동산 임대차","l3":"보증금 및 차임","domain":"real_estate_lease","query":"보증금 반환","keywords":["보증금"],"expectedDocumentIds":["law:민법:제618조"],"relevanceJudgments":{"law:민법:제618조":3},"source":"ops_log","failureType":"baseline","reviewer":"reviewer-a","createdAt":"2026-05-19"}
                {"id":"Q2","split":"holdout","nodeId":"law-001-02-03","query":"계약 갱신","expectedDocumentIds":["case:2024다12345"],"source":"ops_log","failureType":"baseline","reviewer":"reviewer-a"}
                """;

        List<RagEvalItem> items = reader.read(jsonl);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).keywords()).containsExactly("보증금");
        assertThat(items.get(0).relevanceJudgments()).containsEntry("law:민법:제618조", 3);
        assertThat(items.get(1).split()).isEqualTo("holdout");
    }

    @Test
    @DisplayName("reports the failing line for invalid JSONL")
    void read_invalidLine() {
        assertThatThrownBy(() -> reader.read("{\"id\":\"Q1\"}\nnot-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }
}
