package io.github.leontecristian.workflow.core.dto.runtime;
public record TextLiteral(String value) implements ScalarLiteral {
    public TextLiteral { java.util.Objects.requireNonNull(value); }
}
