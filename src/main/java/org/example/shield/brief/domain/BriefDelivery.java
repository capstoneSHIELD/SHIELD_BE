package org.example.shield.brief.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.shield.common.enums.DeliveryStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BriefDelivery {

    /**
     * 의뢰서 전달 후 변호사가 응답할 수 있는 기간 (Issue #106).
     * 이 시간 경과 후에는 수락/거절이 모두 불가능.
     */
    public static final Duration RESPONSE_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID briefId;

    @Column(nullable = false)
    private UUID lawyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "delivery_status")
    private DeliveryStatus status;

    @Column(columnDefinition = "text")
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    private LocalDateTime viewedAt;

    private LocalDateTime respondedAt;

    @Builder
    private BriefDelivery(UUID briefId, UUID lawyerId) {
        this.briefId = briefId;
        this.lawyerId = lawyerId;
        this.status = DeliveryStatus.DELIVERED;
        this.sentAt = LocalDateTime.now();
    }

    public static BriefDelivery create(UUID briefId, UUID lawyerId) {
        return BriefDelivery.builder()
                .briefId(briefId)
                .lawyerId(lawyerId)
                .build();
    }

    public void markViewed() {
        if (this.viewedAt == null) {
            this.viewedAt = LocalDateTime.now();
        }
    }

    /**
     * 24시간 응답 기한 경과 여부 (Issue #106).
     * 이미 응답된 건은 false 반환 (응답 후에는 만료 개념이 의미 없음).
     */
    public boolean isExpired() {
        if (this.status != DeliveryStatus.DELIVERED) return false;
        return this.sentAt.plus(RESPONSE_WINDOW).isBefore(LocalDateTime.now());
    }

    public void accept() {
        if (isExpired()) {
            throw new IllegalStateException("delivery_expired");
        }
        this.status = DeliveryStatus.CONFIRMED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject(String reason) {
        if (isExpired()) {
            throw new IllegalStateException("delivery_expired");
        }
        this.status = DeliveryStatus.REJECTED;
        this.rejectionReason = reason;
        this.respondedAt = LocalDateTime.now();
    }
}
