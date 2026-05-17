package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.DynamicPlanProposal;
import org.example.shield.ai.dto.slot.SlotSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPlanProposerSchemaTest {

    private final DynamicPlanProposer proposer = new DynamicPlanProposer(new ObjectMapper());

    @Test
    @DisplayName("proposal schema contains required P3 fields")
    void proposalSchema() {
        Map<String, Object> schema = proposer.proposalSchema();

        assertThat(schema.toString()).contains(
                "caseType",
                "planConfidence",
                "slots",
                "nextSlotId",
                "allCompleted",
                "staticMappingId");
    }

    @Test
    @DisplayName("proposal JSON parses into DTO")
    void parseProposal() {
        String json = """
                {
                  "caseType": {"l1": "부동산 거래", "l2": "부동산 임대차", "l3": "보증금 및 차임"},
                  "planConfidence": 0.87,
                  "slots": [
                    {
                      "id": "lease_end_date",
                      "label": "계약 종료일",
                      "source": "static_checklist",
                      "staticMappingId": "real-estate.lease_end_date",
                      "required": true,
                      "priority": 1,
                      "status": "missing",
                      "question": "계약은 언제 종료되었나요?",
                      "validationHint": "date"
                    }
                  ],
                  "nextSlotId": "lease_end_date",
                  "allCompleted": false
                }
                """;

        DynamicPlanProposal proposal = proposer.parseProposal(json);

        assertThat(proposal.planConfidence()).isEqualTo(0.87);
        assertThat(proposal.slots()).hasSize(1);
        assertThat(proposal.slots().get(0).source()).isEqualTo(SlotSource.STATIC_CHECKLIST);
        assertThat(proposal.nextSlotId()).isEqualTo("lease_end_date");
    }
}
