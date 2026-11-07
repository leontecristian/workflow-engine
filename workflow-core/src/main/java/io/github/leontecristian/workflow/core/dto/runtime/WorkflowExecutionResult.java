package io.github.leontecristian.workflow.core.dto.runtime;

/**
 * Detached command snapshot, provisional until the host transaction commits. Start identifies the
 * first entered activity/task; completion identifies the completed activity/task, even when the
 * resulting instance waits at a successor. Discover current work through authorized reads. Replay
 * retains this original snapshot after later commands advance the instance.
 */
public record WorkflowExecutionResult(
        java.util.UUID commandId,
        WorkflowOperation operation,
        WorkflowInstanceView instance,
        WorkflowActivityView activity,
        WorkflowTaskView task,
        long firstHistorySequence,
        long lastHistorySequence) {
}
