package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowCommandContext(
        ActorKind actorKind,
        String actorRef,
        java.time.Instant occurredAt,
        String correlationId) {
}
