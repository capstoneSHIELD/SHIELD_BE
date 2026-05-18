package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.checklist.ChecklistScopeLevel;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChecklistScopeResolver {

    private static final String NODE_CHECKLIST_DIR = "ai/checklists/nodes/";

    private final ChecklistLoader checklistLoader;
    private final ResourceLoader resourceLoader;
    private final OntologyService ontologyService;

    private final YAMLMapper yamlMapper = new YAMLMapper();

    public ChecklistScope resolve(String l1Name, String l2Name, String l3Name) {
        List<String> warnings = new ArrayList<>();
        if (isBlank(l1Name)) {
            warnings.add("l1 is blank");
            return empty(l1Name, null, null, warnings);
        }

        JsonNode root = checklistLoader.loadAsTree(l1Name);
        if (root == null) {
            warnings.add("l1 checklist not found: " + l1Name);
            return empty(l1Name, null, null, warnings);
        }

        String slug = textOrDefault(root.path("meta").path("slug"), ChecklistSlugMap.slugFor(l1Name));
        String sourceVersion = root.path("meta").path("version").isNumber()
                ? String.valueOf(root.path("meta").path("version").asInt())
                : null;

        List<ChecklistScopeItem> items = new ArrayList<>();
        int priority = 1;
        priority = addArrayItems(
                items,
                root.path("l1_checklist").path("required"),
                ChecklistScopeLevel.L1,
                true,
                priority,
                "l1_checklist.required",
                slug,
                "root",
                "root",
                null);
        priority = addArrayItems(
                items,
                root.path("l1_checklist").path("domain_specific"),
                ChecklistScopeLevel.L1,
                false,
                priority,
                "l1_checklist.domain_specific",
                slug,
                "root",
                "root",
                null);

        String scopedL2Name = null;
        String scopedL3Name = null;
        if (!isBlank(l2Name)) {
            JsonNode l2Node = root.path("l2_checklists").path(l2Name);
            if (l2Node.isObject()) {
                scopedL2Name = l2Name;
                String l2NodeId = nodeIdOrName(l2Name);
                JsonNode l2Override = loadNodeOverride(l2NodeId, warnings);
                JsonNode focus = l2Override != null && l2Override.path("focus").isArray()
                        ? l2Override.path("focus")
                        : l2Node.path("focus");
                String focusPath = l2Override != null && l2Override.path("focus").isArray()
                        ? "nodes/" + l2NodeId + ".focus"
                        : "l2_checklists." + l2Name + ".focus";
                priority = addArrayItems(
                        items,
                        focus,
                        ChecklistScopeLevel.L2,
                        true,
                        priority,
                        focusPath,
                        slug,
                        l2NodeId,
                        "root",
                        l2NodeId);

                if (!isBlank(l3Name)) {
                    ResolvedItems resolvedL3 = resolveL3Items(l2Name, l3Name, l2Node, l2Override, warnings);
                    if (resolvedL3.items() != null && resolvedL3.items().isArray()) {
                        scopedL3Name = l3Name;
                        String l3NodeId = childNodeIdOrName(l2Name, l3Name);
                        priority = addArrayItems(
                                items,
                                resolvedL3.items(),
                                ChecklistScopeLevel.L3,
                                true,
                                priority,
                                resolvedL3.sourcePath(),
                                slug,
                                l2NodeId,
                                l3NodeId,
                                l3NodeId);
                    } else {
                        warnings.add("l3 checklist not found: " + l2Name + " > " + l3Name);
                    }
                }
            } else {
                warnings.add("l2 checklist not found: " + l2Name);
            }
        }

        return new ChecklistScope(l1Name, scopedL2Name, scopedL3Name, sourceVersion, items, warnings);
    }

    public List<ChecklistScopeItem> resolveAllStaticItems() {
        Map<String, ChecklistScopeItem> bySlotId = new LinkedHashMap<>();
        for (String l1Name : ChecklistSlugMap.L1_TO_SLUG.keySet()) {
            ChecklistScope l1Scope = resolve(l1Name, null, null);
            l1Scope.items().forEach(item -> bySlotId.putIfAbsent(item.slotId(), item));

            JsonNode root = checklistLoader.loadAsTree(l1Name);
            if (root == null || !root.path("l2_checklists").isObject()) {
                continue;
            }
            root.path("l2_checklists").fields().forEachRemaining(l2Entry -> {
                String l2Name = l2Entry.getKey();
                ChecklistScope l2Scope = resolve(l1Name, l2Name, null);
                l2Scope.items().forEach(item -> bySlotId.putIfAbsent(item.slotId(), item));

                JsonNode l3Root = l2Entry.getValue().path("l3_checklists");
                if (!l3Root.isObject()) {
                    return;
                }
                l3Root.fields().forEachRemaining(l3Entry -> {
                    ChecklistScope l3Scope = resolve(l1Name, l2Name, l3Entry.getKey());
                    l3Scope.items().forEach(item -> bySlotId.putIfAbsent(item.slotId(), item));
                });
            });
        }
        return List.copyOf(bySlotId.values());
    }

    private ChecklistScope empty(String l1Name, String l2Name, String l3Name, List<String> warnings) {
        return new ChecklistScope(l1Name, l2Name, l3Name, null, List.of(), warnings);
    }

    private ResolvedItems resolveL3Items(
            String l2Name,
            String l3Name,
            JsonNode fallbackL2Node,
            JsonNode l2Override,
            List<String> warnings
    ) {
        String l3NodeId = childNodeIdOrName(l2Name, l3Name);
        JsonNode l3Override = loadNodeOverride(l3NodeId, warnings);
        JsonNode fromL3Override = extractL3Items(l3Override, l3Name);
        if (fromL3Override != null) {
            return new ResolvedItems(fromL3Override, "nodes/" + l3NodeId + ".items");
        }

        JsonNode fromL2Override = l2Override == null
                ? null
                : l2Override.path("l3_checklists").path(l3Name);
        if (fromL2Override != null && fromL2Override.isArray()) {
            String l2NodeId = nodeIdOrName(l2Name);
            return new ResolvedItems(fromL2Override, "nodes/" + l2NodeId + ".l3_checklists." + l3Name);
        }
        return new ResolvedItems(
                fallbackL2Node.path("l3_checklists").path(l3Name),
                "l2_checklists." + l2Name + ".l3_checklists." + l3Name);
    }

    private JsonNode extractL3Items(JsonNode override, String l3Name) {
        if (override == null) {
            return null;
        }
        if (override.isArray()) {
            return override;
        }
        if (override.path("items").isArray()) {
            return override.path("items");
        }
        if (override.path("checklist").isArray()) {
            return override.path("checklist");
        }
        JsonNode nested = override.path("l3_checklists").path(l3Name);
        return nested.isArray() ? nested : null;
    }

    private JsonNode loadNodeOverride(String nodeId, List<String> warnings) {
        if (isBlank(nodeId) || resourceLoader == null) {
            return null;
        }
        String path = NODE_CHECKLIST_DIR + nodeId + ".yaml";
        Resource resource = resourceLoader.getResource("classpath:" + path);
        if (resource == null || !resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return yamlMapper.readTree(in);
        } catch (IOException e) {
            String warning = "node override parse failed: " + path;
            warnings.add(warning);
            log.warn("{}: {}", warning, e.getMessage());
            return null;
        }
    }

    private int addArrayItems(
            List<ChecklistScopeItem> items,
            JsonNode array,
            ChecklistScopeLevel level,
            boolean required,
            int startPriority,
            String sourcePath,
            String l1Slug,
            String l2Key,
            String l3Key,
            String nodeId
    ) {
        if (array == null || !array.isArray()) {
            return startPriority;
        }
        int priority = startPriority;
        int index = 0;
        for (JsonNode node : array) {
            if (node.isTextual() && !node.asText().isBlank()) {
                String label = node.asText();
                String indexedPath = sourcePath + "[" + index + "]";
                items.add(new ChecklistScopeItem(
                        stableSlotId(l1Slug, l2Key, l3Key, label),
                        label,
                        level,
                        required,
                        priority++,
                        indexedPath,
                        nodeId,
                        inferValueType(label)));
            }
            index++;
        }
        return priority;
    }

    private String stableSlotId(String l1Slug, String l2Key, String l3Key, String label) {
        return "static:"
                + safeKey(l1Slug)
                + ":"
                + safeKey(l2Key)
                + ":"
                + safeKey(l3Key)
                + ":"
                + shortHash(label);
    }

    private String nodeIdOrName(String nodeName) {
        String id = ontologyService == null ? null : ontologyService.idOf(nodeName);
        return isBlank(id) ? safeKey(nodeName) : id;
    }

    private String childNodeIdOrName(String parentName, String childName) {
        String id = ontologyService == null ? null : ontologyService.childIdOf(parentName, childName);
        return isBlank(id) ? safeKey(childName) : id;
    }

    private String safeKey(String value) {
        if (isBlank(value)) {
            return "root";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? shortHash(value) : normalized;
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private SlotValueType inferValueType(String label) {
        String text = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (containsAny(text, "amount", "deposit", "price", "cost", "money",
                "\uAE08\uC561", "\uBCF4\uC99D\uAE08", "\uCC28\uC784", "\uC6D4\uC138",
                "\uBE44\uC6A9", "\uC190\uD574", "\uBC30\uC0C1", "\uAC00\uACA9")) {
            return SlotValueType.MONEY;
        }
        if (containsAny(text, "date", "time", "period", "expiry", "termination",
                "\uB0A0\uC9DC", "\uC77C\uC2DC", "\uC2DC\uC810", "\uAE30\uAC04",
                "\uC885\uB8CC", "\uB9CC\uB8CC", "\uACC4\uC57D\uC77C", "\uAC31\uC2E0")) {
            return SlotValueType.DATE;
        }
        return SlotValueType.TEXT;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (!isBlank(needle) && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String textOrDefault(JsonNode node, String fallback) {
        return node != null && node.isTextual() && !node.asText().isBlank()
                ? node.asText()
                : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedItems(JsonNode items, String sourcePath) {
    }
}
