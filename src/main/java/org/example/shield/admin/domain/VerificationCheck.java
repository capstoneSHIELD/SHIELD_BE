package org.example.shield.admin.domain;

/**
 * 변호사 자동 검증 결과 + 관리자 체크리스트 엔티티.
 *
 * Table: verification_checks
 *
 * TODO: @Entity 구현
 * - id (UUID PK)
 * - lawyerId (UUID FK → lawyers, UNIQUE 1:1)
 * - emailDuplicate (boolean, 이메일 중복 없음)
 * - phoneDuplicate (boolean, 전화번호 중복 없음)
 * - nameDuplicate (boolean, 동일 이름 계정 없음)
 * - requiredFields (boolean, 필수 항목 누락 없음)
 * - licenseVerified (boolean, 변호사 자격 증빙 확인)
 * - documentMatched (boolean, 서류 정보 일치)
 * - specializationValid (boolean, 전문 분야 기재 적절)
 * - experienceVerified (boolean, 경력 정보 확인)
 * - duplicateSignup (boolean, 중복 가입 여부 확인)
 * - documentComplete (boolean, 필수 서류 누락 없음)
 * - updatedAt (LocalDateTime)
 */
public class VerificationCheck {
}
