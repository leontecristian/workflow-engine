package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;

/** Optional labels and bounded directory search; neither grants command permission. */
public interface WorkflowAssignmentDirectory {
    default WorkflowReassignmentTargetPage reassignmentTargets(TaskEligibilityContext context,
            AssignmentTargetKind kind, String query, String cursor, int limit) {
        throw new WorkflowRuntimeException(403, "REASSIGNMENT_FORBIDDEN");
    }

    default String assignmentLabel(WorkflowTaskView task) { return task.assignment().ref(); }
}
