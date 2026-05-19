package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagRetrievalHint;
import org.example.shield.ai.dto.RagRetrievalHintFingerprint;
import org.example.shield.ai.dto.RagRetrievalHintStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalHintLifecycleServiceTest {

    private final RagRetrievalHintLifecycleService service = new RagRetrievalHintLifecycleService();

    @Test
    @DisplayName("fresh hint is usable when all lifecycle fingerprints match")
    void status_freshHintUsable() {
        RagRetrievalHintStatus status = service.status(hint("generator-v1"), fingerprint("generator-v1"));

        assertThat(status.usable()).isTrue();
        assertThat(status.staleReasons()).isEmpty();
        assertThat(service.freshHint(hint("generator-v1"), fingerprint("generator-v1"))).isPresent();
    }

    @Test
    @DisplayName("stale hint is rejected and callers can fall back to baseline retrieval")
    void status_staleHintRejected() {
        RagRetrievalHintStatus status = service.status(hint("generator-v1"), fingerprint("generator-v2"));

        assertThat(status.usable()).isFalse();
        assertThat(status.staleReasons()).contains("generator_version_changed");
        assertThat(service.freshHint(hint("generator-v1"), fingerprint("generator-v2"))).isEmpty();
    }

    @Test
    @DisplayName("missing fingerprint fields are treated as stale")
    void status_missingFieldRejected() {
        RagRetrievalHintStatus status = service.status(
                hint("generator-v1"),
                new RagRetrievalHintFingerprint(null, "ontology-v1", "mapping-v1", "generator-v1"));

        assertThat(status.usable()).isFalse();
        assertThat(status.staleReasons()).contains("evidence_modified_at_missing");
    }

    private RagRetrievalHint hint(String generatorVersion) {
        return new RagRetrievalHint(
                "law-001-02-02",
                List.of("LSI249999"),
                List.of("group:leasing"),
                List.of("보증금"),
                List.of("2024다12345"),
                "evidence-v1",
                "2026-05-19",
                "2026-05-19T00:00:00",
                "ontology-v1",
                "mapping-v1",
                generatorVersion);
    }

    private RagRetrievalHintFingerprint fingerprint(String generatorVersion) {
        return new RagRetrievalHintFingerprint(
                "2026-05-19T00:00:00",
                "ontology-v1",
                "mapping-v1",
                generatorVersion);
    }
}
