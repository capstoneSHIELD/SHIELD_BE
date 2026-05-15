package org.example.shield.consultation.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.application.ClassificationResolution;
import org.example.shield.consultation.domain.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageResponse(
        UUID messageId,
        String role,
        String content,
        LocalDateTime createdAt,
        boolean allCompleted,
        ClassificationResolution classification
) {
    public static SendMessageResponse from(Message message, boolean allCompleted) {
        return from(message, allCompleted, null);
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           ClassificationResolution classification) {
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                classification
        );
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           List<String> primaryField, List<String> tags) {
        ClassificationResolution classif = (allCompleted && primaryField != null)
                ? new ClassificationResolution(
                        false,
                        null,
                        null,
                        new ClassificationCandidate(primaryField, List.of(), tags))
                : null;
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                classif
        );
    }
}
