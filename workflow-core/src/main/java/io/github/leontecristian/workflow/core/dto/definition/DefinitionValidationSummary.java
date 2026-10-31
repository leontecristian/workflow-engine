package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionValidationSummary(
        DefinitionValidationStatus status,
        long contentRevision,
        String contentHash,
        String contextHash,
        java.time.Instant validatedAt,
        String validatedBy) {
}
