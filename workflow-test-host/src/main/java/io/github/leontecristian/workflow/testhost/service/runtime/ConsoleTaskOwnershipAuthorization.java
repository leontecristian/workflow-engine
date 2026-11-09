package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Component;

/** Business access in the sandbox is shared by its authenticated console accounts. */
@Component
public class ConsoleTaskOwnershipAuthorization implements WorkflowTaskOwnershipAuthorization {
    private final ConsoleContext context;
    private final SandboxRequestStore requests;

    public ConsoleTaskOwnershipAuthorization(ConsoleContext context, SandboxRequestStore requests) {
        this.context = context;
        this.requests = requests;
    }

    @Override
    public boolean canAccess(TaskEligibilityContext taskContext) {
        return context.isConsoleActor(taskContext.actorRef())
                && requests.linkedToInstance(taskContext.task().instanceId());
    }

    @Override
    public boolean canAdministrativelyRelease(TaskEligibilityContext taskContext) {
        return context.isConsoleActor(taskContext.actorRef()) && context.admin();
    }
}
