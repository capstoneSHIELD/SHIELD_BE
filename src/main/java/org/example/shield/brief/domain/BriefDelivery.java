package org.example.shield.brief.domain;

/**
 * 의뢰서 전달 엔티티 - deliveries 테이블 매핑.
 *
 * TODO: @Entity 구현 (BaseEntity 상속 안 함)
 * - id: UUID (PK)
 * - briefId: UUID (FK -> briefs.id)
 * - lawyerId: UUID (FK -> users.id)
 * - status: DeliveryStatus (DELIVERED / CONFIRMED / REJECTED)
 * - rejectionReason: String (nullable)
 * - sentAt: LocalDateTime (DEFAULT now())
 * - viewedAt: LocalDateTime (nullable)
 * - respondedAt: LocalDateTime (nullable)
 */
public class BriefDelivery {
}
