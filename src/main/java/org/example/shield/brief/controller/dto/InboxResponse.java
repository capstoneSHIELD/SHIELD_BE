package org.example.shield.brief.controller.dto;

import org.example.shield.brief.domain.Brief;
import org.example.shield.brief.domain.BriefDelivery;

import java.time.LocalDateTime;
import java.util.UUID;

public record InboxResponse(
        UUID deliveryId,
        UUID briefId,
        String briefTitle,
        String legalField,
        String status,
        LocalDateTime sentAt,
        /** 24시간 응답 기한 경과 여부 (Issue #106). FE 가 수락 버튼 비활성화 등에 사용. */
        boolean isExpired
) {
    public static InboxResponse of(BriefDelivery delivery, Brief brief) {
        return new InboxResponse(
                delivery.getId(),
                brief.getId(),
                brief.getTitle(),
                brief.getLegalField(),
                delivery.getStatus().name(),
                delivery.getSentAt(),
                delivery.isExpired()
        );
    }
}
