package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView.State;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleInstanceGraph;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** General display evidence only; synthetic branch/loop fixtures do not enable engine features. */
class ConsoleGeneralGraphUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowDefinitionService definitions = mock(WorkflowDefinitionService.class);
    private final ConsoleContext context = mock(ConsoleContext.class);
    private final ConsoleWire wire = new ConsoleWire();
    private final ConsoleInstanceGraph graphs = new ConsoleInstanceGraph(new ConsoleRuntimeAccess(reads, context), definitions, reads, wire);

    private ObjectNode document(String... pairs) {
        var root = json.createObjectNode(); var nodes = root.putArray("nodes"); root.putArray("transitions");
        for (int i = 0; i < pairs.length; i += 2)
            nodes.addObject().put("key", pairs[i]).put("type", pairs[i + 1]).put("name", pairs[i]);
        return root;
    }
    private void edge(ObjectNode doc, String key, String from, String to) {
        ((ArrayNode) doc.get("transitions")).addObject().put("key", key).put("from", from).put("to", to);
    }
    private WorkflowHistorySummary fact(long seq, String type, Long activity, Long task, String edge, String from, String to) {
        return new WorkflowHistorySummary(1, seq, type, new UUID(0, 1), "unit", ActorKind.USER, "reviewer", Instant.EPOCH,
                activity, task, from, to, edge, (short) 1);
    }
    private WorkflowHistorySummary event(long seq, String type) { return fact(seq, type, null, null, null, null, null); }
    private WorkflowHistorySummary transition(long seq, String key, String from, String to) { return fact(seq, "TRANSITION_TAKEN", null, null, key, from, to); }
    private void setup(ObjectNode document, List<WorkflowHistorySummary> facts, InstanceStatus status, String position, long watermark) {
        when(context.admin()).thenReturn(true);
        when(reads.findInstance(1)).thenReturn(Optional.of(new WorkflowInstanceView(1, 7, "flow", 1, "hash", null, null,
                position, status, status == InstanceStatus.COMPLETED ? "DONE" : null, Instant.EPOCH, "admin", null, null, null, watermark, 1)));
        var summary = mock(DefinitionVersionSummary.class);
        when(summary.id()).thenReturn(7L); when(summary.key()).thenReturn("flow"); when(summary.version()).thenReturn(1); when(summary.contentHash()).thenReturn("hash");
        when(definitions.findVersion(7)).thenReturn(Optional.of(new DefinitionVersionView(summary, document.toString(), null)));
        doAnswer(call -> {
            long after = call.getArgument(1); int count = call.getArgument(2);
            var remaining = facts.stream().filter(f -> f.historySequence() > after).toList();
            var page = remaining.stream().limit(count).toList();
            return new WorkflowHistoryPage(page, remaining.size() > count ? page.get(page.size() - 1).historySequence() : null);
        }).when(reads).listHistory(anyLong(), anyLong(), anyInt());
    }
    private InstanceGraphView.Node node(InstanceGraphView graph, String key) { return graph.nodes().stream().filter(n -> key.equals(n.key())).findFirst().orElseThrow(); }

    @Test void serviceChainProjectsEveryNodeBeforeAndAfterCompletionWithoutPrivateSettings() {
        var doc = document("DONE", "END", "AFTER", "SERVICE_TASK", "REVIEW", "HUMAN_TASK", "BEFORE", "SERVICE_TASK", "BEGIN", "START");
        ((ObjectNode) doc.path("nodes").get(1)).put("action", "private-action").putObject("outputs").put("private", "never expose");
        edge(doc, "start", "BEGIN", "BEFORE"); edge(doc, "review", "BEFORE", "REVIEW"); edge(doc, "finish", "REVIEW", "AFTER"); edge(doc, "end", "AFTER", "DONE");
        var facts = List.of(event(1, "INSTANCE_STARTED"), event(2, "START_INPUTS_ACCEPTED"), event(3, "VARIABLE_WRITTEN"),
                transition(4, "start", "BEGIN", "BEFORE"), event(5, "SERVICE_TASK_COMPLETED"), event(6, "VARIABLE_WRITTEN"), transition(7, "review", "BEFORE", "REVIEW"),
                fact(8, "ACTIVITY_ENTERED", 101L, null, null, null, null), fact(9, "TASK_CREATED", 101L, 201L, null, null, null),
                event(10, "TASK_COMPLETED"), event(11, "ACTIVITY_COMPLETED"), transition(12, "finish", "REVIEW", "AFTER"),
                event(13, "SERVICE_TASK_COMPLETED"), event(14, "VARIABLE_WRITTEN"), transition(15, "end", "AFTER", "DONE"), event(16, "INSTANCE_COMPLETED"));
        setup(doc, facts, InstanceStatus.ACTIVE, "REVIEW", 9);
        var paused = graphs.find(1);
        assertEquals(List.of("BEGIN", "BEFORE", "REVIEW", "AFTER", "DONE"), paused.nodes().stream().map(InstanceGraphView.Node::key).toList());
        assertEquals(List.of(State.COMPLETED, State.COMPLETED, State.ACTIVE, State.PENDING, State.PENDING), paused.nodes().stream().map(InstanceGraphView.Node::state).toList());
        assertEquals(201L, node(paused, "REVIEW").taskId()); assertNull(node(paused, "BEFORE").activityId());
        assertFalse(wire.json(paused).contains("private-action")); assertFalse(wire.json(paused).contains("never expose"));
        setup(doc, facts, InstanceStatus.COMPLETED, "DONE", 16);
        assertTrue(graphs.find(1).nodes().stream().allMatch(n -> n.state() == State.COMPLETED));
    }

    @Test void branchesRepeatedTransitionsAndLatestActivationRetainHonestStateAndLinks() {
        var doc = document("BEGIN", "START", "SPLIT", "EXCLUSIVE_GATEWAY", "LEFT", "HUMAN_TASK", "RIGHT", "CUSTOM_NODE", "LOOP", "SERVICE_TASK", "DONE", "END");
        edge(doc, "start", "BEGIN", "SPLIT"); edge(doc, "select", "SPLIT", "LEFT"); edge(doc, "unused", "SPLIT", "RIGHT");
        edge(doc, "continue", "LEFT", "LOOP"); edge(doc, "self", "LOOP", "LOOP"); edge(doc, "back", "LOOP", "LEFT"); edge(doc, "exit", "LOOP", "DONE");
        var facts = List.of(event(1, "INSTANCE_STARTED"), transition(2, "start", "BEGIN", "SPLIT"), transition(3, "select", "SPLIT", "LEFT"),
                fact(4, "ACTIVITY_ENTERED", 100L, null, null, null, null), fact(5, "TASK_CREATED", 100L, 200L, null, null, null), fact(6, "TASK_CREATED", 100L, 201L, null, null, null),
                transition(7, "continue", "LEFT", "LOOP"), transition(8, "self", "LOOP", "LOOP"), transition(9, "self", "LOOP", "LOOP"), transition(10, "back", "LOOP", "LEFT"),
                fact(11, "ACTIVITY_ENTERED", 101L, null, null, null, null), fact(12, "TASK_CREATED", 101L, 202L, null, null, null));
        setup(doc, facts, InstanceStatus.ACTIVE, "LEFT", 6);
        var multiple = node(graphs.find(1), "LEFT"); assertEquals(100L, multiple.activityId()); assertNull(multiple.taskId());
        setup(doc, facts, InstanceStatus.ACTIVE, "LEFT", 12);
        var revisited = graphs.find(1); var left = node(revisited, "LEFT");
        assertEquals(State.ACTIVE, left.state()); assertEquals(101L, left.activityId()); assertEquals(202L, left.taskId());
        assertEquals(State.PENDING, node(revisited, "RIGHT").state()); assertEquals("CUSTOM_NODE", node(revisited, "RIGHT").type());
        assertEquals(State.COMPLETED, node(revisited, "LOOP").state());
        assertFalse(revisited.edges().stream().filter(e -> e.key().equals("unused") || e.key().equals("exit")).anyMatch(InstanceGraphView.Edge::traversed));
        verify(reads, never()).findActivity(anyLong()); verify(reads, never()).findHistoryEvent(anyLong(), anyLong());
    }

    @Test void multipleHumanStagesKeepTheirOwnRecordedIds() {
        var doc = document("BEGIN", "START", "FIRST", "HUMAN_TASK", "SECOND", "HUMAN_TASK", "DONE", "END");
        edge(doc, "enter", "BEGIN", "FIRST"); edge(doc, "next", "FIRST", "SECOND"); edge(doc, "end", "SECOND", "DONE");
        var facts = List.of(event(1, "INSTANCE_STARTED"), transition(2, "enter", "BEGIN", "FIRST"), fact(3, "ACTIVITY_ENTERED", 100L, null, null, null, null),
                fact(4, "TASK_CREATED", 100L, 200L, null, null, null), event(5, "TASK_COMPLETED"), event(6, "ACTIVITY_COMPLETED"), transition(7, "next", "FIRST", "SECOND"),
                fact(8, "ACTIVITY_ENTERED", 101L, null, null, null, null), fact(9, "TASK_CREATED", 101L, 201L, null, null, null));
        setup(doc, facts, InstanceStatus.ACTIVE, "SECOND", 9);
        var graph = graphs.find(1); assertEquals(200L, node(graph, "FIRST").taskId()); assertEquals(State.COMPLETED, node(graph, "FIRST").state());
        assertEquals(201L, node(graph, "SECOND").taskId()); assertEquals(State.ACTIVE, node(graph, "SECOND").state());
    }

    @Test void pagedPrefixStopsAtSnapshotWatermarkAndRejectsGapsOrNonadvancingPages() {
        var doc = document("BEGIN", "START", "REVIEW", "HUMAN_TASK", "DONE", "END"); edge(doc, "enter", "BEGIN", "REVIEW"); edge(doc, "end", "REVIEW", "DONE");
        List<WorkflowHistorySummary> facts = new ArrayList<>(); facts.add(event(1, "INSTANCE_STARTED"));
        for (int i = 2; i <= 150; i++) facts.add(event(i, "VARIABLE_WRITTEN"));
        facts.add(transition(151, "enter", "BEGIN", "REVIEW")); facts.add(fact(152, "ACTIVITY_ENTERED", 100L, null, null, null, null));
        facts.add(fact(153, "TASK_CREATED", 100L, 200L, null, null, null)); facts.add(transition(154, "end", "REVIEW", "DONE")); facts.add(event(155, "INSTANCE_COMPLETED"));
        setup(doc, facts, InstanceStatus.ACTIVE, "REVIEW", 153);
        assertEquals(State.PENDING, node(graphs.find(1), "DONE").state()); verify(reads).listHistory(1, 100, 100);
        when(reads.listHistory(1, 100, 100)).thenReturn(new WorkflowHistoryPage(facts.subList(101, 153), null));
        assertEquals(500, assertThrows(ConsoleException.class, () -> graphs.find(1)).status());
        when(reads.listHistory(1, 100, 100)).thenReturn(new WorkflowHistoryPage(List.of(event(154, "OTHER")), 100L));
        assertEquals(500, assertThrows(ConsoleException.class, () -> graphs.find(1)).status());
    }

    @Test void malformedTopologyAndInconsistentRecordedRouteFailClosed() {
        var doc = document("BEGIN", "START", "REVIEW", "HUMAN_TASK", "DONE", "END"); edge(doc, "enter", "BEGIN", "REVIEW"); edge(doc, "end", "REVIEW", "DONE");
        var facts = List.of(event(1, "INSTANCE_STARTED"), transition(2, "enter", "REVIEW", "BEGIN"));
        setup(doc, facts, InstanceStatus.ACTIVE, "REVIEW", 2); assertThrows(ConsoleException.class, () -> graphs.find(1));
        ((ObjectNode) doc.path("transitions").get(0)).put("to", "MISSING");
        setup(doc, List.of(event(1, "INSTANCE_STARTED")), InstanceStatus.ACTIVE, "BEGIN", 1); assertThrows(ConsoleException.class, () -> graphs.find(1));
        ((ObjectNode) doc.path("transitions").get(0)).put("to", "REVIEW");
        ((ArrayNode) doc.get("nodes")).add(doc.path("nodes").get(0));
        setup(doc, List.of(event(1, "INSTANCE_STARTED")), InstanceStatus.ACTIVE, "BEGIN", 1); assertThrows(ConsoleException.class, () -> graphs.find(1));
        var tooMany = document("BEGIN", "START"); for (int i = 0; i < 200; i++) ((ArrayNode) tooMany.get("nodes")).addObject().put("key", "N" + i).put("type", "SERVICE_TASK").put("name", "Node");
        setup(tooMany, List.of(event(1, "INSTANCE_STARTED")), InstanceStatus.ACTIVE, "BEGIN", 1); assertThrows(ConsoleException.class, () -> graphs.find(1));
    }

    @Test void completeResponseByteBudgetIsCheckedAfterAllowlisting() {
        var doc = document("BEGIN", "START");
        var nodes = (ArrayNode) doc.get("nodes"); var keys = new ArrayList<String>(); keys.add("B".repeat(100));
        ((ObjectNode) nodes.get(0)).put("key", keys.get(0)).put("name", "\u0001".repeat(200));
        for (int i = 1; i < 200; i++) {
            String key = String.format(java.util.Locale.ROOT, "N%03d", i) + "x".repeat(96); keys.add(key);
            nodes.addObject().put("key", key).put("type", "T".repeat(100)).put("name", "\u0001".repeat(200));
        }
        for (int i = 0; i < 500; i++) {
            edge(doc, String.format(java.util.Locale.ROOT, "E%03d", i) + "x".repeat(96), keys.get(i % 200), keys.get((i + 1) % 200));
            ((ObjectNode) doc.path("transitions").get(i)).put("outcome", "O".repeat(100));
        }
        setup(doc, List.of(event(1, "INSTANCE_STARTED")), InstanceStatus.ACTIVE, keys.get(0), 1);
        assertEquals(422, assertThrows(ConsoleException.class, () -> graphs.find(1)).status());
    }
}
