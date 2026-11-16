package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskReassignmentAuthorization;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import org.springframework.stereotype.Component;

/** Sandbox-only policy: known console fixtures share the same frozen review basis. */
@Component
public class ConsoleTaskReassignmentAuthorization implements WorkflowTaskReassignmentAuthorization {
    private final ConsoleTaskBusinessAccess businessAccess;
    private final io.github.leontecristian.workflow.testhost.service.console.ConsoleContext context;
    private final ConsoleSettings settings;

    public ConsoleTaskReassignmentAuthorization(ConsoleTaskBusinessAccess businessAccess,
            io.github.leontecristian.workflow.testhost.service.console.ConsoleContext context, ConsoleSettings settings) {
        this.businessAccess = businessAccess;
        this.context = context;
        this.settings = settings;
    }

    @Override public boolean canAccess(TaskEligibilityContext task) { return businessAccess.canAccess(task); }
    @Override public boolean canReassign(TaskEligibilityContext task) {
        return context.isConsoleActor(task.actorRef()) && context.admin();
    }
    @Override public boolean isValidTarget(TaskEligibilityContext task, TaskAssignmentTarget target) {
        if (target.kind() == AssignmentTargetKind.USER)
            return java.util.List.of(settings.admin, settings.reviewer, settings.reviewer2).contains(target.ref());
        return target.equals(SandboxSharedAssignmentProvider.FINANCE)
                || target.equals(SandboxSharedAssignmentProvider.APPROVER);
    }
}
