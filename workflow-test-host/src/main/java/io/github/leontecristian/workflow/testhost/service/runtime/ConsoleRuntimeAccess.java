package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
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
    private final ConsoleTaskOwnershipAuthorization ownership;
    private final ConsoleTaskReassignmentAuthorization reassignment;
    private final io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings settings;

    public ConsoleRuntimeAccess(WorkflowRuntimeQueryService reads, ConsoleContext context,
            WorkflowTaskEligibilityService eligibility, ConsoleTaskOwnershipAuthorization ownership,
            ConsoleTaskReassignmentAuthorization reassignment,
            io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings settings) {
        this.reads = reads;
        this.context = context;
        this.eligibility = eligibility;
        this.ownership = ownership;
        this.reassignment = reassignment;
        this.settings = settings;
    }

    public WorkflowTaskView task(long id) {
        var task = reads.findTask(id).orElseThrow(ConsoleException::missing);
        if (!canReadTask(task))
            throw ConsoleException.missing();
        return task;
    }

    public boolean canReadTask(WorkflowTaskView task) {
        if (task.status() == TaskStatus.OPEN) {
            if (!canAccessBusiness(task)) return false;
            if (context.actor().equals(task.assigneeRef())) return true;
        }
        return canReadByAssignment(task);
    }

    public boolean canAccessBusiness(WorkflowTaskView task) {
        try {
            return ownership.canAccess(new TaskEligibilityContext(task, context.actor()));
        } catch (RuntimeException unavailable) {
            throw new WorkflowExecutionException(
                    WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED, unavailable);
        }
    }

    private boolean canReadByAssignment(WorkflowTaskView task) {
        if (context.admin()) return true;
        if (task.assignment().kind() == AssignmentTargetKind.USER)
            return context.actor().equals(task.assignment().ref());
        if (task.status() == TaskStatus.OPEN)
            return eligibility.isEligible(task, context.actor());
        return task.status() == TaskStatus.COMPLETED
                && context.actor().equals(task.closedBy());
    }

    public boolean canReassignTask(WorkflowTaskView task) {
        var taskContext = new TaskEligibilityContext(task, context.actor());
        try { return reassignment.canAccess(taskContext) && reassignment.canReassign(taskContext); }
        catch (RuntimeException failure) {
            throw new WorkflowExecutionException(WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED, failure);
        }
    }

    public io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage reassignmentTargets(
            WorkflowTaskView task, AssignmentTargetKind kind, String query, String cursor, int limit) {
        task(task.id());
        if (!canReassignTask(task)) throw new ConsoleException(403, "REASSIGNMENT_FORBIDDEN");
        if (kind == null || limit < 1 || limit > 50 || query != null && query.length() > 200)
            throw new ConsoleException(400, "INVALID_INPUT");
        // Opaque continuation is an exact fixture reference, never a directory-wide browser cache.
        var candidates = new java.util.TreeMap<String, String>();
        if (kind == AssignmentTargetKind.USER) {
            for (String ref : java.util.List.of(settings.admin, settings.reviewer, settings.reviewer2)) candidates.put(ref, ref);
        } else if (kind == AssignmentTargetKind.GROUP) candidates.put(SandboxSharedAssignmentProvider.FINANCE.ref(), "Finance");
        else candidates.put(SandboxSharedAssignmentProvider.APPROVER.ref(), "Approver");
        if (cursor != null && !candidates.containsKey(cursor)) throw new ConsoleException(400, "INVALID_INPUT");
        String search = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        var selected = candidates.entrySet().stream()
                .filter(entry -> cursor == null || entry.getKey().compareTo(cursor) > 0)
                .filter(entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT).contains(search)
                        || entry.getValue().toLowerCase(java.util.Locale.ROOT).contains(search))
                .limit(limit + 1L).toList();
        boolean more = selected.size() > limit;
        var items = selected.stream().limit(limit)
                .map(entry -> new io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTarget(entry.getKey(), entry.getValue())).toList();
        return new io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage(items,
                more ? items.get(items.size() - 1).ref() : null);
    }

    public boolean canCompleteTask(WorkflowTaskView task) {
        String actor = context.actor();
        return task.status() == TaskStatus.OPEN
                && (task.assigneeRef() == null || actor.equals(task.assigneeRef()))
                && eligibility.isEligible(task, actor);
    }

    public boolean canClaimTask(WorkflowTaskView task) {
        var taskContext = new TaskEligibilityContext(task, context.actor());
        return eligibility.canAccessOwnership(taskContext) && eligibility.isEligible(task, taskContext.actorRef());
    }

    public boolean canReleaseTask(WorkflowTaskView task) {
        var taskContext = new TaskEligibilityContext(task, context.actor());
        return taskContext.actorRef().equals(task.assigneeRef()) && eligibility.canAccessOwnership(taskContext);
    }

    public boolean canAdministrativelyReleaseTask(WorkflowTaskView task) {
        var taskContext = new TaskEligibilityContext(task, context.actor());
        return eligibility.canAccessOwnership(taskContext) && eligibility.canAdministrativelyRelease(taskContext);
    }

    /** Bounded bulk authorization for graph links; avoids an exact-task query per displayed node. */
    public java.util.Set<Long> readableActivities(long instanceId, java.util.Set<Long> candidates) {
        if (context.admin() || candidates.isEmpty()) return java.util.Set.copyOf(candidates);
        var visible = new java.util.HashSet<Long>();
        long cursor = 0;
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            for (var task : tasks.items())
                if (candidates.contains(task.activityId()) && canReadByAssignment(task)) visible.add(task.activityId());
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
                                            && canReadByAssignment(t))) return;
            if (tasks.nextAfterId() == null) throw ConsoleException.missing();
            cursor = tasks.nextAfterId();
        }
        throw new ConsoleException(422, "LIMIT_EXCEEDED");
    }
}
