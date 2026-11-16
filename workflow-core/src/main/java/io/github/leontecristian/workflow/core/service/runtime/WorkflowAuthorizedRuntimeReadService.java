package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowRuntimeReadAccessException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowRuntimeReadAccessException.Access;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Authorized runtime selection and advisory availability for a trusted actor. Construct explicitly
 * for consumers of authorized reads; ordinary core scanning needs no read-policy adapter.
 * Calls retain the raw query service's host transaction participation and snapshot semantics.
 */
public class WorkflowAuthorizedRuntimeReadService {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowRuntimeReadAuthorization authorization;
    private final WorkflowTaskEligibilityService eligibility;
    private final ObjectProvider<WorkflowTaskReassignmentAuthorization> reassignmentProviders;
    private final ObjectProvider<WorkflowCancellationAuthorization> cancellationProviders;

    public WorkflowAuthorizedRuntimeReadService(WorkflowRuntimeQueryService reads,
            WorkflowRuntimeReadAuthorization authorization, WorkflowTaskEligibilityService eligibility,
            ObjectProvider<WorkflowTaskReassignmentAuthorization> reassignmentProviders,
            ObjectProvider<WorkflowCancellationAuthorization> cancellationProviders) {
        this.reads = Objects.requireNonNull(reads);
        this.authorization = Objects.requireNonNull(authorization);
        this.eligibility = Objects.requireNonNull(eligibility);
        this.reassignmentProviders = Objects.requireNonNull(reassignmentProviders);
        this.cancellationProviders = Objects.requireNonNull(cancellationProviders);
    }

    /** Usable before parsing protected collection filters. */
    public void requireMonitorAccess(String actorRef) {
        actor(actorRef);
        if (!authorization.canMonitor(actorRef)) throw new WorkflowRuntimeReadAccessException(Access.MONITOR);
    }

    /** Independent from monitor and exact-task visibility. */
    public void requireTaskAdministrationAccess(String actorRef) {
        actor(actorRef);
        if (!authorization.canAdministerTasks(actorRef)) throw new WorkflowRuntimeReadAccessException(Access.ADMINISTRATION);
    }

    public WorkflowInstancePage listInstances(String actorRef, WorkflowInstanceQuery query) {
        requireMonitorAccess(actorRef);
        var page = reads.listInstances(query);
        return new WorkflowInstancePage(page.items().stream()
                .filter(item -> authorization.canListInstance(actorRef, item)).toList(), page.nextAfterId());
    }

    /** Personal eligible work, independent of ownership-operation permission. */
    public WorkflowTaskPage listMyTasks(String actorRef, long afterId, int limit) {
        actor(actorRef);
        return reads.listEligibleTasks(actorRef, afterId, limit, task -> canReadTask(actorRef, task));
    }

    public WorkflowTaskPage listInstanceTasks(String actorRef, long instanceId, long afterId, int limit) {
        instance(actorRef, instanceId);
        RuntimeInputs.validatePageRequest(afterId, limit);
        var visible = new ArrayList<WorkflowTaskView>();
        long cursor = afterId;
        int examined = 0;
        while (examined < 500) {
            var page = reads.listTasks(instanceId, cursor, Math.min(limit, 500 - examined));
            for (int index = 0; index < page.items().size(); index++) {
                var task = page.items().get(index);
                cursor = task.id();
                examined++;
                if (canReadTask(actorRef, task)) visible.add(task);
                if (visible.size() == limit || examined == 500) {
                    boolean more = index + 1 < page.items().size() || page.nextAfterId() != null;
                    return new WorkflowTaskPage(visible, more ? cursor : null);
                }
            }
            if (page.nextAfterId() == null) return new WorkflowTaskPage(visible, null);
            cursor = page.nextAfterId();
        }
        return new WorkflowTaskPage(visible, cursor);
    }

    public WorkflowTaskAdministrationPage listAdministrationTasks(String actorRef, WorkflowTaskAdministrationQuery query) {
        requireTaskAdministrationAccess(actorRef);
        return reads.listAdministrationTasks(query, item -> canReadTask(actorRef, item.task()));
    }

