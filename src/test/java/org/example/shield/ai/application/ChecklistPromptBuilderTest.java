package org.example.shield.ai.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecklistPromptBuilderTest {

    private final ChecklistPromptBuilder builder =
            new ChecklistPromptBuilder(new ChecklistScopeResolver(new ChecklistLoader(), null, null));

    @Test
    @DisplayName("L1만 있으면 L1 공통 항목만 프롬프트에 포함한다")
    void build_l1Only_includesOnlyL1Checklist() {
        String prompt = builder.build("부동산 거래", null, null);

        assertThat(prompt).contains("# SHIELD scoped checklist");
        assertThat(prompt).contains("l1_checklist:");
        assertThat(prompt).contains("상대방 정보");
        assertThat(prompt).doesNotContain("l2_checklist:");
        assertThat(prompt).doesNotContain("l3_checklist:");
        assertThat(prompt).doesNotContain("부동산 매매");
        assertThat(prompt).doesNotContain("보증금 반환 지연 일수");
    }

    @Test
    @DisplayName("L1+L2면 선택된 L2 focus만 포함하고 형제 L2는 제외한다")
    void build_l1AndL2_includesSelectedL2Only() {
        String prompt = builder.build("부동산 거래", "부동산 임대차", null);

        assertThat(prompt).contains("l1_checklist:");
        assertThat(prompt).contains("l2_checklist:");
        assertThat(prompt).contains("name: \"부동산 임대차\"");
        assertThat(prompt).contains("전입신고·확정일자·대항력 상태");
        assertThat(prompt).doesNotContain("l3_checklist:");
        assertThat(prompt).doesNotContain("부동산 매매");
        assertThat(prompt).doesNotContain("하자 발견 시점");
        assertThat(prompt).doesNotContain("부동산 담보");
    }

    @Test
    @DisplayName("L1+L2+L3면 선택된 L3 항목만 포함하고 형제 L3는 제외한다")
    void build_l1L2L3_includesSelectedL3Only() {
        String prompt = builder.build("부동산 거래", "부동산 임대차", "보증금 및 차임");

        assertThat(prompt).contains("l1_checklist:");
        assertThat(prompt).contains("l2_checklist:");
        assertThat(prompt).contains("l3_checklist:");
        assertThat(prompt).contains("name: \"보증금 및 차임\"");
        assertThat(prompt).contains("보증금 반환 지연 일수");
        assertThat(prompt).contains("차임 연체 회수·금액");
        assertThat(prompt).doesNotContain("계약 체결 및 조건");
        assertThat(prompt).doesNotContain("계약 기간 및 체결일");
        assertThat(prompt).doesNotContain("계약 갱신 및 종료");
        assertThat(prompt).doesNotContain("갱신요구권 행사 시점");
    }

    @Test
    @DisplayName("존재하지 않는 L2/L3는 L1 공통 항목으로 fallback 한다")
    void build_unknownL2L3_fallsBackToL1Only() {
        String prompt = builder.build("부동산 거래", "없는 중분류", "없는 소분류");

        assertThat(prompt).contains("l1_checklist:");
        assertThat(prompt).contains("상대방 정보");
        assertThat(prompt).doesNotContain("l2_checklist:");
        assertThat(prompt).doesNotContain("l3_checklist:");
        assertThat(prompt).doesNotContain("scoped_l2");
        assertThat(prompt).doesNotContain("scoped_l3");
    }

    @Test
    @DisplayName("node-id L2 YAML 이 있으면 해당 focus 로 override 한다")
    void build_l2NodeOverride_replacesFocus() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        OntologyService ontologyService = mock(OntologyService.class);
        when(ontologyService.idOf("부동산 임대차")).thenReturn("law-001-02");
        when(resourceLoader.getResource("classpath:ai/checklists/nodes/law-001-02.yaml"))
                .thenReturn(resource("""
                        focus:
                          - node override focus
                        """));

        ChecklistPromptBuilder overrideBuilder =
                new ChecklistPromptBuilder(new ChecklistScopeResolver(new ChecklistLoader(), resourceLoader, ontologyService));

        String prompt = overrideBuilder.build("부동산 거래", "부동산 임대차", null);

        assertThat(prompt).contains("node override focus");
        assertThat(prompt).doesNotContain("전입신고·확정일자·대항력 상태");
    }

    @Test
    @DisplayName("node-id L3 YAML 이 있으면 해당 items 로 override 한다")
    void build_l3NodeOverride_replacesItems() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        OntologyService ontologyService = mock(OntologyService.class);
        when(ontologyService.childIdOf("부동산 임대차", "보증금 및 차임"))
                .thenReturn("law-001-02-02");
        when(resourceLoader.getResource("classpath:ai/checklists/nodes/law-001-02-02.yaml"))
                .thenReturn(resource("""
                        items:
                          - node override l3 item
                        """));

        ChecklistPromptBuilder overrideBuilder =
                new ChecklistPromptBuilder(new ChecklistScopeResolver(new ChecklistLoader(), resourceLoader, ontologyService));

        String prompt = overrideBuilder.build("부동산 거래", "부동산 임대차", "보증금 및 차임");

        assertThat(prompt).contains("node override l3 item");
        assertThat(prompt).doesNotContain("보증금 반환 지연 일수");
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
