package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Current assignment eligibility only; host business visibility and permission remain additional. */
@Service
public class WorkflowTaskEligibilityService {
    private final List<WorkflowSharedAssignmentProvider> providers;
    private final ObjectProvider<WorkflowTaskOwnershipAuthorization> ownershipProviders;

    public WorkflowTaskEligibilityService(ObjectProvider<WorkflowSharedAssignmentProvider> providers,
            ObjectProvider<WorkflowTaskOwnershipAuthorization> ownershipProviders) {
        this.providers = providers.stream().toList();
        this.ownershipProviders = ownershipProviders;
    }

    public boolean canAccessOwnership(TaskEligibilityContext context) {
        return authorizeOwnership(context, false, WorkflowTaskOwnershipAuthorization::canAccess);
    }

    public boolean canAdministrativelyRelease(TaskEligibilityContext context) {
        return authorizeOwnership(context, false, WorkflowTaskOwnershipAuthorization::canAdministrativelyRelease);
    }

    void requireOwnershipAccess(TaskEligibilityContext context) {
        RuntimeInputs.require(authorizeOwnership(context, true, WorkflowTaskOwnershipAuthorization::canAccess),
                Category.NOT_FOUND);
    }

    private boolean authorizeOwnership(TaskEligibilityContext context, boolean required,
            java.util.function.BiPredicate<WorkflowTaskOwnershipAuthorization, TaskEligibilityContext> decision) {
        var adapters = ownershipProviders.stream().toList();
        if (!required && adapters.isEmpty()) return false;
        RuntimeInputs.require(adapters.size() == 1, Category.HOST_CONFIGURATION);
        try {
            return decision.test(adapters.get(0), context);
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure);
        }
    }

    public boolean isEligible(WorkflowTaskView task, String actorRef) {
        RuntimeInputs.require(task != null && RuntimeInputs.text(actorRef, 200, 200, true), Category.INVALID_INPUT);
        if (task.assignment().kind() == AssignmentTargetKind.USER) return task.assignment().ref().equals(actorRef);
        var provider = provider();
        try { return provider.isCurrentMember(new TaskEligibilityContext(task, actorRef)); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.ASSIGNMENT_LOOKUP_FAILED, failure); }
    }

    void requireTarget(AssignmentContext context, TaskAssignmentTarget target) {
        if (target.kind() == AssignmentTargetKind.USER) return;
        var provider = provider();
        boolean exists;
        try { exists = provider.targetExists(context, target); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.ASSIGNMENT_LOOKUP_FAILED, failure); }
        RuntimeInputs.require(exists, Category.INVALID_ASSIGNMENT);
    }

    public void requireSupport(Set<AssignmentTargetKind> kinds) {
        RuntimeInputs.require(kinds != null && !kinds.isEmpty() && kinds.stream().noneMatch(java.util.Objects::isNull), Category.HOST_CONFIGURATION);
        if (kinds.stream().anyMatch(kind -> kind != AssignmentTargetKind.USER)) provider();
    }

    private WorkflowSharedAssignmentProvider provider() {
        RuntimeInputs.require(providers.size() == 1, Category.HOST_CONFIGURATION);
        return providers.get(0);
    }
}
