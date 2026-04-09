package org.example.shield.lawyer.domain;

/**
 * 변호사 프로필 엔티티 - lawyers 테이블 매핑 (BaseEntity 상속).
 *
 * TODO: @Entity 구현
 * - userId: UUID (FK -> users.id, UNIQUE)
 * - specializations: String (전문 분야)
 * - experienceYears: Integer (nullable)
 * - barAssociationNumber: String (NOT NULL)
 * - verificationStatus: VerificationStatus (PENDING / VERIFIED / REJECTED)
 * - verifiedAt: LocalDateTime (nullable)
 * - tags: JSONB (세부 전문 키워드)
 * - bio: String (nullable, 소개글)
 * - certifications: JSONB (자격증 목록)
 * - caseCount: Integer (수행 사례 수, DEFAULT 0)
 */
public class LawyerProfile {
}
