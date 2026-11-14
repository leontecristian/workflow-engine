package io.github.leontecristian.workflow.core.service.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.eq;

import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionVersionRepository;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class DefinitionVersionCommandsUnitTest {
    private final WorkflowDefinitionRepository families = mock(WorkflowDefinitionRepository.class);
    private final WorkflowDefinitionVersionRepository versions = mock(WorkflowDefinitionVersionRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final TaskTypeCatalogService catalog = mock(TaskTypeCatalogService.class);
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();
    private final WorkflowDefinitionService service = new WorkflowDefinitionService(families, versions, em, codec, null, catalog);
    private final DefinitionManagementContext context = new DefinitionManagementContext("author", Instant.parse("2026-11-14T10:00:00Z"));

    private WorkflowDefinitionVersionEntity source(String json) {
        var doc = codec.ingest(json);
        var source = new WorkflowDefinitionVersionEntity(1L, 2, doc.canonical(), doc.hash(),
                DefinitionContentCanonicalizer.IDENTIFIER, LocalDateTime.now(), "old-author");
        ReflectionTestUtils.setField(source, "id", 3L);
        ReflectionTestUtils.setField(source, "lockVersion", 7L);
        var family = new WorkflowDefinitionEntity("example", "Family metadata", null, LocalDateTime.now(), "old-author");
        ReflectionTestUtils.setField(family, "id", 1L);
        family.advanceAllocatedVersion(10);
        when(families.lockById(1L)).thenReturn(Optional.of(family));
        when(families.findById(1L)).thenReturn(Optional.of(family));
        when(versions.findFamilyId(3L)).thenReturn(Optional.of(1L));
        when(versions.findById(3L)).thenReturn(Optional.of(source));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof WorkflowDefinitionVersionEntity entity) {
                ReflectionTestUtils.setField(entity, "id", 4L);
                ReflectionTestUtils.setField(entity, "lockVersion", 0L);
            }
            return null;
        }).when(em).persist(any());
        return source;
    }

    private String json() { return """
            {"key":"example","version":2,"schemaVersion":1,"name":"Saved", "designer":{"version":77},
             "unknown":{"integer":900719925474099312345,"decimal":1.2300,"array":[1e100,null]}}
            """; }

    @Test void copiesExactSavedDocumentResetsEvidenceAndLocksFamilyBeforeSource() {
        var source = source(json());
        source.recordValidation(false, new byte[32], "{}", LocalDateTime.now(), "validator");
        var original = source.getDefinitionJson();
        var result = service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 7), context);
        var persisted = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(em).persist(persisted.capture());
        var created = (WorkflowDefinitionVersionEntity) persisted.getValue();
        var expected = codec.readObject(original); expected.put("version", 11);
        assertThat(created.getDefinitionJson()).isEqualTo(DefinitionContentCanonicalizer.canonical(expected));
        assertThat(created.getContentHash()).isEqualTo(DefinitionContentCanonicalizer.hash(created.getDefinitionJson()));
        assertThat(created.getContentRevision()).isEqualTo(1);
        assertThat(created.getValidationStatus()).isNull();
        assertThat(created.getPublishedAt()).isNull();
        assertThat(created.getRetiredAt()).isNull();
        assertThat(created.getCreatedBy()).isEqualTo("author");
        assertThat(source.getDefinitionJson()).isEqualTo(original);
        assertThat(result.before()).isEmpty();
        assertThat(result.after().version()).isEqualTo(11);
        assertThat(result.operation()).isEqualTo("CREATE_DRAFT_FROM_VERSION");
        assertThat(families.findById(1L).orElseThrow().getHighestAllocatedVersion()).isEqualTo(11);
        var order = inOrder(families, em);
        order.verify(families).lockById(1L);
        order.verify(em).refresh(any(WorkflowDefinitionEntity.class), eq(LockModeType.PESSIMISTIC_WRITE));
        order.verify(em).refresh(source, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void staleExhaustedAndUnavailableTaskTypeCreateNothing() {
        source(json());
        assertThatThrownBy(() -> service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 6), context))
                .isInstanceOf(WorkflowDefinitionException.class);
        families.findById(1L).orElseThrow().advanceAllocatedVersion(Integer.MAX_VALUE);
        assertThatThrownBy(() -> service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 7), context))
                .isInstanceOfSatisfying(WorkflowDefinitionException.class, e -> assertThat(e.category()).isEqualTo(WorkflowDefinitionException.Category.CONFLICT));
        source(json().replace("\"unknown\":", "\"nodes\":[{\"type\":\"HUMAN_TASK\",\"taskType\":\"DISABLED\"}],\"unknown\":"));
        when(catalog.findByCode("DISABLED")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 7), context))
                .isInstanceOf(WorkflowDefinitionException.class);
        verify(em, never()).persist(any());
    }

    @Test void deletionReturnsBeforeOnlyAndNeverChangesCounter() {
        var source = source(json());
        var result = service.deleteDraft(new DeleteWorkflowDraft(3, 7), context);
        assertThat(result.before().id()).isEqualTo(3);
        assertThat(result.operation()).isEqualTo("DELETE_DRAFT");
        verify(em).remove(source); verify(em).flush();
        assertThat(families.findById(1L).orElseThrow().getHighestAllocatedVersion()).isEqualTo(10);
    }

    @Test void immutableAndStaleDeletionAndInvalidCommandsFailBeforeRemoval() {
        var source = source(json());
        ReflectionTestUtils.setField(source, "status", DefinitionStatus.PUBLISHED);
        assertThatThrownBy(() -> service.deleteDraft(new DeleteWorkflowDraft(3, 7), context)).isInstanceOf(WorkflowDefinitionException.class);
        ReflectionTestUtils.setField(source, "status", DefinitionStatus.RETIRED);
        assertThatThrownBy(() -> service.deleteDraft(new DeleteWorkflowDraft(3, 7), context)).isInstanceOf(WorkflowDefinitionException.class);
        assertThatThrownBy(() -> service.deleteDraft(new DeleteWorkflowDraft(3, 6), context)).isInstanceOf(WorkflowDefinitionException.class);
        assertThatThrownBy(() -> service.deleteDraft(new DeleteWorkflowDraft(0, 0), context)).isInstanceOf(WorkflowDefinitionException.class);
        assertThatThrownBy(() -> service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, -1), context)).isInstanceOf(WorkflowDefinitionException.class);
        verify(em, never()).remove(any());
    }

    @Test void reservedAbsentImportConflictsButRetainedEquivalentReuses() {
        var source = source(json());
        var family = families.findById(1L).orElseThrow();
        when(families.findByWorkflowKey("example")).thenReturn(Optional.of(family));
        assertThatThrownBy(() -> service.importDraft(new ImportWorkflowDraft(json()), context)).isInstanceOf(WorkflowDefinitionException.class);
        when(versions.findByDefinitionIdAndVersion(1L, 2)).thenReturn(Optional.of(source));
        assertThat(service.importDraft(new ImportWorkflowDraft(json()), context).changed()).isFalse();
        verify(em, never()).persist(any());
    }

    @ParameterizedTest
    @EnumSource(DefinitionStatus.class)
    void everyLifecycleCanBeCopiedWithoutChangingSourceState(DefinitionStatus status) {
        var source = source(json());
        ReflectionTestUtils.setField(source, "status", status);
        var result = service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 7), context);
        assertThat(source.getStatus()).isEqualTo(status);
        assertThat(result.after().status()).isEqualTo(DefinitionStatus.DRAFT);
    }

    @Test void largerAllocatedNumberRechecksDocumentBudgetBeforeAdvancingCounter() {
        var tree = codec.readObject(json());
        var payload = tree.putArray("payload");
        for (int index = 0; index < 65; index++) payload.add("x".repeat(16000));
        tree.put("padding", "");
        int padding = 1024 * 1024 - DefinitionContentCanonicalizer.canonical(tree).length();
        assertThat(padding).isBetween(0, 16000);
        tree.put("padding", "x".repeat(padding));
        source(DefinitionContentCanonicalizer.canonical(tree));
        assertThatThrownBy(() -> service.createDraftFromVersion(new CreateWorkflowDraftFromVersion(3, 7), context))
                .isInstanceOf(WorkflowDefinitionException.class);
        assertThat(families.findById(1L).orElseThrow().getHighestAllocatedVersion()).isEqualTo(10);
        verify(em, never()).persist(any());
    }
}
