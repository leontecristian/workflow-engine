package io.github.leontecristian.workflow.core.dto.runtime;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
/**
 * Durable handoff of a workflow history fact to the host.
 * See the <a href="doc-files/workflow-events.html">workflow event catalog</a> for
 * envelope fields, event versions, triggers, payload keys and consumer examples.
 */
public record WorkflowEvent(
        short eventSchemaVersion,
        String eventId,
        UUID commandId,
        long instanceId,
        long definitionVersionId,
        long historySequence,
        String eventType,
        ActorKind actorKind,
        String actorRef,
        Instant occurredAt,
        String correlationId,
        Long activityId,
        Long taskId,
        String sourceNodeKey,
        String targetNodeKey,
        String transitionKey,
        Map<String, String> payload
) {
    public WorkflowEvent {
        payload = Map.copyOf(payload);
    }
}
