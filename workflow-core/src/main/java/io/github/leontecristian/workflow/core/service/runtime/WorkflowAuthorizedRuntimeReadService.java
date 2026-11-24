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
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
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

/**
 * Authorized runtime selection and advisory availability for a trusted actor. Construct explicitly
 * for consumers of authorized reads; ordinary core scanning needs no read-policy adapter.
 * Calls retain the raw query service's host transaction participation and snapshot semantics.
 */
public class WorkflowAuthorizedRuntimeReadService {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowRuntimeReadAuthorization authorization;
    private final WorkflowTaskEligibilityService eligibility;
    private final WorkflowAuthorizationSupport commandAuthorization;

    public WorkflowAuthorizedRuntimeReadService(WorkflowRuntimeQueryService reads,
            WorkflowRuntimeReadAuthorization authorization, WorkflowTaskEligibilityService eligibility,
            WorkflowAuthorizationSupport commandAuthorization) {
        this.reads = Objects.requireNonNull(reads);
        this.authorization = Objects.requireNonNull(authorization);
        this.eligibility = Objects.requireNonNull(eligibility);
        this.commandAuthorization = Objects.requireNonNull(commandAuthorization);
    }

    /** Usable before parsing protected collection filters. */
    public void requireMonitorAccess(String actorRef) {
        validateActorRef(actorRef);
        if (!authorization.canMonitor(actorRef)) throw new WorkflowRuntimeReadAccessException(Access.MONITOR);
    }

    /** Independent from monitor and exact-task visibility. */
    public void requireTaskAdministrationAccess(String actorRef) {
        validateActorRef(actorRef);
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
        validateActorRef(actorRef);
        return reads.listEligibleTasks(actorRef, afterId, limit, task -> canReadTask(actorRef, task));
    }

    /** Personal inbox, including explicitly selected closed work, newest creation first. */
    public WorkflowPersonalTaskPage listMyTasks(String actorRef, WorkflowPersonalTaskQuery query) {
        validateActorRef(actorRef);
        return reads.listPersonalTasks(actorRef, query, task -> canReadTask(actorRef, task));
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
        validateActorRef(actorRef);
        var instance = reads.findInstance(id).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(authorization.canReadInstance(actorRef, instance), Category.NOT_FOUND);
        return instance;
    }

    public WorkflowTaskView task(String actorRef, long id) {
        validateActorRef(actorRef);
        var task = reads.findTask(id).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(canReadTask(actorRef, task), Category.NOT_FOUND);
        return task;
    }

    public WorkflowActivityView activity(String actorRef, long id) {
        validateActorRef(actorRef);
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
                && commandAuthorization.supports(WorkflowOperation.CLAIM_TASK)
                && commandAuthorization.allowsTask(WorkflowScope.ACCESS_OWNERSHIP, context)
                && eligibility.isEligible(task, actorRef);
    }

    public boolean canReleaseTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN && shared(task) && actorRef.equals(task.assigneeRef())
                && commandAuthorization.supports(WorkflowOperation.RELEASE_TASK)
                && commandAuthorization.allowsTask(WorkflowScope.ACCESS_OWNERSHIP, context);
    }

    public boolean canAdministrativelyReleaseTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return task.status() == TaskStatus.OPEN && shared(task) && task.assigneeRef() != null
                && commandAuthorization.supports(WorkflowOperation.ADMIN_RELEASE_TASK)
                && commandAuthorization.allowsTask(WorkflowScope.ACCESS_OWNERSHIP, context)
                && commandAuthorization.allowsTask(WorkflowScope.ADMIN_RELEASE, context);
    }

    /** Operation permission used both by availability and the separate directory lookup gate. */
    public boolean canReassignTask(String actorRef, WorkflowTaskView task) {
        var context = taskContext(actorRef, task);
        return commandAuthorization.supports(WorkflowOperation.REASSIGN_TASK)
                && commandAuthorization.allowsTask(WorkflowScope.ACCESS_REASSIGNMENT, context)
                && commandAuthorization.allowsTask(WorkflowScope.REASSIGN, context);
    }

    /** Allows web to avoid obtaining a command context when this capability is absent. */
    public boolean hasCancellationAuthorization() {
        return commandAuthorization.supports(WorkflowOperation.CANCEL_WORKFLOW);
    }

    /** The caller first authorizes the instance; only this operation takes command context. */
    public boolean canCancelWorkflow(String actorRef, WorkflowInstanceView instance, WorkflowCommandContext context) {
        validateActorRef(actorRef);
        RuntimeInputs.require(instance != null, Category.INVALID_INPUT);
        if (instance.status() != InstanceStatus.ACTIVE || !hasCancellationAuthorization()) return false;
        RuntimeInputs.require(context != null && actorRef.equals(context.actorRef()), Category.HOST_CONFIGURATION);
        var cancellation = new WorkflowCancellationAuthorizationContext(instance.id(), instance.definitionVersionId(),
                instance.businessType(), instance.businessId(), context);
        return commandAuthorization.allowsCancellation(WorkflowScope.ACCESS_CANCELLATION, cancellation)
                && commandAuthorization.allowsCancellation(WorkflowScope.CANCEL, cancellation);
    }

    private static TaskEligibilityContext taskContext(String actorRef, WorkflowTaskView task) {
        validateActorRef(actorRef);
        RuntimeInputs.require(task != null, Category.INVALID_INPUT);
        return new TaskEligibilityContext(task, actorRef);
    }

    private static boolean shared(WorkflowTaskView task) { return task.assignment().kind() != AssignmentTargetKind.USER; }

    private static void validateActorRef(String actorRef) {
        RuntimeInputs.require(RuntimeInputs.isValidText(actorRef, 200, 200, true), Category.INVALID_INPUT);
    }
}
