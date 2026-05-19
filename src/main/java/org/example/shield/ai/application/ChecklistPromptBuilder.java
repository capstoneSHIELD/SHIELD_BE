package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.checklist.ChecklistScopeLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChecklistPromptBuilder {

    private final ChecklistScopeResolver checklistScopeResolver;

    public String build(String l1Name, String l2Name, String l3Name) {
        ChecklistScope scope = checklistScopeResolver.resolve(l1Name, l2Name, l3Name);
        if (scope == null || !scope.hasItems()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# SHIELD scoped checklist\n");
        appendMeta(sb, scope);
        appendL1(sb, scope.items());
        appendL2(sb, scope);
        appendL3(sb, scope);
        appendWarnings(sb, scope.warnings());
        return sb.toString().stripTrailing();
    }

    private void appendMeta(StringBuilder sb, ChecklistScope scope) {
        sb.append("meta:\n");
        appendScalarField(sb, 2, "l1", scope.l1Name());
        if (scope.sourceVersion() != null && !scope.sourceVersion().isBlank()) {
            appendScalarField(sb, 2, "version", scope.sourceVersion());
        }
        if (scope.l2Name() != null && !scope.l2Name().isBlank()) {
            appendScalarField(sb, 2, "scoped_l2", scope.l2Name());
        }
        if (scope.l3Name() != null && !scope.l3Name().isBlank()) {
            appendScalarField(sb, 2, "scoped_l3", scope.l3Name());
        }
    }

    private void appendL1(StringBuilder sb, List<ChecklistScopeItem> items) {
        sb.append("\nl1_checklist:\n");
        appendArrayField(sb, 2, "required", items.stream()
                .filter(item -> item.level() == ChecklistScopeLevel.L1 && item.required())
                .toList());
        appendArrayField(sb, 2, "domain_specific", items.stream()
                .filter(item -> item.level() == ChecklistScopeLevel.L1 && !item.required())
                .toList());
    }

    private void appendL2(StringBuilder sb, ChecklistScope scope) {
        List<ChecklistScopeItem> l2Items = scope.items().stream()
                .filter(item -> item.level() == ChecklistScopeLevel.L2)
                .toList();
        if (l2Items.isEmpty()) {
            return;
        }
        sb.append("\nl2_checklist:\n");
        appendScalarField(sb, 2, "name", scope.l2Name());
        appendArrayField(sb, 2, "focus", l2Items);
    }

    private void appendL3(StringBuilder sb, ChecklistScope scope) {
        List<ChecklistScopeItem> l3Items = scope.items().stream()
                .filter(item -> item.level() == ChecklistScopeLevel.L3)
                .toList();
        if (l3Items.isEmpty()) {
            return;
        }
        sb.append("\nl3_checklist:\n");
        appendScalarField(sb, 2, "name", scope.l3Name());
        appendArrayField(sb, 2, "items", l3Items);
    }

    private void appendWarnings(StringBuilder sb, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        sb.append("\n# scope_warnings:\n");
        warnings.stream()
                .filter(warning -> warning != null && !warning.isBlank())
                .forEach(warning -> sb.append("# - ").append(warning).append('\n'));
    }

    private void appendArrayField(
            StringBuilder sb,
            int indent,
            String fieldName,
            List<ChecklistScopeItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        indent(sb, indent).append(fieldName).append(":\n");
        for (ChecklistScopeItem item : items) {
            indent(sb, indent + 2).append("- ");
            appendQuoted(sb, item.label());
            sb.append(" # slotId: ").append(item.slotId());
            if (item.sourcePath() != null && !item.sourcePath().isBlank()) {
                sb.append(", source: ").append(item.sourcePath());
            }
            sb.append('\n');
        }
    }

    private void appendScalarField(StringBuilder sb, int indent, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        indent(sb, indent).append(fieldName).append(": ");
        appendQuoted(sb, value);
        sb.append('\n');
    }

    private void appendQuoted(StringBuilder sb, String value) {
        String escaped = value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        sb.append('"').append(escaped).append('"');
    }

    private StringBuilder indent(StringBuilder sb, int spaces) {
        return sb.append(" ".repeat(Math.max(0, spaces)));
    }
}
