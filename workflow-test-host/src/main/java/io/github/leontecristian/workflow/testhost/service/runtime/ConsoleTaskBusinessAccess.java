package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import org.springframework.stereotype.Component;

/** Host-private shared business association check; operation grants remain in the sandbox authorization policy. */
@Component
public class ConsoleTaskBusinessAccess {
    private final ConsoleContext context;
    private final SandboxRequestStore requests;

    public ConsoleTaskBusinessAccess(ConsoleContext context, SandboxRequestStore requests) {
        this.context = context;
        this.requests = requests;
    }

    public boolean canAccess(TaskEligibilityContext taskContext) {
        return canAccess(taskContext.actorRef(), taskContext.task().instanceId());
    }
    boolean canAccess(String actorRef, long instanceId) {
        return context.isConsoleActor(actorRef) && requests.linkedToInstance(instanceId);
    }
}
