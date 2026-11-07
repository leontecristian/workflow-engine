package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import java.util.Optional;
/** Host owns trusted actor/time, policy and transactions. Replay never executes a missing command.
 * Normal completion preserves receipt-before-current-state validation, without an exact-read precheck.
 */
public interface WorkflowRuntimeCommandHost {
    WorkflowCommandResult complete(CompleteWorkflowTask command, String correlation);
    Optional<WorkflowCommandResult> replayComplete(CompleteWorkflowTask command, String correlation);
}
