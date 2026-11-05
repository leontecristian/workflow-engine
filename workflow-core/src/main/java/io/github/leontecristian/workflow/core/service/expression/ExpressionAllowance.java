package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.LIMIT;

/** A fresh allowance belongs to exactly one synchronous command, including all its gateways. */
public final class ExpressionAllowance {
    private int remaining = 500;
    void take() {
        if (remaining == 0) throw new ExpressionException(LIMIT);
        remaining--;
    }
}
