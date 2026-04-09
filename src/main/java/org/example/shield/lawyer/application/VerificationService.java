package org.example.shield.lawyer.application;

/**
 * 변호사 검증 서비스 - 검증 신청/상태 확인/관리자 처리.
 *
 * Layer: application
 * Called by: LawyerController (신청/상태), AdminController (처리)
 * Calls: LawyerReader, LawyerWriter, NotificationSender
 *
 * TODO:
 * - requestVerification(userId, barAssociationNumber):
 *   1. lawyers에 barAssociationNumber 저장
 *   2. verificationStatus를 PENDING으로 설정
 *   3. 이미 PENDING이면 409 에러
 *
 * - getVerificationStatus(userId): verificationStatus + verifiedAt 반환
 *
 * - processVerification(lawyerId, action, reason):
 *   1. action이 APPROVE → VERIFIED + verifiedAt 설정
 *   2. action이 REJECT → REJECTED
 *   3. 관리자(ADMIN)만 호출 가능
 *   4. NotificationSender로 변호사에게 이메일 알림
 */
public class VerificationService {
}
