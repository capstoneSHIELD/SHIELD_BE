package org.example.shield.ai.dto.checklist;

import java.util.List;

public record ChecklistScope(
        String l1Name,
        String l2Name,
        String l3Name,
        String sourceVersion,
        List<ChecklistScopeItem> items,
        List<String> warnings
) {
    public ChecklistScope {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }
}
