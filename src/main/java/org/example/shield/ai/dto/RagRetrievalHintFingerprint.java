package org.example.shield.ai.dto;

public record RagRetrievalHintFingerprint(
        String evidenceModifiedAt,
        String ontologyVersion,
        String mappingVersion,
        String generatorVersion
) {
}
