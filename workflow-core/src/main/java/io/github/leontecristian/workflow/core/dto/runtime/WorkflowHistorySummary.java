package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowHistorySummary(
        long instanceId,
        long historySequence,
        String eventType,
        java.util.UUID commandId,
        String correlationId,
        ActorKind actorKind,
        String actorRef,
        java.time.Instant occurredAt,
        Long activityId,
        Long taskId,
        String sourceNodeKey,
        String targetNodeKey,
        String transitionKey,
        short detailsSchemaVersion) {
}
