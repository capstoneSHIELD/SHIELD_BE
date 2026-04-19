package org.example.shield.lawyer.controller.dto;

import org.example.shield.lawyer.domain.LawyerProfile;

import java.time.LocalDateTime;

/**
 * 변호사 가입(/api/lawyers/me/register) 응답.
 *
 * 가입이 완료되면 User.role 이 USER → LAWYER 로 승격되기 때문에
 * 기존 JWT 에 담긴 role 클레임이 더 이상 유효하지 않다.
 * 이에 맞춰 새 accessToken 을 함께 반환하여 프론트가 즉시 교체할 수 있게 한다.
 */
public record LawyerRegisterResponse(
        String accessToken,
        String role,
        String verificationStatus,
        String barAssociationNumber,
        LocalDateTime requestedAt,
        LocalDateTime verifiedAt
) {
    public static LawyerRegisterResponse of(String accessToken, String role, LawyerProfile profile) {
        return new LawyerRegisterResponse(
                accessToken,
                role,
                profile.getVerificationStatus().name(),
                profile.getBarAssociationNumber(),
                // TODO: LawyerProfile 에 verificationRequestedAt 필드 추가 후 매핑 변경
                profile.getCreatedAt(),
                profile.getVerifiedAt()
        );
    }
}
