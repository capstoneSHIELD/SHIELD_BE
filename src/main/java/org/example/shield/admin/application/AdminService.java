package org.example.shield.admin.application;

/**
 * 관리자 서비스 — 대시보드, 변호사 심사, 처리 이력.
 *
 * Layer: application
 * Called by: AdminController
 * Calls: LawyerReader, LawyerWriter, UserReader, VerificationCheckReader,
 *        VerificationLogWriter, LawyerDocumentReader
 *
 * TODO: getDashboardStats()                          — 상태별 카운트 조회
 * TODO: getDashboardAlerts()                         — 긴급 알림 (24시간 미처리, 서류 누락, 중복 의심)
 * TODO: getPendingLawyers(keyword, status, page, size) — 심사 목록 (검색 + 필터 + 페이징)
 * TODO: processVerification(lawyerId, adminId, status, reason) — 승인/보완요청/거절 + 로그 자동 생성
 * TODO: getVerificationChecks(lawyerId)              — 자동 검증 결과 조회
 * TODO: getVerificationLogs(period, status, page, size) — 처리 이력 조회
 * TODO: getLawyerDocuments(lawyerId)                  — 변호사 서류 목록 조회
 */
public class AdminService {
}
