package org.example.shield.consultation.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.shield.consultation.domain.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 메시지 전송 응답 (Issue #45 / Issue #88).
 *
 * <p>{@code progress} 는 의뢰인 화면에서 상담 진행률 표시(예: "3/10", 30%) 에 사용된다.
 * 백엔드는 {@code max-user-turns} 상한과 방금 저장된 USER 턴 수를 기준으로 계산해
 * 응답에 함께 내려준다. 자세한 계약은 {@link Progress} 참조.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageResponse(
        UUID messageId,
        String role,
        String content,
        LocalDateTime createdAt,
        boolean allCompleted,
        Classification classification,
        Progress progress
) {
    public static SendMessageResponse from(Message message, boolean allCompleted) {
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                null,
                null
        );
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           List<String> primaryField, List<String> tags) {
        Classification classif = (allCompleted && primaryField != null)
                ? new Classification(primaryField, tags)
                : null;
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                classif,
                null
        );
    }

    public static SendMessageResponse from(Message message, boolean allCompleted, Progress progress) {
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                null,
                progress
        );
    }

    public static SendMessageResponse from(Message message, boolean allCompleted,
                                           List<String> primaryField, List<String> tags,
                                           Progress progress) {
        Classification classif = (allCompleted && primaryField != null)
                ? new Classification(primaryField, tags)
                : null;
        return new SendMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt(),
                allCompleted,
                classif,
                progress
        );
    }

    public record Classification(
            List<String> primaryField,
            List<String> tags
    ) {}

    /**
     * 상담 진행률 (Issue #88).
     *
     * @param currentTurn     방금 저장한 USER 메시지 포함 누적 USER 턴 수 (1~maxTurns).
     * @param maxTurns        설정된 USER 턴 상한 (현재 10).
     * @param progressPercent {@code currentTurn * 100 / maxTurns} 정수 (10~100).
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
