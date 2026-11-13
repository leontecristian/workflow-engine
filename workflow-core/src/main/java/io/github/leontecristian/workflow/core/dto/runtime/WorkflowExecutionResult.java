package io.github.leontecristian.workflow.core.dto.runtime;

/**
 * Detached command snapshot, provisional until the host transaction commits. Start identifies the
 * first entered activity and its task-key-first representative task. Completion identifies the
 * submitted task and its owning activity (which may remain ACTIVE for ALL contributions), even when the
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
