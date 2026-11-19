package io.github.leontecristian.workflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.exception.WorkflowUnauthenticatedException;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowRuntimeCommandCoordinator;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WorkflowRuntimeCommandCoordinatorUnitTest {
    private static final UUID ID = UUID.randomUUID();
    private static final Instant INSTANT = Instant.parse("2026-11-19T10:11:12.123456789Z");
    private static final Clock CLOCK = Clock.fixed(INSTANT, ZoneOffset.UTC);
    private static final WorkflowCommandContext CONTEXT =
            new WorkflowCommandContext(ActorKind.USER, "reviewer", INSTANT.minusNanos(89), "trusted");

    private final WorkflowExecutionService core = mock(WorkflowExecutionService.class);
    private final WorkflowRequestIdentity identity = mock(WorkflowRequestIdentity.class);
    private final Transactions transactions = new Transactions();
    private final WorkflowRuntimeCommandCoordinator coordinator =
            new WorkflowRuntimeCommandCoordinator(identity, CLOCK, core, transactions);

    @Test
    void routesAllTypedCommandsAndLookups() {
        when(identity.requireActorRef()).thenReturn("reviewer");
        var completeCommand = new CompleteWorkflowTask(ID, 7, 8, 9, "APPROVE", null);
        when(core.completeTask(completeCommand, CONTEXT)).thenReturn(new WorkflowCommandResult(false, null));
        when(core.findReplay(completeCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.complete(completeCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayComplete(completeCommand, "trusted")).isEmpty();
        verify(core).completeTask(completeCommand, CONTEXT);
        verify(core).findReplay(completeCommand, CONTEXT);

        var claimCommand = new ClaimWorkflowTask(ID, 7, 8, 9);
        when(core.claimTask(claimCommand, CONTEXT)).thenReturn(new WorkflowCommandResult(false, null));
        when(core.findReplay(claimCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.claim(claimCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayClaim(claimCommand, "trusted")).isEmpty();
        verify(core).claimTask(claimCommand, CONTEXT);
        verify(core).findReplay(claimCommand, CONTEXT);

        var releaseCommand = new ReleaseWorkflowTask(ID, 7, 8, 9);
        when(core.releaseTask(releaseCommand, CONTEXT)).thenReturn(new WorkflowCommandResult(false, null));
        when(core.findReplay(releaseCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.release(releaseCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayRelease(releaseCommand, "trusted")).isEmpty();
        verify(core).releaseTask(releaseCommand, CONTEXT);
        verify(core).findReplay(releaseCommand, CONTEXT);

        var administrativelyReleaseCommand = new AdministrativelyReleaseWorkflowTask(ID, 7, 8, 9, "reason");
        when(core.administrativelyReleaseTask(administrativelyReleaseCommand, CONTEXT)).thenReturn(new WorkflowCommandResult(false, null));
        when(core.findReplay(administrativelyReleaseCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.administrativelyRelease(administrativelyReleaseCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayAdministrativeRelease(administrativelyReleaseCommand, "trusted")).isEmpty();
        verify(core).administrativelyReleaseTask(administrativelyReleaseCommand, CONTEXT);
        verify(core).findReplay(administrativelyReleaseCommand, CONTEXT);

        var reassignCommand = new ReassignWorkflowTask(ID, 7, 8, 9, new TaskAssignmentTarget(AssignmentTargetKind.USER, "next"), "reason");
        when(core.reassignTask(reassignCommand, CONTEXT)).thenReturn(new WorkflowCommandResult(false, null));
        when(core.findReplay(reassignCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.reassign(reassignCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayReassignment(reassignCommand, "trusted")).isEmpty();
        verify(core).reassignTask(reassignCommand, CONTEXT);
        verify(core).findReplay(reassignCommand, CONTEXT);

        var cancelCommand = new CancelWorkflow(ID, 7, 9, "reason");
        when(core.cancelWorkflow(cancelCommand, CONTEXT)).thenReturn(new WorkflowCancellationCommandResult(false, null));
        when(core.findCancellationReplay(cancelCommand, CONTEXT)).thenReturn(Optional.empty());
        assertThat(coordinator.cancel(cancelCommand, "trusted").replayed()).isFalse();
        assertThat(coordinator.replayCancellation(cancelCommand, "trusted")).isEmpty();
        verify(core).cancelWorkflow(cancelCommand, CONTEXT);
        verify(core).findCancellationReplay(cancelCommand, CONTEXT);

        verifyNoMoreInteractions(core);
        assertThat(transactions.events).containsExactly(
                "write", "commit", "read", "commit", "write", "commit", "read", "commit",
                "write", "commit", "read", "commit", "write", "commit", "read", "commit",
                "write", "commit", "read", "commit", "write", "commit", "read", "commit");
        verify(identity, org.mockito.Mockito.times(12)).requireActorRef();
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"STALE_VERSION", "CONCURRENT_COMMAND"})
    void recoversOnceWithTheOriginalInputAndActorAfterRollback(Category category) {
        when(identity.requireActorRef()).thenReturn("reviewer", "different");
        var command = new CompleteWorkflowTask(ID, 7, 8, 9, "APPROVE", null);
        var result = new WorkflowCommandResult(true, null);
        when(core.completeTask(command, CONTEXT)).thenThrow(new WorkflowExecutionException(category));
        when(core.findReplay(command, CONTEXT)).thenAnswer(invocation -> {
            assertThat(transactions.events).containsExactly("write", "rollback", "read");
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return Optional.of(result);
        });
        assertThat(coordinator.complete(command, "trusted")).isSameAs(result);
        verify(core).completeTask(command, CONTEXT);
        verify(core).findReplay(command, CONTEXT);
        verify(identity).requireActorRef();
        verifyNoMoreInteractions(core, identity);
    }

    @Test
    void keepsTheOriginalMissAndPropagatesCurrentAccessOrReceiptMismatch() {
        when(identity.requireActorRef()).thenReturn("reviewer");
        var command = new ReleaseWorkflowTask(ID, 7, 8, 9);
        var conflict = new WorkflowExecutionException(Category.STALE_VERSION);
        var denied = new WorkflowExecutionException(Category.NOT_FOUND);
        var mismatch = new WorkflowExecutionException(Category.COMMAND_ID_CONFLICT);
        when(core.releaseTask(command, CONTEXT)).thenThrow(conflict);
        when(core.findReplay(command, CONTEXT)).thenReturn(Optional.empty()).thenThrow(denied, mismatch);
        for (var failure : List.of(conflict, denied, mismatch)) {
            assertThatThrownBy(() -> coordinator.release(command, "trusted")).isSameAs(failure);
        }
        verify(core, org.mockito.Mockito.times(3)).releaseTask(command, CONTEXT);
        verify(core, org.mockito.Mockito.times(3)).findReplay(command, CONTEXT);
        verifyNoMoreInteractions(core);
    }

    @Test
    void otherCoreConflictsSkipRecovery() {
        when(identity.requireActorRef()).thenReturn("reviewer");
        var command = new CancelWorkflow(ID, 7, 9, "reason");
        var failure = new WorkflowExecutionException(Category.COMMAND_ID_CONFLICT);
        when(core.cancelWorkflow(command, CONTEXT)).thenThrow(failure);
        assertThatThrownBy(() -> coordinator.cancel(command, "trusted")).isSameAs(failure);
        verify(core).cancelWorkflow(command, CONTEXT);
        verifyNoMoreInteractions(core);
        assertThat(transactions.events).containsExactly("write", "rollback");
    }

    @Test
    void commandOnlyActivationUsesScopedIdentityErrorsBeforeTransactionOrCore() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Host.class, WorkflowRuntimeCommandConfiguration.class);
            context.refresh();
            var identity = context.getBean(WorkflowRequestIdentity.class);
            var execution = context.getBean(WorkflowExecutionService.class);
            var transactions = context.getBean(Transactions.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            org.mockito.Mockito.clearInvocations(execution);
            when(identity.requireActorRef()).thenThrow(new WorkflowUnauthenticatedException("private"))
                    .thenReturn(null, "  ", "reviewer", "next");
            for (String route : List.of("/commands/tasks/8/claim", "/commands/commands/claim/replay")) {
                String extra = route.endsWith("replay") ? ",\"taskId\":\"8\"" : "";
                var request = post(route).contentType("application/json").header("X-Task-Precondition", "invalid")
                        .requestAttr("trustedAttribute", "server").content("{\"commandId\":\"" + ID + "\"" + extra + "}");
                boolean replay = route.endsWith("replay");
                mvc.perform(request).andExpect(status().is(replay ? 500 : 401))
                        .andExpect(jsonPath("$.code").value(replay ? "HOST_FAILURE" : "UNAUTHENTICATED"))
                        .andExpect(jsonPath("$.correlationId").value("server"));
            }
            String commandBody = "{\"commandId\":\"" + ID + "\"}";
            mvc.perform(post("/commands/tasks/8/claim").contentType("application/json")
                            .header("X-Task-Precondition", "invalid").content(commandBody))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("HOST_FAILURE"));
            verifyNoInteractions(execution);
            assertThat(transactions.events).isEmpty();

            var command = new ClaimWorkflowTask(ID, 7, 8, 9);
            var first = new WorkflowCommandContext(ActorKind.USER, "reviewer", CONTEXT.occurredAt(), null);
            var next = new WorkflowCommandContext(ActorKind.USER, "next", CONTEXT.occurredAt(), null);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var task = mock(WorkflowTaskView.class);
            when(task.instanceId()).thenReturn(7L);
            when(task.status()).thenReturn(TaskStatus.OPEN);
            when(reads.findTask(8)).thenReturn(Optional.of(task));
            var instance = mock(WorkflowInstanceView.class);
            when(instance.status()).thenReturn(InstanceStatus.ACTIVE);
            var result = new WorkflowExecutionResult(ID, WorkflowOperation.CLAIM_TASK, instance, null, task, 10, 10);
            when(execution.claimTask(command, first)).thenReturn(new WorkflowCommandResult(false, result));
            when(execution.claimTask(command, next)).thenThrow(new WorkflowExecutionException(Category.NOT_ASSIGNED));
            var tokens = context.getBean(WorkflowTaskTokens.class);
            mvc.perform(post("/commands/tasks/8/claim").contentType("application/json")
                            .header("X-Task-Precondition", tokens.precondition("reviewer", 8, 9))
                            .requestAttr("trustedAttribute", 42).content(commandBody))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.taskStatus").value("OPEN")).andExpect(jsonPath("$.execution").doesNotExist());
            mvc.perform(post("/commands/tasks/8/claim").contentType("application/json")
                            .header("X-Task-Precondition", tokens.precondition("next", 8, 9)).content(commandBody))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
            verify(identity, org.mockito.Mockito.times(5)).requireActorRef();
            verify(execution).claimTask(command, first);
            verify(execution).claimTask(command, next);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Host {
        @Bean WorkflowRuntimeOptions options() { return new WorkflowRuntimeOptions("/commands", "trustedAttribute"); }
        @Bean Clock clock() { return CLOCK; }
        @Bean WorkflowRequestIdentity identity() { return mock(WorkflowRequestIdentity.class); }
        @Bean WorkflowExecutionService execution() { return mock(WorkflowExecutionService.class); }
        @Bean WorkflowRuntimeQueryService reads() { return mock(WorkflowRuntimeQueryService.class); }
        @Bean Transactions transactions() { return new Transactions(); }
    }

    private static final class Transactions extends AbstractPlatformTransactionManager {
        private final List<String> events = new ArrayList<>();

        @Override protected Object doGetTransaction() { return new Object(); }

        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            events.add(definition.isReadOnly() ? "read" : "write");
        }

        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }

        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
