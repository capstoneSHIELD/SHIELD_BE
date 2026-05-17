package org.example.shield.ai.application;

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
    @DisplayName("alias YAML is loaded and static mapping id can be resolved")
    void load_findByStaticMappingId() {
        assertThat(index.size()).isGreaterThan(0);
        assertThat(index.findByStaticMappingId("real-estate.lease_end_date"))
                .isPresent()
                .get()
                .extracting(ChecklistAliasIndex.AliasEntry::label)
                .isEqualTo("계약 종료일");
    }

    @Test
    @DisplayName("dynamic slot label and keywords can resolve to a static alias")
    void resolve_dynamicLabel() {
        assertThat(index.resolve("임대차 종료 시점", List.of("전세 만료")))
                .isPresent()
                .get()
                .extracting(ChecklistAliasIndex.AliasEntry::staticMappingId)
                .isEqualTo("real-estate.lease_end_date");
    }
}
