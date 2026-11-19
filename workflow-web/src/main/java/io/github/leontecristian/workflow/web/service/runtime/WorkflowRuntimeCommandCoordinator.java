package io.github.leontecristian.workflow.web.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.service.WorkflowRequestActors;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final WorkflowRuntimeQueryService reads;
    private final Supplier<WorkflowAuthorizedRuntimeReadService> readAccess;
    private final WorkflowTaskTokens tokens;
    private final TransactionTemplate commandTransaction;
    private final TransactionTemplate readTransaction;

    public WorkflowRuntimeCommandCoordinator(WorkflowRequestIdentity identity, Clock clock,
            WorkflowExecutionService execution, PlatformTransactionManager transactionManager) {
        this(identity, clock, execution, transactionManager, null, () -> null, new WorkflowTaskTokens(null));
    }

    public WorkflowRuntimeCommandCoordinator(WorkflowRequestIdentity identity, Clock clock,
            WorkflowExecutionService execution, PlatformTransactionManager transactionManager,
            WorkflowRuntimeQueryService reads, Supplier<WorkflowAuthorizedRuntimeReadService> readAccess, WorkflowTaskTokens tokens) {
        this.identity = identity;
        this.clock = clock;
        this.execution = execution;
        this.reads = reads;
        this.readAccess = readAccess;
        this.tokens = tokens;
        this.commandTransaction = new TransactionTemplate(transactionManager);
        commandTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readTransaction.setReadOnly(true);
    }

    public record TaskTarget(long taskId, long instanceId, long expectedVersion) {}

    /** Resolve only the owning instance. Core checks current business access before mutation or receipt lookup. */
    public TaskTarget taskTarget(long taskId, String precondition) {
        String actorRef = WorkflowRequestActors.requireActor(identity);
        long expectedVersion = tokens.expectedVersion(precondition, actorRef, taskId);
        if (reads == null) throw new WorkflowRuntimeException(500, "HOST_CONFIGURATION");
        // A later assignment can revoke the task view while the original actor retains receipt access.
        var task = reads.findTask(taskId).orElseThrow(() -> new WorkflowRuntimeException(404, "NOT_FOUND"));
        return new TaskTarget(taskId, task.instanceId(), expectedVersion);
    }

    public Map<String, Object> taskResult(WorkflowCommandResult result) {
        var executionResult = result.execution();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("replayed", result.replayed());
        view.put("status", executionResult.instance().status());
        view.put("taskStatus", executionResult.task().status());
        if (executionResult.operation() == WorkflowOperation.COMPLETE_TASK) {
            var activity = executionResult.activity();
            if (activity.completionPolicy() == CompletionPolicy.ALL)
                view.put("collectivePending", activity.status() == ActivityStatus.ACTIVE);
            if (executionResult.instance().status() == InstanceStatus.ACTIVE) {
                var next = nextTask(executionResult.instance().id(), executionResult.task().id());
                if (next != null) view.put("nextTaskId", next);
            }
        }
        return view;
    }

    /** Navigation is optional and uses present read authorization, including on receipt replay. */
    private Long nextTask(long instanceId, long submittedTaskId) {
        var access = readAccess.get();
        if (access == null) return null;
        String actorRef = WorkflowRequestActors.requireActor(identity);
        try {
            var instance = access.instance(actorRef, instanceId);
            if (instance.status() != InstanceStatus.ACTIVE) return null;
            long cursor = 0;
            for (int pageNumber = 0; pageNumber < 5; pageNumber++) {
                var page = access.listInstanceTasks(actorRef, instanceId, cursor, 100);
                for (var task : page.items()) {
                    if (task.id() != submittedTaskId && task.status() == TaskStatus.OPEN) return task.id();
                }
                if (page.nextAfterId() == null) return null;
                cursor = page.nextAfterId();
            }
        } catch (RuntimeException unavailable) {
            org.slf4j.LoggerFactory.getLogger(WorkflowRuntimeCommandCoordinator.class)
                    .debug("Could not load onward task navigation after a committed command", unavailable);
        }
        return null;
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
