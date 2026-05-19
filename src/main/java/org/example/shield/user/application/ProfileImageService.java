package org.example.shield.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.common.exception.BusinessException;
import org.example.shield.common.exception.ErrorCode;
import org.example.shield.common.storage.StorageClient;
import org.example.shield.user.controller.dto.ProfileImageResponse;
import org.example.shield.user.domain.User;
import org.example.shield.user.domain.UserReader;
import org.example.shield.user.domain.UserWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * 프로필 이미지 업로드/교체/삭제 서비스 (Issue #97).
 *
 * <p>저장소 정책:</p>
 * <ul>
 *   <li>버킷: {@code supabase.storage.profile-bucket} (기본 {@code profile_images}).
 *       Supabase 콘솔에서 Public 으로 설정되어 있어야 반환되는 URL 이 직접 접근 가능하다.</li>
 *   <li>객체 경로: {@code {userId}/{uuid}.{ext}} — 사용자별 폴더로 격리.</li>
 *   <li>기존 이미지가 있으면 새 업로드 성공 후 best-effort 로 이전 객체를 삭제한다.
 *       삭제 실패는 신규 업로드 결과에 영향 주지 않는다 (잔여 객체는 후속 정리).</li>
 * </ul>
 *
 * <p>검증 정책:</p>
 * <ul>
 *   <li>빈 파일 거부.</li>
 *   <li>크기 상한 5MB.</li>
 *   <li>허용 MIME: {@code image/jpeg}, {@code image/png}, {@code image/webp}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /** MIME → 파일 확장자 매핑. 허용 목록은 이 맵의 key set 으로 정의된다. */
    private static final Map<String, String> ALLOWED_MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final StorageClient storageClient;
    private final UserReader userReader;
    private final UserWriter userWriter;

    @Value("${supabase.storage.profile-bucket:profile_images}")
    private String profileBucket;

    /**
     * 새 프로필 이미지 업로드 (또는 교체).
     *
     * @return 새로 저장된 공개 URL 을 담은 응답.
     */
    @Transactional
    public ProfileImageResponse upload(UUID userId, MultipartFile file) {
        validate(file);

        User user = userReader.findById(userId);
        String previousUrl = user.getProfileImageUrl();

        String ext = ALLOWED_MIME_TO_EXT.get(file.getContentType());
        String storagePath = userId + "/" + UUID.randomUUID() + "." + ext;

        // 새 파일을 먼저 업로드한 뒤에 기존 객체를 정리한다 (장애 격리).
        storageClient.uploadTo(profileBucket, storagePath, file);
        String publicUrl = storageClient.getPublicUrl(profileBucket, storagePath);

        user.updateProfileImageUrl(publicUrl);
        userWriter.save(user);

        if (previousUrl != null && !previousUrl.isBlank()) {
            tryDeleteByPublicUrl(previousUrl);
        }

        log.info("프로필 이미지 업로드 완료. userId={}, path={}", userId, storagePath);
        return ProfileImageResponse.of(publicUrl);
    }

    /**
     * 프로필 이미지 제거. 기존 이미지가 있으면 객체도 best-effort 로 삭제.
     */
    @Transactional
    public ProfileImageResponse delete(UUID userId) {
        User user = userReader.findById(userId);
        String previousUrl = user.getProfileImageUrl();

        if (previousUrl == null || previousUrl.isBlank()) {
            return ProfileImageResponse.cleared();
        }

        user.clearProfileImageUrl();
        userWriter.save(user);
        tryDeleteByPublicUrl(previousUrl);

        log.info("프로필 이미지 삭제 완료. userId={}", userId);
        return ProfileImageResponse.cleared();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_EMPTY) {};
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_SIZE_EXCEEDED) {};
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TO_EXT.containsKey(mime.toLowerCase())) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TYPE_NOT_SUPPORTED) {};
        }
    }

    /**
     * 공개 URL 에서 객체 경로를 복원해 삭제 시도. 외부 URL(예: 카카오 프로필)이거나
     * 경로 추출 실패 시 조용히 skip 한다 — 사용자 흐름에 영향 없어야 함.
     */
    private void tryDeleteByPublicUrl(String publicUrl) {
        try {
            String marker = "/object/public/" + profileBucket + "/";
            int idx = publicUrl.indexOf(marker);
            if (idx < 0) {
                log.debug("이전 프로필 이미지 URL 이 우리 버킷 경로가 아님 — 삭제 skip: {}", publicUrl);
                return;
            }
            String path = publicUrl.substring(idx + marker.length());
            storageClient.deleteFrom(profileBucket, path);
        } catch (Exception e) {
            log.warn("이전 프로필 이미지 삭제 실패 (best-effort, 사용자 흐름 영향 없음): url={}, err={}",
                    publicUrl, e.getMessage());
        }
    }
}
