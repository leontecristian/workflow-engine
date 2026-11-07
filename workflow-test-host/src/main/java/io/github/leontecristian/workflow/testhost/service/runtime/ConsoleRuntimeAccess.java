package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Service;

@Service
public class ConsoleRuntimeAccess {
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleContext context;
    private final WorkflowTaskEligibilityService eligibility;

    public ConsoleRuntimeAccess(WorkflowRuntimeQueryService reads, ConsoleContext context,
            WorkflowTaskEligibilityService eligibility) {
        this.reads = reads;
        this.context = context;
        this.eligibility = eligibility;
    }

    public WorkflowTaskView task(long id) {
        var task = reads.findTask(id).orElseThrow(ConsoleException::missing);
        if (!canReadTask(task))
            throw ConsoleException.missing();
        return task;
    }

    public boolean canReadTask(WorkflowTaskView task) {
        if (context.admin()) return true;
        if (task.assignment().kind() == AssignmentTargetKind.USER)
            return context.actor().equals(task.assignment().ref());
        if (task.status() == TaskStatus.OPEN)
            return eligibility.isEligible(task, context.actor());
        return task.status() == TaskStatus.COMPLETED
                && context.actor().equals(task.closedBy());
    }

    public boolean canCompleteTask(WorkflowTaskView task) {
        return task.status() == TaskStatus.OPEN
                && eligibility.isEligible(task, context.actor());
    }

    /** Bounded bulk authorization for graph links; avoids an exact-task query per displayed node. */
    public java.util.Set<Long> readableActivities(long instanceId, java.util.Set<Long> candidates) {
        if (context.admin() || candidates.isEmpty()) return java.util.Set.copyOf(candidates);
        var visible = new java.util.HashSet<Long>();
        long cursor = 0;
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            for (var task : tasks.items())
                if (candidates.contains(task.activityId()) && canReadTask(task)) visible.add(task.activityId());
            if (tasks.nextAfterId() == null) return java.util.Set.copyOf(visible);
            cursor = tasks.nextAfterId();
        }
        throw new ConsoleException(422, "LIMIT_EXCEEDED");
    }

    public WorkflowInstanceView instance(long id) {
        var instance = reads.findInstance(id).orElseThrow(ConsoleException::missing);
        if (!context.admin()) assigned(id, null);
        return instance;
    }

    public WorkflowActivityView activity(long id) {
        var activity = reads.findActivity(id).orElseThrow(ConsoleException::missing);
        if (!context.admin()) assigned(activity.instanceId(), id);
        return activity;
    }

    private void assigned(long instanceId, Long activityId) {
        long cursor = 0;
        // Includes completed tasks; reviewer access does not vanish on completion/retirement.
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            if (tasks.items().stream()
                    .anyMatch(
                            t ->
                                    (activityId == null || t.activityId() == activityId)
                                            && canReadTask(t))) return;
            if (tasks.nextAfterId() == null) throw ConsoleException.missing();
            cursor = tasks.nextAfterId();
        }
        throw new ConsoleException(422, "LIMIT_EXCEEDED");
    }
}
