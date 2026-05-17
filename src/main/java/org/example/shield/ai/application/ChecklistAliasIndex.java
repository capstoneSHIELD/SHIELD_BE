package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
    private Map<String, AliasEntry> byMappingId = Map.of();
    private List<AliasEntry> entries = List.of();

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
                AliasEntry entry = new AliasEntry(staticMappingId, domain, slotId, label, keywords);
                mapping.put(staticMappingId, entry);
                loaded.add(entry);
            });
        });
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record AliasEntry(
            String staticMappingId,
            String domain,
            String slotId,
            String label,
            List<String> keywords
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
}
