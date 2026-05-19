package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ChecklistAliasIndex {

    private static final String ALIAS_PATTERN = "classpath*:ai/checklists/aliases/*.yaml";

    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private ChecklistScopeResolver checklistScopeResolver;
    private Map<String, AliasEntry> byMappingId = Map.of();
    private List<AliasEntry> entries = List.of();

    @Autowired(required = false)
    void setChecklistScopeResolver(ChecklistScopeResolver checklistScopeResolver) {
        this.checklistScopeResolver = checklistScopeResolver;
    }

    @PostConstruct
    void load() {
        Map<String, AliasEntry> mapping = new LinkedHashMap<>();
        List<AliasEntry> loaded = new ArrayList<>();
        try {
            for (Resource resource : resolver.getResources(ALIAS_PATTERN)) {
                try (InputStream in = resource.getInputStream()) {
                    loadResource(yamlMapper.readTree(in), mapping, loaded);
                }
            }
            loadDefaultScopeEntries(mapping, loaded);
            this.byMappingId = Map.copyOf(mapping);
            this.entries = List.copyOf(loaded);
            log.info("Checklist alias index loaded: {} entries", entries.size());
        } catch (Exception e) {
            this.byMappingId = Map.of();
            this.entries = List.of();
            log.warn("Checklist alias index failed to load. Dynamic plan feature should stay disabled: {}",
                    e.getMessage());
        }
    }

    public Optional<AliasEntry> findByStaticMappingId(String staticMappingId) {
        if (staticMappingId == null || staticMappingId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byMappingId.get(staticMappingId));
    }

    public Optional<AliasEntry> resolve(String label, List<String> keywords) {
        String haystack = normalize((label == null ? "" : label)
                + " "
                + String.join(" ", keywords == null ? List.of() : keywords));
        if (haystack.isBlank()) {
            return Optional.empty();
        }
        return entries.stream()
                .filter(entry -> entry.matches(haystack))
                .findFirst();
    }

    public int size() {
        return entries.size();
    }

    public AliasCoverageReport coverageReport() {
        long generated = entries.stream().filter(entry -> "generated_scope".equals(entry.source())).count();
        long manual = entries.stream().filter(entry -> "manual_alias".equals(entry.source())).count();
        long withoutKeywords = entries.stream().filter(entry -> entry.keywords().isEmpty()).count();
        return new AliasCoverageReport(entries.size(), generated, manual, withoutKeywords);
    }

    private void loadDefaultScopeEntries(
            Map<String, AliasEntry> mapping,
            List<AliasEntry> loaded
    ) {
        if (checklistScopeResolver == null) {
            return;
        }
        for (ChecklistScopeItem item : checklistScopeResolver.resolveAllStaticItems()) {
            String staticMappingId = item.slotId();
            if (staticMappingId == null || staticMappingId.isBlank()
                    || mapping.containsKey(staticMappingId)) {
                continue;
            }
            List<String> keywords = new ArrayList<>(ChecklistTokenizer.tokensOf(item.label()));
            AliasEntry entry = new AliasEntry(
                    staticMappingId,
                    domainFromSlotId(staticMappingId),
                    staticMappingId,
                    item.label(),
                    keywords,
                    "generated_scope",
                    item.level() == null ? null : item.level().name(),
                    item.sourcePath());
            mapping.put(staticMappingId, entry);
            loaded.add(entry);
        }
    }

    private void loadResource(
            JsonNode root,
            Map<String, AliasEntry> mapping,
            List<AliasEntry> loaded
    ) {
        if (root == null || !root.isObject()) {
            return;
        }
        root.fields().forEachRemaining(domainEntry -> {
            String domain = domainEntry.getKey();
            JsonNode slots = domainEntry.getValue();
            if (!slots.isObject()) {
                return;
            }
            slots.fields().forEachRemaining(slotEntry -> {
                String slotId = slotEntry.getKey();
                JsonNode node = slotEntry.getValue();
                String label = node.path("label").asText(slotId);
                List<String> keywords = new ArrayList<>();
                if (node.path("keywords").isArray()) {
                    node.path("keywords").forEach(keyword -> {
                        if (keyword.isTextual() && !keyword.asText().isBlank()) {
                            keywords.add(keyword.asText());
                        }
                    });
                }
                String staticMappingId = domain + "." + slotId;
                AliasEntry entry = new AliasEntry(
                        staticMappingId,
                        domain,
                        slotId,
                        label,
                        keywords,
                        "manual_alias",
                        null,
                        null);
                mapping.put(staticMappingId, entry);
                loaded.add(entry);
            });
        });
    }

    private String domainFromSlotId(String staticMappingId) {
        if (staticMappingId == null || !staticMappingId.startsWith("static:")) {
            return "";
        }
        String[] parts = staticMappingId.split(":");
        return parts.length > 1 ? parts[1] : "";
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record AliasEntry(
            String staticMappingId,
            String domain,
            String slotId,
            String label,
            List<String> keywords,
            String source,
            String level,
            String sourcePath
    ) {
        boolean matches(String haystack) {
            if (haystack.contains(normalize(label))) {
                return true;
            }
            for (String keyword : keywords) {
                String normalized = normalize(keyword);
                if (!normalized.isBlank() && haystack.contains(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record AliasCoverageReport(
            int totalEntries,
            long generatedScopeEntries,
            long manualAliasEntries,
            long entriesWithoutKeywords
    ) {
    }
}
