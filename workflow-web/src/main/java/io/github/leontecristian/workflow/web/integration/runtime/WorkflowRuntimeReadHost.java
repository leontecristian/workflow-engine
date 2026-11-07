package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
/** Required policy boundary: pages are already authorized and preserve source continuation. */
public interface WorkflowRuntimeReadHost {
    void requireMonitorAccess();
    WorkflowInstancePage listInstances(WorkflowInstanceQuery query);
    WorkflowTaskPage listAssignedTasks(long afterId, int limit);
    WorkflowTaskPage listTasks(long instanceId, long afterId, int limit);
    WorkflowInstanceView instance(long id);
    WorkflowTaskView task(long id);
    WorkflowActivityView activity(long id);
    /** Presentation hints only; command authorization is always checked again. */
    default boolean canCompleteTask(WorkflowTaskView task) { return false; }
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
