package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;

/** Required host policy. Current access protects receipts; completion authority applies only to fresh decisions. */
public interface WorkflowTaskCompletionAuthorization {
    /** Current business access to the exact target, including closed tasks and old receipts.
     * Must not require current assignment, completion permission or general runtime-read visibility.
     */
    boolean canAccess(TaskEligibilityContext context);
    /** Fresh authority for the selected outcome. Never invoked for a successful replay. */
    boolean canComplete(TaskCompletionContext context);
}
