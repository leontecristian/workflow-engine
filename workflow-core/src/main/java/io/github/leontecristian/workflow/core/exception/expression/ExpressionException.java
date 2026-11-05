package io.github.leontecristian.workflow.core.exception.expression;

/** Internal classification; messages contain no expression source, values or provider details. */
public final class ExpressionException extends RuntimeException {
    public enum Kind { INVALID, LIMIT, EVALUATION, UNINITIALIZED, UNSUPPORTED }
    private final Kind kind;
    public ExpressionException(Kind kind) { super("Restricted expression failed: " + kind); this.kind = kind; }
    public Kind kind() { return kind; }
}
