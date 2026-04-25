package org.example.shield.ai.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 카테고리-법령 매핑 POJO.
 * YAML 파일(category-law-mappings.yml)에서 로드되는 인메모리 도메인 객체.
 */
@Getter
@Setter
@NoArgsConstructor
public class CategoryLawMapping {

    private String categoryId;
    private String name;
    private List<LawRef> primaryLawIds;
    private List<LawRef> secondaryLawIds;
    /**
     * 이 카테고리(온톨로지 노드)에 매핑되는 {@code legal_chunks.category_ids} 토큰 목록.
     * 예: {@code law-001-02} → {@code [group:leasing, group:jeonse]}.
     * null/empty면 retrieval 단계에서 카테고리 soft-filter가 적용되지 않는다.
     */
    private List<String> categoryIds;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LawRef {
        /** legal_chunks.law_id 와 동일한 slug 식별자 (예: "law-housing-lease"). */
        private String lawId;
        /** 사람이 읽는 법률 한글명. */
        private String name;
        /**
         * 법제처 LOD 공식 LSI 코드 (예: "LSI249999"). 인제스트(SpecialLawIngestService)에서
         * 시드 파일의 source_law_id 로 카테고리를 역조회할 때 사용. 외부 데이터 소스와의
         * 연결고리 보존 목적으로 slug 와 별도로 유지한다. EXTERNAL 법률은 null.
         */
        private String lsiCode;
    }
}
