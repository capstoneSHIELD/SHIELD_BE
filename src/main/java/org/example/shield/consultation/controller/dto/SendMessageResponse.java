package org.example.shield.consultation.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.checklist.ChecklistScopeLevel;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.application.ClassificationResolution;
import org.example.shield.consultation.domain.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Message send response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageResponse(
        UUID messageId,
        String role,
        String content,
        LocalDateTime createdAt,
        boolean allCompleted,
        ClassificationResolution classification,
        Progress progress,
        Checklist checklist
) {
    public SendMessageResponse {
        checklist = checklistOrEmpty(checklist);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted) {
        return from(message, allCompleted, (ClassificationResolution) null, null);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           ClassificationResolution classification) {
        return from(message, allCompleted, classification, null);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           Progress progress) {
        return from(message, allCompleted, (ClassificationResolution) null, progress);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           ClassificationResolution classification,
                                           Progress progress) {
        return from(message, allCompleted, classification, progress, null);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           ClassificationResolution classification,
                                           Progress progress,
                                           Checklist checklist) {
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                classification,
                progress,
                checklistOrEmpty(checklist)
        );
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           Progress progress,
                                           Checklist checklist) {
        return from(message, allCompleted, (ClassificationResolution) null, progress, checklist);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           List<String> primaryField, List<String> tags) {
        return from(message, allCompleted, primaryField, tags, null);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           List<String> primaryField, List<String> tags,
                                           Progress progress) {
        ClassificationResolution classif = (allCompleted && primaryField != null)
                ? new ClassificationResolution(
                        false,
                        null,
                        null,
                        new ClassificationCandidate(primaryField, List.of(), tags))
                : null;
        return from(message, allCompleted, classif, progress);
    }

    private static Checklist checklistOrEmpty(Checklist checklist) {
        return checklist == null ? Checklist.empty() : checklist;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Checklist(
            CaseType caseType,
            String sourceVersion,
            List<Item> items,
            List<String> warnings
    ) {
        public Checklist {
            caseType = caseType == null ? CaseType.empty() : caseType;
            items = items == null ? List.of() : List.copyOf(items);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public static Checklist empty() {
            return new Checklist(CaseType.empty(), null, List.of(), List.of());
        }

        public static Checklist from(ChecklistScope scope) {
            if (scope == null) {
                return empty();
            }
            return new Checklist(
                    new CaseType(scope.l1Name(), scope.l2Name(), scope.l3Name()),
                    scope.sourceVersion(),
                    scope.items().stream().map(Item::from).toList(),
                    scope.warnings()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseType(
            String l1,
            String l2,
            String l3
    ) {
        public static CaseType empty() {
            return new CaseType(null, null, null);
        }
    }

    public record Item(
            String slotId,
            String label,
            ChecklistScopeLevel level,
            boolean required,
            int priority,
            String sourcePath,
            String nodeId,
            SlotValueType valueType
    ) {
        private static Item from(ChecklistScopeItem item) {
            return new Item(
                    item.slotId(),
                    item.label(),
                    item.level(),
                    item.required(),
                    item.priority(),
                    item.sourcePath(),
                    item.nodeId(),
                    item.valueType()
            );
        }
    }

    /**
     * Consultation progress for the requester UI.
     *
     * @param currentTurn     cumulative USER turn count including the message just saved.
     * @param maxTurns        configured USER turn limit.
     * @param progressPercent integer percentage of currentTurn / maxTurns.
     */
    public record Progress(
            int currentTurn,
            int maxTurns,
            int progressPercent
    ) {
        public static Progress of(int currentTurn, int maxTurns) {
            int safeMax = Math.max(maxTurns, 1);
            int clampedCurrent = Math.min(Math.max(currentTurn, 0), safeMax);
            int percent = clampedCurrent * 100 / safeMax;
            return new Progress(clampedCurrent, safeMax, percent);
        }
    }
}
