package org.example.shield.admin.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.admin.controller.dto.LawyerDetailResponse;
import org.example.shield.admin.controller.dto.PendingLawyerResponse;
import org.example.shield.admin.controller.dto.VerificationResponse;
import org.example.shield.admin.domain.VerificationLog;
import org.example.shield.admin.domain.VerificationLogWriter;
import org.example.shield.common.enums.VerificationStatus;
import org.example.shield.common.exception.BusinessException;
import org.example.shield.common.exception.ErrorCode;
import org.example.shield.common.response.PageResponse;
import org.example.shield.lawyer.domain.LawyerProfile;
import org.example.shield.lawyer.domain.LawyerReader;
import org.example.shield.lawyer.domain.LawyerWriter;
import org.example.shield.lawyer.infrastructure.LawyerDocumentRepository;
import org.example.shield.user.domain.User;
import org.example.shield.user.domain.UserReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final LawyerReader lawyerReader;
    private final LawyerWriter lawyerWriter;
    private final UserReader userReader;
    private final LawyerDocumentRepository lawyerDocumentRepository;
    private final VerificationLogWriter verificationLogWriter;

    public PageResponse<PendingLawyerResponse> getPendingLawyers(String keyword, String status,
                                                                  Pageable pageable) {
        log.info("변호사 심사 목록 조회. keyword={}, status={}", keyword, status);

        Page<LawyerProfile> lawyerPage = lawyerReader.searchByStatusAndKeyword(
                status, keyword, pageable);

        List<LawyerProfile> lawyers = lawyerPage.getContent();
        if (lawyers.isEmpty()) {
            return PageResponse.from(lawyerPage.map(lp -> null));
        }

        List<UUID> userIds = lawyers.stream()
                .map(LawyerProfile::getUserId)
                .toList();
        Map<UUID, User> userMap = userReader.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UUID> lawyerIds = lawyers.stream()
                .map(LawyerProfile::getId)
                .toList();
        Map<UUID, Long> documentCountMap = lawyerDocumentRepository.countByLawyerIds(lawyerIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        Page<PendingLawyerResponse> responsePage = lawyerPage.map(lawyer -> {
            User user = userMap.get(lawyer.getUserId());
            long docCount = documentCountMap.getOrDefault(lawyer.getId(), 0L);
            return PendingLawyerResponse.from(lawyer, user, docCount);
        });

        return PageResponse.from(responsePage);
    }

    public LawyerDetailResponse getLawyerDetail(UUID lawyerId) {
        log.info("변호사 상세 조회. lawyerId={}", lawyerId);
        LawyerProfile lawyer = lawyerReader.findById(lawyerId);
        User user = userReader.findById(lawyer.getUserId());
        return LawyerDetailResponse.from(lawyer, user);
    }

    @Transactional
    public VerificationResponse processVerification(UUID lawyerId, UUID adminId,
                                                     String statusStr, String reason) {
        log.info("변호사 인증 처리. lawyerId={}, adminId={}, status={}", lawyerId, adminId, statusStr);

        LawyerProfile lawyer = lawyerReader.findById(lawyerId);
        String previousStatus = lawyer.getVerificationStatus().name();

        VerificationStatus newStatus = parseVerificationStatus(statusStr);
        validateReasonRequired(newStatus, reason);

        lawyer.updateVerificationStatus(newStatus);
        lawyerWriter.save(lawyer);

        VerificationLog log = VerificationLog.create(lawyerId, adminId, previousStatus, newStatus.name(), reason);
        verificationLogWriter.save(log);

        return new VerificationResponse(
                lawyerId,
                previousStatus,
                newStatus.name(),
                reason,
                log.getCreatedAt()
        );
    }

    private VerificationStatus parseVerificationStatus(String status) {
        try {
            return VerificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE) {};
        }
    }

    private void validateReasonRequired(VerificationStatus status, String reason) {
        if ((status == VerificationStatus.REJECTED || status == VerificationStatus.SUPPLEMENT_REQUESTED)
                && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE) {};
        }
    }
}
