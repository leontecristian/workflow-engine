package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Component;

/** Business access in the sandbox is shared by its authenticated console accounts. */
@Component
public class ConsoleTaskOwnershipAuthorization implements WorkflowTaskOwnershipAuthorization {
    private final ConsoleContext context;
    private final ConsoleTaskBusinessAccess businessAccess;

    public ConsoleTaskOwnershipAuthorization(ConsoleContext context, ConsoleTaskBusinessAccess businessAccess) {
        this.context = context;
        this.businessAccess = businessAccess;
    }

    @Override
    public boolean canAccess(TaskEligibilityContext taskContext) {
        return businessAccess.canAccess(taskContext);
    }

    @Override
    public boolean canAdministrativelyRelease(TaskEligibilityContext taskContext) {
        return context.isConsoleActor(taskContext.actorRef()) && context.admin();
    }
}
