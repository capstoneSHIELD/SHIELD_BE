package org.example.shield.brief.controller.dto;

import org.example.shield.brief.domain.Brief;
import org.example.shield.brief.domain.BriefDelivery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TODO [Issue #16] keyIssues 필드 추가
 *
 * - record에 List<String> keyIssues 필드 추가 (keywords 다음 위치)
 * - of() 메서드에 brief.getKeyIssues() 전달
 * - Notion 수신 의뢰서 상세 Response에도 keyIssues 필드 추가
 * - nullable: brief에 keyIssues가 없을 수 있음 (List<String> | null)
 */
public record InboxDetailResponse(
        UUID deliveryId,
        UUID briefId,
        String title,
        String legalField,
        String content,
        List<String> keywords,
        String status,
        String clientName,
        String clientEmail,
        LocalDateTime sentAt
) {
    public static InboxDetailResponse of(BriefDelivery delivery, Brief brief,
                                          String clientName, String clientEmail) {
        return new InboxDetailResponse(
                delivery.getId(),
                brief.getId(),
                brief.getTitle(),
                brief.getLegalField(),
                brief.getContent(),
                brief.getKeywords(),
                delivery.getStatus().name(),
                clientName,
                clientEmail,
                delivery.getSentAt()
        );
    }
}
