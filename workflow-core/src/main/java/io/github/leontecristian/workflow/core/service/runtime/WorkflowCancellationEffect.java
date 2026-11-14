package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationContext;

/** Participates in the existing host transaction; no independent commit, retry or external delivery. */
@FunctionalInterface
public interface WorkflowCancellationEffect {
    void apply(WorkflowCancellationContext context);
}
