package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;

/** Host business access and fresh cancellation privilege, evaluated separately. */
public interface WorkflowCancellationAuthorization {
    boolean canAccess(WorkflowCancellationAuthorizationContext context);
    boolean canCancel(WorkflowCancellationAuthorizationContext context);
}
