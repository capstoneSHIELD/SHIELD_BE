package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class ChecklistEvidenceQualityTest {

    private static final Path ONTOLOGY_PATH = Path.of("src/main/resources/ontology/legal-ontology-slim.json");
    private static final Path OVERRIDE_DIR = Path.of("src/main/resources/ai/checklists/nodes");
    private static final Path EVIDENCE_DIR = Path.of("docs/ai-rag-v2.2/checklist-evidence");
    private static final int MIN_ITEMS = 5;
    private static final int MAX_ITEMS = 9;

    private static final List<Pattern> JUDGMENT_LIKE_PATTERNS = List.of(
            Pattern.compile("승소"),
            Pattern.compile("패소"),
            Pattern.compile("가능합니다"),
            Pattern.compile("인정됩니다"),
            Pattern.compile("받을 수"),
            Pattern.compile("위법\\s*여부"),
            Pattern.compile("불법\\s*여부"),
            Pattern.compile("적법\\s*여부"),
            Pattern.compile("합법\\s*여부"),
            Pattern.compile("소송\\s*가능성"),
            Pattern.compile("청구\\s*가능성"),
            Pattern.compile("인정\\s*가능성"),
            Pattern.compile("유불리")
    );
    private static final Pattern LAW_OR_CASE_MARKER = Pattern.compile(
            "LSI\\d+|precSeq=\\d+|detcSeq=\\d+|대법원|헌재|법령|제\\d+조"
    );

    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    @DisplayName("ontology node ids are unique and follow L1/L2/L3 parent prefixes")
    void ontologyNodeIdsAreUniqueAndStable() throws Exception {
        Map<String, String> l3PathsById = loadL3PathsById();

        assertThat(l3PathsById).isNotEmpty();
    }

    @Test
    @DisplayName("every evidence file has a matching node override")
    void everyEvidenceFileHasMatchingOverride() throws Exception {
        List<String> orphanEvidence = new ArrayList<>();
        for (Path evidencePath : listFiles(EVIDENCE_DIR, ".md")) {
            String nodeId = stripExtension(evidencePath);
            if (!Files.exists(OVERRIDE_DIR.resolve(nodeId + ".yaml"))) {
                orphanEvidence.add(nodeId);
            }
        }

        assertThat(orphanEvidence).isEmpty();
    }

    @TestFactory
    @DisplayName("all node overrides are parseable, factual, and evidence-backed")
    List<DynamicTest> allNodeOverridesPassQualityGate() throws Exception {
        Map<String, String> l3PathsById = loadL3PathsById();
        List<Path> overrideFiles = listFiles(OVERRIDE_DIR, ".yaml");

        assertThat(overrideFiles).isNotEmpty();

        return overrideFiles.stream()
                .map(path -> dynamicTest(path.getFileName().toString(),
                        () -> assertOverrideQuality(path, l3PathsById)))
                .toList();
    }

    private void assertOverrideQuality(Path yamlPath, Map<String, String> l3PathsById) throws Exception {
        String nodeId = stripExtension(yamlPath);
        assertThat(l3PathsById)
                .as("node override must target an ontology L3 node: %s", nodeId)
                .containsKey(nodeId);

        JsonNode root;
        try (InputStream in = Files.newInputStream(yamlPath)) {
            root = yamlMapper.readTree(in);
        }

        JsonNode items = root.path("items");
        assertThat(items.isArray())
                .as("items must be a YAML array: %s", yamlPath)
                .isTrue();
        assertThat(items.size())
                .as("L3 item count must stay within %s..%s: %s", MIN_ITEMS, MAX_ITEMS, nodeId)
                .isBetween(MIN_ITEMS, MAX_ITEMS);

        Set<String> unique = new HashSet<>();
        List<String> labels = new ArrayList<>();
        for (JsonNode item : items) {
            String label = item.asText();
            labels.add(label);
            assertThat(label).as("item label must not be blank: %s", nodeId).isNotBlank();
            assertThat(label).as("scaffold placeholder must be replaced: %s", nodeId).doesNotContain("TODO");
            assertThat(unique.add(label)).as("duplicate item in %s: %s", nodeId, label).isTrue();
            for (Pattern pattern : JUDGMENT_LIKE_PATTERNS) {
                assertThat(pattern.matcher(label).find())
                        .as("judgment-like checklist item in %s: %s", nodeId, label)
                        .isFalse();
            }
        }

        Path evidencePath = EVIDENCE_DIR.resolve(nodeId + ".md");
        assertThat(Files.exists(evidencePath))
                .as("evidence file must exist for %s", nodeId)
                .isTrue();

        String evidenceText = Files.readString(evidencePath, StandardCharsets.UTF_8);
        assertThat(evidenceText).as("evidence scaffold must be completed: %s", nodeId).doesNotContain("TODO");
        assertThat(evidenceText).as("evidence must contain final item mapping: %s", nodeId)
                .contains("최종 YAML Items");
        assertThat(LAW_OR_CASE_MARKER.matcher(evidenceText).find())
                .as("evidence must contain law/case markers: %s", nodeId)
                .isTrue();

        for (String label : labels) {
            assertThat(evidenceText)
                    .as("evidence must mention every YAML item in %s", nodeId)
                    .contains(label);
        }
    }

    private Map<String, String> loadL3PathsById() throws Exception {
        JsonNode root;
        try (InputStream in = Files.newInputStream(ONTOLOGY_PATH)) {
            root = jsonMapper.readTree(in);
        }

        assertThat(root.path("id").asText()).isEqualTo("law-000");

        Set<String> allIds = new HashSet<>();
        allIds.add("law-000");
        Map<String, String> l3PathsById = new LinkedHashMap<>();

        for (JsonNode l1 : root.path("c")) {
            String l1Id = assertOntologyNode(l1, "law-\\d{3}", null, allIds);
            String l1Name = l1.path("name").asText();
            for (JsonNode l2 : l1.path("c")) {
                String l2Id = assertOntologyNode(l2, "law-\\d{3}-\\d{2}", l1Id, allIds);
                String l2Name = l2.path("name").asText();
                for (JsonNode l3 : l2.path("c")) {
                    String l3Id = assertOntologyNode(l3, "law-\\d{3}-\\d{2}-\\d{2}", l2Id, allIds);
                    String l3Name = l3.path("name").asText();
                    l3PathsById.put(l3Id, l1Name + " > " + l2Name + " > " + l3Name);
                }
            }
        }

        return l3PathsById;
    }

    private String assertOntologyNode(JsonNode node, String pattern, String parentId, Set<String> allIds) {
        String id = node.path("id").asText();
        String name = node.path("name").asText();

        assertThat(id).as("ontology id for %s", name).matches(pattern);
        assertThat(name).as("ontology name for %s", id).isNotBlank();
        if (parentId != null) {
            assertThat(id).as("ontology id must include parent prefix: %s", id).startsWith(parentId + "-");
        }
        assertThat(allIds.add(id)).as("duplicate ontology id: %s", id).isTrue();

        return id;
    }

    private List<Path> listFiles(Path dir, String suffix) throws Exception {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private String stripExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
