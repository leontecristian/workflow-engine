package io.github.leontecristian.workflow.testhost.configuration.integration;

import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Test-owned transaction boundary for historical direct core integration cases. */
@Transactional
public class RuntimeCommandFixture {
    private final WorkflowExecutionService execution;
    private final WorkflowRuntimeQueryService reads;

    public RuntimeCommandFixture(WorkflowExecutionService execution, WorkflowRuntimeQueryService reads) {
        this.execution = execution;
        this.reads = reads;
    }

    public WorkflowCommandResult complete(CompleteWorkflowTask command, WorkflowCommandContext context) {
        var result = execution.completeTask(command, context);
        if (!result.replayed()) {
            var submission = reads.findTaskSubmission(command.taskId()).orElseThrow();
            if (!submission.commandId().equals(command.commandId())) {
                throw new IllegalStateException("Submission command mismatch");
            }
        }
        return result;
    }

    public void completeThenFail(CompleteWorkflowTask command, WorkflowCommandContext context) {
        complete(command, context);
        throw new IllegalStateException("Configured post-result failure");
    }

    public WorkflowCommandResult claim(ClaimWorkflowTask command, WorkflowCommandContext context) {
        return execution.claimTask(command, context);
    }

    public WorkflowCommandResult release(ReleaseWorkflowTask command, WorkflowCommandContext context) {
        return execution.releaseTask(command, context);
    }

    public WorkflowCommandResult administrativelyRelease(AdministrativelyReleaseWorkflowTask command,
            WorkflowCommandContext context) {
        return execution.administrativelyReleaseTask(command, context);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowCommandResult> replayComplete(CompleteWorkflowTask command, WorkflowCommandContext context) {
        return execution.findReplay(command, context);
    }
}
