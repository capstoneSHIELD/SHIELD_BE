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

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LawRef {
        private String lawId;
        private String name;
    }
}
