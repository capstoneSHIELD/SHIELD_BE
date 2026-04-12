package org.example.shield.admin.domain;

/**
 * 변호사 인증 상태 변경 이력 엔티티.
 *
 * Table: verification_logs
 *
 * TODO: @Entity 구현
 * - id (UUID PK)
 * - lawyerId (UUID FK → lawyers)
 * - adminId (UUID FK → users, 처리한 관리자)
 * - fromStatus (String, 이전 상태)
 * - toStatus (String, 변경된 상태)
 * - reason (String, 처리 사유)
 * - createdAt (LocalDateTime)
 */
public class VerificationLog {
}
