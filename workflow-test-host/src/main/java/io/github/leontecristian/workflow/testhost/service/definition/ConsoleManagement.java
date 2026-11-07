package io.github.leontecristian.workflow.testhost.service.definition;

import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionMetadataChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCheckResult;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ReplaceWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.RetireWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.UpdateWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeChange;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Separate transactional boundary: controller only reports success after this proxy commits. */
@Service
public class ConsoleManagement {
    private final WorkflowDefinitionService definitions;
    private final TaskTypeCatalogService catalog;
    private final ManagementAuditStore audits;
    private final ConsoleContext context;
    private final ConsoleWire wire;
    private final ConsoleSettings settings;

    public ConsoleManagement(
            WorkflowDefinitionService definitions,
            TaskTypeCatalogService catalog,
            ManagementAuditStore audits,
            ConsoleContext context,
            ConsoleWire wire,
            ConsoleSettings settings) {
        this.definitions = definitions;
        this.catalog = catalog;
        this.audits = audits;
        this.context = context;
        this.wire = wire;
        this.settings = settings;
    }

    @Transactional
    public DefinitionChange importDraft(String document, String correlation) {
        var result =
                definitions.importDraft(new ImportWorkflowDraft(document), context.management());
        audit(result, correlation);
        return result;
    }

    @Transactional
    public DefinitionChange replace(long id, long token, String document, String correlation) {
        var result =
                definitions.replaceDraft(
                        new ReplaceWorkflowDraft(id, token, document), context.management());
        audit(result, correlation);
        return result;
    }

    @Transactional
    public DefinitionMetadataChange metadata(
            long id, long token, String name, String description, String correlation) {
        var result =
                definitions.updateMetadata(
                        new UpdateWorkflowDefinition(id, token, name, description),
                        context.management());
        audit(
                result.changed(),
                result.operation(),
                "DEFINITION",
                id,
                result.before().orElse(null),
                result.after(),
                correlation);
        return result;
    }

    @Transactional
    public DefinitionChange retire(long id, long token, String correlation) {
        var result = definitions.retire(new RetireWorkflowDefinition(id, token), context.management());
        audit(result, correlation);
        return result;
    }

    @Transactional
    public DefinitionCheckResult validate(long id, long token, String correlation) {
        var result = definitions.validateDraft(new CheckWorkflowDefinition(id, token), context.management());
        audit(result.change(), correlation);
        return result;
    }

    @Transactional
    public DefinitionCheckResult publish(long id, long token, String correlation) {
        var result = definitions.publish(new CheckWorkflowDefinition(id, token), context.management());
        audit(result.change(), correlation);
        return result;
    }

    @Transactional
    public TaskTypeChange createTaskType(CreateTaskType command, String correlation) {
        var result = catalog.create(command);
        auditTask(result, "CREATE_TASK_TYPE", correlation);
        return result;
    }

    @Transactional
    public TaskTypeChange updateTaskType(UpdateTaskType command, String correlation) {
        var result = catalog.update(command);
        auditTask(result, "UPDATE_TASK_TYPE", correlation);
        return result;
    }

    private void audit(DefinitionChange result, String correlation) {
        audit(
                result.changed(),
                result.operation(),
                "DEFINITION_VERSION",
                result.after().id(),
                result.before().orElse(null),
                result.after(),
                correlation);
    }

    private void auditTask(TaskTypeChange result, String operation, String correlation) {
        audit(
                result.changed(),
                operation,
                "TASK_TYPE",
                result.after().id(),
                result.before().orElse(null),
                result.after(),
                correlation);
    }

    private void audit(
            boolean changed,
            String operation,
            String kind,
            long id,
            Object before,
            Object after,
            String correlation) {
        if (!changed) return;
        if (settings.failAudit) throw new IllegalStateException("Configured audit failure");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("operation", operation);
        evidence.put("before", before);
        evidence.put("after", after);
        String json = wire.json(evidence);
        if (json.length() * 2L > 512 * 1024)
            throw new IllegalStateException("Audit evidence exceeds bound");
        audits.insert(
                context.now(),
                context.actor(),
                operation,
                kind,
                id,
                UUID.fromString(correlation),
                json);
    }
}
