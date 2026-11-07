package io.github.leontecristian.workflow.testhost.service.administration;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCheckResult;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionMetadataChange;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeChange;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationHost;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import io.github.leontecristian.workflow.testhost.service.definition.ConsoleManagement;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/** Sandbox policy and existing transactional management proxy. */
public class ConsoleAdministrationHost implements WorkflowAdministrationHost {
    private final ConsoleManagement management;
    public ConsoleAdministrationHost(ConsoleManagement management) { this.management = management; }
    @Override public void requireAdministrationAccess() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken)
            throw new WorkflowAdministrationException(401, "UNAUTHENTICATED");
        if (authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CONSOLE_ADMIN")))
            throw new WorkflowAdministrationException(403, "FORBIDDEN");
    }
    @Override public TaskTypeChange createTaskType(CreateTaskType command, String correlation) { return management.createTaskType(command, correlation); }
    @Override public TaskTypeChange updateTaskType(UpdateTaskType command, String correlation) { return management.updateTaskType(command, correlation); }
    @Override public DefinitionChange importDraft(String document, String correlation) { return management.importDraft(document, correlation); }
    @Override public DefinitionChange replace(long id, long token, String document, String correlation) { return management.replace(id, token, document, correlation); }
    @Override public DefinitionMetadataChange metadata(long id, long token, String name, String description, String correlation) { return management.metadata(id, token, name, description, correlation); }
    @Override public DefinitionCheckResult validate(long id, long token, String correlation) { return management.validate(id, token, correlation); }
    @Override public DefinitionCheckResult publish(long id, long token, String correlation) { return management.publish(id, token, correlation); }
    @Override public DefinitionChange retire(long id, long token, String correlation) { return management.retire(id, token, correlation); }
}
