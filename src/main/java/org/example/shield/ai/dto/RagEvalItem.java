package org.example.shield.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;

/**
 * RAG 평가 셋의 단일 항목.
 *
 * <p>P5.2 Commit 1 (v1.6 스키마): {@code dialogueIntent}, {@code lowEvidence},
 * {@code mixedType} 필드가 추가됨. 기존 v1.5 jsonl은 누락된 필드를 default로 채워
 * 그대로 read 가능 (backward compat).
 *
 * <ul>
 *   <li>{@code dialogueIntent} — {@code greeting | irrelevant | change_topic |
 *       ask_legal_advice | provide_info} (default: {@code ask_legal_advice})</li>
 *   <li>{@code lowEvidence} — true면 expected document 없음 허용
 *       (validator에서 enforce, default: false)</li>
 *   <li>{@code mixedType} — {@code statute_only | case_only | mixed}
 *       (default: {@code statute_only})</li>
 * </ul>
 */
public record RagEvalItem(
        String id,
        String split,
        @JsonAlias({"dialogue_intent"}) String dialogueIntent,
        @JsonAlias({"low_evidence"}) boolean lowEvidence,
        @JsonAlias({"mixed_type"}) String mixedType,
        String nodeId,
        String l1,
        String l2,
        String l3,
        String domain,
        String query,
        List<String> keywords,
        List<String> expectedChunkIds,
        List<RagEvalLawRef> expectedLawRefs,
        List<String> expectedDocumentIds,
        Map<String, Integer> relevanceJudgments,
        String failureType,
        String source,
        String reviewer,
        String createdAt
) {
    public RagEvalItem {
        split = split == null || split.isBlank() ? "dev" : split;
        dialogueIntent = dialogueIntent == null || dialogueIntent.isBlank()
                ? "ask_legal_advice" : dialogueIntent;
        mixedType = mixedType == null || mixedType.isBlank() ? "statute_only" : mixedType;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        expectedChunkIds = expectedChunkIds == null ? List.of() : List.copyOf(expectedChunkIds);
        expectedLawRefs = expectedLawRefs == null ? List.of() : List.copyOf(expectedLawRefs);
        expectedDocumentIds = expectedDocumentIds == null ? List.of() : List.copyOf(expectedDocumentIds);
        relevanceJudgments = relevanceJudgments == null ? Map.of() : Map.copyOf(relevanceJudgments);
    }

    public RagEvalItem(
            String id,
            String domain,
            String query,
            List<String> expectedChunkIds,
            List<RagEvalLawRef> expectedLawRefs,
            String failureType,
            String source,
            String reviewer
    ) {
        this(id,
                "dev",
                "ask_legal_advice",
                false,
                "statute_only",
                null,
                domain,
                null,
                null,
                domain,
                query,
                List.of(),
                expectedChunkIds,
                expectedLawRefs,
                List.of(),
                Map.of(),
                failureType,
                source,
                reviewer,
                null);
    }
}
