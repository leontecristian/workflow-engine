package io.github.leontecristian.workflow.core.dto.definition.document;
import java.util.List;
/** Internal validated subset; canonical source is retained separately, including all designer values. */
public record MinimalWorkflowDefinition(Node start, Human human, Node end, List<Transition> transitions) {
    public MinimalWorkflowDefinition { transitions = List.copyOf(transitions); }
    public record Node(String key, String type, String name, String result) {}
    public record Assignment(String resolver) {}
    public record Outcome(String key, String label, boolean requiresComment) {}
    public record Human(String key, String taskType, Assignment assignment, Outcome outcome) {}
    public record Transition(String key, String from, String to, String outcome) {}
}
