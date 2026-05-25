package org.example.shield.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Naver OAuth 로그인 요청 (Issue #83).
 *
 * <p>{@code redirectUri} 는 모바일 native 앱 지원 시 클라이언트가 사용한 redirect_uri 를
 * 명시 전달하기 위한 필드 (Issue #114). 미지정 시 BE 의 기본 redirect_uri 사용.</p>
 */
public record NaverLoginRequest(
        @NotBlank(message = "인증 코드는 필수입니다")
        String authorizationCode,

        String redirectUri,

        String role
) {}