package io.github.leontecristian.workflow.web.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.service.WorkflowRequestActors;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates shared HTTP commands through the host's transaction manager. */
public final class WorkflowRuntimeCommandCoordinator {
    private final WorkflowRequestIdentity identity;
    private final Clock clock;
    private final WorkflowExecutionService execution;
    private final TransactionTemplate commandTransaction;
    private final TransactionTemplate readTransaction;

    public WorkflowRuntimeCommandCoordinator(WorkflowRequestIdentity identity, Clock clock,
            WorkflowExecutionService execution, PlatformTransactionManager transactionManager) {
        this.identity = identity;
        this.clock = clock;
        this.execution = execution;
        this.commandTransaction = new TransactionTemplate(transactionManager);
        commandTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readTransaction.setReadOnly(true);
    }

    public WorkflowCommandResult complete(CompleteWorkflowTask command, String correlation) {
        return execute(correlation, context -> execution.completeTask(command, context),
                context -> execution.findReplay(command, context));
    }

    public Optional<WorkflowCommandResult> replayComplete(CompleteWorkflowTask command, String correlation) {
        return replay(correlation, context -> execution.findReplay(command, context));
    }

    public WorkflowCommandResult claim(ClaimWorkflowTask command, String correlation) {
        return execute(correlation, context -> execution.claimTask(command, context),
                context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> replayClaim(ClaimWorkflowTask command, String correlation) {
        return replay(correlation, context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public WorkflowCommandResult release(ReleaseWorkflowTask command, String correlation) {
        return execute(correlation, context -> execution.releaseTask(command, context),
                context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> replayRelease(ReleaseWorkflowTask command, String correlation) {
        return replay(correlation, context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public WorkflowCommandResult administrativelyRelease(AdministrativelyReleaseWorkflowTask command, String correlation) {
        return execute(correlation, context -> execution.administrativelyReleaseTask(command, context),
                context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> replayAdministrativeRelease(AdministrativelyReleaseWorkflowTask command, String correlation) {
        return replay(correlation, context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public WorkflowCommandResult reassign(ReassignWorkflowTask command, String correlation) {
        return execute(correlation, context -> execution.reassignTask(command, context),
                context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> replayReassignment(ReassignWorkflowTask command, String correlation) {
        return replay(correlation, context -> execution.findReplay(command, context)
                        .map(result -> new WorkflowCommandResult(true, result)));
    }

    public WorkflowCancellationCommandResult cancel(CancelWorkflow command, String correlation) {
        return execute(correlation, context -> execution.cancelWorkflow(command, context),
                context -> execution.findCancellationReplay(command, context)
                        .map(result -> new WorkflowCancellationCommandResult(true, result)));
    }

    public Optional<WorkflowCancellationCommandResult> replayCancellation(CancelWorkflow command, String correlation) {
        return replay(correlation, context -> execution.findCancellationReplay(command, context)
                        .map(result -> new WorkflowCancellationCommandResult(true, result)));
    }

    private <T> T execute(String correlation, Function<WorkflowCommandContext, T> mutation,
            Function<WorkflowCommandContext, Optional<T>> lookup) {
        var context = context(correlation);
        var outcome = new CommandTransactionOutcome();
        T result;
        try {
            result = commandTransaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(outcome);
                return mutation.apply(context);
            });
        } catch (WorkflowExecutionException failure) {
            if (outcome.status == TransactionSynchronization.STATUS_ROLLED_BACK
                    && (failure.category() == WorkflowExecutionException.Category.STALE_VERSION
                            || failure.category() == WorkflowExecutionException.Category.CONCURRENT_COMMAND)) {
                return readTransaction.execute(status -> lookup.apply(context)).orElseThrow(() -> failure);
            }
            throw failure;
        }
        if (outcome.status != TransactionSynchronization.STATUS_COMMITTED) {
            throw new IllegalStateException("Runtime command transaction did not commit");
        }
        return result;
    }

    private <T> Optional<T> replay(String correlation, Function<WorkflowCommandContext, Optional<T>> lookup) {
        var context = context(correlation);
        return readTransaction.execute(status -> lookup.apply(context));
    }

    private WorkflowCommandContext context(String correlation) {
        String actorRef = WorkflowRequestActors.requireActor(identity);
        Instant time = clock.instant();
        return new WorkflowCommandContext(ActorKind.USER, actorRef,
                Instant.ofEpochSecond(time.getEpochSecond(), time.getNano() / 100 * 100L), correlation);
    }

    private static final class CommandTransactionOutcome implements TransactionSynchronization {
        private int status = STATUS_UNKNOWN;

        @Override
        public void afterCompletion(int status) {
            this.status = status;
        }
    }
}
