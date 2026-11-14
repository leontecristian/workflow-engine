package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowCancellationAuthorization;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Component;

/** Cancellation preserves the sandbox request's link and business status. */
@Component
public class ConsoleWorkflowCancellationAuthorization implements WorkflowCancellationAuthorization {
    private final ConsoleContext context;
    private final SandboxRequestStore requests;

    public ConsoleWorkflowCancellationAuthorization(ConsoleContext context, SandboxRequestStore requests) {
        this.context = context;
        this.requests = requests;
    }

    @Override
    public boolean canAccess(WorkflowCancellationAuthorizationContext authorization) {
        return context.isConsoleActor(authorization.commandContext().actorRef())
                && requests.linkedToInstance(authorization.instanceId());
    }

    @Override
    public boolean canCancel(WorkflowCancellationAuthorizationContext authorization) {
        return context.isConsoleActor(authorization.commandContext().actorRef()) && context.admin();
    }
}
