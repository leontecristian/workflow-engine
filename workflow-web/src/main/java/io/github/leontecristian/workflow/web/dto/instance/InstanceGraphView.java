package io.github.leontecristian.workflow.web.dto.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;

import java.util.List;

/** Authorized display projection; raw definition settings and task submissions are excluded. */
public record InstanceGraphView(WorkflowInstanceView instance, List<Node> nodes, List<Edge> edges) {
    public InstanceGraphView {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public enum State {
        ACTIVE,
        COMPLETED,
        PENDING
    }

    public record Node(
            String key, String name, String type, State state, Long activityId, Long taskId) {}

    public record Edge(String key, String from, String to, String outcome, boolean traversed) {}
}
