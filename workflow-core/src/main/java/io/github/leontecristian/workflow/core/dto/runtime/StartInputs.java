package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.Map;
public record StartInputs(Map<String, WorkflowLiteral> values) {
    public StartInputs {
        java.util.Objects.requireNonNull(values);
        if (values.size() > 100) throw new IllegalArgumentException("Too many inputs");
        values = Map.copyOf(values);
    }
    public static StartInputs empty() { return new StartInputs(Map.of()); }
}
