package io.github.leontecristian.workflow.web;

import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCheckResult;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionDeletion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionMetadataChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionView;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ReplaceWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.RetireWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.UpdateWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeChange;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.dto.administration.WorkflowAdministrationAudit;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import io.github.leontecristian.workflow.web.service.administration.WorkflowAdministrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Detached coordinator coverage; transaction behavior belongs to host integration tests. */
class WorkflowAdministrationServiceUnitTest {
    private final WorkflowDefinitionService definitions = mock(WorkflowDefinitionService.class);
    private final TaskTypeCatalogService catalog = mock(TaskTypeCatalogService.class);
    private final WorkflowAdministrationAuditRecorder recorder = mock(WorkflowAdministrationAuditRecorder.class);
    private final Clock clock = mock(Clock.class);
    private final Instant precise = Instant.parse("2026-11-16T10:15:30.123456789Z");
    private final DefinitionManagementContext expected = new DefinitionManagementContext(
            "trusted-actor", Instant.parse("2026-11-16T10:15:30.123456700Z"));
    private final WorkflowAdministrationService service = new WorkflowAdministrationService(definitions, catalog, recorder, clock);

    private DefinitionVersionSummary version(long token) {
        return new DefinitionVersionSummary(42, 7, "workflow", 1, (short) 1, DefinitionStatus.DRAFT,
                1, token, "hash", "1", precise, "creator", precise, "editor", null, null, null, null, null);
    }
    private void tickingClock() {
        when(clock.instant()).thenReturn(precise, precise.plusSeconds(1));
    }
    private void verifyAudit(String operation, String kind, long id, Object before, Object after) {
        verify(recorder).record(new WorkflowAdministrationAudit(operation, kind, id, expected, "trusted-correlation", before, after));
        verifyNoMoreInteractions(recorder);
        verify(clock).instant();
    }
    @Test void importPreservesRawDocumentAndSharesOneTruncatedClockContextWithAudit() {
        tickingClock();
        var after = version(1);
        String raw = " {\"large\":900719925474099312345,\"decimal\":1.00}\n";
        when(definitions.importDraft(any(), any())).thenAnswer(call ->
                new DefinitionChange(Optional.empty(), after, true, "IMPORT_DRAFT", call.getArgument(1)));
        var result = service.importDraft("trusted-actor", raw, "trusted-correlation");
        var context = ArgumentCaptor.forClass(DefinitionManagementContext.class);
        verify(definitions).importDraft(eq(new ImportWorkflowDraft(raw)), context.capture());
        assertThat(context.getValue()).isEqualTo(expected);
        assertThat(result.context()).isSameAs(context.getValue());
        var audit = ArgumentCaptor.forClass(WorkflowAdministrationAudit.class);
        verify(recorder).record(audit.capture());
        assertThat(audit.getValue().context()).isSameAs(context.getValue());
        assertThat(audit.getValue()).isEqualTo(new WorkflowAdministrationAudit("IMPORT_DRAFT", "DEFINITION_VERSION",
                42, expected, "trusted-correlation", null, after));
        verify(clock).instant();
    }
    @Test void replacementAndMetadataUseCoreSummarySnapshots() {
        tickingClock();
        var before = version(1); var after = version(2);
        when(definitions.replaceDraft(any(), any())).thenAnswer(call ->
                new DefinitionChange(Optional.of(before), after, true, "REPLACE_DRAFT", call.getArgument(1)));
        service.replace("trusted-actor", 42, 1, "raw", "trusted-correlation");
        verify(definitions).replaceDraft(new ReplaceWorkflowDraft(42, 1, "raw"), expected);
        verifyAudit("REPLACE_DRAFT", "DEFINITION_VERSION", 42, before, after);

        reset(clock, recorder); tickingClock();
        var oldMetadata = new DefinitionView(7, "workflow", "Old", null, precise, "creator", 1);
        var newMetadata = new DefinitionView(7, "workflow", "New", "description", precise, "creator", 2);
        when(definitions.updateMetadata(any(), any())).thenAnswer(call -> new DefinitionMetadataChange(
                Optional.of(oldMetadata), newMetadata, true, "UPDATE_METADATA", call.getArgument(1)));
        service.metadata("trusted-actor", 7, 1, "New", "description", "trusted-correlation");
        verify(definitions).updateMetadata(new UpdateWorkflowDefinition(7, 1, "New", "description"), expected);
        verifyAudit("UPDATE_METADATA", "DEFINITION", 7, oldMetadata, newMetadata);
    }
    @Test void creationAndDeletionKeepNullBeforeAndAfterRespectively() {
        tickingClock();
        var after = version(1);
        when(definitions.createDraftFromVersion(any(), any())).thenAnswer(call -> new DefinitionChange(
                Optional.empty(), after, true, "CREATE_DRAFT_FROM_VERSION", call.getArgument(1)));
        service.createDraftFromVersion("trusted-actor", 41, 9, "trusted-correlation");
        verify(definitions).createDraftFromVersion(new CreateWorkflowDraftFromVersion(41, 9), expected);
        verifyAudit("CREATE_DRAFT_FROM_VERSION", "DEFINITION_VERSION", 42, null, after);

        reset(clock, recorder); tickingClock();
        when(definitions.deleteDraft(any(), any())).thenAnswer(call -> new DefinitionDeletion(after, "DELETE_DRAFT", call.getArgument(1)));
        service.deleteDraft("trusted-actor", 42, 1, "trusted-correlation");
        verify(definitions).deleteDraft(new DeleteWorkflowDraft(42, 1), expected);
        verifyAudit("DELETE_DRAFT", "DEFINITION_VERSION", 42, after, null);
    }
    @Test void validatePublishAndRetireAuditOnlyChangedResults() {
        for (boolean changed : new boolean[] {false, true}) {
            for (String action : new String[] {"validate", "publish", "retire"}) {
                reset(clock, recorder, definitions); tickingClock();
                var before = version(1); var after = version(2);
                var change = new DefinitionChange(Optional.of(before), after, changed, action.toUpperCase(), expected);
                when(definitions.validateDraft(any(), any())).thenReturn(new DefinitionCheckResult(change, null, false));
                when(definitions.publish(any(), any())).thenReturn(new DefinitionCheckResult(change, null, true));
                when(definitions.retire(any(), any())).thenReturn(change);
                switch (action) {
                    case "validate" -> { service.validate("trusted-actor", 42, 1, "trusted-correlation");
                        verify(definitions).validateDraft(new CheckWorkflowDefinition(42, 1), expected); }
                    case "publish" -> { service.publish("trusted-actor", 42, 1, "trusted-correlation");
                        verify(definitions).publish(new CheckWorkflowDefinition(42, 1), expected); }
                    default -> { service.retire("trusted-actor", 42, 1, "trusted-correlation");
                        verify(definitions).retire(new RetireWorkflowDefinition(42, 1), expected); }
                }
                if (changed) verifyAudit(action.toUpperCase(), "DEFINITION_VERSION", 42, before, after);
                else { verifyNoInteractions(recorder); verify(clock).instant(); }
            }
        }
    }
    @Test void taskTypeCoreSignaturesStayUnchangedAndActorTimeGoToAuditOnly() {
        tickingClock();
        var after = new TaskTypeView(8, "TYPE", "Name", null, true, 1);
        var create = new CreateTaskType("TYPE", "Name", null, true);
        when(catalog.create(create)).thenReturn(new TaskTypeChange(Optional.empty(), after, true));
        service.createTaskType("trusted-actor", create, "trusted-correlation");
        verify(catalog).create(create);
        verifyAudit("CREATE_TASK_TYPE", "TASK_TYPE", 8, null, after);
        reset(clock, recorder); tickingClock();
        var update = new UpdateTaskType(8, 1, "Name", null, true);
        when(catalog.update(update)).thenReturn(new TaskTypeChange(Optional.of(after), after, false));
        service.updateTaskType("trusted-actor", update, "trusted-correlation");
        verify(catalog).update(update);
        verifyNoInteractions(recorder);
        verify(clock).instant();
    }
    @Test void unchangedReplacementAndMetadataProduceNoAudit() {
        tickingClock();
        var snapshot = version(1);
        when(definitions.replaceDraft(any(), any())).thenAnswer(call -> new DefinitionChange(
                Optional.of(snapshot), snapshot, false, "REPLACE_DRAFT", call.getArgument(1)));
        service.replace("trusted-actor", 42, 1, "raw", "trusted-correlation");
        var metadata = new DefinitionView(7, "workflow", "Same", null, precise, "creator", 1);
        when(definitions.updateMetadata(any(), any())).thenAnswer(call -> new DefinitionMetadataChange(
                Optional.of(metadata), metadata, false, "UPDATE_METADATA", call.getArgument(1)));
        service.metadata("trusted-actor", 7, 1, "Same", null, "trusted-correlation");
        verifyNoInteractions(recorder);
        verify(clock, times(2)).instant();
    }
    @Test void changedTaskTypeUpdateKeepsBeforeAndAfterSummaries() {
        tickingClock();
        var before = new TaskTypeView(8, "TYPE", "Before", null, true, 1);
        var after = new TaskTypeView(8, "TYPE", "After", null, true, 2);
        var command = new UpdateTaskType(8, 1, "After", null, true);
        when(catalog.update(command)).thenReturn(new TaskTypeChange(Optional.of(before), after, true));
        service.updateTaskType("trusted-actor", command, "trusted-correlation");
        verify(catalog).update(command);
        verifyAudit("UPDATE_TASK_TYPE", "TASK_TYPE", 8, before, after);
    }
    @Test void configuredAuditFailuresPropagateUnchanged() {
        tickingClock();
        when(catalog.create(any())).thenReturn(new TaskTypeChange(Optional.empty(),
                new TaskTypeView(8, "TYPE", "Name", null, true, 1), true));
        var failure = new IllegalStateException("audit unavailable");
        doThrow(failure).when(recorder).record(any());
        assertThatThrownBy(() -> service.createTaskType("trusted-actor",
                new CreateTaskType("TYPE", "Name", null, true), "trusted-correlation")).isSameAs(failure);
        verify(catalog, times(1)).create(any());
    }
}
