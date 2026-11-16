package io.github.leontecristian.workflow.web.service.administration;

import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionDeletion;
import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
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
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.web.dto.administration.WorkflowAdministrationAudit;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.Clock;

/** Internal shared web coordination, not a supported host command API.
 * Call the managed proxy with an actor authenticated and authorized by shared web.
 * REQUIRED joins an outer transaction; its caller then owns final commit/rollback.
 */
public class WorkflowAdministrationService {
    private final WorkflowDefinitionService definitions;
    private final TaskTypeCatalogService catalog;
    private final WorkflowAdministrationAuditRecorder audits;
    private final Clock clock;

    public WorkflowAdministrationService(WorkflowDefinitionService definitions, TaskTypeCatalogService catalog,
            WorkflowAdministrationAuditRecorder audits, Clock clock) {
        this.definitions = definitions;
        this.catalog = catalog;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionChange importDraft(String actorRef, String document, String correlation) {
        var context = context(actorRef);
        var result =
                definitions.importDraft(new ImportWorkflowDraft(document), context);
        audit(result, context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionChange createDraftFromVersion(String actorRef, long sourceVersionId, long token, String correlation) {
        var context = context(actorRef);
        var result = definitions.createDraftFromVersion(
                new CreateWorkflowDraftFromVersion(sourceVersionId, token), context);
        audit(result, context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionDeletion deleteDraft(String actorRef, long id, long token, String correlation) {
        var context = context(actorRef);
        var result = definitions.deleteDraft(new DeleteWorkflowDraft(id, token), context);
        audit(true, result.operation(), "DEFINITION_VERSION", result.before().id(), result.before(), null, context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionChange replace(String actorRef, long id, long token, String document, String correlation) {
        var context = context(actorRef);
        var result =
                definitions.replaceDraft(
                        new ReplaceWorkflowDraft(id, token, document), context);
        audit(result, context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionMetadataChange metadata(
            String actorRef, long id, long token, String name, String description, String correlation) {
        var context = context(actorRef);
        var result =
                definitions.updateMetadata(
                        new UpdateWorkflowDefinition(id, token, name, description),
                        context);
        audit(
                result.changed(),
                result.operation(),
                "DEFINITION",
                id,
                result.before().orElse(null),
                result.after(),
                context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionChange retire(String actorRef, long id, long token, String correlation) {
        var context = context(actorRef);
        var result = definitions.retire(new RetireWorkflowDefinition(id, token), context);
        audit(result, context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionCheckResult validate(String actorRef, long id, long token, String correlation) {
        var context = context(actorRef);
        var result = definitions.validateDraft(new CheckWorkflowDefinition(id, token), context);
        audit(result.change(), context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DefinitionCheckResult publish(String actorRef, long id, long token, String correlation) {
        var context = context(actorRef);
        var result = definitions.publish(new CheckWorkflowDefinition(id, token), context);
        audit(result.change(), context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskTypeChange createTaskType(String actorRef, CreateTaskType command, String correlation) {
        var context = context(actorRef);
        var result = catalog.create(command);
        auditTask(result, "CREATE_TASK_TYPE", context, correlation);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskTypeChange updateTaskType(String actorRef, UpdateTaskType command, String correlation) {
        var context = context(actorRef);
        var result = catalog.update(command);
        auditTask(result, "UPDATE_TASK_TYPE", context, correlation);
        return result;
    }

    private void audit(DefinitionChange result, DefinitionManagementContext context, String correlation) {
        audit(
                result.changed(),
                result.operation(),
                "DEFINITION_VERSION",
                result.after().id(),
                result.before().orElse(null),
                result.after(),
                context, correlation);
    }

    private void auditTask(TaskTypeChange result, String operation, DefinitionManagementContext context, String correlation) {
        audit(
                result.changed(),
                operation,
                "TASK_TYPE",
                result.after().id(),
                result.before().orElse(null),
                result.after(),
                context, correlation);
    }

    private void audit(
            boolean changed,
            String operation,
            String kind,
            long id,
            Object before,
            Object after,
            DefinitionManagementContext context, String correlation) {
        if (!changed) return;
        audits.record(new WorkflowAdministrationAudit(operation, kind, id, context, correlation, before, after));
    }

    private DefinitionManagementContext context(String actorRef) {
        var time = clock.instant();
        return new DefinitionManagementContext(actorRef,
                java.time.Instant.ofEpochSecond(time.getEpochSecond(), time.getNano() / 100 * 100L));
    }
}
