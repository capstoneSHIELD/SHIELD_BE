package org.example.shield.user.controller.dto;

/**
 * 프로필 이미지 업로드/삭제 응답 (Issue #97).
 *
 * <p>삭제 응답에서는 {@code profileImageUrl} 가 null 로 내려간다.</p>
 */
public record ProfileImageResponse(String profileImageUrl) {
    public static ProfileImageResponse of(String url) {
        return new ProfileImageResponse(url);
    }

    public static ProfileImageResponse cleared() {
        return new ProfileImageResponse(null);
    }
}
