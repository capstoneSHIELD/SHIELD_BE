package org.example.shield.ai.application;

import org.example.shield.ai.dto.LegalChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layer 3: RAG 컨텍스트 빌더.
 * 검색된 법률 조문 청크를 시스템 프롬프트에 삽입할 문자열로 포맷.
 */
@Component
public class RagContextBuilder {

    /**
     * LegalChunk 목록을 프롬프트 컨텍스트 문자열로 변환.
     *
     * @param chunks        검색된 법률 조문 청크 목록
     * @param intentSummary 의도 분류 요약 (메타 정보로 포함)
     * @return 포맷된 컨텍스트 문자열 (빈 chunks면 빈 문자열)
     */
    public String build(List<LegalChunk> chunks, String intentSummary) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 참고 법령 (출처는 반드시 응답에 인용할 것)\n\n");
        sb.append("분류: ").append(intentSummary).append("\n\n");
        sb.append("다음은 본 사건과 관련된 현행 법령 조문입니다. 응답 시 이 정보를 우선 참고하고,\n");
        sb.append("인용 시 반드시 법령명과 조항 번호를 함께 표시하세요.\n");
        sb.append("법령에 없는 내용은 추측하지 말고 \"관련 법령에서 확인할 수 없습니다\"라고 답하세요.\n");

        for (int i = 0; i < chunks.size(); i++) {
            LegalChunk chunk = chunks.get(i);
            sb.append("\n---\n");
            sb.append("[").append(i + 1).append("] ");
            sb.append(chunk.lawName()).append(" ").append(chunk.articleNo());
            sb.append(" (").append(chunk.articleTitle()).append(")\n");
            sb.append("시행일: ").append(chunk.effectiveDate());
            if (chunk.sourceUrl() != null && !chunk.sourceUrl().isEmpty()) {
                sb.append(" / 출처: ").append(chunk.sourceUrl());
            }
            sb.append("\n\n");
            sb.append(chunk.content()).append("\n");
        }

        return sb.toString();
    }
}
