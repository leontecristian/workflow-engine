package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.controller.runtime.ConsoleInstanceGraphController;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView.State;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleInstanceGraph;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure public-core-service doubles, actual host access policy/codec, no Spring context or database.
 */
class ConsoleInstanceGraphUnitTest {
    private static final long ID = 9007199254740993L;
    private static final long TASK = 9007199254740995L;
    private static final long ACTIVITY = 9007199254740997L;
    private static final String DOCUMENT =
            """
            {"key":"review","version":2,"nodes":[
              {"key":"DONE","name":"Finished","type":"END"},
              {"key":"REVIEW","name":"Review <script>","type":"HUMAN_TASK",
               "completion":{"policy":"SINGLE"},
               "assignment":{"resolver":"private-resolver","inputs":{"secret":"private-input"}}},
              {"key":"BEGIN","name":"Start","type":"START"}],
             "transitions":[{"key":"finish","from":"REVIEW","to":"DONE","outcome":"COMPLETE"},
                            {"key":"begin","from":"BEGIN","to":"REVIEW"}],
             "designer":{"privateMetadata":"never disclose"}}
            """;
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowDefinitionService definitions = mock(WorkflowDefinitionService.class);
    private final ConsoleContext context = mock(ConsoleContext.class);
    private final ConsoleWire wire = new ConsoleWire();
    private final ConsoleInstanceGraph graphs =
            new ConsoleInstanceGraph(
                    new ConsoleRuntimeAccess(reads, context), definitions, reads, wire);

    private WorkflowInstanceView instance(InstanceStatus status, long sequence) {
        return new WorkflowInstanceView(
                ID,
                7,
                "review",
                2,
                "pinned-hash",
                null,
                null,
                status == InstanceStatus.COMPLETED ? "DONE" : "REVIEW",
                status,
                status == InstanceStatus.COMPLETED ? "COMPLETED" : null,
                Instant.EPOCH,
                "admin",
                null,
                null,
                null,
                sequence,
                9007199254740999L);
    }

    private void setup(InstanceStatus status, long sequence) {
        when(context.admin()).thenReturn(true);
        when(reads.findInstance(ID)).thenReturn(Optional.of(instance(status, sequence)));
        var summary = mock(DefinitionVersionSummary.class);
        when(summary.id()).thenReturn(7L);
        when(summary.key()).thenReturn("review");
        when(summary.version()).thenReturn(2);
        when(summary.contentHash()).thenReturn("pinned-hash");
        when(summary.status()).thenReturn(DefinitionStatus.RETIRED);
        when(definitions.findVersion(7))
                .thenReturn(Optional.of(new DefinitionVersionView(summary, DOCUMENT, null)));
        when(reads.listHistory(ID, 0, 100)).thenReturn(new WorkflowHistoryPage(facts(), null));
    }

    private WorkflowHistorySummary fact(
            long sequence, String type, String key, String from, String to) {
        return new WorkflowHistorySummary(
                ID,
                sequence,
                type,
                UUID.randomUUID(),
                "unit-correlation",
                ActorKind.USER,
                "reviewer",
                Instant.EPOCH,
                sequence == 3 || sequence == 4 || sequence == 5 || sequence == 6 ? ACTIVITY : null,
                sequence == 4 || sequence == 5 ? TASK : null,
                from,
                to,
                key,
                (short) 1);
    }

    private List<WorkflowHistorySummary> facts() {
        return List.of(
                fact(1, "INSTANCE_STARTED", null, null, null),
                fact(2, "TRANSITION_TAKEN", "begin", "BEGIN", "REVIEW"),
                fact(3, "ACTIVITY_ENTERED", null, null, null),
                fact(4, "TASK_CREATED", null, null, null),
                fact(5, "TASK_COMPLETED", null, null, null),
                fact(6, "ACTIVITY_COMPLETED", null, null, null),
                fact(7, "TRANSITION_TAKEN", "finish", "REVIEW", "DONE"),
                fact(8, "INSTANCE_COMPLETED", null, null, null));
    }

    @Test
    void pinnedRetiredGraphIsOrderedByEdgesAndLaterCompletionFactsAreExcluded() {
        setup(InstanceStatus.ACTIVE, 4);
        var graph = graphs.find(ID);
        assertEquals(
                List.of("BEGIN", "REVIEW", "DONE"),
                graph.nodes().stream().map(n -> n.key()).toList());
        assertEquals(
                List.of(State.COMPLETED, State.ACTIVE, State.PENDING),
                graph.nodes().stream().map(n -> n.state()).toList());
        assertEquals(TASK, graph.nodes().get(1).taskId());
        assertEquals(ACTIVITY, graph.nodes().get(1).activityId());
        assertEquals(List.of(false, true), graph.edges().stream().map(e -> e.traversed()).toList());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        verify(definitions).findVersion(7);
        verifyNoMoreInteractions(definitions);
    }

    @Test
    void completedInstanceHasNoActiveNodeAndEndRequiresItsRecordedFact() {
        setup(InstanceStatus.COMPLETED, 8);
        var graph = graphs.find(ID);
        assertTrue(graph.nodes().stream().allMatch(n -> n.state() == State.COMPLETED));
        assertTrue(graph.edges().stream().allMatch(e -> e.traversed()));
        when(reads.listHistory(ID, 0, 100))
                .thenReturn(
                        new WorkflowHistoryPage(
                                List.of(
                                        facts().get(0),
                                        facts().get(1),
                                        facts().get(2),
                                        facts().get(3),
                                        facts().get(4),
                                        facts().get(5),
                                        facts().get(6),
                                        fact(8, "OTHER", null, null, null)),
                                null));
        assertEquals(
                "HOST_CONFIGURATION",
                assertThrows(ConsoleException.class, () -> graphs.find(ID)).code());
    }

