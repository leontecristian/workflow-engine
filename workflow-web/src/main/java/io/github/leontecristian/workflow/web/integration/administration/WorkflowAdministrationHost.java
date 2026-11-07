package io.github.leontecristian.workflow.web.integration.administration;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCheckResult;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionMetadataChange;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeChange;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;

/** Host-owned policy, trusted context and committed mutation/audit boundary. */
public interface WorkflowAdministrationHost {
    void requireAdministrationAccess();
    TaskTypeChange createTaskType(CreateTaskType command, String correlation);
    TaskTypeChange updateTaskType(UpdateTaskType command, String correlation);
    DefinitionChange importDraft(String document, String correlation);
    DefinitionChange replace(long id, long token, String document, String correlation);
    DefinitionMetadataChange metadata(long id, long token, String name, String description, String correlation);
    DefinitionCheckResult validate(long id, long token, String correlation);
    DefinitionCheckResult publish(long id, long token, String correlation);
    DefinitionChange retire(long id, long token, String correlation);
}
