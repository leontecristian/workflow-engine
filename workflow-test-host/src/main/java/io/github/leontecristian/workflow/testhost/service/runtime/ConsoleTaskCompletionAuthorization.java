package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskCompletionAuthorization;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Component;

@Component
public class ConsoleTaskCompletionAuthorization implements WorkflowTaskCompletionAuthorization {
    private final ConsoleContext identity;
    private final SandboxRequestStore requests;
    private final SandboxCompletionPermissions permissions;

    public ConsoleTaskCompletionAuthorization(ConsoleContext identity, SandboxRequestStore requests,
            SandboxCompletionPermissions permissions) {
        this.identity = identity;
        this.requests = requests;
        this.permissions = permissions;
    }

    @Override public boolean canAccess(TaskEligibilityContext context) {
        return allowed(context.actorRef(), context.task().instanceId(), SandboxCompletionPermissions.Scope.VIEW);
    }

    @Override public boolean canComplete(TaskCompletionContext context) {
        return allowed(context.commandContext().actorRef(), context.task().instanceId(), SandboxCompletionPermissions.Scope.DECIDE);
    }

    private boolean allowed(String actor, long instanceId, SandboxCompletionPermissions.Scope scope) {
        return identity.isConsoleActor(actor) && requests.findByInstance(instanceId)
                .map(request -> permissions.isAllowed(actor, SandboxCompletionPermissions.Resource.BOOKING, scope, request))
                .orElse(false);
    }
}