    @Test
    void unrelatedReviewerIsDeniedBeforeDefinitionOrHistoryDisclosure() {
        when(reads.findInstance(ID)).thenReturn(Optional.of(instance(InstanceStatus.ACTIVE, 4)));
        when(context.actor()).thenReturn("other");
        when(reads.listTasks(ID, 0, 100)).thenReturn(new WorkflowTaskPage(List.of(), null));
        assertEquals(404, assertThrows(ConsoleException.class, () -> graphs.find(ID)).status());
        verifyNoInteractions(definitions);
        verify(reads, never()).listHistory(anyLong(), anyLong(), anyInt());
    }

    @Test
    void assignedReviewerCanReadProjectionWithoutDefinitionManagementPrivilege() {
        setup(InstanceStatus.ACTIVE, 4);
        when(context.admin()).thenReturn(false);
        when(context.actor()).thenReturn("reviewer");
        var task = mock(WorkflowTaskView.class);
        when(task.assignedUserRef()).thenReturn("reviewer");
        when(reads.listTasks(ID, 0, 100)).thenReturn(new WorkflowTaskPage(List.of(task), null));
        assertEquals(State.ACTIVE, graphs.find(ID).nodes().get(1).state());
    }

    @Test
    void missingPrefixAndMismatchedTransitionFailRatherThanInventingProgress() {
        setup(InstanceStatus.ACTIVE, 4);
        when(reads.listHistory(ID, 0, 100))
                .thenReturn(new WorkflowHistoryPage(facts().subList(1, 4), null));
        assertEquals(500, assertThrows(ConsoleException.class, () -> graphs.find(ID)).status());
        when(reads.listHistory(ID, 0, 100))
                .thenReturn(
                        new WorkflowHistoryPage(
                                List.of(
                                        facts().get(0),
                                        fact(2, "TRANSITION_TAKEN", "begin", "REVIEW", "BEGIN"),
                                        facts().get(2),
                                        facts().get(3)),
                                null));
        assertEquals(500, assertThrows(ConsoleException.class, () -> graphs.find(ID)).status());
    }

    @Test
    void historyWorkIsBoundedBeforeDefinitionRead() {
        when(context.admin()).thenReturn(true);
        when(reads.findInstance(ID)).thenReturn(Optional.of(instance(InstanceStatus.ACTIVE, 101)));
        assertEquals(422, assertThrows(ConsoleException.class, () -> graphs.find(ID)).status());
        verifyNoInteractions(definitions);
    }

    @Test
    void unsupportedShapeOrPinMismatchCannotProduceMisleadingDiagram() {
        setup(InstanceStatus.ACTIVE, 4);
        var pinned = definitions.findVersion(7).orElseThrow();
        when(definitions.findVersion(7))
                .thenReturn(
                        Optional.of(
                                new DefinitionVersionView(
                                        pinned.summary(), "invalid stored JSON", null)));
        assertEquals(500, assertThrows(ConsoleException.class, () -> graphs.find(ID)).status());
        when(definitions.findVersion(7))
                .thenReturn(
                        Optional.of(
                                new DefinitionVersionView(
                                        pinned.summary(),
                                        DOCUMENT.replace("SINGLE", "ALL"),
                                        null)));
        assertEquals(
                "HOST_CONFIGURATION",
                assertThrows(ConsoleException.class, () -> graphs.find(ID)).code());
        when(definitions.findVersion(7)).thenReturn(Optional.of(pinned));
        when(pinned.summary().contentHash()).thenReturn("different-hash");
        assertEquals(
                "HOST_CONFIGURATION",
                assertThrows(ConsoleException.class, () -> graphs.find(ID)).code());
    }

    @Test
    void wireKeepsExactIdentifiersAndExcludesPrivateDefinitionContent() {
        setup(InstanceStatus.ACTIVE, 4);
        var controller = new ConsoleInstanceGraphController(graphs, wire);
        var json = controller.graph(Long.toString(ID), new MockHttpServletRequest()).getBody();
        assertTrue(json.contains("\"id\":\"9007199254740993\""));
        assertTrue(json.contains("\"taskId\":\"9007199254740995\""));
        assertTrue(json.contains("\"lockVersion\":\"9007199254740999\""));
        for (String excluded :
                List.of(
                        "private-resolver",
                        "private-input",
                        "privateMetadata",
                        "documentJson",
                        "designer")) assertFalse(json.contains(excluded));
        assertTrue(
                json.contains(
                        "Review <script>")); // Text remains text; Angular never uses innerHTML.
        var invalid = new MockHttpServletRequest();
        invalid.addParameter("definitionVersionId", "8");
        assertEquals(
                400,
                assertThrows(
                                ConsoleException.class,
                                () -> controller.graph(Long.toString(ID), invalid))
                        .status());
    }

    @Test
    void cancellationIsNotGivenInventedExecutionProgress() {
        when(context.admin()).thenReturn(true);
        when(reads.findInstance(ID)).thenReturn(Optional.of(instance(InstanceStatus.CANCELLED, 4)));
        assertEquals(
                "HOST_CONFIGURATION",
                assertThrows(ConsoleException.class, () -> graphs.find(ID)).code());
        verifyNoInteractions(definitions);
        assertEquals(
                InstanceStatus.CANCELLED,
                new ConsoleRuntimeAccess(reads, context).instance(ID).status());
    }
}
