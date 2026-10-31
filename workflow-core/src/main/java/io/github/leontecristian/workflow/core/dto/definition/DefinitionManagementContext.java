package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionManagementContext(String actorRef, java.time.Instant occurredAt) {
}
