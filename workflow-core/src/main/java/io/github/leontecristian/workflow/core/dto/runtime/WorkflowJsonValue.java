package io.github.leontecristian.workflow.core.dto.runtime;

/** Immutable, exact object/array snapshot. Jackson and host DTOs never cross this boundary. */
public final class WorkflowJsonValue implements WorkflowLiteral {
    public static final int MAX_UTF16_BYTES = 8 * 1024 * 1024;
    public static final int MAX_UTF8_BYTES = 12 * 1024 * 1024;
    public static final int MAX_RAW_UTF8_BYTES = 16 * 1024 * 1024;
    public static final int MAX_DEPTH = 18;
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
