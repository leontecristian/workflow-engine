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
    void append(long sequence, String type, Long activityId, Long taskId,
                MinimalWorkflowDefinition.Transition transition, Object details, Map<String,String> payload) {
        RuntimeInputs.require(sequence==this.sequence+1 && events.size()<6000,io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
        String encoded=json.history(details);
        utf8Bytes+=encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; utf16Bytes+=encoded.length()*2L;
        RuntimeInputs.require(utf8Bytes<=16*1024*1024 && utf16Bytes<=16*1024*1024,io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
        this.sequence=sequence;
        WorkflowHistorySummary fact = new WorkflowHistorySummary(instanceId, sequence, type, commandId, context.correlationId(),
                context.actorKind(), context.actorRef(), context.occurredAt(), activityId, taskId,
                transition == null ? null : transition.from(), transition == null ? null : transition.to(),
                transition == null ? null : transition.key(), (short) ("TASK_CREATED".equals(type) ? 3 : 2));
        RuntimeInputs.require(json.encode(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent(fact,encoded))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=8*1024*1024,
                io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
        entityManager.persist(new WorkflowHistoryEntity(fact, encoded));
        var event=new WorkflowEvent((short) ("TASK_CREATED".equals(type) ? 2 : 1), instanceId + ":" + sequence, commandId, instanceId, definitionVersionId,
                sequence, type, context.actorKind(), context.actorRef(), context.occurredAt(), context.correlationId(),
                activityId, taskId, fact.sourceNodeKey(), fact.targetNodeKey(), fact.transitionKey(), payload);
        json.evidence(event); events.add(event);
    }
    List<WorkflowEvent> events() { return List.copyOf(events); }
}
