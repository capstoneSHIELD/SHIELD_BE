package org.example.shield.auth.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.auth.domain.OAuthClient;
import org.example.shield.auth.domain.OAuthUserInfo;
import org.example.shield.auth.exception.OAuthFailedException;
import org.example.shield.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Naver OAuth 2.0 클라이언트 (Issue #83).
 *
 * <ul>
 *   <li>토큰 교환: {@code https://nid.naver.com/oauth2.0/token}</li>
 *   <li>유저 정보: {@code https://openapi.naver.com/v1/nid/me}</li>
 *   <li>응답 구조: {@code { resultcode, message, response: { id, email, name, ... } }}</li>
 * </ul>
 *
 * <p>Issue #114: 모바일 native 앱 지원을 위해 redirect_uri 를 동적으로 받고
 * {@code allowed-redirect-uris} 화이트리스트로 검증한다.</p>
 */
@Slf4j
@Component("naverOAuthClient")
public class NaverOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String defaultRedirectUri;
    private final Set<String> allowedRedirectUris;

    public NaverOAuthClient(
            @Value("${naver.client-id}") String clientId,
            @Value("${naver.client-secret}") String clientSecret,
            @Value("${naver.redirect-uri}") String defaultRedirectUri,
            @Value("${naver.allowed-redirect-uris}") String allowedRedirectUrisCsv) {
        this.webClient = WebClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.defaultRedirectUri = defaultRedirectUri;
        this.allowedRedirectUris = Arrays.stream(allowedRedirectUrisCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode, String redirectUri) {
        String resolvedRedirectUri = resolveRedirectUri(redirectUri);
        String accessToken = exchangeCodeForToken(authorizationCode, resolvedRedirectUri);
        return fetchUserInfo(accessToken);
    }

    private String resolveRedirectUri(String requested) {
        if (requested == null || requested.isBlank()) {
            return defaultRedirectUri;
        }
        if (!allowedRedirectUris.contains(requested)) {
            log.warn("Naver OAuth: rejected redirect_uri='{}' (not in whitelist)", requested);
            throw new OAuthFailedException(ErrorCode.OAUTH_INVALID_REDIRECT_URI);
        }
        return requested;
    }

    private String exchangeCodeForToken(String authorizationCode, String redirectUri) {
        try {
            String url = UriComponentsBuilder.fromUriString(TOKEN_URI)
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", clientId)
                    .queryParam("client_secret", clientSecret)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("code", authorizationCode)
                    .build()
                    .toUriString();

            NaverTokenResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(NaverTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
            }
            return response.accessToken();
        } catch (OAuthFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver OAuth token exchange failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
        }
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        try {
            NaverUserResponse response = webClient.get()
                    .uri(USER_INFO_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(NaverUserResponse.class)
                    .block();

            if (response == null
                    || response.response() == null
                    || response.response().email() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
            }
            NaverUserResponse.Profile profile = response.response();
            return new OAuthUserInfo(profile.email(), profile.name(), profile.id());
        } catch (OAuthFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver OAuth user info fetch failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
        }
    }

    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") String expiresIn
    ) {}

    private record NaverUserResponse(
            String resultcode,
            String message,
            Profile response
    ) {
        record Profile(
                String id,
                String email,
                String name,
                String nickname,
                @JsonProperty("profile_image") String profileImage
        ) {}
    }
}