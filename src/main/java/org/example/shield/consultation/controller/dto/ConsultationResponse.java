package org.example.shield.consultation.controller.dto;

/**
 * 내 상담 목록 응답 DTO.
 *
 * TODO: record로 구현
 * - consultationId: UUID
 * - status: String (COLLECTING / ANALYZING / AWAITING_CONFIRM / CONFIRMED / REJECTED)
 * - primaryField: List<String> (nullable, 분류 전 null)
 * - tags: List<String> (nullable)
 * - lastMessage: String (nullable)
 * - lastMessageAt: LocalDateTime (nullable)
 * - createdAt: LocalDateTime
 * - brief: BriefSummary (nullable, 의뢰서 생성 전 null)
 *   - briefId: UUID
 *   - title: String
 *   - status: String
 */
public class ConsultationResponse {
}
