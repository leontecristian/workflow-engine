package io.github.leontecristian.workflow.core.dto.runtime;

/**
 * Exact filters except for case-insensitive task-name substring.
 * Null status means ALL. A null after position starts at the newest task.
 */
public record WorkflowPersonalTaskQuery(
        TaskStatus status,
        String taskTypeCode,
        AssignmentTargetKind assignmentKind,
        String assignmentRef,
        String assigneeRef,
        Long instanceId,
        WorkflowPersonalTaskCursor after,
        int limit,
        String taskName) {
}
