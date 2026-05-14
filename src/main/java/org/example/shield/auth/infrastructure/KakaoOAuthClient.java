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
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Kakao OAuth 2.0 클라이언트.
 *
 * <ul>
 *   <li>토큰 교환: POST {@code https://kauth.kakao.com/oauth/token} (form-urlencoded)</li>
 *   <li>유저 정보: GET {@code https://kapi.kakao.com/v2/user/me}</li>
 *   <li>응답 구조: {@code { id(Long), kakao_account: { email, profile: { nickname } } }}</li>
 *   <li>이메일은 비즈앱 전환을 거쳐야 권한이 부여된다. 개인 앱 단계에서는 권한 자체가 없어
 *       사용자가 동의해도 응답에 포함되지 않으므로, {@code email} 이 비어있을 경우
 *       {@code kakao-{providerId}@shield.local} 형태의 합성 이메일을 발급한다.
 *       비즈앱 전환 후에는 실제 이메일이 들어오므로 자연스럽게 전환된다.</li>
 * </ul>
 */
@Slf4j
@Component("kakaoOAuthClient")
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthClient(
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret}") String clientSecret,
            @Value("${kakao.redirect-uri}") String redirectUri) {
        this.webClient = WebClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {
        String accessToken = exchangeCodeForToken(authorizationCode);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCodeForToken(String authorizationCode) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("code", authorizationCode);

            KakaoTokenResponse response = webClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(KakaoTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
            }
            return response.accessToken();
        } catch (OAuthFailedException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Kakao OAuth token exchange failed: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
        } catch (Exception e) {
            log.error("Kakao OAuth token exchange failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_CODE_INVALID);
        }
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = webClient.get()
                    .uri(USER_INFO_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(KakaoUserResponse.class)
                    .block();

            if (response == null || response.id() == null) {
                throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
            }

            String email = response.kakaoAccount() != null ? response.kakaoAccount().email() : null;
            String nickname = null;
            if (response.kakaoAccount() != null && response.kakaoAccount().profile() != null) {
                nickname = response.kakaoAccount().profile().nickname();
            }

            String providerId = String.valueOf(response.id());

            // 카카오 비즈앱 전환 전에는 이메일 권한이 없어 응답에 포함되지 않는다.
            // User.email NOT NULL UNIQUE 제약을 만족시키기 위해 providerId 기반 합성 이메일을 사용.
            if (email == null || email.isBlank()) {
                email = "kakao-" + providerId + "@shield.local";
            }

            // 닉네임 미동의 시 fallback (현실적으로 동의항목 필수로 설정해 발생하지 않음)
            if (nickname == null || nickname.isBlank()) {
                nickname = "카카오사용자";
            }

            return new OAuthUserInfo(email, nickname, providerId);
        } catch (OAuthFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao OAuth user info fetch failed", e);
            throw new OAuthFailedException(ErrorCode.OAUTH_USER_INFO_FAILED);
        }
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Integer expiresIn,
            String scope
    ) {}

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
        record KakaoAccount(
                String email,
                Profile profile
        ) {
            record Profile(
                    String nickname
            ) {}
        }
    }
}
