package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record CompleteWorkflowTask(
        java.util.UUID commandId,
        long instanceId,
        long taskId,
        long expectedLockVersion,
        String outcomeKey,
        String comment) {
}
