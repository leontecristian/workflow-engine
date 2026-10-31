package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
/** Cardinality is checked before copying or traversing provider-owned rosters. */
public record SingleAssignmentResult(List<ResolvedTask> tasks, String reviewRevisionRef) {
    public SingleAssignmentResult {
        if (tasks == null || tasks.size() != 1 || tasks.get(0) == null)
            throw new WorkflowExecutionException(WorkflowExecutionException.Category.INVALID_ASSIGNMENT);
        tasks = List.copyOf(tasks);
    }
}
