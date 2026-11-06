package io.github.leontecristian.workflow.core.dto.runtime;

/** Immutable, exact object/array snapshot. Jackson and host DTOs never cross this boundary. */
public final class WorkflowJsonValue implements WorkflowLiteral {
    private final String json;

    private WorkflowJsonValue(String json) { this.json = json; }

    public static WorkflowJsonValue parse(String json) {
        return new WorkflowJsonValue(WorkflowJsonCodec.canonical(json));
    }

    public String toJson() { return json; }

    @Override public boolean equals(Object other) {
        return other instanceof WorkflowJsonValue value && json.equals(value.json);
    }

    @Override public int hashCode() { return json.hashCode(); }
}
