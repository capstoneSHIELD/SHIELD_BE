package org.example.shield.consultation.controller.dto;

import org.example.shield.consultation.application.ClassificationResolution;
import org.example.shield.consultation.domain.Consultation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConsultationResponse(
        UUID consultationId,
        String status,
        List<String> userDomains,
        List<String> userSubDomains,
        List<String> userTags,
        List<String> aiDomains,
        List<String> aiSubDomains,
        List<String> aiTags,
        ClassificationResolution classification,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        BriefSummary brief,
        /** AI 사실관계 수집 완료 여부 (Issue #100). FE 의 의뢰서 생성 버튼 노출 복원용. */
        boolean allCompleted
) {
    public record BriefSummary(UUID briefId, String title, String status) {}

    public static ConsultationResponse from(Consultation consultation, BriefSummary brief,
                                            ClassificationResolution classification) {
        return new ConsultationResponse(
                consultation.getId(),
                consultation.getStatus().name(),
                consultation.getUserDomains(),
                consultation.getUserSubDomains(),
                consultation.getUserTags(),
                consultation.getAiDomains(),
                consultation.getAiSubDomains(),
                consultation.getAiTags(),
                classification,
                consultation.getLastMessage(),
                consultation.getLastMessageAt(),
                consultation.getCreatedAt(),
                brief,
                consultation.isAllCompleted()
        );
    }
}
