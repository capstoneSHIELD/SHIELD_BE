package org.example.shield.ai.dto;

/**
 * User-selected legal label metadata supplied by benchmark runners.
 *
 * <p>This is an experiment DTO, not a persisted domain model.
 */
public record ExperimentSelectedLabel(
        String nodeId,
        String l1,
        String l2,
        String l3
) {
}
