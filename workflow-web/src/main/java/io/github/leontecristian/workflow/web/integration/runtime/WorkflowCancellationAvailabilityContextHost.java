package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;

/** Optional trusted operation context, requested only for cancellation availability. */
public interface WorkflowCancellationAvailabilityContextHost {
    WorkflowCommandContext currentCancellationContext();
}
