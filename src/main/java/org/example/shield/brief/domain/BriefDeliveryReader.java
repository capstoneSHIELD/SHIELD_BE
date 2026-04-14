package org.example.shield.brief.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * TODO [Issue #16] status 필터 메서드 추가
 *
 * - Page<BriefDelivery> findAllByLawyerIdAndStatus(UUID lawyerId, DeliveryStatus status, Pageable pageable)
 * - BriefDeliveryRepository + BriefDeliveryReaderImpl에도 동일 추가
 * - long countByLawyerIdAndStatus(UUID lawyerId, DeliveryStatus status) — 대시보드 통계용
 * - long countByLawyerIdAndRespondedAtAfter(UUID lawyerId, LocalDateTime after) — 이번 주 완료 카운트용
 */
public interface BriefDeliveryReader {
    BriefDelivery findById(UUID id);
    List<BriefDelivery> findAllByBriefId(UUID briefId);
    Page<BriefDelivery> findAllByLawyerId(UUID lawyerId, Pageable pageable);
    boolean existsByBriefIdAndLawyerId(UUID briefId, UUID lawyerId);
}
