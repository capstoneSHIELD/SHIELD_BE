package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DynamicPlanProposal;
import org.example.shield.ai.dto.DynamicPlanSlotProposal;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DynamicPlanProposer {

    private final ObjectMapper objectMapper;

    public DynamicPlanProposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DynamicPlanProposal parseProposal(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode caseType = root.path("caseType");
            List<DynamicPlanSlotProposal> slots = new ArrayList<>();
            if (root.path("slots").isArray()) {
                for (JsonNode slot : root.path("slots")) {
                    slots.add(new DynamicPlanSlotProposal(
                            slot.path("id").asText(),
                            slot.path("label").asText(),
                            SlotSource.from(slot.path("source").asText()),
                            textOrNull(slot.path("staticMappingId")),
                            slot.path("required").asBoolean(false),
                            slot.path("priority").asInt(100),
                            SlotStatus.from(slot.path("status").asText("missing")),
                            slot.path("question").asText(""),
                            textOrNull(slot.path("validationHint")),
                            textOrNull(slot.path("skipCondition"))));
                }
            }
            return new DynamicPlanProposal(
                    new CaseTypeResult(
                            textOrNull(caseType.path("l1")),
                            textOrNull(caseType.path("l2")),
                            textOrNull(caseType.path("l3")),
                            caseType.path("confidence").asDouble(root.path("planConfidence").asDouble(0.0))),
                    root.path("planConfidence").asDouble(0.0),
                    slots,
                    textOrNull(root.path("nextSlotId")),
                    root.path("allCompleted").asBoolean(false));
        } catch (Exception e) {
            throw new IllegalArgumentException("Dynamic plan proposal JSON parsing failed", e);
        }
    }

    public Map<String, Object> proposalSchema() {
        Map<String, Object> slotSchema = objectSchema(
                List.of("id", "label", "source", "required", "priority", "status", "question"),
                Map.of(
                        "id", Map.of("type", "string"),
                        "label", Map.of("type", "string"),
                        "source", Map.of("type", "string", "enum", List.of("static_checklist", "dynamic")),
                        "staticMappingId", Map.of("type", "string"),
                        "required", Map.of("type", "boolean"),
                        "priority", Map.of("type", "integer"),
                        "status", Map.of("type", "string", "enum",
                                List.of("missing", "collected", "pending_confirmation", "skipped")),
                        "question", Map.of("type", "string"),
                        "validationHint", Map.of("type", "string"),
                        "skipCondition", Map.of("type", "string")
                ));
        Map<String, Object> caseTypeSchema = objectSchema(
                List.of("l1", "l2", "l3"),
                Map.of(
                        "l1", Map.of("type", "string"),
                        "l2", Map.of("type", "string"),
                        "l3", Map.of("type", "string"),
                        "confidence", Map.of("type", "number")
                ));
        return objectSchema(
                List.of("caseType", "planConfidence", "slots", "nextSlotId", "allCompleted"),
                Map.of(
                        "caseType", caseTypeSchema,
                        "planConfidence", Map.of("type", "number"),
                        "slots", Map.of("type", "array", "items", slotSchema),
                        "nextSlotId", Map.of("type", "string"),
                        "allCompleted", Map.of("type", "boolean")
                ));
    }

    private Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", required);
        schema.put("properties", properties);
        return schema;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
