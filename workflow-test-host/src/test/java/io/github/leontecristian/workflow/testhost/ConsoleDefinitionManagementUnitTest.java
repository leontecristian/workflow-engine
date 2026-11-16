package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionDeletion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.administration.ConsoleAdministrationHost;
import io.github.leontecristian.workflow.web.service.administration.WorkflowAdministrationService;
import io.github.leontecristian.workflow.web.dto.administration.WorkflowAdministrationAudit;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Detached command/audit mapping only; transaction rollback is verified by the database harness. */
class ConsoleDefinitionManagementUnitTest {
    private final WorkflowDefinitionService definitions = mock(WorkflowDefinitionService.class);
    private final ManagementAuditStore audits = mock(ManagementAuditStore.class);
    private final ConsoleWire wire = new ConsoleWire();
    private final Instant time = Instant.parse("2026-11-14T10:00:00Z");
    private final WorkflowAdministrationService management = new WorkflowAdministrationService(definitions,
            mock(TaskTypeCatalogService.class), new ConsoleAdministrationHost(audits, wire, mock(ConsoleSettings.class)),
            Clock.fixed(time, ZoneOffset.UTC));
    private final DefinitionManagementContext trusted = new DefinitionManagementContext("trusted-admin", time);
    private final UUID correlation = UUID.fromString("00000000-0000-0000-0000-000000000023");

    private DefinitionVersionSummary summary(long id) {
        return new DefinitionVersionSummary(id, 2, "test", 3, (short) 1, DefinitionStatus.DRAFT, 1, 7,
                "hash", "1", time, "trusted-admin", time, "trusted-admin", null, null, null, null, null);
    }

    @Test void createUsesTrustedContextAndAuditsNewVersionIdentity() {
        var result = new DefinitionChange(Optional.empty(), summary(43), true, "CREATE_DRAFT_FROM_VERSION", trusted);
        when(definitions.createDraftFromVersion(new CreateWorkflowDraftFromVersion(42, 7), trusted)).thenReturn(result);
        assertThat(management.createDraftFromVersion(trusted.actorRef(), 42, 7, correlation.toString())).isSameAs(result);
        var evidence = ArgumentCaptor.forClass(String.class);
        verify(audits).insert(eq(time), eq("trusted-admin"), eq("CREATE_DRAFT_FROM_VERSION"), eq("DEFINITION_VERSION"),
                eq(43L), eq(correlation), evidence.capture());
        var json = wire.tree(evidence.getValue());
        assertThat(json.get("schemaVersion").intValue()).isEqualTo(1);
        assertThat(json.get("operation").textValue()).isEqualTo("CREATE_DRAFT_FROM_VERSION");
        assertThat(json.get("before").isNull()).isTrue();
        assertThat(json.get("after").get("id").textValue()).isEqualTo("43");
    }

    @Test void deleteAuditsRemovedSummaryAndNeverFabricatesAfter() {
        var result = new DefinitionDeletion(summary(42), "DELETE_DRAFT", trusted);
        when(definitions.deleteDraft(new DeleteWorkflowDraft(42, 7), trusted)).thenReturn(result);
        assertThat(management.deleteDraft(trusted.actorRef(), 42, 7, correlation.toString())).isSameAs(result);
        var evidence = ArgumentCaptor.forClass(String.class);
        verify(audits).insert(eq(time), eq("trusted-admin"), eq("DELETE_DRAFT"), eq("DEFINITION_VERSION"),
                eq(42L), eq(correlation), evidence.capture());
        var json = wire.tree(evidence.getValue());
        assertThat(json.get("before").get("id").textValue()).isEqualTo("42");
        assertThat(json.get("before").has("document")).isFalse();
        assertThat(json.get("after").isNull()).isTrue();
    }

    @Test void deleteAuditFailureEscapesTransactionalBoundary() {
        when(definitions.deleteDraft(new DeleteWorkflowDraft(42, 7), trusted))
                .thenReturn(new DefinitionDeletion(summary(42), "DELETE_DRAFT", trusted));
        var failure = new IllegalStateException("audit failed");
        doThrow(failure).when(audits).insert(any(), anyString(), anyString(), anyString(), eq(42L), any(), anyString());
        assertThatThrownBy(() -> management.deleteDraft(trusted.actorRef(), 42, 7, correlation.toString())).isSameAs(failure);
    }

    @Test void adapterRetainsUtf16EvidenceBoundBeforePersistence() {
        var adapter = new ConsoleAdministrationHost(audits, wire, mock(ConsoleSettings.class));
        var largeSummary = new TaskTypeView(42, "TEST", "Test", "x".repeat(256 * 1024), true, 0);
        var audit = new WorkflowAdministrationAudit("CREATE_TASK_TYPE", "TASK_TYPE", 42, trusted,
                correlation.toString(), null, largeSummary);
        assertThatThrownBy(() -> adapter.record(audit)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Audit evidence exceeds bound");
        verifyNoInteractions(audits);
    }
}
