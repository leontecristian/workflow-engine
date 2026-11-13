package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One command's ordered facts; no independently committed delivery or mutable singleton command state. */
final class RuntimeHistoryBatch {
    private final EntityManager entityManager;
    private final RuntimeJson json;
    private final long instanceId;
    private final long definitionVersionId;
    private final UUID commandId;
    private final WorkflowCommandContext context;
    private long sequence;
    private long utf8Bytes, utf16Bytes;
    private final List<WorkflowEvent> events = new ArrayList<>();
    RuntimeHistoryBatch(EntityManager entityManager, RuntimeJson json, long instanceId, long definitionVersionId,
                        UUID commandId, WorkflowCommandContext context) {
        this.entityManager = entityManager; this.json = json; this.instanceId = instanceId;
        this.definitionVersionId = definitionVersionId; this.commandId = commandId; this.context = context;
    }
    void beginAt(long previous) { this.sequence=previous; }
    long last() { return sequence; }
    long next(String type, Long activityId, Long taskId, MinimalWorkflowDefinition.Transition transition, Object details, Map<String,String> payload) {
        long next=RuntimeProgression.advance(sequence,1); append(next,type,activityId,taskId,transition,details,payload); return next;
    }
    void next(String type, Long activityId, Long taskId, Object details, Map<String,String> payload, short detailsVersion) {
        append(RuntimeProgression.advance(sequence,1),type,activityId,taskId,null,details,payload,detailsVersion);
    }
    void append(long sequence, String type, Long activityId, Long taskId,
                MinimalWorkflowDefinition.Transition transition, Object details, Map<String,String> payload) {
        short detailsVersion = switch (type) {
            case "TASK_CREATED" -> 4;
            case "TASK_CLAIMED", "TASK_RELEASED" -> 1;
            default -> 2;
        };
        append(sequence, type, activityId, taskId, transition, details, payload, detailsVersion);
    }
    void append(long sequence, String type, Long activityId, Long taskId,
                MinimalWorkflowDefinition.Transition transition, Object details, Map<String,String> payload,
                short detailsVersion) {
        RuntimeInputs.require(sequence==this.sequence+1 && events.size()<6000,io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
        String encoded=json.history(details);
        utf8Bytes+=encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; utf16Bytes+=encoded.length()*2L;
        if (utf8Bytes > RuntimeJson.COMMAND_HISTORY_UTF8_BYTES || utf16Bytes > RuntimeJson.COMMAND_HISTORY_UTF16_BYTES)
            throw io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.resultLimit("The cumulative command history exceeds its capacity.");
        this.sequence=sequence;
        WorkflowHistorySummary fact = new WorkflowHistorySummary(instanceId, sequence, type, commandId, context.correlationId(),
                context.actorKind(), context.actorRef(), context.occurredAt(), activityId, taskId,
                transition == null ? null : transition.from(), transition == null ? null : transition.to(),
                transition == null ? null : transition.key(), detailsVersion);
        RuntimeInputs.require(json.encode(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent(fact,encoded))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES,
                io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
        entityManager.persist(new WorkflowHistoryEntity(fact, encoded));
        var event=new WorkflowEvent((short) ("TASK_CREATED".equals(type) ? 3 : 1), instanceId + ":" + sequence, commandId, instanceId, definitionVersionId,
                sequence, type, context.actorKind(), context.actorRef(), context.occurredAt(), context.correlationId(),
                activityId, taskId, fact.sourceNodeKey(), fact.targetNodeKey(), fact.transitionKey(), payload);
        json.evidence(event); events.add(event);
    }
    List<WorkflowEvent> events() { return List.copyOf(events); }
}
