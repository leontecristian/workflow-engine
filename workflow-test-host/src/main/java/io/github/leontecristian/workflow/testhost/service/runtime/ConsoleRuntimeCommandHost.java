package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import java.util.Optional;
import java.util.function.Supplier;
import io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict;
/** Delegates through retained host coordinator/transaction proxies, preserving receipt ordering. */
public class ConsoleRuntimeCommandHost implements WorkflowRuntimeCommandHost {
    private final ConsoleRuntimeCoordinator commands;
    private final ConsoleRuntimeTransaction transactions;
    private final ConsoleRuntimeAccess access;
    private final ConsoleContext context;
    public ConsoleRuntimeCommandHost(ConsoleRuntimeCoordinator commands, ConsoleRuntimeTransaction transactions,
            ConsoleRuntimeAccess access, ConsoleContext context) {
        this.commands = commands; this.transactions = transactions; this.access = access; this.context = context;
    }
    @Override public WorkflowCommandResult complete(CompleteWorkflowTask command, String correlation) {
        return policy(() -> commands.complete(command, context.runtime(correlation)));
    }
    @Override public Optional<WorkflowCommandResult> replayComplete(CompleteWorkflowTask command, String correlation) {
        return policy(() -> {
            access.task(command.taskId());
            return transactions.replayComplete(command, context.runtime(correlation));
        });
    }
    private <T> T policy(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException failure) {
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (cause instanceof SandboxRequestConflict) throw translated(409, "SANDBOX_REQUEST_CONFLICT", failure);
            }
            if (failure instanceof ConsoleException e) throw translated(e.status(), e.code(), failure);
            throw failure;
        }
    }
    private WorkflowRuntimeException translated(int status, String code, RuntimeException cause) {
        var failure = new WorkflowRuntimeException(status, code);
        failure.initCause(cause);
        return failure;
    }
}
