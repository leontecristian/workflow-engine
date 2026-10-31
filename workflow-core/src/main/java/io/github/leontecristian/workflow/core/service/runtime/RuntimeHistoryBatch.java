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
    private final List<WorkflowEvent> events = new ArrayList<>();
    RuntimeHistoryBatch(EntityManager entityManager, RuntimeJson json, long instanceId, long definitionVersionId,
                        UUID commandId, WorkflowCommandContext context) {
        this.entityManager = entityManager; this.json = json; this.instanceId = instanceId;
        this.definitionVersionId = definitionVersionId; this.commandId = commandId; this.context = context;
    }
    void append(long sequence, String type, Long activityId, Long taskId,
                MinimalWorkflowDefinition.Transition transition, Object details, Map<String,String> payload) {
        WorkflowHistorySummary fact = new WorkflowHistorySummary(instanceId, sequence, type, commandId, context.correlationId(),
                context.actorKind(), context.actorRef(), context.occurredAt(), activityId, taskId,
                transition == null ? null : transition.from(), transition == null ? null : transition.to(),
                transition == null ? null : transition.key(), (short) 1);
        entityManager.persist(new WorkflowHistoryEntity(fact, json.evidence(details)));
        events.add(new WorkflowEvent((short) 1, instanceId + ":" + sequence, commandId, instanceId, definitionVersionId,
                sequence, type, context.actorKind(), context.actorRef(), context.occurredAt(), context.correlationId(),
                activityId, taskId, fact.sourceNodeKey(), fact.targetNodeKey(), fact.transitionKey(), payload));
    }
    List<WorkflowEvent> events() { return List.copyOf(events); }
}
