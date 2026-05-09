package org.example.shield.fcm.infrastructure;

import org.example.shield.fcm.domain.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FcmTokenJpaRepository extends JpaRepository<FcmToken, UUID> {

    List<FcmToken> findAllByUserId(UUID userId);

    Optional<FcmToken> findByToken(String token);

    boolean existsByToken(String token);

    void deleteByToken(String token);

    // FcmToken 엔티티 필드 추가/변경 시 본 SQL 도 동기화 필요.
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO fcm_tokens (id, user_id, token, device_type, created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, :token, :deviceType, NOW(), NOW())
            ON CONFLICT (token)
            DO UPDATE SET
                user_id = EXCLUDED.user_id,
                device_type = EXCLUDED.device_type,
                updated_at = NOW()
            """, nativeQuery = true)
    void upsertToken(@Param("userId") UUID userId,
                     @Param("token") String token,
                     @Param("deviceType") String deviceType);
}
