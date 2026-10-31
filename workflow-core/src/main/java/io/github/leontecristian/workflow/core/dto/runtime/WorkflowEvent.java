package io.github.leontecristian.workflow.core.dto.runtime;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
/** Durable-handoff fact with a deliberately limited payload; no comments or business material. */
public record WorkflowEvent(short eventSchemaVersion, String eventId, UUID commandId, long instanceId,
        long definitionVersionId, long historySequence, String eventType, ActorKind actorKind,
        String actorRef, Instant occurredAt, String correlationId, Long activityId, Long taskId,
        String sourceNodeKey, String targetNodeKey, String transitionKey, Map<String,String> payload) {
    public WorkflowEvent { payload = Map.copyOf(payload); }
}
