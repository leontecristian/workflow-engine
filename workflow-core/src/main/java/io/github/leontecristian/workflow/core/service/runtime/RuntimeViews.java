package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.repository.runtime.RuntimeHistoryRow;
import io.github.leontecristian.workflow.core.repository.runtime.RuntimeInstanceRow;
import io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RuntimeViews {
    private RuntimeViews() { }
    static Instant instant(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC); }
    static WorkflowInstanceSummary instanceSummary(RuntimeInstanceRow row) {
        return new WorkflowInstanceSummary(row.getId(), row.getDefinitionVersionId(), row.getDefinitionKey(),
                row.getDefinitionVersion(), row.getStatus(), row.getCurrentNodeKey(), row.getResultKey(),
                row.getBusinessType(), row.getBusinessId(), instant(row.getStartedAt()), row.getStartedBy(),
                instant(row.getEndedAt()), row.getLastHistorySequence(), row.getLockVersion());
    }
    static WorkflowInstanceView instance(WorkflowInstanceEntity entity, ExecutableDefinition definition) {
        return instance(entity, definition.key(), definition.version(), definition.contentHash());
    }
    static WorkflowInstanceView instance(WorkflowInstanceEntity entity, io.github.leontecristian.workflow.core.dto.definition.document.PinnedDefinitionIdentity definition) {
        return instance(entity, definition.key(), definition.version(), definition.contentHash());
    }
    private static WorkflowInstanceView instance(WorkflowInstanceEntity entity, String key, int version, String hash) {
        return new WorkflowInstanceView(entity.getId(), entity.getDefinitionVersionId(), key, version,
                hash, entity.getBusinessType(), entity.getBusinessId(), entity.getCurrentNodeKey(),
                entity.getStatus(), entity.getResultKey(), instant(entity.getStartedAt()), entity.getStartedBy(),
                instant(entity.getEndedAt()), entity.getEndedBy(), entity.getTerminationReason(), entity.getLastHistorySequence(), entity.getLockVersion());
    }
    static WorkflowActivityView activity(WorkflowActivityEntity entity) {
        return new WorkflowActivityView(entity.getId(), entity.getInstanceId(), entity.getNodeKey(), entity.getActivationSequence(),
                entity.getTaskTypeId(), entity.getCompletionPolicy(), entity.getContributionOutcomeKey(), entity.getRequiredTaskCount(),
                entity.getReviewRevisionRef(), entity.getStatus(), entity.getOutcomeKey(), instant(entity.getEnteredAt()),
                instant(entity.getClosedAt()), entity.getClosedBy(), entity.getClosureReason());
    }
    static WorkflowTaskView task(WorkflowTaskEntity entity, WorkflowActivityEntity activity) {
        return new WorkflowTaskView(entity.getId(), entity.getActivityId(), activity.getInstanceId(), activity.getTaskTypeId(),
                entity.getTaskKey(), entity.getAssignment(), entity.getStatus(), entity.getOutcomeKey(),
                instant(entity.getCreatedAt()), instant(entity.getDueAt()), instant(entity.getClosedAt()), entity.getClosedBy(), entity.getClosureReason());
    }
    static WorkflowTaskView task(RuntimeTaskRow row) {
        return new WorkflowTaskView(row.getId(), row.getActivityId(), row.getInstanceId(), row.getTaskTypeId(),
                row.getTaskKey(), new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(row.getAssignmentKind(), row.getAssignmentRef()), row.getStatus(), row.getOutcomeKey(),
                instant(row.getCreatedAt()), instant(row.getDueAt()), instant(row.getClosedAt()),
                row.getClosedBy(), row.getClosureReason());
    }
    static WorkflowHistorySummary history(WorkflowHistoryEntity entity) {
        return new WorkflowHistorySummary(entity.getId().getInstanceId(), entity.getId().getSequenceNumber(), entity.getEventType(),
                entity.getCommandId(), entity.getCorrelationId(), entity.getActorKind(), entity.getActorRef(), instant(entity.getOccurredAt()),
                entity.getActivityId(), entity.getTaskId(), entity.getSourceNodeKey(), entity.getTargetNodeKey(), entity.getTransitionKey(), entity.getDetailsSchemaVersion());
    }
    static WorkflowHistorySummary history(RuntimeHistoryRow entity) {
        return new WorkflowHistorySummary(entity.getInstanceId(), entity.getHistorySequence(), entity.getEventType(),
                entity.getCommandId(), entity.getCorrelationId(), entity.getActorKind(), entity.getActorRef(), instant(entity.getOccurredAt()),
                entity.getActivityId(), entity.getTaskId(), entity.getSourceNodeKey(), entity.getTargetNodeKey(), entity.getTransitionKey(), entity.getDetailsSchemaVersion());
    }
}
