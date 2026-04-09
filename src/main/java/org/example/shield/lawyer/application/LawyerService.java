package org.example.shield.lawyer.application;

/**
 * 변호사 서비스 - 변호사 목록/프로필 조회/수정.
 *
 * Layer: application
 * Called by: LawyerController
 * Calls: LawyerReader, LawyerWriter
 *
 * TODO:
 * - getLawyers(pageable, specialization, minExperience, sort):
 *   변호사 목록 (VERIFIED만, 필터/정렬/페이징)
 *   기본 정렬: 경력순
 * - getLawyer(lawyerId): 변호사 프로필 상세
 * - getMyProfile(userId): 변호사 본인 프로필
 * - updateMyProfile(userId, request): 전문분야/경력/자격증/소개글 수정
 */
public class LawyerService {
}
