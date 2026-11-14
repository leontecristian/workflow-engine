package io.github.leontecristian.workflow.core.service.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.Category;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionVersionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/** Repository doubles check call ordering; SQL ordering/serialization is covered by FamilyWorkflowStartIT. */
class FamilyStartResolutionUnitTest {
    private final WorkflowDefinitionRepository families = mock(WorkflowDefinitionRepository.class);
    private final WorkflowDefinitionVersionRepository versions = mock(WorkflowDefinitionVersionRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final WorkflowDefinitionRuntimeReader reader =
            new WorkflowDefinitionRuntimeReader(families, versions, em, null, null, null, null);

    @Test void missingTargetsAndNoPublicationAreDistinctFromCorruptPins() {
        assertCategory(Category.NOT_FOUND, () -> reader.loadForStart(42));
        assertCategory(Category.NOT_FOUND, () -> reader.loadForStart(command("unknown", null)));
        assertCategory(Category.NOT_FOUND, () -> reader.loadForStart(command(null, 1L)));
        var family = mock(WorkflowDefinitionEntity.class);
        when(families.lockById(1L)).thenReturn(Optional.of(family));
        assertCategory(Category.NO_PUBLISHED_VERSION, () -> reader.loadForStart(command(null, 1L)));
        when(versions.findFamilyId(42L)).thenReturn(Optional.of(1L));
        assertCategory(Category.NOT_FOUND, () -> reader.loadForStart(42));
        assertCategory(Category.HOST_CONFIGURATION, () -> reader.loadPinned(42));
    }

    @Test void familyLockAndRefreshPrecedeBoundedSelectionAndVersionRefresh() {
        var family = mock(WorkflowDefinitionEntity.class);
        var version = mock(WorkflowDefinitionVersionEntity.class);
        when(family.getId()).thenReturn(1L);
        when(families.findByWorkflowKey("example")).thenReturn(Optional.of(family));
        when(families.lockById(1L)).thenReturn(Optional.of(family));
        when(versions.findLatestPublished(1L, DefinitionStatus.PUBLISHED, PageRequest.of(0, 1)))
                .thenReturn(List.of(7L));
        when(versions.findById(7L)).thenReturn(Optional.of(version));
        // A stale nonpublished candidate must be refreshed before its status is inspected.
        doAnswer(call -> { when(version.getStatus()).thenReturn(DefinitionStatus.PUBLISHED); return null; })
                .when(em).refresh(version, LockModeType.PESSIMISTIC_WRITE);
        assertCategory(Category.UNSUPPORTED_CANONICALIZATION, () -> reader.loadForStart(command("example", null)));
        var order = inOrder(families, versions, em, version);
        order.verify(families).lockById(1L);
        order.verify(em).refresh(family, LockModeType.PESSIMISTIC_WRITE);
        order.verify(versions).findLatestPublished(1L, DefinitionStatus.PUBLISHED, PageRequest.of(0, 1));
        order.verify(versions).findById(7L);
        order.verify(em).refresh(version, LockModeType.PESSIMISTIC_WRITE);
        order.verify(version).getStatus();
        verify(versions, times(1)).findLatestPublished(anyLong(), any(), any());
    }

    @Test void exactNonpublishedSelectionNeverFallsBack() {
        var family = mock(WorkflowDefinitionEntity.class);
        var version = mock(WorkflowDefinitionVersionEntity.class);
        when(versions.findFamilyId(42L)).thenReturn(Optional.of(1L));
        when(families.lockById(1L)).thenReturn(Optional.of(family));
        when(versions.findById(42L)).thenReturn(Optional.of(version));
        when(version.getStatus()).thenReturn(DefinitionStatus.RETIRED);
        assertCategory(Category.INVALID_STATE, () -> reader.loadForStart(42L));
        verify(versions, never()).findLatestPublished(anyLong(), any(), any());
        var order = inOrder(families, em);
        order.verify(families).lockById(1L);
        order.verify(em).refresh(family, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(version, LockModeType.PESSIMISTIC_WRITE);
    }

    private StartWorkflow command(String key, Long familyId) {
        return new StartWorkflow(UUID.randomUUID(), key, familyId, null, null, null, null);
    }
    private void assertCategory(Category expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(WorkflowDefinitionException.class, action).category());
    }
}
