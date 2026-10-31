package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.repository.runtime.RuntimeInstanceRow;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Plain collaborators and a real codec; no database, Spring context or migration. */
class RuntimeInstanceQueryUnitTest {
    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final WorkflowActivityRepository activities = mock(WorkflowActivityRepository.class);
    private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
    private final WorkflowHistoryRepository history = mock(WorkflowHistoryRepository.class);
    private final RuntimeDefinitionPins pins = mock(RuntimeDefinitionPins.class);
    private final WorkflowRuntimeQueryService service =
            new WorkflowRuntimeQueryService(
                    instances, activities, tasks, history, pins, new RuntimeJson());

    @Test
    void invalidBoundsFailBeforeAnyStorageWork() {
        for (var query :
                java.util.Arrays.asList(
                        null,
                        new WorkflowInstanceQuery(null, -1, 50),
                        new WorkflowInstanceQuery(null, 0, 0),
                        new WorkflowInstanceQuery(null, 0, 101))) {
            var error =
                    assertThrows(
                            WorkflowExecutionException.class, () -> service.listInstances(query));
            assertEquals(WorkflowExecutionException.Category.INVALID_INPUT, error.category());
        }
        verifyNoInteractions(instances, activities, tasks, history, pins);
    }

    @Test
    void lookaheadReturnsExactCursorAndUtcScalarsWithoutPerRowLookups() {
        long id = 9007199254740993L;
        var rows = List.of(row(id), row(id + 1));
        when(instances.findSummaryPage(InstanceStatus.ACTIVE, 0, PageRequest.of(0, 2)))
                .thenReturn(rows);
        var page = service.listInstances(new WorkflowInstanceQuery(InstanceStatus.ACTIVE, 0, 1));
        assertEquals(1, page.items().size());
        assertEquals(Long.valueOf(id), page.nextAfterId());
        var summary = page.items().get(0);
        assertEquals(id, summary.id());
        assertEquals("sandbox.review", summary.definitionKey());
        assertEquals(3, summary.definitionVersion());
        assertEquals("REVIEW", summary.currentNodeKey());
        assertEquals(
                LocalDateTime.of(2026, 10, 31, 12, 0, 0, 123456700).toInstant(ZoneOffset.UTC),
                summary.startedAt());
        assertNull(summary.endedAt());
        verify(instances).findSummaryPage(InstanceStatus.ACTIVE, 0, PageRequest.of(0, 2));
        verifyNoMoreInteractions(instances);
        verifyNoInteractions(activities, tasks, history, pins);
    }

    @Test
    void lastFullPageHasNoFalseContinuationAndReturnedItemsAreDetached() {
        var rows = new ArrayList<>(List.of(row(12)));
        when(instances.findSummaryPage(InstanceStatus.ACTIVE, 10, PageRequest.of(0, 2)))
                .thenReturn(rows);
        var page = service.listInstances(new WorkflowInstanceQuery(InstanceStatus.ACTIVE, 10, 1));
        rows.clear();
        assertEquals(1, page.items().size());
        assertNull(page.nextAfterId());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
    }

    @Test
    void allStateQueryPreservesExclusiveCursorAndEmptyPages() {
        assertEquals(InstanceStatus.ACTIVE, new WorkflowInstanceQuery().status());
        when(instances.findSummaryPage(null, 9, PageRequest.of(0, 51))).thenReturn(List.of());
        var page = service.listInstances(new WorkflowInstanceQuery(null, 9, 50));
        assertTrue(page.items().isEmpty());
        assertNull(page.nextAfterId());
        verify(instances).findSummaryPage(null, 9, PageRequest.of(0, 51));
        verifyNoInteractions(activities, tasks, history, pins);
    }

    private RuntimeInstanceRow row(long id) {
        var row = mock(RuntimeInstanceRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getDefinitionVersionId()).thenReturn(3L);
        when(row.getDefinitionKey()).thenReturn("sandbox.review");
        when(row.getDefinitionVersion()).thenReturn(3);
        when(row.getStatus()).thenReturn(InstanceStatus.ACTIVE);
        when(row.getCurrentNodeKey()).thenReturn("REVIEW");
        when(row.getBusinessType()).thenReturn("SANDBOX_REQUEST");
        when(row.getBusinessId()).thenReturn("9");
        when(row.getStartedAt()).thenReturn(LocalDateTime.of(2026, 10, 31, 12, 0, 0, 123456700));
        when(row.getStartedBy()).thenReturn("administrator");
        when(row.getLastHistorySequence()).thenReturn(4L);
        when(row.getLockVersion()).thenReturn(1L);
        return row;
    }
}
