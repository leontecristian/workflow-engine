package io.github.leontecristian.workflow.core.dto.runtime;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import java.util.List;

/** Cardinality is checked before copying or traversing the provider-owned fixed roster. */
public record AllAssignmentResult(List<ResolvedTask> tasks, String reviewRevisionRef) {
    public AllAssignmentResult {
        if (tasks == null || tasks.size() < 1 || tasks.size() > 100)
            throw new WorkflowExecutionException(WorkflowExecutionException.Category.INVALID_ASSIGNMENT);
        for (var task : tasks) {
            if (task == null) throw new WorkflowExecutionException(WorkflowExecutionException.Category.INVALID_ASSIGNMENT);
        }
        tasks = List.copyOf(tasks);
    }
}
