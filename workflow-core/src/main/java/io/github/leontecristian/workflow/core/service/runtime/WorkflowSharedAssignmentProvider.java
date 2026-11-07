package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;

/** Host-owned authoritative, read-only membership lookup. Failures must throw, never allow. */
public interface WorkflowSharedAssignmentProvider {
    boolean targetExists(AssignmentContext context, TaskAssignmentTarget target);
    boolean isCurrentMember(TaskEligibilityContext context);
}
