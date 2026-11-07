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
}