    public WorkflowInstanceView instance(String actorRef, long id) {
        actor(actorRef);
        var instance = reads.findInstance(id).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(authorization.canReadInstance(actorRef, instance), Category.NOT_FOUND);
        return instance;
    }

    public WorkflowTaskView task(String actorRef, long id) {
        actor(actorRef);
        var task = reads.findTask(id).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(canReadTask(actorRef, task), Category.NOT_FOUND);
        return task;
    }

    public WorkflowActivityView activity(String actorRef, long id) {
        actor(actorRef);
        var activity = reads.findActivity(id).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(authorization.canReadActivity(actorRef, activity), Category.NOT_FOUND);
        return activity;
    }

    /** Evaluates a freshly loaded task without re-fetching or caching the grant. */
    public boolean canReadTask(String actorRef, WorkflowTaskView task) {
        return authorization.canReadTask(taskContext(actorRef, task));
    }

    public boolean canCompleteTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN
                && (task.assigneeRef() == null || actorRef.equals(task.assigneeRef()))
                && (!shared(task) || task.assigneeRef() != null
                    || reads.taskClaimPolicy(task.instanceId(), task.id()) == TaskClaimPolicy.OPTIONAL)
                && authorization.canCompleteTask(context) && eligibility.isEligible(task, actorRef);
    }

    public boolean canClaimTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN && shared(task) && task.assigneeRef() == null
                && eligibility.canAccessOwnership(context) && eligibility.isEligible(task, actorRef);
    }

    public boolean canReleaseTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN && shared(task) && actorRef.equals(task.assigneeRef())
                && eligibility.canAccessOwnership(context);
    }

    public boolean canAdministrativelyReleaseTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN && shared(task) && task.assigneeRef() != null
                && eligibility.canAccessOwnership(context) && eligibility.canAdministrativelyRelease(context);
    }

    /** Operation permission used both by availability and the separate directory lookup gate. */
    public boolean canReassignTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        var adapters = reassignmentProviders.stream().toList();
        if (adapters.isEmpty()) return false;
        RuntimeInputs.require(adapters.size() == 1, Category.HOST_CONFIGURATION);
        try { return adapters.get(0).canAccess(context) && adapters.get(0).canReassign(context); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure); }
    }

    /** Allows web to avoid obtaining a command context when this capability is absent. */
    public boolean hasCancellationAuthorization() {
        var adapters = cancellationProviders.stream().toList();
        RuntimeInputs.require(adapters.size() <= 1, Category.HOST_CONFIGURATION);
        return !adapters.isEmpty();
    }

    /** The caller first authorizes the instance; only this operation takes command context. */
    public boolean canCancelWorkflow(String actorRef, WorkflowInstanceView instance, WorkflowCommandContext context) {
        actor(actorRef);
        RuntimeInputs.require(instance != null, Category.INVALID_INPUT);
        if (instance.status() != InstanceStatus.ACTIVE) return false;
        var adapters = cancellationProviders.stream().toList();
        if (adapters.isEmpty()) return false;
        RuntimeInputs.require(adapters.size() == 1 && context != null && actorRef.equals(context.actorRef()), Category.HOST_CONFIGURATION);
        var cancellation = new WorkflowCancellationAuthorizationContext(instance.id(), instance.definitionVersionId(),
                instance.businessType(), instance.businessId(), context);
        try { return adapters.get(0).canAccess(cancellation) && adapters.get(0).canCancel(cancellation); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure); }
    }

    private static TaskEligibilityContext taskContext(String actorRef, WorkflowTaskView task) {
        actor(actorRef);
        RuntimeInputs.require(task != null, Category.INVALID_INPUT);
        return new TaskEligibilityContext(task, actorRef);
    }

    private static boolean shared(WorkflowTaskView task) { return task.assignment().kind() != AssignmentTargetKind.USER; }

    private static void actor(String actorRef) {
        RuntimeInputs.require(RuntimeInputs.isValidText(actorRef, 200, 200, true), Category.INVALID_INPUT);
    }
}
