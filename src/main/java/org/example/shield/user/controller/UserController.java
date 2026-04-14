package org.example.shield.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.shield.common.response.ApiResponse;
import org.example.shield.user.application.UserService;
import org.example.shield.user.controller.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
/**
 * TODO [Issue #16] PATCH /api/users/me — 내 정보 수정 추가
 *
 * - Request: UserUpdateRequest(record) { name (optional), phone (optional) }
 * - User 엔티티에 updateProfile(String name, String phone) 비즈니스 메서드 추가
 * - UserService에 updateMyInfo(UUID userId, UserUpdateRequest request) 추가
 * - UserWriter 주입 필요 (현재 UserService에 UserReader만 있음)
 * - Response: UserResponse (기존 from() 재활용)
 * - Notion PATCH /api/users/me 페이지에 Request/Response 양식 작성 필요
 */
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
            @AuthenticationPrincipal UUID userId) {
        UserResponse response = userService.getMyInfo(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 정보 조회 성공", response));
    }
}
