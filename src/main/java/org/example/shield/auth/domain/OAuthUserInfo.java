package org.example.shield.auth.domain;

/**
 * 외부 OAuth 프로바이더에서 받은 사용자 정보 (provider 무관 일반화).
 *
 * <p>{@code providerId} 는 해당 프로바이더에서 발급한 외부 사용자 식별자다.
 * - Google: Google 계정 ID
 * - Naver: Naver 계정 ID
 * 저장 시 User 엔티티의 provider 별 컬럼(googleId / naverId)에 매핑한다.</p>
 */
public record OAuthUserInfo(
        String email,
        String name,
        String providerId
) {}
