package org.example.shield.user.application;

import org.example.shield.common.enums.UserRole;
import org.example.shield.common.exception.BusinessException;
import org.example.shield.common.storage.StorageClient;
import org.example.shield.user.controller.dto.ProfileImageResponse;
import org.example.shield.user.domain.User;
import org.example.shield.user.domain.UserReader;
import org.example.shield.user.domain.UserWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileImageServiceTest {

    private static final String BUCKET = "profile_images";

    @Mock private StorageClient storageClient;
    @Mock private UserReader userReader;
    @Mock private UserWriter userWriter;

    @InjectMocks private ProfileImageService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "profileBucket", BUCKET);
        userId = UUID.randomUUID();
        user = User.builder()
                .email("test@shield.local")
                .name("테스트")
                .role(UserRole.USER)
                .provider("DEV")
                .googleId("dev-" + UUID.randomUUID())
                .build();
        given(userReader.findById(userId)).willReturn(user);
        given(userWriter.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(storageClient.getPublicUrl(eq(BUCKET), anyString()))
                .willAnswer(inv -> "https://supa/storage/v1/object/public/" + BUCKET + "/" + inv.getArgument(1));
    }

    @Test
    @DisplayName("정상 PNG 업로드 → 새 URL 저장 + 이전 이미지 없으면 delete 호출 안 함")
    void upload_validPng_persistsNewUrl() {
        MultipartFile file = pngFile(1024);

        ProfileImageResponse response = service.upload(userId, file);

        assertThat(response.profileImageUrl()).startsWith("https://supa/storage/v1/object/public/" + BUCKET + "/" + userId);
        assertThat(response.profileImageUrl()).endsWith(".png");
        assertThat(user.getProfileImageUrl()).isEqualTo(response.profileImageUrl());
        verify(storageClient, times(1)).uploadTo(eq(BUCKET), anyString(), eq(file));
        verify(userWriter, times(1)).save(user);
        verify(storageClient, never()).deleteFrom(anyString(), anyString());
    }

    @Test
    @DisplayName("기존 이미지가 있는 사용자가 새 이미지 업로드 → 새 URL 저장 + 이전 객체 best-effort 삭제")
    void upload_replacesExisting() {
        String prev = "https://supa/storage/v1/object/public/" + BUCKET + "/" + userId + "/old-uuid.jpg";
        user.updateProfileImageUrl(prev);

        ProfileImageResponse response = service.upload(userId, jpegFile(1024));

        assertThat(response.profileImageUrl()).isNotEqualTo(prev);
        assertThat(user.getProfileImageUrl()).isEqualTo(response.profileImageUrl());
        verify(storageClient, times(1)).deleteFrom(BUCKET, userId + "/old-uuid.jpg");
    }

    @Test
    @DisplayName("이전 객체 삭제 실패해도 신규 업로드는 성공 처리 (best-effort)")
    void upload_previousDeletionFailureIsSwallowed() {
        String prev = "https://supa/storage/v1/object/public/" + BUCKET + "/" + userId + "/old-uuid.jpg";
        user.updateProfileImageUrl(prev);
        willThrow(new RuntimeException("supabase 5xx")).given(storageClient)
                .deleteFrom(eq(BUCKET), anyString());

        ProfileImageResponse response = service.upload(userId, jpegFile(1024));

        assertThat(response.profileImageUrl()).isNotEqualTo(prev);
        assertThat(user.getProfileImageUrl()).isEqualTo(response.profileImageUrl());
    }

    @Test
    @DisplayName("외부 URL (예: 카카오 프로필) 이 기존 값이면 삭제 시도 skip — 우리 버킷 경로 아님")
    void upload_previousExternalUrl_skipsDeletion() {
        user.updateProfileImageUrl("https://k.kakaocdn.net/some/avatar.jpg");

        service.upload(userId, pngFile(1024));

        verify(storageClient, never()).deleteFrom(anyString(), anyString());
    }

    @Test
    @DisplayName("빈 파일 → PROFILE_IMAGE_EMPTY 400")
    void upload_emptyFile_rejected() {
        MultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(userId, empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비어");

        verify(storageClient, never()).uploadTo(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("5MB 초과 → PROFILE_IMAGE_SIZE_EXCEEDED 400")
    void upload_oversize_rejected() {
        MultipartFile big = pngFile(5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.upload(userId, big))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    @DisplayName("허용되지 않은 MIME (예: gif) → PROFILE_IMAGE_TYPE_NOT_SUPPORTED 400")
    void upload_disallowedMime_rejected() {
        MultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif", new byte[]{0x47, 0x49, 0x46});

        assertThatThrownBy(() -> service.upload(userId, gif))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미지 형식");
    }

    @Test
    @DisplayName("이미지가 있는 사용자 delete → 필드 null + 객체 삭제 호출")
    void delete_existingImage_clearsAndDeletes() {
        String prev = "https://supa/storage/v1/object/public/" + BUCKET + "/" + userId + "/cur.webp";
        user.updateProfileImageUrl(prev);

        ProfileImageResponse response = service.delete(userId);

        assertThat(response.profileImageUrl()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        verify(storageClient, times(1)).deleteFrom(BUCKET, userId + "/cur.webp");
        verify(userWriter, times(1)).save(user);
    }

    @Test
    @DisplayName("이미지가 없는 사용자 delete → 객체 삭제 호출 없이 cleared 응답")
    void delete_noImage_noop() {
        ProfileImageResponse response = service.delete(userId);

        assertThat(response.profileImageUrl()).isNull();
        verify(storageClient, never()).deleteFrom(anyString(), anyString());
        verify(userWriter, never()).save(any());
    }

    private MultipartFile pngFile(int size) {
        return new MockMultipartFile("file", "p.png", "image/png", new byte[size]);
    }

    private MultipartFile jpegFile(int size) {
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[size]);
    }
}
