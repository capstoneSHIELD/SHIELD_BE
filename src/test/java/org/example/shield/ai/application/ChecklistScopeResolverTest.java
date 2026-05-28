package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.checklist.ChecklistScopeLevel;
import org.example.shield.consultation.domain.MessageReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        ChecklistCoverageService coverageService = new ChecklistCoverageService(mock(MessageReader.class), resolver, 0.5);

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

    @Test
    @DisplayName("all ontology L3 node YAML overrides are loaded through the backend resolver")
    void resolve_allOntologyL3NodeOverridesFromClasspath() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String ontologyJson = readResource("ontology/legal-ontology-slim.json");
        OntologyService ontologyService = new OntologyService(ontologyJson, objectMapper);
        ontologyService.loadOntology();
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(
                new ChecklistLoader(),
                new DefaultResourceLoader(),
                ontologyService);

        List<L3Node> nodes = loadL3Nodes(objectMapper);

        assertThat(nodes).hasSize(136);
        for (L3Node node : nodes) {
            ChecklistScope scope = resolver.resolve(node.l1Name(), node.l2Name(), node.l3Name());
            List<ChecklistScopeItem> l3Items = scope.items().stream()
                    .filter(item -> item.level() == ChecklistScopeLevel.L3)
                    .toList();

            assertThat(scope.warnings()).as(node.path()).isEmpty();
            assertThat(scope.l3Name()).as(node.path()).isEqualTo(node.l3Name());
            assertThat(l3Items).as(node.path()).hasSizeBetween(5, 9);
            assertThat(l3Items).as(node.path()).allSatisfy(item -> {
                assertThat(item.nodeId()).isEqualTo(node.id());
                assertThat(item.sourcePath()).startsWith("nodes/" + node.id() + ".items[");
            });
        }
    }

    private ByteArrayResource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private String readResource(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<L3Node> loadL3Nodes(ObjectMapper objectMapper) throws Exception {
        JsonNode root;
        try (InputStream in = new ClassPathResource("ontology/legal-ontology-slim.json").getInputStream()) {
            root = objectMapper.readTree(in);
        }

        List<L3Node> nodes = new ArrayList<>();
        for (JsonNode l1 : root.path("c")) {
            String l1Name = l1.path("name").asText();
            for (JsonNode l2 : l1.path("c")) {
                String l2Name = l2.path("name").asText();
                for (JsonNode l3 : l2.path("c")) {
                    nodes.add(new L3Node(
                            l3.path("id").asText(),
                            l1Name,
                            l2Name,
                            l3.path("name").asText()));
                }
            }
        }
        return nodes;
    }

    private record L3Node(String id, String l1Name, String l2Name, String l3Name) {
        String path() {
            return l1Name + " > " + l2Name + " > " + l3Name + " (" + id + ")";
        }
    }
}
