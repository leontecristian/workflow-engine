package io.github.leontecristian.workflow.core.dto.definition;

/** Provisional until the host commits deletion and durable summary audit together. */
public record DefinitionDeletion(DefinitionVersionSummary before, String operation, DefinitionManagementContext context) { }
