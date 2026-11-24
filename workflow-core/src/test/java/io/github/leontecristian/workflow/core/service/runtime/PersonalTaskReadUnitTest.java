package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskCursor;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskAdministrationRow;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class PersonalTaskReadUnitTest {
    private static final String ACTOR = "alice";
    private static final LocalDateTime CREATED = LocalDateTime.parse("2026-11-24T09:03:42.1234567");
    private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
    private final WorkflowTaskEligibilityService eligibility = mock(WorkflowTaskEligibilityService.class);
    private final WorkflowRuntimeReadAuthorization policy = mock(WorkflowRuntimeReadAuthorization.class);
    private final RuntimeDefinitionPins pins = mock(RuntimeDefinitionPins.class);

    @Test
    void combinedInboxRequiresCurrentReadAndChecksEligibilityOnlyWhenOpenAssigneeDoesNotMatch() {
        var currentAssignee = row(9, TaskStatus.OPEN, ACTOR);
        var otherOpen = row(8, TaskStatus.OPEN, null);
        var completed = row(7, TaskStatus.COMPLETED, null);
        var unreadableClosed = row(6, TaskStatus.CANCELLED, ACTOR);
        var cancelled = row(5, TaskStatus.CANCELLED, ACTOR);
        candidates(List.of(currentAssignee, otherOpen, completed, unreadableClosed, cancelled));
        when(policy.canReadTask(any())).thenAnswer(call -> {
            TaskEligibilityContext context = call.getArgument(0);
            assertEquals(ACTOR, context.actorRef());
            return context.task().id() != 6;
        });

        var access = new WorkflowAuthorizedRuntimeReadService(reads(new RuntimeJson()), policy, eligibility,
                new WorkflowAuthorizationSupport(new StaticListableBeanFactory().getBeanProvider(WorkflowAuthorization.class)));
        var firstPage = access.listMyTasks(ACTOR, query(null, 2));
        assertEquals(List.of(9L, 7L), taskIds(firstPage));
        assertEquals(cursor(7), firstPage.next());

        var secondPage = access.listMyTasks(ACTOR, query(firstPage.next(), 2));
        assertEquals(List.of(5L), taskIds(secondPage));
        assertNull(secondPage.next());
        verify(eligibility).isEligible(RuntimeViews.task(otherOpen), ACTOR);
        verifyNoMoreInteractions(eligibility);
        verify(policy, times(5)).canReadTask(any());
        verifyNoInteractions(pins);
    }

    @Test
    void exactFiltersAndFullCursorReachStorageAndPinnedNameMatchesCaseInsensitiveLiteralText() {
        var nameMismatch = row(8, TaskStatus.OPEN, ACTOR);
        var accepted = row(7, TaskStatus.OPEN, ACTOR);
        when(nameMismatch.getNodeKey()).thenReturn("other");
        when(pins.nodeNames(1L)).thenReturn(Map.of("other", "Review finance 99", "review", "Review FINANCE %_"));
        when(tasks.findPersonalCandidatesPage(ACTOR, 10, TaskStatus.OPEN, "REVIEW", 6,
                AssignmentTargetKind.GROUP, "finance ", 16, ACTOR, 10, 4L, CREATED, 9L,
                PageRequest.of(0, 100))).thenReturn(List.of(nameMismatch, accepted));

        var query = new WorkflowPersonalTaskQuery(TaskStatus.OPEN, "REVIEW", AssignmentTargetKind.GROUP,
                "finance ", ACTOR, 4L, cursor(9), 50, "finance %_");
        var page = reads(new RuntimeJson()).listPersonalTasks(ACTOR, query, task -> true);
        assertEquals(List.of(7L), taskIds(page));
        assertEquals(CREATED.toInstant(ZoneOffset.UTC), page.items().get(0).createdAt());
        assertNull(page.next());
        verify(pins).nodeNames(1L);
    }

    @Test
    void emptyPageAdvancesAfterFiveHundredDeniedCandidatesAndResumesFromTheLastExamined() {
        var rows = LongStream.iterate(501, id -> id - 1).limit(501)
                .mapToObj(id -> row(id, TaskStatus.CANCELLED, ACTOR)).toList();
        var fetchSizes = candidates(rows);
        var examined = new ArrayList<Long>();
        var reads = reads(new RuntimeJson());

        var firstPage = reads.listPersonalTasks(ACTOR, query(null, 100), task -> {
            examined.add(task.id());
            return task.id() == 1;
        });
        assertEquals(List.of(), taskIds(firstPage));
        assertEquals(cursor(2), firstPage.next());
        assertEquals(LongStream.iterate(501, id -> id - 1).limit(500).boxed().toList(), examined);
        assertEquals(List.of(100, 100, 100, 100, 100, 6), fetchSizes);

        var secondPage = reads.listPersonalTasks(ACTOR, query(firstPage.next(), 100), task -> task.id() == 1);
        assertEquals(List.of(1L), taskIds(secondPage));
        assertNull(secondPage.next());
    }

    @Test
    void utf8LimitContinuesAfterDeniedRowButBeforeAnAuthorizedWithheldRow() {
        candidates(List.of(row(9, TaskStatus.COMPLETED, null), row(8, TaskStatus.COMPLETED, null),
                row(7, TaskStatus.COMPLETED, null)));
        var json = mock(RuntimeJson.class);
        String oversized = "é".repeat(512 * 1024 + 1);
        when(json.encode(any())).thenAnswer(call -> {
            WorkflowPersonalTaskPage page = call.getArgument(0);
            return page.items().size() > 1 ? oversized : "";
        });
        var reads = reads(json);

        var firstPage = reads.listPersonalTasks(ACTOR, query(null, 50), task -> task.id() != 8);
        assertEquals(List.of(9L), taskIds(firstPage));
        assertEquals(cursor(8), firstPage.next());
        var secondPage = reads.listPersonalTasks(ACTOR, query(firstPage.next(), 50), task -> task.id() != 8);
        assertEquals(List.of(7L), taskIds(secondPage));
        assertNull(secondPage.next());
    }

    @Test
    void invalidFiltersAndContinuationFailBeforeReads() {
        var reads = reads(new RuntimeJson());
        var invalid = List.of(
                new WorkflowPersonalTaskQuery(TaskStatus.OPEN, null, null, "finance", null,
                        null, null, 50, null),
                new WorkflowPersonalTaskQuery(TaskStatus.OPEN, null, null, null, null,
                        null, null, 50, "🚀".repeat(101)),
                query(new WorkflowPersonalTaskCursor(Instant.MAX, 1), 50),
                query(new WorkflowPersonalTaskCursor(CREATED.toInstant(ZoneOffset.UTC).plusNanos(1), 1), 50),
                query(new WorkflowPersonalTaskCursor(CREATED.toInstant(ZoneOffset.UTC), 0), 50));
        for (var query : invalid) {
            assertEquals(Category.INVALID_INPUT, assertThrows(WorkflowExecutionException.class,
                    () -> reads.listPersonalTasks(ACTOR, query, task -> true)).category());
        }
        verifyNoInteractions(tasks, pins, eligibility);
    }

    private WorkflowRuntimeQueryService reads(RuntimeJson json) {
        return new WorkflowRuntimeQueryService(null, null, tasks, null, pins, json, null, eligibility);
    }

    private List<Integer> candidates(List<RuntimeTaskAdministrationRow> rows) {
        var fetchSizes = new ArrayList<Integer>();
        when(tasks.findPersonalCandidatesPage(eq(ACTOR), eq(10), isNull(), isNull(), anyInt(),
                isNull(), isNull(), anyInt(), isNull(), anyInt(), isNull(), any(), anyLong(), any()))
                .thenAnswer(call -> {
                    LocalDateTime afterCreatedAt = call.getArgument(11);
                    long afterId = call.getArgument(12);
                    Pageable pageable = call.getArgument(13);
                    fetchSizes.add(pageable.getPageSize());
                    return rows.stream().filter(row -> afterCreatedAt == null || row.getCreatedAt().isBefore(afterCreatedAt)
                                    || row.getCreatedAt().equals(afterCreatedAt) && row.getId() < afterId)
                            .limit(pageable.getPageSize()).toList();
                });
        return fetchSizes;
    }

    private static RuntimeTaskAdministrationRow row(long id, TaskStatus status, String assignee) {
        var row = mock(RuntimeTaskAdministrationRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getActivityId()).thenReturn(3L);
        when(row.getInstanceId()).thenReturn(4L);
        when(row.getTaskTypeId()).thenReturn(5L);
        when(row.getTaskKey()).thenReturn("task");
        when(row.getAssignmentKind()).thenReturn(AssignmentTargetKind.GROUP);
        when(row.getAssignmentRef()).thenReturn("finance ");
        when(row.getAssigneeRef()).thenReturn(assignee);
        when(row.getStatus()).thenReturn(status);
        when(row.getCreatedAt()).thenReturn(CREATED);
        when(row.getDefinitionVersionId()).thenReturn(1L);
        when(row.getNodeKey()).thenReturn("review");
        return row;
    }

    private static WorkflowPersonalTaskQuery query(WorkflowPersonalTaskCursor after, int limit) {
        return new WorkflowPersonalTaskQuery(null, null, null, null, null, null, after, limit, null);
    }

    private static WorkflowPersonalTaskCursor cursor(long id) {
        return new WorkflowPersonalTaskCursor(CREATED.toInstant(ZoneOffset.UTC), id);
    }

    private static List<Long> taskIds(WorkflowPersonalTaskPage page) {
        return page.items().stream().map(WorkflowTaskView::id).toList();
    }
}
