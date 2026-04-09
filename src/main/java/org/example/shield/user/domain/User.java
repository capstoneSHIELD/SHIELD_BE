package org.example.shield.user.domain;

/**
 * 사용자 엔티티 - users 테이블 매핑.
 *
 * TODO: @Entity 구현 (BaseEntity 상속 안 함)
 * - id: UUID (PK)
 * - email: String (UNIQUE, NOT NULL)
 * - name: String (NOT NULL)
 * - role: UserRole (USER / LAWYER / ADMIN)
 * - provider: String (GOOGLE)
 * - googleId: String (NOT NULL)
 * - refreshToken: String (nullable)
 * - profileImageUrl: String (nullable)
 */
public class User {
}
