package org.example.shield.lawyer.controller;

/**
 * 변호사 API 컨트롤러.
 *
 * Layer: controller
 * Called by: 프론트엔드
 * Calls: LawyerService, VerificationService
 *
 * API 목록 (Issue #12 포함):
 * TODO: GET   /api/lawyers                             — 변호사 목록 조회
 * TODO: GET   /api/lawyers/{lawyerId}                  — 변호사 프로필 상세 (의뢰인/관리자 공용)
 * TODO: GET   /api/lawyers/me                          — 내 프로필 조회 (차후 구현)
 * TODO: PATCH /api/lawyers/me                          — 내 프로필 수정 (차후 구현)
 * TODO: POST  /api/lawyers/me/verification-request     — 검증 신청 (body: barAssociationNumber)
 * TODO: GET   /api/lawyers/me/verification-status      — 검증 상태 확인
 * TODO: POST  /api/lawyers/me/documents                — 서류 업로드 (MultipartFile → Supabase Storage)
 */
public class LawyerController {
}
