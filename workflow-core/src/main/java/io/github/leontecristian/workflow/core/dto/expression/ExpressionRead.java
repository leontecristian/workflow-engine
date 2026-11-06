package io.github.leontecristian.workflow.core.dto.expression;

import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import java.util.List;

/** Detached exact operand evidence, retaining declared root type, resolved path and read state. */
public record ExpressionRead(String variableKey, VariableValueType type, List<PathSegment> path, String state, String valueJson) {
    public ExpressionRead { path = List.copyOf(path); }
    public record PathSegment(String kind, String key, String index) { }
}
