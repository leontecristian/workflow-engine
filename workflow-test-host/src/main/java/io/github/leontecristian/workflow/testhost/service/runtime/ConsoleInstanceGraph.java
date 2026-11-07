package io.github.leontecristian.workflow.testhost.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView.Edge;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView.Node;
import io.github.leontecristian.workflow.testhost.dto.runtime.InstanceGraphView.State;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorized topology and retained progress, independent of executable node capabilities. */
@Service
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleInstanceGraph {
    private static final long MAX_HISTORY = 12_000;
    private final ConsoleRuntimeAccess access;
    private final WorkflowDefinitionService definitions;
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleWire wire;

    public ConsoleInstanceGraph(ConsoleRuntimeAccess access, WorkflowDefinitionService definitions,
            WorkflowRuntimeQueryService reads, ConsoleWire wire) {
        this.access = access;
        this.definitions = definitions;
        this.reads = reads;
        this.wire = wire;
    }

    @Transactional(readOnly = true)
    public InstanceGraphView find(long id) {
        // Authorize first, before disclosing pinned topology or reading retained evidence.
        var instance = access.instance(id);
        require(instance.status() == InstanceStatus.ACTIVE || instance.status() == InstanceStatus.COMPLETED);
        long watermark = instance.lastHistorySequence();
        if (watermark > MAX_HISTORY) throw new ConsoleException(422, "LIMIT_EXCEEDED");
        require(watermark > 0);
        var version = definitions.findVersion(instance.definitionVersionId()).orElseThrow(ConsoleInstanceGraph::unavailable);
        require(version.summary().id() == instance.definitionVersionId()
                && Objects.equals(version.summary().contentHash(), instance.definitionHash())
                && Objects.equals(version.summary().key(), instance.definitionKey())
                && version.summary().version() == instance.definitionVersion());
        JsonNode document;
        try { document = wire.tree(version.documentJson()); }
        catch (ConsoleException invalid) {
            var failure = unavailable(); failure.initCause(invalid); throw failure;
        }
        var nodes = displayNodes(document);
        var edges = displayEdges(document, nodes);
        require(nodes.containsKey(instance.currentNodeKey()));
        var start = nodes.values().stream().filter(n -> "START".equals(n.type())).findFirst().orElseThrow(ConsoleInstanceGraph::unavailable);
        var edgesByKey = new HashMap<String, Edge>();
        edges.forEach(e -> edgesByKey.put(e.key(), e));
        Set<String> completed = new HashSet<>(), traversed = new HashSet<>();
        Map<Long, String> activityNodes = new HashMap<>();
        Map<String, Links> latestLinks = new HashMap<>();
        Set<Long> taskIds = new HashSet<>();
        String position = start.key();
        boolean started = false, ended = false;
        for (var fact : prefix(id, watermark)) {
            require(!ended);
            switch (fact.eventType()) {
                case "INSTANCE_STARTED" -> {
                    require(!started && fact.historySequence() == 1);
                    started = true;
                }
                case "TRANSITION_TAKEN" -> {
                    require(started);
                    var edge = edgesByKey.get(fact.transitionKey());
                    require(edge != null && edge.from().equals(position)
                            && edge.from().equals(fact.sourceNodeKey()) && edge.to().equals(fact.targetNodeKey()));
                    completed.add(edge.from()); traversed.add(edge.key()); position = edge.to();
                }
                case "ACTIVITY_ENTERED" -> {
                    require(started && "HUMAN_TASK".equals(nodes.get(position).type())
                            && fact.activityId() != null && fact.activityId() > 0 && fact.taskId() == null
                            && activityNodes.putIfAbsent(fact.activityId(), position) == null);
                    latestLinks.put(position, new Links(fact.activityId()));
                }
                case "TASK_CREATED" -> {
                    require(fact.activityId() != null && fact.taskId() != null && fact.taskId() > 0
                            && taskIds.add(fact.taskId()) && position.equals(activityNodes.get(fact.activityId())));
                    var links = latestLinks.get(position);
                    require(links != null && links.activityId == fact.activityId().longValue());
                    links.addTask(fact.taskId());
                }
                case "INSTANCE_COMPLETED" -> {
                    require(started && instance.status() == InstanceStatus.COMPLETED
                            && position.equals(instance.currentNodeKey()) && "END".equals(nodes.get(position).type()));
                    completed.add(position); ended = true;
                }
                default -> require(started); // Scalar/service facts do not disclose values or invent topology.
            }
        }
        require(started && position.equals(instance.currentNodeKey())
                && (instance.status() != InstanceStatus.COMPLETED || ended));
        List<Node> displayedNodes = new ArrayList<>();
        var readableActivities = access.readableActivities(id, latestLinks.values().stream()
                .map(links -> links.activityId).collect(java.util.stream.Collectors.toSet()));
        for (String key : orderedKeys(start.key(), nodes, edges)) {
            var node = nodes.get(key); var links = latestLinks.get(key);
            boolean readable = links != null && readableActivities.contains(links.activityId);
            State state = instance.status() == InstanceStatus.ACTIVE && key.equals(instance.currentNodeKey())
                    ? State.ACTIVE : completed.contains(key) ? State.COMPLETED : State.PENDING;
            displayedNodes.add(new Node(key, node.name(), node.type(), state,
                    readable ? links.activityId : null, readable ? links.singleTaskId : null));
        }
        var displayedEdges = edges.stream().map(e -> new Edge(e.key(), e.from(), e.to(), e.outcome(), traversed.contains(e.key()))).toList();
        var result = new InstanceGraphView(instance, displayedNodes, displayedEdges);
        if (!wire.fits(wire.json(result), 1024 * 1024)) throw new ConsoleException(422, "LIMIT_EXCEEDED");
        return result;
    }

    /** Read only through the initial committed watermark, even if a later command commits while paging. */
    private List<WorkflowHistorySummary> prefix(long id, long watermark) {
        List<WorkflowHistorySummary> result = new ArrayList<>();
        long cursor = 0;
        while (cursor < watermark) {
            long previous = cursor;
            var page = reads.listHistory(id, cursor, 100);
            require(!page.items().isEmpty() && page.items().size() <= 100);
            for (var fact : page.items()) {
                if (fact.historySequence() > watermark) break;
                require(fact.instanceId() == id && fact.historySequence() == cursor + 1);
                result.add(fact); cursor = fact.historySequence();
            }
            if (cursor == watermark) break;
            require(cursor > previous && page.nextAfterSequence() != null && page.nextAfterSequence() == cursor);
        }
        require(result.size() == watermark);
        return result;
    }

    private Map<String, Node> displayNodes(JsonNode document) {
        var raw = document.path("nodes");
        require(raw.isArray() && !raw.isEmpty() && raw.size() <= 200);
        Map<String, Node> result = new LinkedHashMap<>();
        int starts = 0;
        for (var node : raw) {
            String key = key(node, "key"), name = text(node, "name"), type = key(node, "type");
            require(!name.isBlank() && name.codePointCount(0, name.length()) <= 200);
            require(result.putIfAbsent(key, new Node(key, name, type, State.PENDING, null, null)) == null);
            if ("START".equals(type)) starts++;
        }
        require(starts == 1);
        return result;
    }

    private List<Edge> displayEdges(JsonNode document, Map<String, Node> nodes) {
        var raw = document.path("transitions");
        require(raw.isArray() && raw.size() <= 500);
        List<Edge> result = new ArrayList<>(); Set<String> keys = new HashSet<>();
        for (var edge : raw) {
            String key = key(edge, "key"), from = key(edge, "from"), to = key(edge, "to");
            require(keys.add(key) && nodes.containsKey(from) && nodes.containsKey(to));
            result.add(new Edge(key, from, to, edge.has("outcome") ? key(edge, "outcome") : null, false));
        }
        return result;
    }

    private List<String> orderedKeys(String start, Map<String, Node> nodes, List<Edge> edges) {
        Map<String, List<String>> outgoing = new HashMap<>();
        edges.stream().sorted(Comparator.comparing(Edge::to).thenComparing(Edge::key))
                .forEach(e -> outgoing.computeIfAbsent(e.from(), ignored -> new ArrayList<>()).add(e.to()));
        List<String> result = new ArrayList<>(); Set<String> visited = new HashSet<>();
        var queue = new ArrayDeque<String>(); queue.add(start);
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            if (visited.add(key)) { result.add(key); queue.addAll(outgoing.getOrDefault(key, List.of())); }
        }
        nodes.keySet().stream().sorted().filter(k -> !visited.contains(k)).forEach(result::add);
        return result;
    }

    private String text(JsonNode node, String field) {
        require(node.path(field).isTextual()); return node.get(field).textValue();
    }
    private String key(JsonNode node, String field) {
        String value = text(node, field); require(value.matches("[A-Za-z][A-Za-z0-9._-]{0,99}")); return value;
    }
    private static void require(boolean condition) { if (!condition) throw unavailable(); }
    private static ConsoleException unavailable() { return new ConsoleException(500, "HOST_CONFIGURATION"); }

    private static final class Links {
        private final long activityId;
        private int taskCount;
        private Long singleTaskId;
        private Links(long activityId) { this.activityId = activityId; }
        private void addTask(Long taskId) { singleTaskId = ++taskCount == 1 ? taskId : null; }
    }
}
