package io.github.leontecristian.workflow.core.dto.expression;

import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import java.util.Objects;

/** One initialized scalar actually read, including its declared type and explicit null. */
public record ExpressionRead(String variableKey, ScalarValue value) {
    public ExpressionRead { Objects.requireNonNull(variableKey); Objects.requireNonNull(value); }
}
