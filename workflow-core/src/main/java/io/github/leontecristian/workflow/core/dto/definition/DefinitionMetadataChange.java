package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionMetadataChange(
        java.util.Optional<DefinitionView> before,
        DefinitionView after,
        boolean changed,
        String operation,
        DefinitionManagementContext context) {
}
