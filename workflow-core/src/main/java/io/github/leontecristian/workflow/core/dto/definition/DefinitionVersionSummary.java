package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionVersionSummary(
        long id,
        long definitionId,
        String key,
        int version,
        short schemaVersion,
        DefinitionStatus status,
        long contentRevision,
        long lockVersion,
        String contentHash,
        String canonicalizationVersion,
        java.time.Instant createdAt,
        String createdBy,
        java.time.Instant updatedAt,
        String updatedBy,
        java.time.Instant publishedAt,
        String publishedBy,
        java.time.Instant retiredAt,
        String retiredBy,
        DefinitionValidationSummary validation) {
}
