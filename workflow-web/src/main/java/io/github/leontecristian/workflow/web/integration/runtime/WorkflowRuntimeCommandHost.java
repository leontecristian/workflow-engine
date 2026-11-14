package io.github.leontecristian.workflow.web.integration.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import java.util.Optional;
/** Host owns trusted actor/time, policy and transactions. Replay never executes a missing command.
 * Normal completion preserves receipt-before-current-state validation, without an exact-read precheck.
 */
public interface WorkflowRuntimeCommandHost {
    /** Optional reassignment support; hosts must supply current policy and transactional execution. */
    default WorkflowCommandResult reassign(ReassignWorkflowTask command, String correlation) {
        throw new io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException(500, "HOST_CONFIGURATION");
    }
    default Optional<WorkflowCommandResult> replayReassignment(ReassignWorkflowTask command, String correlation) {
        throw new io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException(500, "HOST_CONFIGURATION");
    }
    WorkflowCancellationCommandResult cancel(CancelWorkflow command, String correlation);
    Optional<WorkflowCancellationCommandResult> replayCancellation(CancelWorkflow command, String correlation);
    WorkflowCommandResult complete(CompleteWorkflowTask command, String correlation);
    Optional<WorkflowCommandResult> replayComplete(CompleteWorkflowTask command, String correlation);
    WorkflowCommandResult claim(ClaimWorkflowTask command, String correlation);
    WorkflowCommandResult release(ReleaseWorkflowTask command, String correlation);
    WorkflowCommandResult administrativelyRelease(AdministrativelyReleaseWorkflowTask command, String correlation);
    Optional<WorkflowCommandResult> replayClaim(ClaimWorkflowTask command, String correlation);
    Optional<WorkflowCommandResult> replayRelease(ReleaseWorkflowTask command, String correlation);
    Optional<WorkflowCommandResult> replayAdministrativeRelease(AdministrativelyReleaseWorkflowTask command, String correlation);
}
