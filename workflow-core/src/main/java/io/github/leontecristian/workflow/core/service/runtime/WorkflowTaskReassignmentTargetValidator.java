package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;

/** Host validation of the proposed destination on a fresh authorized reassignment. */
public interface WorkflowTaskReassignmentTargetValidator {
    boolean isValidTarget(TaskEligibilityContext task, TaskAssignmentTarget target);
}
