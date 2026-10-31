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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only presentation of the established minimal graph, never a second execution engine. */
@Service
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleInstanceGraph {
    private final ConsoleRuntimeAccess access;
    private final WorkflowDefinitionService definitions;
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleWire wire;

    public ConsoleInstanceGraph(
            ConsoleRuntimeAccess access,
            WorkflowDefinitionService definitions,
            WorkflowRuntimeQueryService reads,
            ConsoleWire wire) {
        this.access = access;
        this.definitions = definitions;
        this.reads = reads;
        this.wire = wire;
    }

    @Transactional(readOnly = true)
    public InstanceGraphView find(long id) {
        // Authorize first, before reading definition content or any graph evidence.
        var instance = access.instance(id);
        require(
                instance.status() == InstanceStatus.ACTIVE
                        || instance.status() == InstanceStatus.COMPLETED);
        long watermark = instance.lastHistorySequence();
        if (watermark > 100) throw new ConsoleException(422, "LIMIT_EXCEEDED");
        require(watermark > 0);
        var version =
                definitions
                        .findVersion(instance.definitionVersionId())
                        .orElseThrow(ConsoleInstanceGraph::unavailable);
        require(
                version.summary().id() == instance.definitionVersionId()
                        && Objects.equals(
                                version.summary().contentHash(), instance.definitionHash())
                        && Objects.equals(version.summary().key(), instance.definitionKey())
                        && version.summary().version() == instance.definitionVersion());
        JsonNode document;
        try {
            document = wire.tree(version.documentJson());
        } catch (ConsoleException invalidStoredDocument) {
            var failure = unavailable();
            failure.initCause(invalidStoredDocument);
            throw failure;
        }
        var nodes = displayNodes(document);
        var edges = displayEdges(document, nodes);
        require(nodes.containsKey(instance.currentNodeKey()));
        var facts = prefix(id, watermark);
        Set<String> completed = new HashSet<>(), traversed = new HashSet<>();
        Long taskId = null, activityId = null;
        boolean ended = false;
        for (var fact : facts) {
            if ("TRANSITION_TAKEN".equals(fact.eventType())) {
                var edge =
                        edges.stream()
                                .filter(e -> e.key().equals(fact.transitionKey()))
                                .findFirst()
                                .orElseThrow(ConsoleInstanceGraph::unavailable);
                require(
                        edge.from().equals(fact.sourceNodeKey())
                                && edge.to().equals(fact.targetNodeKey()));
                completed.add(edge.from());
                require(traversed.add(edge.key()));
            } else if ("TASK_CREATED".equals(fact.eventType())) {
                require(
                        taskId == null
                                && fact.taskId() != null
                                && fact.taskId() > 0
                                && fact.activityId() != null
                                && fact.activityId() > 0);
                taskId = fact.taskId();
                activityId = fact.activityId();
            } else if ("INSTANCE_COMPLETED".equals(fact.eventType())) {
                require(
                        !ended
                                && instance.status() == InstanceStatus.COMPLETED
                                && "END".equals(nodes.get(instance.currentNodeKey()).type()));
                completed.add(instance.currentNodeKey());
                ended = true;
            }
        }
        require(
                nodes.containsKey(instance.currentNodeKey())
                        && taskId != null
                        && (instance.status() != InstanceStatus.COMPLETED || ended));
        List<Node> ordered = new ArrayList<>();
        String key =
                nodes.values().stream()
                        .filter(n -> "START".equals(n.type()))
                        .findFirst()
                        .orElseThrow(ConsoleInstanceGraph::unavailable)
                        .key();
        for (int i = 0; i < 3; i++) {
            var node = nodes.get(key);
            require(node != null && ordered.stream().noneMatch(n -> n.key().equals(node.key())));
            State state =
                    instance.status() == InstanceStatus.ACTIVE
                                    && key.equals(instance.currentNodeKey())
                            ? State.ACTIVE
                            : completed.contains(key) ? State.COMPLETED : State.PENDING;
            boolean human = "HUMAN_TASK".equals(node.type());
            ordered.add(
                    new Node(
                            key,
                            node.name(),
                            node.type(),
                            state,
                            human ? activityId : null,
                            human ? taskId : null));
            if (i < 2) {
                final String from = key;
                key =
                        edges.stream()
                                .filter(e -> from.equals(e.from()))
                                .findFirst()
                                .orElseThrow(ConsoleInstanceGraph::unavailable)
                                .to();
            }
        }
        require("HUMAN_TASK".equals(ordered.get(1).type()) && "END".equals(ordered.get(2).type()));
        require(completed.contains(ordered.get(0).key()));
        if (instance.status() == InstanceStatus.ACTIVE) {
            require(
                    instance.currentNodeKey().equals(ordered.get(1).key())
                            && !completed.contains(instance.currentNodeKey()));
        }
        if (instance.status() == InstanceStatus.COMPLETED) require(traversed.size() == 2);
        var displayed =
                edges.stream()
                        .map(
                                e ->
                                        new Edge(
                                                e.key(),
                                                e.from(),
                                                e.to(),
                                                e.outcome(),
                                                traversed.contains(e.key())))
                        .toList();
        return new InstanceGraphView(instance, ordered, displayed);
    }

    private List<WorkflowHistorySummary> prefix(long id, long watermark) {
        var page = reads.listHistory(id, 0, 100);
        var prefix = page.items().stream().filter(f -> f.historySequence() <= watermark).toList();
        require(prefix.size() == watermark);
        for (int i = 0; i < prefix.size(); i++) {
            require(prefix.get(i).instanceId() == id && prefix.get(i).historySequence() == i + 1L);
        }
        return prefix;
    }

    private Map<String, Node> displayNodes(JsonNode document) {
        var raw = document.path("nodes");
        require(raw.isArray() && raw.size() == 3);
        Map<String, Node> result = new LinkedHashMap<>();
        Set<String> types = new HashSet<>();
        for (var node : raw) {
            String key = text(node, "key"), name = text(node, "name"), type = text(node, "type");
            if ("HUMAN_TASK".equals(type))
                require("SINGLE".equals(node.path("completion").path("policy").asText()));
            require(
                    result.put(key, new Node(key, name, type, State.PENDING, null, null)) == null
                            && types.add(type));
        }
        require(types.equals(Set.of("START", "HUMAN_TASK", "END")));
        return result;
    }

    private List<Edge> displayEdges(JsonNode document, Map<String, Node> nodes) {
        var raw = document.path("transitions");
        require(raw.isArray() && raw.size() == 2);
        List<Edge> result = new ArrayList<>();
        Set<String> keys = new HashSet<>(), sources = new HashSet<>();
        for (var edge : raw) {
            String key = text(edge, "key"), from = text(edge, "from"), to = text(edge, "to");
            require(
                    keys.add(key)
                            && sources.add(from)
                            && nodes.containsKey(from)
                            && nodes.containsKey(to));
            result.add(
                    new Edge(
                            key,
                            from,
                            to,
                            edge.has("outcome") ? text(edge, "outcome") : null,
                            false));
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        require(node.path(field).isTextual());
        return node.get(field).textValue();
    }

    private static void require(boolean condition) {
        if (!condition) throw unavailable();
    }

    private static ConsoleException unavailable() {
        return new ConsoleException(500, "HOST_CONFIGURATION");
    }
}
