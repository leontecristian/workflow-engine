package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorization;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConsoleWorkflowAuthorization implements WorkflowAuthorization {
    private final ConsoleContext identity;
    private final ConsoleTaskBusinessAccess businessAccess;
    private final SandboxRequestStore requests;
    private final SandboxCompletionPermissions completionPermissions;

    public ConsoleWorkflowAuthorization(ConsoleContext identity, ConsoleTaskBusinessAccess businessAccess,
            SandboxRequestStore requests, SandboxCompletionPermissions completionPermissions) {
        this.identity = identity;
        this.businessAccess = businessAccess;
        this.requests = requests;
        this.completionPermissions = completionPermissions;
    }

    @Override
    public Set<WorkflowOperation> supportedOperations() {
        return Set.of(WorkflowOperation.COMPLETE_TASK, WorkflowOperation.CLAIM_TASK, WorkflowOperation.RELEASE_TASK,
                WorkflowOperation.ADMIN_RELEASE_TASK, WorkflowOperation.REASSIGN_TASK, WorkflowOperation.CANCEL_WORKFLOW);
    }

    @Override
    public boolean isAllowed(WorkflowAuthorizationRequest request) {
        return switch (request.permission().scope()) {
            case ACCESS_COMPLETION -> canAccessBooking(request.actorRef(), request.instanceId(), SandboxCompletionPermissions.Scope.VIEW);
            case COMPLETE -> canAccessBooking(request.actorRef(), request.instanceId(), SandboxCompletionPermissions.Scope.DECIDE);
            case ACCESS_OWNERSHIP, ACCESS_REASSIGNMENT ->
                    canAccessLinkedTask(((WorkflowAuthorizationRequest.Task) request).task());
            case ACCESS_CANCELLATION -> businessAccess.canAccess(request.actorRef(), request.instanceId());
            case ADMIN_RELEASE, REASSIGN, CANCEL -> isAdministrator(request.actorRef());
        };
    }

    boolean canAccessLinkedTask(TaskEligibilityContext task) {
        return businessAccess.canAccess(task);
    }

    boolean isAdministrator(String actorRef) {
        return identity.isConsoleActor(actorRef) && identity.admin();
    }

    public boolean canDecide(TaskEligibilityContext task) {
        return canAccessBooking(task.actorRef(), task.task().instanceId(), SandboxCompletionPermissions.Scope.DECIDE);
    }

    private boolean canAccessBooking(String actor, long instanceId, SandboxCompletionPermissions.Scope scope) {
        return identity.isConsoleActor(actor) && requests.findByInstance(instanceId)
                .map(booking -> completionPermissions.isAllowed(
                        actor, SandboxCompletionPermissions.Resource.BOOKING, scope, booking))
                .orElse(false);
    }
}
