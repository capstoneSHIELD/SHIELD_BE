package org.example.shield.ai.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PromptService} 단위 테스트 — Issue #40 A 후속 (slug 매핑).
 *
 * <p>프로덕션 리소스({@code src/main/resources/ai/checklists/}) 를 그대로 사용한다.</p>
 */
class PromptServiceTest {

    private PromptService service;

    @BeforeEach
    void setUp() {
        service = new PromptService(new DefaultResourceLoader());
    }

    @Test
    @DisplayName("loadChecklist — 8개 L1 한글 이름 모두 YAML 로드 성공")
    void loadChecklist_allEightL1Names() {
        for (Map.Entry<String, String> entry : ChecklistSlugMap.L1_TO_SLUG.entrySet()) {
            String l1Name = entry.getKey();
            String slug = entry.getValue();

            String yaml = service.loadChecklist(l1Name);

            assertThat(yaml).as("YAML 로드 — L1='%s'", l1Name).isNotNull();
            // YAML 내용에 slug 가 포함되어 올바른 파일이 로드됐음을 간접 검증
            assertThat(yaml).as("slug 일치 확인 — %s", slug).contains("slug: " + slug);
            assertThat(yaml).as("meta.l1 포함 — %s", slug).contains("l1: \"" + l1Name + "\"");
        }
    }

    @Test
    @DisplayName("loadChecklist — null 입력 시 null 반환")
    void loadChecklist_nullInput() {
        assertThat(service.loadChecklist(null)).isNull();
    }

    @Test
    @DisplayName("loadChecklist — 미지원 L1 이름 (구 enum 코드) 시 null 반환")
    void loadChecklist_legacyEnumReturnsNull() {
        // 구 DomainType enum 기반 코드 — 더 이상 지원하지 않음
        assertThat(service.loadChecklist("CRIMINAL_LAW")).isNull();
        assertThat(service.loadChecklist("CIVIL_LAW")).isNull();
        assertThat(service.loadChecklist("SOCIAL_SECURITY_LAW")).isNull();
        assertThat(service.loadChecklist("COMMERCIAL_LAW")).isNull();
    }

    @Test
    @DisplayName("loadChecklist — 온톨로지 밖 임의 문자열 시 null 반환")
    void loadChecklist_unknownStringReturnsNull() {
        assertThat(service.loadChecklist("가족법")).isNull();
        assertThat(service.loadChecklist("형사")).isNull();
        assertThat(service.loadChecklist("")).isNull();
    }

    @Test
    @DisplayName("loadRouterChatPrompt — chat.md 로드 성공")
    void loadRouterChatPrompt_success() {
        String prompt = service.loadRouterChatPrompt();
        assertThat(prompt).isNotNull().isNotEmpty();
        assertThat(prompt).contains("개인정보 최소 수집");
        assertThat(prompt).contains("실명/성명");
        assertThat(prompt).contains("실명 대신 A씨처럼 지칭해도 됩니다");
        assertThat(prompt).contains("연락처/실명/신분증 번호를 요구하지 마세요");
    }

    @Test
    @DisplayName("loadRouterBriefPrompt — brief.md 로드 성공")
    void loadRouterBriefPrompt_success() {
        String prompt = service.loadRouterBriefPrompt();
        assertThat(prompt).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("ChecklistSlugMap — 8개 매핑 존재, 불변")
    void checklistSlugMap_sizeAndImmutable() {
        assertThat(ChecklistSlugMap.L1_TO_SLUG).hasSize(8);
        assertThat(ChecklistSlugMap.slugFor("부동산 거래")).isEqualTo("real-estate");
        assertThat(ChecklistSlugMap.slugFor("기업·상사거래")).isEqualTo("commercial");
        assertThat(ChecklistSlugMap.slugFor(null)).isNull();
        assertThat(ChecklistSlugMap.slugFor("존재하지 않는 이름")).isNull();
    }

    @Test
    @DisplayName("ChecklistSlugMap.slugFor — 8개 카테고리 대표 alias 매핑 (프론트 축약 라벨 호환)")
    void checklistSlugMap_aliasMappings() {
        // 부동산 거래
        assertThat(ChecklistSlugMap.slugFor("부동산")).isEqualTo("real-estate");
        assertThat(ChecklistSlugMap.slugFor("부동산거래")).isEqualTo("real-estate");
        // 이혼·위자료·재산분할
        assertThat(ChecklistSlugMap.slugFor("이혼")).isEqualTo("divorce");
        assertThat(ChecklistSlugMap.slugFor("가사")).isEqualTo("divorce");
        // 상속·유류분·유언
        assertThat(ChecklistSlugMap.slugFor("상속")).isEqualTo("inheritance");
        // 근로계약·해고·임금
        assertThat(ChecklistSlugMap.slugFor("근로")).isEqualTo("labor");
        assertThat(ChecklistSlugMap.slugFor("노동")).isEqualTo("labor");
        // 손해배상·불법행위
        assertThat(ChecklistSlugMap.slugFor("교통사고")).isEqualTo("damages-tort");
        // 채무·보증·개인파산·회생
        assertThat(ChecklistSlugMap.slugFor("파산")).isEqualTo("debt");
        // 임대차보호
        assertThat(ChecklistSlugMap.slugFor("임대차")).isEqualTo("lease-protection");
        assertThat(ChecklistSlugMap.slugFor("전세")).isEqualTo("lease-protection");
        // 기업·상사거래
        assertThat(ChecklistSlugMap.slugFor("기업")).isEqualTo("commercial");
    }

    @Test
    @DisplayName("ChecklistSlugMap.slugFor — 입력 trim 처리 + blank 입력 null")
    void checklistSlugMap_trim() {
        assertThat(ChecklistSlugMap.slugFor(" 부동산 ")).isEqualTo("real-estate");
        assertThat(ChecklistSlugMap.slugFor("  부동산 거래  ")).isEqualTo("real-estate");
        assertThat(ChecklistSlugMap.slugFor("   ")).isNull();
    }

    @Test
    @DisplayName("ChecklistSlugMap.canonicalL1 — 정식명 그대로 / alias 정규화 / 미존재 null")
    void checklistSlugMap_canonicalL1() {
        assertThat(ChecklistSlugMap.canonicalL1("부동산 거래")).isEqualTo("부동산 거래");
        assertThat(ChecklistSlugMap.canonicalL1("부동산")).isEqualTo("부동산 거래");
        assertThat(ChecklistSlugMap.canonicalL1("이혼")).isEqualTo("이혼·위자료·재산분할");
        assertThat(ChecklistSlugMap.canonicalL1("전세")).isEqualTo("임대차보호");
        assertThat(ChecklistSlugMap.canonicalL1(" 부동산 ")).isEqualTo("부동산 거래");
        assertThat(ChecklistSlugMap.canonicalL1(null)).isNull();
        assertThat(ChecklistSlugMap.canonicalL1("")).isNull();
        assertThat(ChecklistSlugMap.canonicalL1("법인세")).isNull();
    }

    @Test
    @DisplayName("loadChecklist — alias 입력으로도 정식 카테고리 YAML 로드 (end-to-end)")
    void loadChecklist_aliasInputLoadsCanonicalYaml() {
        String yaml = service.loadChecklist("부동산");
        assertThat(yaml).as("\"부동산\" alias → real-estate.yaml 로드").isNotNull();
        assertThat(yaml).contains("slug: real-estate");
        assertThat(yaml).contains("l1: \"부동산 거래\"");
    }
}
