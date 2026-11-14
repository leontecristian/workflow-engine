package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
/** Host-owned access, administration and destination/frozen-basis suitability. */
public interface WorkflowTaskReassignmentAuthorization {
 boolean canAccess(TaskEligibilityContext context);
 boolean canReassign(TaskEligibilityContext context);
 boolean isValidTarget(TaskEligibilityContext context,TaskAssignmentTarget target);
}
