package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionChange(
        java.util.Optional<DefinitionVersionSummary> before,
        DefinitionVersionSummary after,
        boolean changed,
        String operation,
        DefinitionManagementContext context) {
}
