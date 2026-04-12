package org.example.shield.admin.controller;

/**
 * 관리자 API 컨트롤러.
 *
 * Layer: controller
 * Called by: 프론트엔드 (관리자 화면)
 * Calls: AdminService
 *
 * API 목록 (Issue #12):
 *
 * TODO: GET  /api/admin/dashboard/stats                        — 대시보드 통계 (승인 대기/검토 중/보완 요청/오늘 처리 카운트)
 * TODO: GET  /api/admin/dashboard/alerts                       — 긴급 알림 (24시간 미처리/서류 누락/중복 의심)
 * TODO: GET  /api/admin/lawyers/pending                        — 변호사 가입 심사 목록 (검색 + 상태 필터 + 페이징)
 * TODO: PATCH /api/admin/lawyers/{lawyerId}/verification       — 승인/보완요청/거절 처리 → verification_logs 자동 생성
 * TODO: GET  /api/admin/lawyers/{lawyerId}/verification-checks — 자동 검증 결과 + 체크리스트 조회
 * TODO: GET  /api/admin/lawyers/{lawyerId}/documents           — 변호사 서류 조회/다운로드
 * TODO: GET  /api/admin/verification-logs                      — 처리 이력 (기간/상태 필터 + 페이징)
 * TODO: GET  /api/admin/consultations                          — 상담 모니터링 (차후 구현)
 */
public class AdminController {
}
