package org.example.shield.auth.application;

/**
 * 인증 서비스 - Google OAuth 로그인/로그아웃/토큰 갱신 비즈니스 로직.
 *
 * Layer: application
 * Called by: AuthController
 * Calls: GoogleOAuthService, JwtService, UserReader, UserWriter
 *
 * TODO:
 * - googleLogin(authorizationCode, role):
 *   1. GoogleOAuthService로 인증코드 → 사용자 정보(email, name, googleId) 받기
 *   2. UserReader에서 email로 기존 회원 조회
 *   3. 없으면 새 User 생성 (role: USER/LAWYER/ADMIN)
 *   4. JwtService로 Access Token + Refresh Token 생성
 *   5. Refresh Token을 users.refresh_token에 저장
 *   6. LoginResponse 반환
 *
 * - logout(userId):
 *   1. users.refresh_token = null
 *
 * - refreshToken(refreshToken):
 *   1. Refresh Token 유효성 검증
 *   2. 새 Access Token 발급
 */
public class AuthService {
}
