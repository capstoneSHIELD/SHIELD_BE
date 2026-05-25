package org.example.shield.auth.application;

import org.example.shield.auth.domain.OAuthClient;
import org.example.shield.auth.domain.OAuthUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Kakao OAuth 사용자 정보 조회 서비스.
 *
 * <p>{@link org.example.shield.auth.infrastructure.KakaoOAuthClient} 를 명시적으로
 * 주입받아 사용. {@code GoogleOAuthService}/{@code NaverOAuthService} 와 동일한 구조 유지.</p>
 */
@Service
public class KakaoOAuthService {

    private final OAuthClient oAuthClient;

    public KakaoOAuthService(@Qualifier("kakaoOAuthClient") OAuthClient oAuthClient) {
        this.oAuthClient = oAuthClient;
    }

    public OAuthUserInfo getUserInfo(String authorizationCode, String redirectUri) {
        return oAuthClient.getUserInfo(authorizationCode, redirectUri);
    }
}