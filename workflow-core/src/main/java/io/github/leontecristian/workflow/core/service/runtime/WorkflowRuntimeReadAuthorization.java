package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;

/**
 * Host-owned visibility decisions over loaded, detached resources. Actor references must come
 * from trusted server identity; validating a reference does not authenticate it. Failures must
 * remain failures rather than being converted to grants or empty successful reads.
 */
public interface WorkflowRuntimeReadAuthorization {
    boolean canMonitor(String actorRef);
    default boolean canAdministerTasks(String actorRef) { return false; }
    boolean canListInstance(String actorRef, WorkflowInstanceSummary instance);
    boolean canReadInstance(String actorRef, WorkflowInstanceView instance);
    boolean canReadTask(TaskEligibilityContext context);
    boolean canReadActivity(String actorRef, WorkflowActivityView activity);

    /** Advisory permission only; command execution retains its existing authorization boundary. */
    default boolean canCompleteTask(TaskEligibilityContext context) { return false; }
}
