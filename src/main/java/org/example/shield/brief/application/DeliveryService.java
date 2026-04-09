package org.example.shield.brief.application;

/**
 * 의뢰서 전달 서비스 - 전달/현황조회/접수/거절.
 *
 * Layer: application
 * Called by: BriefController, LawyerInboxController
 * Calls: DeliveryReader, DeliveryWriter, BriefReader, NotificationSender
 *
 * TODO:
 * - createDelivery(briefId, lawyerId):
 *   1. brief가 CONFIRMED 상태인지 확인
 *   2. deliveries에 새 row (status: DELIVERED, sentAt: now())
 *   3. NotificationSender로 변호사에게 이메일 알림
 *
 * - getDeliveries(briefId): 전달 현황 (sentAt, viewedAt, respondedAt 포함)
 * - getInbox(lawyerId, pageable): 수신 의뢰서 목록 (변호사용)
 * - getInboxDetail(deliveryId): 수신 의뢰서 상세 (privacySetting 적용)
 * - updateDeliveryStatus(deliveryId, status, rejectionReason):
 *   - CONFIRMED: 수락 → respondedAt 기록 + 의뢰인에게 알림
 *   - REJECTED: 거절 → rejectionReason 저장 + respondedAt 기록 + 의뢰인에게 알림
 */
public class DeliveryService {
}
