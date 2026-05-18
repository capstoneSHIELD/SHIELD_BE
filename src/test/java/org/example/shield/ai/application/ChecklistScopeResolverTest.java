package org.example.shield.ai.application;

import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.checklist.ChecklistScopeLevel;
import org.example.shield.consultation.domain.MessageReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecklistScopeResolverTest {

    @Test
    @DisplayName("L1/L2/L3 scope uses one stable item model")
    void resolve_l1L2L3_returnsScopedItemsWithStableIds() {
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(new ChecklistLoader(), null, null);

        ChecklistScope l1 = resolver.resolve("부동산 거래", null, null);
        ChecklistScope l2 = resolver.resolve("부동산 거래", "부동산 임대차", null);
        ChecklistScope l3 = resolver.resolve("부동산 거래", "부동산 임대차", "보증금 및 차임");

        assertThat(l1.items()).allMatch(item -> item.level() == ChecklistScopeLevel.L1);
        assertThat(l2.items()).anyMatch(item -> item.level() == ChecklistScopeLevel.L2);
        assertThat(l2.items()).noneMatch(item -> item.level() == ChecklistScopeLevel.L3);
        assertThat(l3.items()).anyMatch(item -> item.level() == ChecklistScopeLevel.L3);
        assertThat(l3.items()).allMatch(item -> item.slotId().startsWith("static:"));
        assertThat(l3.items()).extracting(ChecklistScopeItem::slotId).doesNotContain("static_001");
    }

    @Test
    @DisplayName("unknown L2/L3 falls back to narrower valid scope and records warnings")
    void resolve_unknownNodes_fallsBackWithWarnings() {
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(new ChecklistLoader(), null, null);

        ChecklistScope unknownL2 = resolver.resolve("부동산 거래", "없는 중분류", "없는 소분류");
        ChecklistScope unknownL3 = resolver.resolve("부동산 거래", "부동산 임대차", "없는 소분류");

        assertThat(unknownL2.l2Name()).isNull();
        assertThat(unknownL2.items()).allMatch(item -> item.level() == ChecklistScopeLevel.L1);
        assertThat(unknownL2.warnings()).isNotEmpty();

        assertThat(unknownL3.l2Name()).isEqualTo("부동산 임대차");
        assertThat(unknownL3.l3Name()).isNull();
        assertThat(unknownL3.items()).noneMatch(item -> item.level() == ChecklistScopeLevel.L3);
        assertThat(unknownL3.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("node override is resolved through the same item model")
    void resolve_nodeOverride_replacesFocus() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        OntologyService ontologyService = mock(OntologyService.class);
        when(ontologyService.idOf("부동산 임대차")).thenReturn("law-001-02");
        when(resourceLoader.getResource("classpath:ai/checklists/nodes/law-001-02.yaml"))
                .thenReturn(resource("""
                        focus:
                          - node override focus
                        """));

        ChecklistScopeResolver resolver = new ChecklistScopeResolver(
                new ChecklistLoader(), resourceLoader, ontologyService);

        ChecklistScope scope = resolver.resolve("부동산 거래", "부동산 임대차", null);

        assertThat(scope.items())
                .anyMatch(item -> item.label().equals("node override focus")
                        && item.sourcePath().startsWith("nodes/law-001-02.focus"));
        assertThat(scope.items())
                .noneMatch(item -> item.label().contains("전입신고"));
    }

    @Test
    @DisplayName("prompt and coverage consume the same resolved scoped items")
    void promptAndCoverage_useSameScopeItems() {
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(new ChecklistLoader(), null, null);
        ChecklistPromptBuilder promptBuilder = new ChecklistPromptBuilder(resolver);
        ChecklistCoverageService coverageService = new ChecklistCoverageService(mock(MessageReader.class), resolver);

        String prompt = promptBuilder.build("부동산 거래", "부동산 임대차", "보증금 및 차임");
        List<String> coverageLabels = coverageService
                .buildCoverageItems("부동산 거래", "부동산 임대차", "보증금 및 차임", List.of())
                .stream()
                .map(ChecklistCoverageService.CoverageItem::label)
                .toList();

        assertThat(coverageLabels).isNotEmpty();
        assertThat(coverageLabels).allSatisfy(label -> assertThat(prompt).contains(label));
    }

    @Test
    @DisplayName("all canonical L1 YAML files contribute generated static items")
    void resolveAllStaticItems_allDomains() {
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(new ChecklistLoader(), null, null);

        List<ChecklistScopeItem> items = resolver.resolveAllStaticItems();
        Set<String> slotIds = items.stream().map(ChecklistScopeItem::slotId).collect(Collectors.toSet());

        assertThat(items).hasSizeGreaterThan(100);
        assertThat(slotIds).hasSameSizeAs(items);
        assertThat(items).allMatch(item -> item.slotId().startsWith("static:"));
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
