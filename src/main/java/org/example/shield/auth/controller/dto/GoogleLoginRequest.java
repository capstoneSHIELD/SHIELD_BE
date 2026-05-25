package org.example.shield.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Google OAuth 로그인 요청.
 *
 * <p>{@code redirectUri} 는 Issue #114 에서 추가된 필드로, 인터페이스 일관성을 위해
 * Kakao/Naver 와 동일하게 받지만 현재 Google 클라이언트는 환경변수 redirect-uri 만 사용.
 * Phase 2 에서 Google native SDK 또는 Android OAuth Client 도입 시 활용 예정.</p>
 */
public record GoogleLoginRequest(
        @NotBlank(message = "인증 코드는 필수입니다")
        String authorizationCode,

        String redirectUri,

        String role
) {}