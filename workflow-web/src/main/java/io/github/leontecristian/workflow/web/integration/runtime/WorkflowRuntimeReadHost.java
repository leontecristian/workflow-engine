package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationPage;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage;
/** Required policy boundary: pages are already authorized and preserve source continuation. */
public interface WorkflowRuntimeReadHost {
    /** Separate collection grant; ordinary task visibility never grants administration. */
    default void requireTaskAdministrationAccess() {
        throw new io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException(403, "ADMINISTRATION_FORBIDDEN");
    }
    /** Return only authorized metadata, retaining the source continuation even for empty pages. */
    default WorkflowTaskAdministrationPage listAdministrationTasks(WorkflowTaskAdministrationQuery query) {
        throw new io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException(403, "ADMINISTRATION_FORBIDDEN");
    }
    default boolean canReassignTask(WorkflowTaskView task) { return false; }
    /** Called after exact-task access and reassignment permission. Revalidate on execution. */
    default WorkflowReassignmentTargetPage reassignmentTargets(WorkflowTaskView task, AssignmentTargetKind kind,
            String query, String cursor, int limit) {
        throw new io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException(403, "REASSIGNMENT_FORBIDDEN");
    }
    void requireMonitorAccess();
    WorkflowInstancePage listInstances(WorkflowInstanceQuery query);
    WorkflowTaskPage listAssignedTasks(long afterId, int limit);
    WorkflowTaskPage listTasks(long instanceId, long afterId, int limit);
    WorkflowInstanceView instance(long id);
    WorkflowTaskView task(long id);
    WorkflowActivityView activity(long id);
    /** Advisory permission only; defaults to unavailable for read-only hosts. */
    default boolean canCancelWorkflow(WorkflowInstanceView instance) { return false; }
    /** Presentation hints only; command authorization is always checked again. */
    default boolean canCompleteTask(WorkflowTaskView task) { return false; }
    default boolean canClaimTask(WorkflowTaskView task) { return false; }
    default boolean canReleaseTask(WorkflowTaskView task) { return false; }
    default boolean canAdministrativelyReleaseTask(WorkflowTaskView task) { return false; }
    default String assignmentLabel(WorkflowTaskView task) { return task.assignment().ref(); }
    /** Exact-task filtering is required even when the containing activity is readable. */
    default boolean canReadTask(WorkflowTaskView task) {
        try { task(task.id()); return true; }
        catch (io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException denied) {
            if (denied.status() == 404) return false;
            throw denied;
        }
    }
}
