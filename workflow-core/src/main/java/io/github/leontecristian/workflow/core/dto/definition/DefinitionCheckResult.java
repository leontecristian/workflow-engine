package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionCheckResult(DefinitionChange change, DefinitionValidationReport report, boolean published) {
}
