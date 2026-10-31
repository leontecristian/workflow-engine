package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowExecutionResult(
        java.util.UUID commandId,
        WorkflowOperation operation,
        WorkflowInstanceView instance,
        WorkflowActivityView activity,
        WorkflowTaskView task,
        long firstHistorySequence,
        long lastHistorySequence) {
}
