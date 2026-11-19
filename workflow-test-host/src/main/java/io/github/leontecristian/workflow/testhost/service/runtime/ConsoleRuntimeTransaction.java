package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Optional;

@Service
public class ConsoleRuntimeTransaction {
    private final WorkflowExecutionService execution;
    private final SandboxRequestStore requests;
    private final ConsoleSettings settings;

    public ConsoleRuntimeTransaction(
            WorkflowExecutionService execution,
            SandboxRequestStore requests,
            ConsoleSettings settings) {
        this.execution = execution;
        this.requests = requests;
        this.settings = settings;
    }

    @Transactional
    public WorkflowCommandResult start(SandboxStart command, WorkflowCommandContext context) {
        var inputs = startInputs(command); // No stale/link precheck: core receipts remain authoritative.
        var result =
                execution.start(
                        command.core(),
                        inputs,
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

    private WorkflowCommandResult after(WorkflowCommandResult result) {
        if (!result.replayed() && settings.failRuntimeAfterResult)
            throw new IllegalStateException("Configured post-result failure");
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowCommandResult> replayStart(
            SandboxStart command, WorkflowCommandContext context) {
        return execution.findReplay(command.core(), startInputs(command), context);
    }

    private StartInputs startInputs(SandboxStart command) {
        if (command.inputs().values().containsKey("requestData")) {
            throw new WorkflowExecutionException(WorkflowExecutionException.Category.INVALID_INPUT);
        }
        var request = requests.find(command.requestId());
        // Saved source data has no update API; retry/recovery therefore uses the same fingerprint.
        if (request.requestDataJson() == null) return command.inputs();
        var values = new HashMap<>(command.inputs().values());
        values.put("requestData", WorkflowJsonValue.parse(request.requestDataJson()));
        return new StartInputs(values);
    }

}
