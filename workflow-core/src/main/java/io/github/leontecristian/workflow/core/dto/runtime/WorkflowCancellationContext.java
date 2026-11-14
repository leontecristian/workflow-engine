package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached pre-cancellation snapshots captured after the aggregate claim. */
public record WorkflowCancellationContext(CancelWorkflow command, WorkflowCommandContext commandContext,
        WorkflowInstanceView instance, WorkflowActivityView activity) { }
