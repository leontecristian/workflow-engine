package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowTaskView(
        long id,
        long activityId,
        long instanceId,
        long taskTypeId,
        String taskKey,
        String assignedUserRef,
        TaskStatus status,
        String outcomeKey,
        java.time.Instant createdAt,
        java.time.Instant dueAt,
        java.time.Instant closedAt,
        String closedBy,
        TaskClosureReason closureReason) {
}
