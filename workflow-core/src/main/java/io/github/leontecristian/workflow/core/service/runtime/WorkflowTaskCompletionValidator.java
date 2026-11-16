package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;

/** Required read-only business check for every fresh decision, including absent results.
 * Hosts must protect the checked business basis through commit in the same transaction.
 */
public interface WorkflowTaskCompletionValidator {
    void validate(TaskCompletionContext context, WorkflowJsonValue result);
}
