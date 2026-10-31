package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ConsoleRuntimeTransaction {
    private final WorkflowExecutionService execution;
    private final WorkflowRuntimeQueryService reads;
    private final SandboxRequestStore requests;
    private final ConsoleSettings settings;

    public ConsoleRuntimeTransaction(
            WorkflowExecutionService execution,
            WorkflowRuntimeQueryService reads,
            SandboxRequestStore requests,
            ConsoleSettings settings) {
        this.execution = execution;
        this.reads = reads;
        this.requests = requests;
        this.settings = settings;
    }

    @Transactional
    public WorkflowCommandResult start(SandboxStart command, WorkflowCommandContext context) {
        requests.find(command.requestId()); // No stale/link precheck: the core receipt remains
        // authoritative.
        var result =
                execution.start(
                        command.core(),
                        context,
                        allocated -> {
                            requests.link(
                                    command.requestId(),
                                    command.expectedRequestVersion(),
                                    allocated.instanceId());
                            if (settings.failStartLink)
                                throw new IllegalStateException("Configured start link failure");
                        });
        return after(result);
    }

    @Transactional
    public WorkflowCommandResult complete(
            CompleteWorkflowTask command, WorkflowCommandContext context) {
        var result = execution.completeTask(command, context);
        if (!result.replayed()) {
            // Real host-side business read in the same transaction, before successful
            // return/commit.
            var submission =
                    reads.findTaskSubmission(command.taskId())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Accepted submission not visible"));
            if (!submission.commandId().equals(command.commandId()))
                throw new IllegalStateException("Submission command mismatch");
        }
        return after(result);
    }

    private WorkflowCommandResult after(WorkflowCommandResult result) {
        if (!result.replayed() && settings.failRuntimeAfterResult)
            throw new IllegalStateException("Configured post-result failure");
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowCommandResult> replayStart(
            SandboxStart command, WorkflowCommandContext context) {
        return execution.findReplay(command.core(), context);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowCommandResult> replayComplete(
            CompleteWorkflowTask command, WorkflowCommandContext context) {
        return execution.findReplay(command, context);
    }
}
