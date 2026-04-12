package org.example.shield.lawyer.domain;

/**
 * 변호사 제출 서류 엔티티.
 *
 * Table: lawyer_documents
 *
 * TODO: @Entity 구현
 * - id (UUID PK)
 * - lawyerId (UUID FK → lawyers)
 * - fileName (String, 원본 파일명)
 * - fileUrl (String, Supabase Storage URL)
 * - fileSize (Long, bytes)
 * - fileType (String, PDF/JPG/PNG)
 * - createdAt (LocalDateTime)
 */
public class LawyerDocument {
}
