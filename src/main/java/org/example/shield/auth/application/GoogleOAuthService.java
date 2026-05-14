package org.example.shield.auth.application;

import org.example.shield.auth.domain.OAuthClient;
import org.example.shield.auth.domain.OAuthUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthService {

    private final OAuthClient oAuthClient;

    public GoogleOAuthService(@Qualifier("googleOAuthClient") OAuthClient oAuthClient) {
        this.oAuthClient = oAuthClient;
    }

    public OAuthUserInfo getUserInfo(String authorizationCode) {
        return oAuthClient.getUserInfo(authorizationCode);
    }
}
