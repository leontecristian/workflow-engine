package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;

/** Per-command budget: START/END cost one; human entry and transitions cost zero. */
final class RuntimeProgression {
    private int automaticSteps;
    void visit(MinimalWorkflowDefinition.Node node, String expectedType) {
        RuntimeInputs.require(expectedType.equals(node.type()), Category.INVALID_STATE);
        RuntimeInputs.require(automaticSteps < 50, Category.LIMIT_EXCEEDED);
        automaticSteps++;
    }
    static long advance(long sequence, long amount) {
        try { return Math.addExact(sequence, amount); }
        catch (ArithmeticException failure) { throw new io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException(Category.LIMIT_EXCEEDED, failure); }
    }
}
