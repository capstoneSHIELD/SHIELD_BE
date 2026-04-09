package org.example.shield.consultation.controller;

/**
 * 상담 API 컨트롤러.
 *
 * Layer: controller
 * Called by: 프론트엔드
 * Calls: ConsultationService, MessageService, AnalysisService
 *
 * API 목록:
 * - POST  /api/consultations                                 상담 생성
 * - GET   /api/consultations                                 내 상담 목록
 * - POST  /api/consultations/{consultationId}/messages        메시지 전송
 * - GET   /api/consultations/{consultationId}/messages        메시지 목록 조회
 * - PATCH /api/consultations/{consultationId}/classify        분류 직접 수정
 * - POST  /api/consultations/{consultationId}/analyze         의뢰서 생성 (비동기)
 * - GET   /api/legal-fields                                  법률 분야 목록 조회
 */
public class ConsultationController {
}
