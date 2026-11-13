package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.repository.runtime.PreviousDecisionRow;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain repository doubles exercise projection integrity and encoded page bounds without a database. */
class PreviousDecisionsUnitTest {
    private final WorkflowActivityRepository activities = mock(WorkflowActivityRepository.class);
    private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
    private final WorkflowHistoryRepository history = mock(WorkflowHistoryRepository.class);
    private final RuntimeJson json = new RuntimeJson();
    private final RuntimePreviousDecisions reader = new RuntimePreviousDecisions(activities, tasks, history, json);
    private final MinimalWorkflowDefinition model = mock(MinimalWorkflowDefinition.class);
    private final MinimalWorkflowDefinition.Human human = mock(MinimalWorkflowDefinition.Human.class);
    private final MinimalWorkflowDefinition.Human receivingHuman = mock(MinimalWorkflowDefinition.Human.class);
    private final LocalDateTime time = LocalDateTime.of(2026, 11, 13, 10, 0);
    private WorkflowActivityEntity receiving, source;
    private static final long SOURCE_ID = 9007199254740993L;

    @BeforeEach void setup() {
        receiving = activity(20, "later", 200);
        source = activity(SOURCE_ID, "earlier", 2);
        when(source.getStatus()).thenReturn(io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.COMPLETED);
        when(source.getClosedAt()).thenReturn(time);
        when(source.getClosedBy()).thenReturn("actual-user");
        when(source.getClosureReason()).thenReturn("TASK_OUTCOME");
        when(source.getOutcomeKey()).thenReturn("APPROVE");
        when(model.humans()).thenReturn(Map.of("later", receivingHuman, "earlier", human));
        when(receivingHuman.previousDecisions()).thenReturn(PreviousDecisionsPolicy.ALL_PREVIOUS);
        when(human.key()).thenReturn("earlier"); when(human.name()).thenReturn("Pinned stage");
        when(human.resultContract()).thenReturn(TaskResultContract.FORBIDDEN);
        when(human.outcome("APPROVE")).thenReturn(Optional.of(new MinimalWorkflowDefinition.Outcome("APPROVE", "Pinned approve", false)));
        when(history.findActivityFacts(20L, "ACTIVITY_ENTERED", PageRequest.of(0, 2)))
                .thenReturn(List.of(fact(200, "ACTIVITY_ENTERED", 20, null, (short) 2,
                        "{\"nodeKey\":\"later\",\"activationSequence\":\"200\",\"taskTypeId\":\"1\",\"completionPolicy\":\"SINGLE\",\"requiredTaskCount\":1}")));
        when(history.findActivityFacts(SOURCE_ID, "ACTIVITY_COMPLETED", PageRequest.of(0, 2)))
                .thenReturn(List.of(fact(100, "ACTIVITY_COMPLETED", SOURCE_ID, null, (short) 2,
                        "{\"outcomeKey\":\"APPROVE\",\"closureReason\":\"TASK_OUTCOME\"}")));
        when(activities.findPreviousSources(eq(1L), eq(20L), anyCollection(), eq(200L), eq(0L), any()))
                .thenReturn(List.of(source));
    }

    @Test void noneAndTerminalCursorDoNotScanSourceHistory() {
        when(receivingHuman.previousDecisions()).thenReturn(PreviousDecisionsPolicy.NONE);
        assertTrue(reader.read(receiving, model, 0, 20).items().isEmpty());
        verifyNoInteractions(history, activities, tasks);
        when(receivingHuman.previousDecisions()).thenReturn(PreviousDecisionsPolicy.ALL_PREVIOUS);
        assertTrue(reader.read(receiving, model, 200, 20).items().isEmpty());
        verifyNoInteractions(activities, tasks);
    }

    @Test void exactAttributionUsesEvidenceAndPinnedLabelsWithoutResults() {
        var row = row(5, " \n<b>safe text</b>\t");
        when(history.findPreviousDecisionPage(anyCollection(), eq(0L), any())).thenReturn(List.of(row));
        var page = reader.read(receiving, model, 0, 20);
        var item = page.items().get(0);
        assertEquals(SOURCE_ID, item.activityId()); assertEquals(SOURCE_ID + 5, item.taskId());
        assertEquals("Pinned stage", item.nodeName()); assertEquals("Pinned approve", item.outcomeLabel());
        assertEquals("actual-user", item.actorRef()); assertEquals(time.toInstant(ZoneOffset.UTC), item.occurredAt());
        assertEquals(" \n<b>safe text</b>\t", item.comment()); assertNull(page.nextAfterSequence());
        String encoded = json.encode(page);
        assertTrue(encoded.contains("\"activityId\":\"" + SOURCE_ID + "\""));
        assertFalse(encoded.contains("result")); assertFalse(encoded.contains("inputs"));
    }

