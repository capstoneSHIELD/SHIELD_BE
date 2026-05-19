package org.example.shield.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.shield.common.response.ApiResponse;
import org.example.shield.user.application.ProfileImageService;
import org.example.shield.user.application.UserService;
import org.example.shield.user.controller.dto.ProfileImageResponse;
import org.example.shield.user.controller.dto.UserResponse;
import org.example.shield.user.controller.dto.UserUpdateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(
            @AuthenticationPrincipal UUID userId) {
        UserResponse response = userService.getMyInfo(userId);
        return ApiResponse.success("조회 성공", response);
    }

    @Operation(summary = "내 정보 수정", description = "이름, 전화번호를 선택적으로 수정합니다")
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMyInfo(
            @AuthenticationPrincipal UUID userId,
            @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateMyInfo(userId, request);
        return ApiResponse.success("수정 완료", response);
    }

    @Operation(
            summary = "프로필 이미지 업로드/교체",
            description = "JPEG/PNG/WEBP, 5MB 이하. 기존 이미지가 있으면 새 이미지로 교체된다 (Issue #97)."
    )
    @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
    public ApiResponse<ProfileImageResponse> uploadProfileImage(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("file") MultipartFile file) {
        ProfileImageResponse response = profileImageService.upload(userId, file);
        return ApiResponse.success("프로필 이미지가 업로드되었습니다", response);
    }

    @Operation(summary = "프로필 이미지 삭제", description = "프로필 이미지를 제거하고 기본 상태로 복귀시킨다 (Issue #97).")
    @DeleteMapping("/me/profile-image")
    public ApiResponse<ProfileImageResponse> deleteProfileImage(
            @AuthenticationPrincipal UUID userId) {
        ProfileImageResponse response = profileImageService.delete(userId);
        return ApiResponse.success("프로필 이미지가 삭제되었습니다", response);
    }
}
