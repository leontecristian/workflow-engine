package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionValidationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;

/** Optional host validation inside the completion transaction, before acceptance and downstream effects. */
@FunctionalInterface
public interface WorkflowTaskSubmissionValidator {
    /**
     * Accept by returning normally or throw a sanitized rejection from
     * {@link WorkflowExecutionException#rejectResult(String)}. An optional result may be null.
     */
    void validate(TaskSubmissionValidationContext context, WorkflowJsonValue result);
}
