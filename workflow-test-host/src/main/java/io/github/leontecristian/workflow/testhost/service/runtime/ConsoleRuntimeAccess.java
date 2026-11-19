package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Service;

@Service
public class ConsoleRuntimeAccess implements WorkflowRuntimeReadAuthorization {
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleContext context;
    private final WorkflowTaskEligibilityService eligibility;
    private final ConsoleTaskBusinessAccess businessAccess;

    public ConsoleRuntimeAccess(WorkflowRuntimeQueryService reads, ConsoleContext context,
            WorkflowTaskEligibilityService eligibility, ConsoleTaskBusinessAccess businessAccess) {
        this.reads = reads;
        this.context = context;
        this.eligibility = eligibility;
        this.businessAccess = businessAccess;
    }

    @Override public boolean canMonitor(String actorRef) { return context.isConsoleAdministrator(actorRef); }
    @Override public boolean canAdministerTasks(String actorRef) { return context.isConsoleAdministrator(actorRef); }
    @Override public boolean canListInstance(String actorRef, WorkflowInstanceSummary instance) {
        return context.isConsoleAdministrator(actorRef);
    }
    @Override public boolean canReadInstance(String actorRef, WorkflowInstanceView instance) {
        return context.isConsoleAdministrator(actorRef) || assigned(instance.id(), null, actorRef);
    }
    @Override public boolean canReadActivity(String actorRef, WorkflowActivityView activity) {
        return context.isConsoleAdministrator(actorRef) || assigned(activity.instanceId(), activity.id(), actorRef);
    }

    public WorkflowTaskView task(String actorRef, long id) {
        var task = reads.findTask(id).orElseThrow(ConsoleException::missing);
        if (!canReadTask(new TaskEligibilityContext(task, actorRef)))
            throw ConsoleException.missing();
        return task;
    }

    private boolean canAccessBusiness(TaskEligibilityContext taskContext) {
        try {
            return businessAccess.canAccess(taskContext);
        } catch (RuntimeException unavailable) {
            throw new WorkflowExecutionException(
                    WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED, unavailable);
        }
    }

    public boolean canReadTask(WorkflowTaskView task) {
        return canReadTask(new TaskEligibilityContext(task, context.actor()));
    }

    @Override public boolean canReadTask(TaskEligibilityContext taskContext) {
        var task = taskContext.task();
        String actor = taskContext.actorRef();
        if (context.isConsoleAdministrator(actor)) return true;
        if (task.status() == TaskStatus.OPEN) {
            if (!canAccessBusiness(taskContext)) return false;
            if (actor.equals(task.assigneeRef())) return true;
        }
        return canReadByAssignment(task, actor);
    }

    private boolean canReadByAssignment(WorkflowTaskView task, String actor) {
        if (context.isConsoleAdministrator(actor)) return true;
        if (task.assignment().kind() == AssignmentTargetKind.USER)
            return actor.equals(task.assignment().ref());
        if (task.status() == TaskStatus.OPEN)
            return eligibility.isEligible(task, actor);
        return task.status() == TaskStatus.COMPLETED && actor.equals(task.closedBy());
    }

    @Override public boolean canCompleteTask(TaskEligibilityContext taskContext) {
        // Sandbox supplies no extra completion veto beyond the core state/assignment policy.
        return true;
    }

    /** Bounded bulk authorization for graph links; avoids an exact-task query per displayed node. */
    public java.util.Set<Long> readableActivities(String actorRef, long instanceId, java.util.Set<Long> candidates) {
        if (context.isConsoleAdministrator(actorRef) || candidates.isEmpty()) return java.util.Set.copyOf(candidates);
        var visible = new java.util.HashSet<Long>();
        long cursor = 0;
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            for (var task : tasks.items())
                if (candidates.contains(task.activityId()) && canReadByAssignment(task, actorRef)) visible.add(task.activityId());
            if (tasks.nextAfterId() == null) return java.util.Set.copyOf(visible);
            cursor = tasks.nextAfterId();
        }
        throw new ConsoleException(422, "LIMIT_EXCEEDED");
    }

    public WorkflowInstanceView instance(long id) {
        return instance(context.actor(), id);
    }

    public WorkflowInstanceView instance(String actorRef, long id) {
        var instance = reads.findInstance(id).orElseThrow(ConsoleException::missing);
        try {
            if (!canReadInstance(actorRef, instance)) throw ConsoleException.missing();
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == WorkflowExecutionException.Category.LIMIT_EXCEEDED)
                throw new ConsoleException(422, "LIMIT_EXCEEDED");
            throw failure;
        }
        return instance;
    }

    public WorkflowActivityView activity(long id) {
        var activity = reads.findActivity(id).orElseThrow(ConsoleException::missing);
        try {
            if (!canReadActivity(context.actor(), activity)) throw ConsoleException.missing();
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == WorkflowExecutionException.Category.LIMIT_EXCEEDED)
                throw new ConsoleException(422, "LIMIT_EXCEEDED");
            throw failure;
        }
        return activity;
    }

    private boolean assigned(long instanceId, Long activityId, String actor) {
        long cursor = 0;
        // Includes completed tasks; reviewer access does not vanish on completion/retirement.
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            if (tasks.items().stream()
                    .anyMatch(
                            t ->
                                    (activityId == null || t.activityId() == activityId)
                                            && canReadByAssignment(t, actor))) return true;
            if (tasks.nextAfterId() == null) return false;
            cursor = tasks.nextAfterId();
        }
        throw new WorkflowExecutionException(WorkflowExecutionException.Category.LIMIT_EXCEEDED);
    }
}
