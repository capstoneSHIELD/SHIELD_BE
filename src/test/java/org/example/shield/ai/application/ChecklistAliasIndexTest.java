package org.example.shield.ai.application;

import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistAliasIndexTest {

    private ChecklistAliasIndex index;

    @BeforeEach
    void setUp() {
        index = new ChecklistAliasIndex();
        index.load();
    }

    @Test
    @DisplayName("manual alias YAML is loaded and legacy static mapping id can be resolved")
    void load_findByStaticMappingId() {
        assertThat(index.size()).isGreaterThan(0);
        assertThat(index.findByStaticMappingId("real-estate.lease_end_date")).isPresent();
    }

    @Test
    @DisplayName("manual dynamic slot keywords can resolve to a legacy static alias")
    void resolve_dynamicLabel() {
        assertThat(index.resolve("contract termination", List.of("lease_expiry")))
                .isPresent()
                .get()
                .extracting(ChecklistAliasIndex.AliasEntry::staticMappingId)
                .isEqualTo("real-estate.lease_end_date");
    }

    @Test
    @DisplayName("generated scope aliases cover all checklist domains")
    void load_generatedScopeAliases() {
        ChecklistScopeResolver resolver = new ChecklistScopeResolver(new ChecklistLoader(), null, null);
        ChecklistScopeItem firstGenerated = resolver.resolveAllStaticItems().get(0);
        ChecklistAliasIndex generated = new ChecklistAliasIndex();
        generated.setChecklistScopeResolver(resolver);
        generated.load();

        assertThat(generated.size()).isGreaterThan(index.size());
        assertThat(generated.coverageReport().generatedScopeEntries()).isGreaterThan(100);
        assertThat(generated.findByStaticMappingId(firstGenerated.slotId())).isPresent();
        assertThat(generated.resolve(firstGenerated.label(), List.of()))
                .isPresent()
                .get()
                .extracting(ChecklistAliasIndex.AliasEntry::staticMappingId)
                .isEqualTo(firstGenerated.slotId());
    }
}
