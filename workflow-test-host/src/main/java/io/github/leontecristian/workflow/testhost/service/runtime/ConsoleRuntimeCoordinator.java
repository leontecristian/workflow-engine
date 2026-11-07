package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Intentionally nontransactional. The transactional proxy has rolled back before recovery runs. */
@Service
public class ConsoleRuntimeCoordinator {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(ConsoleRuntimeCoordinator.class);
    private final ConsoleRuntimeTransaction transactions;
    private final ConsoleRuntimeAccess access;

    public ConsoleRuntimeCoordinator(ConsoleRuntimeTransaction transactions, ConsoleRuntimeAccess access) {
        this.transactions = transactions;
        this.access = access;
    }

    public WorkflowCommandResult start(SandboxStart command, WorkflowCommandContext context) {
        LOG.info(
                "Console start begin command={} request={}",
                command.commandId(),
                command.requestId());
        try {
            return transactions.start(command, context);
        } catch (RuntimeException original) {
            if (eligible(original)) {
                var replay = transactions.replayStart(command, context);
                if (replay.isPresent()) return replay.get();
            }
            throw original;
        } finally {
            LOG.info("Console start end command={}", command.commandId());
        }
    }

    public WorkflowCommandResult complete(
            CompleteWorkflowTask command, WorkflowCommandContext context) {
        LOG.info(
                "Console complete begin command={} instance={}",
                command.commandId(),
                command.instanceId());
        try {
            return transactions.complete(command, context);
        } catch (RuntimeException original) {
            if (eligible(original)) {
                access.task(command.taskId());
                var replay = transactions.replayComplete(command, context);
                if (replay.isPresent()) return replay.get();
            }
            throw original;
        } finally {
            LOG.info("Console complete end command={}", command.commandId());
        }
    }

    public static boolean eligible(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable item = failure; item != null && seen.add(item); item = item.getCause()) {
            if (item instanceof SandboxRequestConflict) return true;
            if (item instanceof WorkflowExecutionException e
                    && (e.category() == WorkflowExecutionException.Category.STALE_VERSION
                            || e.category()
                                    == WorkflowExecutionException.Category.CONCURRENT_COMMAND))
                return true;
        }
        return false;
    }
}
