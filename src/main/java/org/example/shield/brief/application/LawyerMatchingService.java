package org.example.shield.brief.application;

/**
 * 변호사 매칭 서비스 - 의뢰서 기반 변호사 매칭 (여러 명).
 *
 * Layer: application
 * Called by: BriefController.getLawyerRecommendations()
 * Calls: BriefReader, LawyerReader
 *
 * TODO:
 * - findMatching(briefId, pageable):
 *   1. brief에서 keywords, legalField 조회
 *   2. lawyers에서 verificationStatus = VERIFIED인 변호사 필터
 *   3. keywords와 lawyers.tags 비교 → matchedKeywords 추출
 *   4. 기본 정렬: 경력순 (experience)
 *   5. PageResponse로 반환
 */
public class LawyerMatchingService {
}
