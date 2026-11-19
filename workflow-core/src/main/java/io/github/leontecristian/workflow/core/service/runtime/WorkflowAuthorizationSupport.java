package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPermission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowResource;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Captures operation support at wiring and dispatches current policy decisions. */
@Component
@Lazy(false)
public class WorkflowAuthorizationSupport {
    private final WorkflowAuthorization policy;
    private final Set<WorkflowOperation> operations;

    public WorkflowAuthorizationSupport(ObjectProvider<WorkflowAuthorization> policies) {
        List<WorkflowAuthorization> beans;
        try {
            beans = policies.stream().limit(2).toList();
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
        RuntimeInputs.require(beans.size() <= 1, Category.HOST_CONFIGURATION);
        policy = beans.isEmpty() ? null : beans.get(0);
        try {
            operations = policy == null ? Set.of() : Set.copyOf(policy.supportedOperations());
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
        RuntimeInputs.require(Set.of(WorkflowOperation.COMPLETE_TASK, WorkflowOperation.CLAIM_TASK,
                WorkflowOperation.RELEASE_TASK, WorkflowOperation.ADMIN_RELEASE_TASK,
                WorkflowOperation.REASSIGN_TASK, WorkflowOperation.CANCEL_WORKFLOW).containsAll(operations),
                Category.HOST_CONFIGURATION);
    }

    boolean supports(WorkflowOperation operation) {
        return operations.contains(operation);
    }

    void requireSupport(WorkflowOperation operation) {
        RuntimeInputs.require(supports(operation), Category.HOST_CONFIGURATION);
    }

    boolean allowsTask(WorkflowScope scope, TaskEligibilityContext task) {
        return isAllowed(new WorkflowAuthorizationRequest.Task(new WorkflowPermission(WorkflowResource.TASK, scope), task));
    }

    boolean allowsCompletion(TaskCompletionContext task) {
        return isAllowed(new WorkflowAuthorizationRequest.Completion(
                new WorkflowPermission(WorkflowResource.TASK, WorkflowScope.COMPLETE), task));
    }

    boolean allowsCancellation(WorkflowScope scope, WorkflowCancellationAuthorizationContext cancellation) {
        return isAllowed(new WorkflowAuthorizationRequest.Cancellation(
                new WorkflowPermission(WorkflowResource.INSTANCE, scope), cancellation));
    }

    private boolean isAllowed(WorkflowAuthorizationRequest request) {
        try {
            return policy.isAllowed(request);
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure);
        }
    }
}
