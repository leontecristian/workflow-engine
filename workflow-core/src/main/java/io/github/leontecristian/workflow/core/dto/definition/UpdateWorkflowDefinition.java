package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record UpdateWorkflowDefinition(
        long definitionId,
        long expectedLockVersion,
        String name,
        String description) {
}
