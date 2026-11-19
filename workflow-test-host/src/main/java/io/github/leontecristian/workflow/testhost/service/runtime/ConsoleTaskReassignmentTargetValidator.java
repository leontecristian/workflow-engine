package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskReassignmentTargetValidator;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import java.util.List;
import org.springframework.stereotype.Component;

/** Known sandbox destinations share the same frozen review basis. */
@Component
public class ConsoleTaskReassignmentTargetValidator implements WorkflowTaskReassignmentTargetValidator {
    private final ConsoleSettings settings;

    public ConsoleTaskReassignmentTargetValidator(ConsoleSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isValidTarget(TaskEligibilityContext task, TaskAssignmentTarget target) {
        if (target.kind() == AssignmentTargetKind.USER) {
            return List.of(settings.admin, settings.reviewer, settings.reviewer2).contains(target.ref());
        }
        return target.equals(SandboxSharedAssignmentProvider.FINANCE)
                || target.equals(SandboxSharedAssignmentProvider.APPROVER);
    }
}
