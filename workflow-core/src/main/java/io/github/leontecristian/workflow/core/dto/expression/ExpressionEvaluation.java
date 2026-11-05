package io.github.leontecristian.workflow.core.dto.expression;

import java.util.List;

public record ExpressionEvaluation(boolean result, List<ExpressionRead> reads) {
    public ExpressionEvaluation { reads = List.copyOf(reads); }
}
