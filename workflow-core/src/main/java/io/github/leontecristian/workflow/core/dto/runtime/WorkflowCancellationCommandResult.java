package io.github.leontecristian.workflow.core.dto.runtime;

public record WorkflowCancellationCommandResult(boolean replayed, WorkflowCancellationResult execution) { }
