package org.example.shield.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 객체 스토리지 추상화.
 *
 * <p>기본 메서드들은 단일 default 버킷({@code supabase.storage.bucket}, 변호사 자격증명용)
 * 을 대상으로 동작한다. 프로필 이미지처럼 별도 버킷이 필요한 경우 {@code *To} 시리즈 메서드로
 * 명시적 bucket 을 지정한다.</p>
 */
public interface StorageClient {
    /** Default 버킷에 업로드. 기존 호출부와의 호환 유지용. */
    String upload(String path, MultipartFile file);

    /** Default 버킷의 객체 삭제. */
    void delete(String path);

    /** Default 버킷의 객체에 대한 signed URL 발급. */
    String getSignedUrl(String path, int expiresInSeconds);

    /** 지정 버킷에 업로드 (Issue #97 — 프로필 이미지 별도 버킷 지원). */
    String uploadTo(String bucket, String path, MultipartFile file);

    /** 지정 버킷의 객체 삭제. */
    void deleteFrom(String bucket, String path);

    /**
     * 지정 public 버킷의 객체에 대한 공개 URL 을 반환한다.
     * 버킷이 public 으로 설정되어 있어야 의미가 있다.
     */
    String getPublicUrl(String bucket, String path);
}
