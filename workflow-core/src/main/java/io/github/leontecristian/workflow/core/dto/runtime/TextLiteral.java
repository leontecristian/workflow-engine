package io.github.leontecristian.workflow.core.dto.runtime;
public record TextLiteral(String value) implements WorkflowLiteral {
    public TextLiteral { java.util.Objects.requireNonNull(value); }
}
