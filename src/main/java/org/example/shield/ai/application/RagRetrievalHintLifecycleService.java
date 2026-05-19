package org.example.shield.ai.application;

import org.example.shield.ai.dto.RagRetrievalHint;
import org.example.shield.ai.dto.RagRetrievalHintFingerprint;
import org.example.shield.ai.dto.RagRetrievalHintStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RagRetrievalHintLifecycleService {

    public RagRetrievalHintStatus status(RagRetrievalHint hint, RagRetrievalHintFingerprint current) {
        if (hint == null) {
            return RagRetrievalHintStatus.stale(List.of("hint_missing"));
        }
        if (current == null) {
            return RagRetrievalHintStatus.stale(List.of("fingerprint_missing"));
        }

        List<String> reasons = new ArrayList<>();
        compare(reasons, "evidence_modified_at", hint.evidenceModifiedAt(), current.evidenceModifiedAt());
        compare(reasons, "ontology_version", hint.ontologyVersion(), current.ontologyVersion());
        compare(reasons, "mapping_version", hint.mappingVersion(), current.mappingVersion());
        compare(reasons, "generator_version", hint.generatorVersion(), current.generatorVersion());

        return reasons.isEmpty()
                ? RagRetrievalHintStatus.fresh()
                : RagRetrievalHintStatus.stale(reasons);
    }

    public Optional<RagRetrievalHint> freshHint(RagRetrievalHint hint, RagRetrievalHintFingerprint current) {
        return status(hint, current).usable()
                ? Optional.of(hint)
                : Optional.empty();
    }

    private void compare(List<String> reasons, String field, String hintValue, String currentValue) {
        if (isBlank(hintValue) || isBlank(currentValue)) {
            reasons.add(field + "_missing");
            return;
        }
        if (!hintValue.equals(currentValue)) {
            reasons.add(field + "_changed");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