    @Test void encodedByteBoundContinuesWholeEscapedEntriesExactlyOnce() {
        var rows = new ArrayList<PreviousDecisionRow>();
        for (int i = 3; i < 54; i++) rows.add(row(i, "\u0001".repeat(2000)));
        when(history.findPreviousDecisionPage(anyCollection(), anyLong(), any())).thenAnswer(call ->
                rows.stream().filter(row -> row.fact().getId().getSequenceNumber() > (long) call.getArgument(1)).toList());
        var seen = new ArrayList<Long>(); long cursor = 0;
        do {
            var page = reader.read(receiving, model, cursor, 50);
            assertTrue(json.encode(page).length() * 2L <= 256 * 1024);
            assertTrue(RuntimeJson.utf8Fits(json.encode(page), 256 * 1024));
            assertFalse(page.items().isEmpty());
            for (var item : page.items()) { seen.add(item.historySequence()); assertEquals("\u0001".repeat(2000), item.comment()); }
            if (page.nextAfterSequence() == null) break;
            cursor = page.nextAfterSequence();
        } while (true);
        assertEquals(rows.stream().map(row -> row.fact().getId().getSequenceNumber()).toList(), seen);
    }

    @Test void selectedKeysArePushedToPersistenceAndSkippedSourceIsEmpty() {
        when(receivingHuman.previousDecisions()).thenReturn(new PreviousDecisionsPolicy(PreviousDecisionsPolicy.Mode.SELECTED, List.of("earlier")));
        when(activities.findPreviousSources(eq(1L), eq(20L), eq(java.util.Set.of("earlier")), eq(200L), eq(0L), any())).thenReturn(List.of());
        assertTrue(reader.read(receiving, model, 0, 20).items().isEmpty());
        verifyNoInteractions(tasks);
    }

    @Test void missingAndDuplicateCutoffsAndCompletedTaskFactsFailExplicitly() {
        var duplicate = fact(201, "ACTIVITY_ENTERED", 20, null, (short) 2, "{}");
        when(history.findActivityFacts(20L, "ACTIVITY_ENTERED", PageRequest.of(0, 2))).thenReturn(List.of(duplicate, duplicate));
        hostFailure(() -> reader.read(receiving, model, 0, 20));
        when(history.findActivityFacts(20L, "ACTIVITY_ENTERED", PageRequest.of(0, 2))).thenReturn(List.of());
        hostFailure(() -> reader.read(receiving, model, 0, 20));
    }

    @Test void missingTaskFactAndMismatchedOutcomeCannotDisappearAsEmpty() {
        when(tasks.findPreviousTasksWithoutUniqueFact(anyCollection(), any())).thenReturn(List.of(99L));
        hostFailure(() -> reader.read(receiving, model, 0, 20));
        when(tasks.findPreviousTasksWithoutUniqueFact(anyCollection(), any())).thenReturn(List.of());
        var row = row(5, null); when(row.task().getOutcomeKey()).thenReturn("WRONG");
        when(history.findPreviousDecisionPage(anyCollection(), anyLong(), any())).thenReturn(List.of(row));
        hostFailure(() -> reader.read(receiving, model, 0, 20));
    }

    private void hostFailure(Runnable action) {
        assertEquals(WorkflowExecutionException.Category.HOST_CONFIGURATION,
                assertThrows(WorkflowExecutionException.class, action::run).category());
    }
    private WorkflowActivityEntity activity(long id, String key, long activation) {
        var activity = mock(WorkflowActivityEntity.class);
        when(activity.getId()).thenReturn(id); when(activity.getInstanceId()).thenReturn(1L);
        when(activity.getNodeKey()).thenReturn(key); when(activity.getActivationSequence()).thenReturn(activation);
        when(activity.getEnteredAt()).thenReturn(time); when(activity.getTaskTypeId()).thenReturn(1L);
        when(activity.getCompletionPolicy()).thenReturn(CompletionPolicy.SINGLE); when(activity.getRequiredTaskCount()).thenReturn(1);
        return activity;
    }
    private PreviousDecisionRow row(long sequence, String comment) {
        var task = mock(WorkflowTaskEntity.class);
        when(task.getId()).thenReturn(SOURCE_ID + sequence); when(task.getActivityId()).thenReturn(SOURCE_ID);
        when(task.getStatus()).thenReturn(TaskStatus.COMPLETED); when(task.getClosureReason()).thenReturn(TaskClosureReason.TASK_OUTCOME);
        when(task.getAssignmentKind()).thenReturn(AssignmentTargetKind.USER); when(task.getAssignmentRef()).thenReturn("actual-user");
        when(task.getClosedBy()).thenReturn("actual-user"); when(task.getClosedAt()).thenReturn(time); when(task.getCreatedAt()).thenReturn(time);
        when(task.getOutcomeKey()).thenReturn("APPROVE");
        var payload = new java.util.LinkedHashMap<String,Object>(); payload.put("outcomeKey", "APPROVE"); payload.put("comment", comment); payload.put("inputs", Map.of());
        return new PreviousDecisionRow(task, fact(sequence, "TASK_COMPLETED", SOURCE_ID, task.getId(), (short) 2, json.encode(payload)));
    }
    private WorkflowHistoryEntity fact(long sequence, String event, long activity, Long task, short version, String details) {
        return new WorkflowHistoryEntity(new WorkflowHistorySummary(1, sequence, event, UUID.randomUUID(), null,
                ActorKind.USER, "actual-user", time.toInstant(ZoneOffset.UTC), activity, task, null, null, null, version), details);
    }
}
