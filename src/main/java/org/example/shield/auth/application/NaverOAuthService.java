package org.example.shield.auth.application;

import org.example.shield.auth.domain.OAuthClient;
import org.example.shield.auth.domain.OAuthUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Naver OAuth 사용자 정보 조회 서비스 (Issue #83).
 *
 * <p>{@link org.example.shield.auth.infrastructure.NaverOAuthClient} 를 명시적으로
 * 주입받아 사용. {@code GoogleOAuthService} 와 동일한 구조 유지.</p>
 */
@Service
public class NaverOAuthService {

    private final OAuthClient oAuthClient;

    public NaverOAuthService(@Qualifier("naverOAuthClient") OAuthClient oAuthClient) {
        this.oAuthClient = oAuthClient;
    }

    public OAuthUserInfo getUserInfo(String authorizationCode, String redirectUri) {
        return oAuthClient.getUserInfo(authorizationCode, redirectUri);
    }
}