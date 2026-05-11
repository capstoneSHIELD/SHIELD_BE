package org.example.shield.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Kakao OAuth 로그인 요청.
 */
public record KakaoLoginRequest(
        @NotBlank(message = "인증 코드는 필수입니다")
        String authorizationCode,

        String role
) {}
