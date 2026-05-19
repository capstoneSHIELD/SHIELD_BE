package org.example.shield.ai.dto.slot;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SlotStateItem {

    private String slotId;
    private String label;
    private SlotSource source = SlotSource.STATIC_CHECKLIST;
    private boolean required;
    private int priority;
    private SlotStatus status = SlotStatus.MISSING;
    private String collectedValue;
    private String pendingValue;
    private SlotValueType valueType = SlotValueType.TEXT;
    private Double confidence;
    private List<String> askedQuestions = new ArrayList<>();
    private String answeredAt;
    private String updatedAt;
    private String sourcePath;
    private String nodeId;
    private boolean outOfScope;
    private String legacySlotId;

    public static SlotStateItem staticChecklist(
            String slotId,
            String label,
            boolean required,
            int priority,
            boolean collected,
            SlotValueType valueType
    ) {
        SlotStateItem item = new SlotStateItem();
        item.slotId = slotId;
        item.label = label;
        item.source = SlotSource.STATIC_CHECKLIST;
        item.required = required;
        item.priority = priority;
        item.status = collected ? SlotStatus.COLLECTED : SlotStatus.MISSING;
        item.valueType = valueType == null ? SlotValueType.TEXT : valueType;
        item.updatedAt = now();
        if (collected) {
            item.answeredAt = item.updatedAt;
        }
        return item;
    }

    public static SlotStateItem staticChecklist(
            String slotId,
            String label,
            boolean required,
            int priority,
            boolean collected,
            SlotValueType valueType,
            String sourcePath,
            String nodeId
    ) {
        SlotStateItem item = staticChecklist(slotId, label, required, priority, collected, valueType);
        item.sourcePath = sourcePath;
        item.nodeId = nodeId;
        return item;
    }

    public void appendAskedQuestion(String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        if (askedQuestions == null) {
            askedQuestions = new ArrayList<>();
        }
        String normalized = question.trim().replaceAll("\\s+", " ");
        if (!askedQuestions.contains(normalized)) {
            askedQuestions.add(normalized);
        }
        updatedAt = now();
    }

    public String displayCollectedValue() {
        if (collectedValue == null || collectedValue.isBlank()) {
            return "value not confirmed";
        }
        return collectedValue;
    }

    public static String now() {
        return LocalDateTime.now().toString();
    }
}
