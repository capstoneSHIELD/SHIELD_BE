package org.example.shield.auth.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.auth.domain.OAuthClient;
import org.example.shield.auth.domain.OAuthUserInfo;
import org.example.shield.auth.exception.OAuthFailedException;
import org.example.shield.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google OAuth 2.0 클라이언트.
 *
 * <p>Issue #116: 모바일 native 앱 지원을 위해 redirect_uri 를 동적으로 받고
 * {@code allowed-redirect-uris} 화이트리스트로 검증한다. Kakao/Naver 와 동일 패턴.</p>
 */
@Slf4j
@Component("googleOAuthClient")
public class GoogleOAuthClient implements OAuthClient {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String defaultRedirectUri;
    private final Set<String> allowedRedirectUris;

    public GoogleOAuthClient(
            @Value("${google.client-id}") String clientId,
            @Value("${google.client-secret}") String clientSecret,
            @Value("${google.redirect-uri}") String defaultRedirectUri,
            @Value("${google.allowed-redirect-uris}") String allowedRedirectUrisCsv) {
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
            log.warn("Google OAuth: rejected redirect_uri='{}' (not in whitelist)", requested);
            throw new OAuthFailedException(ErrorCode.OAUTH_INVALID_REDIRECT_URI);
        }
        return requested;
    }

    private String exchangeCodeForToken(String authorizationCode, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("code", authorizationCode);

            GoogleTokenResponse response = webClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(GoogleTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
            }
            return response.accessToken();
        } catch (OAuthFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth token exchange failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
        }
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        try {
            GoogleUserResponse response = webClient.get()
                    .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(GoogleUserResponse.class)
                    .block();

            if (response == null || response.email() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
            }
            return new OAuthUserInfo(response.email(), response.name(), response.id());
        } catch (OAuthFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth user info fetch failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
        }
    }

    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType
    ) {}

    private record GoogleUserResponse(
            String id,
            String email,
            String name,
            String picture
    ) {}
}