package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;

/** Host business authorization for exact-task ownership commands and their receipt recovery. */
public interface WorkflowTaskOwnershipAuthorization {
    boolean canAccess(TaskEligibilityContext context);
    boolean canAdministrativelyRelease(TaskEligibilityContext context);
}
