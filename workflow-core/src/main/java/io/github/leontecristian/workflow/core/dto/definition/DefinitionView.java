package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionView(
        long id,
        String key,
        String name,
        String description,
        java.time.Instant createdAt,
        String createdBy,
        long lockVersion) {
}
